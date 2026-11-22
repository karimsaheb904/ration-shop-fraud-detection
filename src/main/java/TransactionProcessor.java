import model.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Scanner;

/**
 * TransactionProcessor provides a software console interface for dealers/testers
 * to execute ration distribution transactions, enter manual scale readings,
 * and verify the 28-day lockout mechanism.
 */
public class TransactionProcessor {

    public static void main(String[] args) {
        TransactionService service = new TransactionService();

        // If "--test" flag is passed or no args, show test execution options
        if (args.length > 0 && args[0].equalsIgnoreCase("--test")) {
            runAutomatedAudit(service);
            return;
        }

        Scanner sc = new Scanner(System.in);
        System.out.println("==================================================");
        System.out.println("   RATION TRANSACTION & WEIGHING VERIFICATION");
        System.out.println("==================================================");
        System.out.println("1. Process live distribution transaction");
        System.out.println("2. Run automated 28-day block & transaction verification");
        System.out.print("Select an option (1 or 2): ");

        String choice = sc.nextLine().trim();
        if ("2".equals(choice)) {
            runAutomatedAudit(service);
            return;
        }

        System.out.print("\nEnter Family ID (e.g., f016, F001): ");
        String familyId = sc.nextLine().trim();

        System.out.print("Enter actual scale weight reading in kg (e.g., 12.0): ");
        double actualWeight = Double.parseDouble(sc.nextLine().trim());

        try {
            TransactionService.DistributionResult result = service.processDistribution(familyId, actualWeight, LocalDate.now());
            if (result.success) {
                service.printReceipt(result);
            } else {
                System.out.println("\n[!] TRANSACTION FAILED: " + result.message);
            }
        } catch (SQLException e) {
            System.out.println("\n[!] DATABASE ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the mandatory audit tests:
     * 1. Successful transaction for an eligible family (e.g., f016).
     * 2. Rejection of a second transaction attempted during the 28-day block window.
     */
    public static void runAutomatedAudit(TransactionService service) {
        System.out.println("\n=======================================================");
        System.out.println("       RUNNING TRANSACTION & 28-DAY BLOCK AUDIT        ");
        System.out.println("=======================================================");

        String targetFamilyId = "f016";
        LocalDate today = LocalDate.now();

        // --- STEP 1: First Transaction ---
        System.out.println("\n[STEP 1] Attempting first distribution for Family: " + targetFamilyId);
        System.out.println("Date: " + today + " | Entitlement: 3 active members x 4.0 kg = 12.00 kg");
        System.out.println("Simulated Scale Reading: 12.00 kg");

        try {
            TransactionService.DistributionResult result1 =
                    service.processDistribution(targetFamilyId, 12.00, today);

            if (result1.success) {
                System.out.println("SUCCESS: First distribution completed and saved to SQLite.");
                service.printReceipt(result1);
            } else {
                System.out.println("RESULT: " + result1.message);
            }

            // --- STEP 2: Second Transaction during 28-Day Lockout ---
            System.out.println("[STEP 2] Attempting second distribution for the same family during 28-day lockout...");
            System.out.println("Attempt Date: " + today.plusDays(5) + " (Within 28-day block window)");

            TransactionService.DistributionResult result2 =
                    service.processDistribution(targetFamilyId, 12.00, today.plusDays(5));

            if (!result2.success) {
                System.out.println("\nSUCCESSFUL BLOCK: Second transaction was correctly BLOCKED!");
                System.out.println("Enforcement Reason: " + result2.message);
            } else {
                System.out.println("\nFAILURE: Second transaction should have been blocked!");
            }

        } catch (SQLException e) {
            System.out.println("DATABASE ERROR during audit: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=======================================================");
        System.out.println("                 AUDIT RUN COMPLETE                    ");
        System.out.println("=======================================================");
    }
}

