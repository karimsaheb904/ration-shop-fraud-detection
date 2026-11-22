import model.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Map;

/**
 * TransactionService encapsulates business logic for monthly ration entitlements,
 * software-based weighing verification, fraud analysis, and 28-day card lockouts.
 */
public class TransactionService {

    private double ricePerActivePerson;
    private double pricePerKg;
    private double quantityToleranceKg;
    private int blockDays;

    private final FamilyDAO familyDAO;
    private final TransactionDAO transactionDAO;
    private final RationBlockDAO blockDAO;

    public TransactionService() {
        this(4.0, 5.0, 0.25, 28);
    }

    public TransactionService(double ricePerActivePerson, double pricePerKg,
                              double quantityToleranceKg, int blockDays) {
        this.ricePerActivePerson = ricePerActivePerson;
        this.pricePerKg = pricePerKg;
        this.quantityToleranceKg = quantityToleranceKg;
        this.blockDays = blockDays;

        this.familyDAO = new FamilyDAO();
        this.transactionDAO = new TransactionDAO();
        this.blockDAO = new RationBlockDAO();
    }

    // Configurable getters and setters
    public double getRicePerActivePerson() { return ricePerActivePerson; }
    public void setRicePerActivePerson(double rate) { this.ricePerActivePerson = rate; }

    public double getPricePerKg() { return pricePerKg; }
    public void setPricePerKg(double price) { this.pricePerKg = price; }

    public double getQuantityToleranceKg() { return quantityToleranceKg; }
    public void setQuantityToleranceKg(double tol) { this.quantityToleranceKg = tol; }

    public int getBlockDays() { return blockDays; }
    public void setBlockDays(int days) { this.blockDays = days; }

    /**
     * DTO representing the outcome of a transaction attempt.
     */
    public static class DistributionResult {
        public final boolean success;
        public final String message;
        public final Transaction transaction;
        public final FraudAnalysisResult fraudResult;
        public final double entitlementKg;
        public final LocalDate blockedUntil;
        public final boolean cardBlocked;

        public DistributionResult(boolean success, String message, Transaction transaction,
                                  FraudAnalysisResult fraudResult, double entitlementKg,
                                  LocalDate blockedUntil) {
            this(success, message, transaction, fraudResult, entitlementKg, blockedUntil, blockedUntil != null);
        }

        public DistributionResult(boolean success, String message, Transaction transaction,
                                  FraudAnalysisResult fraudResult, double entitlementKg,
                                  LocalDate blockedUntil, boolean cardBlocked) {
            this.success = success;
            this.message = message;
            this.transaction = transaction;
            this.fraudResult = fraudResult;
            this.entitlementKg = entitlementKg;
            this.blockedUntil = blockedUntil;
            this.cardBlocked = cardBlocked;
        }
    }

