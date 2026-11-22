import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import model.*;


import java.time.LocalDate;
import java.util.*;

/**
 * WebServer
 * High-Performance Javalin REST API & Static Asset Server for RationGuard.
 * Runs on port 8080 and serves the Executive GovTech / FinTech Dashboard from /public.
 * Supports Multi-Shop Operations and Rice + Jowar Split Distribution APIs.
 */
public class WebServer {

    public static final int PORT = 8080;

    // Thread-safe Gson with custom ISO-8601 LocalDate serialization
    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(src != null ? src.toString() : ""))
            .setPrettyPrinting()
            .create();

    public static void main(String[] args) {
        try {
            DatabaseManager.initializeDatabase();
            DatabaseManager.reevaluateAndSyncTransactionScores();
            System.out.println("[RationGuard] Verified startup state & re-evaluated transaction scores in ration.db.");
        } catch (Exception e) {
            System.err.println("[RationGuard] Warning on startup: " + e.getMessage());
        }

        Javalin app = Javalin.create(config -> {
            // Serve static assets from classpath /public
            config.staticFiles.add("/public", Location.CLASSPATH);

            // Enable CORS for modern web clients
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
        });

        // Cache-Control headers for all API endpoints to guarantee fresh database reads on refresh
        app.before("/api/*", ctx -> {
            ctx.header("Cache-Control", "no-cache, no-store, must-revalidate");
            ctx.header("Pragma", "no-cache");
            ctx.header("Expires", "0");
        });

        // Global Exception Handlers
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(HttpStatus.BAD_REQUEST)
               .contentType("application/json")
               .result(errorJson(e.getMessage()));
        });

        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
               .contentType("application/json")
               .result(errorJson(e.getMessage()));
        });

        // =====================================================================
        // REST API ENDPOINTS
        // =====================================================================

        // 0. Ration Shops Directory (/api/shops)
        app.get("/api/shops", ctx -> {
            try {
                List<RationShop> shops = DatabaseManager.fetchAllShops();
                ctx.contentType("application/json").result(gson.toJson(shops));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        // 1. Executive Dashboard KPI & Analytics
        app.get("/api/dashboard", ctx -> {
            try {
                String shopId = ctx.queryParam("shopId");
                if (shopId == null || shopId.trim().isEmpty()) shopId = "ALL";
                Map<String, Object> stats = DatabaseManager.fetchDashboardStats(shopId);
                ctx.contentType("application/json").result(gson.toJson(stats));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        // 2. Beneficiaries & Card Management (/api/families)
        app.get("/api/families", ctx -> {
            try {
                String shopId = ctx.queryParam("shopId");
                if (shopId == null || shopId.trim().isEmpty()) shopId = "ALL";
                List<Family> families = DatabaseManager.fetchFamilies(shopId);
                ctx.contentType("application/json").result(gson.toJson(families));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/families", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                String cardNumber = (String) body.get("cardNumber");
                String shopId = (String) body.getOrDefault("shopId", "RS001");

                List<?> membersRaw = (List<?>) body.get("members");
                List<Person> members = new ArrayList<>();
                if (membersRaw != null) {
                    for (Object mObj : membersRaw) {
                        Map<?, ?> mMap = (Map<?, ?>) mObj;
                        String name = (String) mMap.get("name");
                        int age = parseDoubleOrInt(mMap.get("age"));
                        boolean gov = Boolean.TRUE.equals(mMap.get("governmentEmployee"));
                        boolean act = !Boolean.FALSE.equals(mMap.get("active"));
                        members.add(new Person(name, age, gov, act));
                    }
                }

                DatabaseManager.addFamily(familyId, cardNumber, shopId, members);
                ctx.status(HttpStatus.CREATED)
                   .contentType("application/json")
                   .result(successJson("Family " + familyId + " registered successfully with " + members.size() + " member(s)."));
            } catch (IllegalArgumentException e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/families/card", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                String cardNumber = (String) body.get("cardNumber");
                DatabaseManager.updateFamilyCard(familyId, cardNumber);
                ctx.contentType("application/json")
                   .result(successJson("Ration card updated successfully."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/families/member", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                String name = (String) body.get("name");
                int age = parseDoubleOrInt(body.get("age"));
                boolean gov = Boolean.TRUE.equals(body.get("governmentEmployee"));
                boolean act = !Boolean.FALSE.equals(body.get("active"));

                DatabaseManager.addBeneficiary(familyId, name, age, gov, act);
                ctx.status(HttpStatus.CREATED)
                   .contentType("application/json")
                   .result(successJson("Beneficiary added successfully."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.put("/api/families/member", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                int beneficiaryId = parseDoubleOrInt(body.get("beneficiaryId"));
                String name = (String) body.get("name");
                int age = parseDoubleOrInt(body.get("age"));
                boolean gov = Boolean.TRUE.equals(body.get("governmentEmployee"));
                boolean act = !Boolean.FALSE.equals(body.get("active"));

                DatabaseManager.updateBeneficiary(beneficiaryId, name, age, gov, act);
                ctx.contentType("application/json")
                   .result(successJson("Beneficiary updated successfully."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/families/toggle-active", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                int beneficiaryId = parseDoubleOrInt(body.get("beneficiaryId"));
                boolean active = Boolean.TRUE.equals(body.get("active"));

                DatabaseManager.toggleBeneficiaryActive(beneficiaryId, active);
                ctx.contentType("application/json")
                   .result(successJson("Beneficiary active status updated to " + active + "."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/families/toggle-gov", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                int beneficiaryId = parseDoubleOrInt(body.get("beneficiaryId"));
                boolean govEmp = Boolean.TRUE.equals(body.get("governmentEmployee"));

                DatabaseManager.toggleBeneficiaryGov(beneficiaryId, govEmp);
                ctx.contentType("application/json")
                   .result(successJson("Government employee status updated to " + govEmp + "."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/families/block", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                boolean block = Boolean.TRUE.equals(body.get("block"));
                int days = parseDoubleOrInt(body.getOrDefault("days", 28));
                String reason = (String) body.getOrDefault("reason", "Officer manual block (28 days)");

                DatabaseManager.toggleBlock(familyId, block, days, reason);
                ctx.contentType("application/json")
                   .result(successJson("Ration card block status updated for " + familyId + "."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        // 3. Transactions & Live POS Terminal (/api/transactions)
        app.get("/api/transactions", ctx -> {
            try {
                String shopId = ctx.queryParam("shopId");
                if (shopId == null || shopId.trim().isEmpty()) shopId = "ALL";
                List<Transaction> transactions = DatabaseManager.fetchTransactionHistory(shopId);
                ctx.contentType("application/json").result(gson.toJson(transactions));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/transactions/preview", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                double riceQty = parseDouble(body.get("riceQuantity"));
                double jowarQty = parseDouble(body.get("jowarQuantity"));
                if (!body.containsKey("riceQuantity") && !body.containsKey("jowarQuantity") && body.containsKey("quantity")) {
                    riceQty = parseDouble(body.get("quantity"));
                }
                String date = (String) body.get("date");

                Map<String, Object> preview = DatabaseManager.previewTransaction(familyId, riceQty, jowarQty, date);
                ctx.contentType("application/json").result(gson.toJson(preview));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/transactions", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                double riceQty = parseDouble(body.get("riceQuantity"));
                double jowarQty = parseDouble(body.get("jowarQuantity"));
                if (!body.containsKey("riceQuantity") && !body.containsKey("jowarQuantity") && body.containsKey("quantity")) {
                    riceQty = parseDouble(body.get("quantity"));
                }
                String date = (String) body.get("date");

                Map<String, Object> result = DatabaseManager.insertTransaction(familyId, riceQty, jowarQty, date);
                if (Boolean.TRUE.equals(result.get("denied")) || (result.containsKey("riskScore") && ((Number)result.get("riskScore")).intValue() >= 100)) {
                    ctx.status(HttpStatus.FORBIDDEN)
                       .contentType("application/json")
                       .result(gson.toJson(result));
                } else {
                    ctx.status(HttpStatus.OK)
                       .contentType("application/json")
                       .result(gson.toJson(result));
                }
            } catch (DatabaseManager.CardBlockedException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("blocked", true);
                err.put("error", "YOUR CARD IS BLOCKED");
                err.put("message", "Your card is blocked until " + e.blockedUntil + " (28-day monthly cycle active).");
                ctx.status(HttpStatus.FORBIDDEN)
                   .contentType("application/json")
                   .result(gson.toJson(err));
            } catch (IllegalArgumentException e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        // 4. Stock & Inventory Ledger (/api/stock)
        app.get("/api/stock", ctx -> {
            try {
                String shopId = ctx.queryParam("shopId");
                if (shopId == null || shopId.trim().isEmpty()) shopId = "ALL";

                Map<String, Object> summary = DatabaseManager.fetchShopSummary(shopId);
                List<Map<String, Object>> ledger = DatabaseManager.fetchStockLedger(shopId);

                Map<String, Object> response = new LinkedHashMap<>(summary);
                response.put("summary", summary);
                response.put("ledger", ledger);
                response.put("shopBreakdown", summary.get("shopBreakdown"));
                ctx.contentType("application/json").result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/stock", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String shopId = (String) body.getOrDefault("shopId", "RS001");
                String item = (String) body.getOrDefault("itemName", body.getOrDefault("commodity", "Rice"));
                double quantityReceived = parseDouble(body.get("quantityReceived"));
                double quantityDistributed = parseDouble(body.getOrDefault("quantityDistributed", 0.0));
                String date = (String) body.get("recordDate");

                DatabaseManager.addStockRecord(shopId, item, quantityReceived, quantityDistributed, date);
                ctx.status(HttpStatus.CREATED)
                   .contentType("application/json")
                   .result(successJson("Stock record added successfully for " + item + " in shop " + shopId + "."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        // 5. Ration Blocks & Lockouts (/api/blocks)
        app.get("/api/blocks", ctx -> {
            try {
                String shopId = ctx.queryParam("shopId");
                if (shopId == null || shopId.trim().isEmpty()) shopId = "ALL";

                List<Map<String, Object>> blocks = DatabaseManager.fetchBlocks(shopId);
                ctx.contentType("application/json").result(gson.toJson(blocks));
            } catch (Exception e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/blocks/toggle", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                boolean block = Boolean.TRUE.equals(body.get("block"));
                int days = parseDoubleOrInt(body.getOrDefault("days", 28));
                String reason = (String) body.getOrDefault("reason", "Manual Lockout");

                DatabaseManager.toggleBlock(familyId, block, days, reason);
                ctx.contentType("application/json")
                   .result(successJson("Block status updated for family " + familyId + "."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        app.post("/api/blocks/unblock", ctx -> {
            try {
                Map<String, Object> body = parseJsonMap(ctx.body());
                String familyId = (String) body.get("familyId");
                if (familyId == null || familyId.trim().isEmpty()) {
                    familyId = ctx.queryParam("familyId");
                }
                if (familyId == null || familyId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Family ID cannot be blank.");
                }

                DatabaseManager.toggleBlock(familyId, false, 0, "Officer manual unblock");
                ctx.contentType("application/json")
                   .result(successJson("Ration card unblocked successfully for family " + familyId + "."));
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .contentType("application/json")
                   .result(errorJson(e.getMessage()));
            }
        });

        // Start HTTP server on port 8080
        app.start(PORT);

        System.out.println("=======================================================");
        System.out.println("      RATIONGUARD JAVALIN SERVER RUNNING (PORT 8080)   ");
        System.out.println("=======================================================");
        System.out.println(" Dashboard URL : http://localhost:" + PORT);
        System.out.println(" Endpoints     :");
        System.out.println("   - GET  /api/shops");
        System.out.println("   - GET  /api/dashboard?shopId=...");
        System.out.println("   - GET/POST /api/families?shopId=...");
        System.out.println("   - GET/POST /api/transactions?shopId=...");
        System.out.println("   - GET/POST /api/stock?shopId=...");
        System.out.println("   - GET/POST /api/blocks?shopId=...");
        System.out.println("=======================================================");
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            Map<String, Object> map = gson.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed or invalid JSON payload: " + e.getMessage());
        }
    }

    private static int parseDoubleOrInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return (int) Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String errorJson(String message) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("success", false);
        map.put("error", message);
        map.put("message", message);
        return new com.google.gson.Gson().toJson(map);
    }

    private static String successJson(String message) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("success", true);
        map.put("message", message);
        return new com.google.gson.Gson().toJson(map);
    }

    private static String deniedJson(String error, String message, int riskScore) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("success", false);
        map.put("denied", true);
        map.put("riskScore", riskScore);
        map.put("riskLevel", "HIGH");
        map.put("error", error != null ? error : "AUTHORIZATION NOT ALLOWED (SCORE 100/100)");
        map.put("message", message != null ? message : "Fraud risk score is 100/100. Grain authorization is strictly prohibited.");
        return new com.google.gson.Gson().toJson(map);
    }

    private static String deniedJson(String message) {
        return deniedJson("AUTHORIZATION NOT ALLOWED (SCORE 100/100)", message, 100);
    }
}
