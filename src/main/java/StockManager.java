import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/**
 * StockManager
 * Beginner-friendly interactive console application for Ration Stock Management.
 * Allows dealers and testers to record incoming rice shipments, view current stock
 * balances, detect deficits/fraud, and inspect shipment history.
 */
public class StockManager {

    public static void main(String[] args) {
        StockService stockService = new StockService();
        StockDAO stockDAO = new StockDAO();
        Scanner scanner = new Scanner(System.in);

        boolean running = true;

        System.out.println("=======================================================");
        System.out.println("        RATION DISTRIBUTION STOCK MANAGER              ");
        System.out.println("=======================================================");

        while (running) {
            System.out.println("\n----------------- MAIN MENU -----------------");
            System.out.println("1. Record new Rice stock received");
            System.out.println("2. View current stock report & fraud audit");
            System.out.println("3. View stock shipment history");
            System.out.println("4. Exit");
            System.out.print("Please enter your choice (1-4): ");

            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    handleRecordStock(stockService, scanner);
                    break;

                case "2":
                    handleViewStockReport(stockService, scanner);
                    break;

                case "3":
                    handleViewShipmentHistory(stockDAO, scanner);
                    break;

                case "4":
                    System.out.println("\nExiting Stock Manager. Thank you!");
                    running = false;
                    break;

                default:
                    System.out.println("\n[!] Invalid selection. Please enter a valid number (1, 2, 3, or 4).");
                    break;
            }
        }
    }

    /**
     * Menu Option 1: Records a new shipment of Rice received by a shop.
     * Validates that quantity is a valid positive number.
     */
    private static void handleRecordStock(StockService service, Scanner scanner) {
        System.out.println("\n--- RECORD NEW RICE SHIPMENT ---");

        System.out.print("Enter Ration Shop ID (e.g., RS001): ");
        String shopId = scanner.nextLine().trim();
        if (shopId.isEmpty()) {
            System.out.println("[!] Error: Shop ID cannot be blank.");
            return;
        }

        System.out.print("Enter quantity received in kg (e.g., 500.0): ");
        String qtyInput = scanner.nextLine().trim();

        double quantity;
        try {
            quantity = Double.parseDouble(qtyInput);
        } catch (NumberFormatException e) {
            System.out.println("[!] Error: Please enter a valid numerical quantity.");
            return;
        }

        if (quantity <= 0) {
            System.out.println("[!] Error: Received quantity must be strictly greater than 0 kg.");
            return;
        }

        LocalDate today = LocalDate.now();

        try {
            service.recordStockReceipt(shopId, quantity, today);
            System.out.println("\n[+] SUCCESS: Recorded " + quantity + " kg of Rice for Shop " + shopId + " on " + today + ".");

            // Display the updated stock status immediately
            service.printStockReport(shopId);
        } catch (SQLException e) {
            System.out.println("[!] Database Error while saving stock: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("[!] Validation Error: " + e.getMessage());
        }
    }

    /**
     * Menu Option 2: Displays the stock balance, distributed total,
     * remaining quantity, and health status (NORMAL / LOW STOCK / CRITICAL DEFICIT).
     */
    private static void handleViewStockReport(StockService service, Scanner scanner) {
        System.out.println("\n--- VIEW STOCK REPORT ---");

        System.out.print("Enter Ration Shop ID (e.g., RS001): ");
        String shopId = scanner.nextLine().trim();
        if (shopId.isEmpty()) {
            System.out.println("[!] Error: Shop ID cannot be blank.");
            return;
        }

        try {
            service.printStockReport(shopId);
        } catch (SQLException e) {
            System.out.println("[!] Database Error while fetching stock report: " + e.getMessage());
        }
    }

    /**
     * Menu Option 3: Displays past shipment receipts for the shop.
     */
    private static void handleViewShipmentHistory(StockDAO dao, Scanner scanner) {
        System.out.println("\n--- STOCK SHIPMENT HISTORY ---");

        System.out.print("Enter Ration Shop ID (e.g., RS001): ");
        String shopId = scanner.nextLine().trim();
        if (shopId.isEmpty()) {
            System.out.println("[!] Error: Shop ID cannot be blank.");
            return;
        }

        try {
            List<StockDAO.StockEntry> entries = dao.getAllStockEntries(shopId);

            if (entries.isEmpty()) {
                System.out.println("\nNo stock shipments recorded for Shop " + shopId + " yet.");
                return;
            }

            System.out.println("\n=======================================================");
            System.out.printf("  SHIPMENT LOGS FOR SHOP %s (Item: Rice)%n", shopId);
            System.out.println("=======================================================");
            System.out.printf(" %-8s | %-12s | %-15s%n", "Entry ID", "Date", "Quantity (kg)");
            System.out.println("-------------------------------------------------------");

            for (StockDAO.StockEntry entry : entries) {
                System.out.printf(" #%-7d | %-12s | %.2f kg%n",
                        entry.stockId, entry.recordDate, entry.quantityReceived);
            }
            System.out.println("=======================================================");

        } catch (SQLException e) {
            System.out.println("[!] Database Error while fetching shipment history: " + e.getMessage());
        }
    }
}

