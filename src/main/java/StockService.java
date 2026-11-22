import java.sql.SQLException;
import java.time.LocalDate;

/**
 * StockService
 * Contains the business logic for managing Rice inventory in a ration shop.
 * Calculates totals, determines stock status, detects inventory fraud, and prints reports.
 */
public class StockService {

    private final StockDAO stockDAO;

    // Safety threshold: 20 kg
    public static final double LOW_STOCK_THRESHOLD = 20.0;

    public StockService() {
        this.stockDAO = new StockDAO();
    }

    /**
     * DTO (Data Transfer Object) class that holds all calculated stock report numbers.
     */
    public static class StockReport {
        public final String shopId;
        public final double totalReceived;
        public final double totalDistributed;
        public final double remainingStock;
        public final String status;
        public final String alertMessage;

        public StockReport(String shopId, double totalReceived, double totalDistributed,
                           double remainingStock, String status, String alertMessage) {
            this.shopId = shopId;
            this.totalReceived = totalReceived;
            this.totalDistributed = totalDistributed;
            this.remainingStock = remainingStock;
            this.status = status;
            this.alertMessage = alertMessage;
        }
    }

    /**
     * Records a new shipment of Rice received by a shop.
     * Validates that the received quantity is strictly greater than zero.
     */
    public void recordStockReceipt(String shopId, double quantityReceived, LocalDate date) throws SQLException {
        if (quantityReceived <= 0) {
            throw new IllegalArgumentException("Invalid quantity: Stock received must be greater than zero.");
        }

        if (date == null) {
            date = LocalDate.now();
        }

        stockDAO.addStockReceipt(shopId, quantityReceived, date);
    }

    /**
     * Helper method to calculate the remaining stock for a shop:
     * Remaining = Total Received - Total Distributed
     */
    public double calculateRemainingStock(String shopId) throws SQLException {
        double received = stockDAO.getTotalStockReceived(shopId);
        double distributed = stockDAO.getTotalStockDistributed(shopId);
        return received - distributed;
    }

    /**
     * Determines the stock health status based on simple if/else conditions:
     * - NORMAL: Remaining stock is above 20 kg.
     * - LOW STOCK: Remaining stock is between 0 and 20 kg.
     * - CRITICAL DEFICIT ALERT: Remaining stock is below 0 kg (fraud / inventory leakage).
     */
    public String evaluateStockStatus(double remainingStock) {
        if (remainingStock > LOW_STOCK_THRESHOLD) {
            return "NORMAL";
        } else if (remainingStock >= 0.0) {
            return "LOW STOCK";
        } else {
            return "CRITICAL DEFICIT ALERT";
        }
    }

    /**
     * Generates a complete stock report object with all numbers and status messages.
     */
    public StockReport generateStockReport(String shopId) throws SQLException {
        double totalReceived = stockDAO.getTotalStockReceived(shopId);
        double totalDistributed = stockDAO.getTotalStockDistributed(shopId);
        double remainingStock = totalReceived - totalDistributed;

        String status = evaluateStockStatus(remainingStock);
        String alertMessage;

        if (status.equals("CRITICAL DEFICIT ALERT")) {
            double deficit = Math.abs(remainingStock);
            alertMessage = String.format("SUSPICIOUS LEAKAGE: Distributed quantity exceeds received stock by %.2f kg!", deficit);
        } else if (status.equals("LOW STOCK")) {
            alertMessage = "WARNING: Stock is running low (<= 20 kg). Please request new allocation.";
        } else {
            alertMessage = "Inventory level is healthy.";
        }

        return new StockReport(shopId, totalReceived, totalDistributed, remainingStock, status, alertMessage);
    }

    /**
     * Prints a clean, beginner-friendly stock report in the console.
     */
    public void printStockReport(String shopId) throws SQLException {
        StockReport report = generateStockReport(shopId);

        System.out.println("\n=======================================================");
        System.out.println("               RATION SHOP STOCK REPORT                ");
        System.out.println("=======================================================");
        System.out.printf(" Shop ID           : %s%n", report.shopId);
        System.out.println(" Item Tracked      : Rice");
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Total Received    : %.2f kg%n", report.totalReceived);
        System.out.printf(" Total Distributed : %.2f kg (from transaction records)%n", report.totalDistributed);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Remaining Stock   : %.2f kg%n", report.remainingStock);
        System.out.printf(" Stock Status      : %s%n", report.status);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Audit Message     : %s%n", report.alertMessage);
        System.out.println("=======================================================\n");
    }
}