    /**
     * Processes a monthly distribution with full eligibility, lockout, weighing, and fraud checks.
     */
    public DistributionResult processDistribution(String familyId, double actualScaleWeight, LocalDate txDate) throws SQLException {
        if (txDate == null) txDate = LocalDate.now();

        // 1. Retrieve family information from SQLite
        Map<String, Family> familyMap = familyDAO.getAllFamiliesMap();
        Family family = familyMap.get(familyId);
        if (family == null) {
            return new DistributionResult(false, "Family ID '" + familyId + "' not found in database.", null, null, 0, null);
        }

        // Step 1: Check 28-day active lockout in ration_blocks
        RationBlockDAO.BlockRecord activeBlock = blockDAO.getActiveBlock(familyId, txDate);
        if (activeBlock != null) {
            String msg = String.format("TRANSACTION REJECTED: Card is BLOCKED until %s. Reason: %s",
                    activeBlock.blockedUntil, activeBlock.reason);
            return new DistributionResult(false, msg, null, null, 0, activeBlock.blockedUntil, true);
        }

        RationShop shop = familyDAO.getRationShop("RS001");

        // Step 2: If hasGovernmentEmployee -> insert HIGH (100/100) audit transaction (0.0 kg dispensed), lock 28 days
        if (family.hasGovernmentEmployee()) {
            String txId = transactionDAO.generateNextTransactionId();
            Transaction transaction = new Transaction(txId, family, shop, 0.0, pricePerKg, txDate);

            FraudAnalysisResult fraudResult = new FraudAnalysisResult();
            fraudResult.addReason("INELIGIBLE BENEFICIARY: Government employee recorded in household (Automatic 100/100 High Risk).", 100);

            LocalDate blockedUntil = txDate.plusDays(blockDays);
            String blockReason = "INELIGIBLE BENEFICIARY: Government employee recorded in household (Automatic 100/100 High Risk).";
            transactionDAO.saveTransactionWithBlock(transaction, fraudResult, blockedUntil, blockReason);

            return new DistributionResult(false, "AUTHORIZATION NOT ALLOWED (SCORE 100/100): Fraud risk score is 100/100. Grain authorization is strictly prohibited.",
                    transaction, fraudResult, 0.0, blockedUntil, true);
        }

        // Step 3: If activeMembers == 0 (isGhostBeneficiary) -> insert HIGH (80/100) audit transaction (0.0 kg dispensed), lock 28 days
        int activeCount = family.getActiveMemberCount();
        if (activeCount == 0 || family.isGhostBeneficiary()) {
            String txId = transactionDAO.generateNextTransactionId();
            Transaction transaction = new Transaction(txId, family, shop, 0.0, pricePerKg, txDate);

            FraudAnalysisResult fraudResult = new FraudAnalysisResult();
            fraudResult.addReason("GHOST BENEFICIARY: Zero active family members recorded in household registry.", 80);

            LocalDate blockedUntil = txDate.plusDays(blockDays);
            String blockReason = "GHOST BENEFICIARY: Household has 0 active members recorded in registry (Score: 80/100 High Risk).";
            transactionDAO.saveTransactionWithBlock(transaction, fraudResult, blockedUntil, blockReason);

            return new DistributionResult(false, "TRANSACTION DENIED: GHOST BENEFICIARY - Family has 0 active members (80/100 High Risk). Card locked for 28 days.",
                    transaction, fraudResult, 0.0, blockedUntil, true);
        }

        // Step 4: Normal eligible families
        double entitlementKg = activeCount * ricePerActivePerson;
        if (actualScaleWeight <= 0) {
            return new DistributionResult(false, "At least one grain quantity must be greater than zero kg.", null, null, entitlementKg, null);
        }
        if (actualScaleWeight > (entitlementKg + quantityToleranceKg)) {
            return new DistributionResult(false, String.format("ALLOCATION EXCEEDED: Scale reading %.2f kg exceeds quota %.2f kg.", actualScaleWeight, entitlementKg), null, null, entitlementKg, null);
        }

        // Prevent duplicate monthly claims in the same calendar month
        boolean alreadyClaimed = transactionDAO.hasClaimInMonth(familyId, txDate.getYear(), txDate.getMonthValue());
        if (alreadyClaimed) {
            String msg = String.format("TRANSACTION REJECTED: Ration already claimed for Family %s in %s.",
                    familyId, YearMonth.from(txDate));
            return new DistributionResult(false, msg, null, null, entitlementKg, null);
        }

        String txId = transactionDAO.generateNextTransactionId();
        Transaction transaction = new Transaction(txId, family, shop, actualScaleWeight, pricePerKg, txDate);

        ArrayList<Transaction> history = transactionDAO.getAllTransactions(familyMap, shop);
        FraudDetector detector = new FraudDetector(ricePerActivePerson, quantityToleranceKg);
        double average = detector.calculateAverageQuantity(history);
        double stdDev = detector.calculateStandardDeviation(history, average);
        FraudAnalysisResult fraudResult = detector.analyzeTransaction(transaction, history, average, stdDev);

        if (fraudResult.score >= 100) {
            LocalDate blockedUntil = txDate.plusDays(blockDays);
            String blockReason = "AUTHORIZATION NOT ALLOWED (SCORE 100/100): Fraud risk score is 100/100. Grain authorization is strictly prohibited.";
            transactionDAO.saveTransactionWithBlock(transaction, fraudResult, blockedUntil, blockReason);
            return new DistributionResult(false, "AUTHORIZATION NOT ALLOWED (SCORE 100/100): Fraud risk score is 100/100. Grain authorization is strictly prohibited.",
                    transaction, fraudResult, 0.0, blockedUntil, true);
        }

        // Clean NORMAL Transaction on Authorized Dispense
        fraudResult.score = 0;
        fraudResult.reasons.clear();

        LocalDate blockedUntil = txDate.plusDays(blockDays);
        String blockReason = "Monthly ration distributed (28-day cycle active)";
        transactionDAO.saveTransactionWithBlock(transaction, fraudResult, blockedUntil, blockReason);

        return new DistributionResult(true, "Transaction processed successfully.",
                transaction, fraudResult, entitlementKg, blockedUntil);
    }

    /**
     * Generates a detailed receipt / bill for a completed distribution.
     */
    public void printReceipt(DistributionResult result) {
        if (!result.success || result.transaction == null) {
            System.out.println("\n[!] No bill available: " + result.message);
            return;
        }

        Transaction tx = result.transaction;
        System.out.println("\n=======================================================");
        System.out.println("              GOVERNMENT OF INDIA - PDS               ");
        System.out.println("            RATION DISTRIBUTION CASH BILL             ");
        System.out.println("=======================================================");
        System.out.printf(" Receipt No.      : %s%n", tx.transactionId);
        System.out.printf(" Date of Issue    : %s%n", tx.date);
        System.out.printf(" Fair Price Shop  : %s (%s)%n", tx.shop.shopName, tx.shop.shopId);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Family ID        : %s%n", tx.family.familyId);
        System.out.printf(" Ration Card No.  : %s%n", tx.family.rationCardNumber);
        System.out.printf(" Total Members    : %d%n", tx.family.getMemberCount());
        System.out.printf(" Active Members   : %d%n", tx.family.getActiveMemberCount());
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Monthly Entitlement : %.2f kg (%.2f kg/active member)%n",
                result.entitlementKg, ricePerActivePerson);
        System.out.printf(" Weighed Reading     : %.2f kg (Software Verified)%n", tx.quantity);
        System.out.printf(" Price per Kg        : Rs. %.2f%n", tx.pricePerKg);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" TOTAL AMOUNT DUE    : Rs. %.2f%n", tx.totalAmount);
        System.out.println("-------------------------------------------------------");
        System.out.printf(" Fraud Risk Level    : %s (Score: %d/100)%n",
                result.fraudResult.getRiskLevel(), result.fraudResult.score);
        if (!result.fraudResult.reasons.isEmpty()) {
            System.out.println(" Risk Notes / Flags  :");
            for (String reason : result.fraudResult.reasons) {
                System.out.println("   - " + reason);
            }
        }
        System.out.println("-------------------------------------------------------");
        System.out.printf(" NEXT ELIGIBLE DATE  : %s (28-Day Lockout Active)%n", result.blockedUntil);
        System.out.println("=======================================================\n");
    }
}

