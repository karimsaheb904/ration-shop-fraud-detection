package model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;

public class FraudDetector {
    public final double ricePerActivePerson;
    public final double quantityToleranceKg;
    private final WeighingMachine weighingMachine = new WeighingMachine();

    public FraudDetector(double ricePerActivePerson, double quantityToleranceKg) {
        this.ricePerActivePerson = ricePerActivePerson;
        this.quantityToleranceKg = quantityToleranceKg;
    }

    public boolean isDuplicateClaim(Transaction transaction, ArrayList<Transaction> history) {
        for (int i = 0; i < history.size(); i++) {
            Transaction old = history.get(i);
            if (old == transaction) continue;
            if (old.transactionId != null && old.transactionId.equalsIgnoreCase(transaction.transactionId)) continue;

            boolean sameCard = (old.family != null && transaction.family != null) &&
                    (old.family.rationCardNumber.equalsIgnoreCase(transaction.family.rationCardNumber)
                    || old.family.familyId.equalsIgnoreCase(transaction.family.familyId));

            if (!sameCard) continue;

            boolean sameMonth = YearMonth.from(old.date).equals(YearMonth.from(transaction.date));
            if (!sameMonth) continue;

            // Fix: Compare chronological dates and list order (database row order) rather than lexicographical string comparison
            boolean oldComesFirst = old.date.isBefore(transaction.date)
                    || (old.date.equals(transaction.date) && isEarlierRow(old, transaction, history));

            if (oldComesFirst) return true;
        }
        return false;
    }

    private boolean isEarlierRow(Transaction oldTx, Transaction newTx, ArrayList<Transaction> history) {
        int oldIndex = history.indexOf(oldTx);
        int newIndex = history.indexOf(newTx);
        if (oldIndex != -1 && newIndex != -1) {
            return oldIndex < newIndex;
        }
        int oldNum = extractNumericId(oldTx.transactionId);
        int newNum = extractNumericId(newTx.transactionId);
        if (oldNum > 0 && newNum > 0) {
            return oldNum < newNum;
        }
        return false;
    }

    private int extractNumericId(String id) {
        if (id == null) return -1;
        try {
            String digits = id.replaceAll("\\D+", "");
            return digits.isEmpty() ? -1 : Integer.parseInt(digits);
        } catch (Exception e) {
            return -1;
        }
    }

    public double expectedQuantity(Family family) {
        if (family == null) return 0.0;
        return family.getActiveMemberCount() * ricePerActivePerson;
    }

    public double calculateAverageQuantity(ArrayList<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;
        double total = 0;
        for (Transaction transaction : transactions) total += transaction.quantity;
        return total / transactions.size();
    }

    public double calculateStandardDeviation(ArrayList<Transaction> transactions, double average) {
        if (transactions.isEmpty()) return 0;
        double sum = 0;
        for (Transaction transaction : transactions) {
            double difference = transaction.quantity - average;
            sum += difference * difference;
        }
        return Math.sqrt(sum / transactions.size());
    }

    public FraudAnalysisResult analyzeTransaction(Transaction transaction,
                                                  ArrayList<Transaction> history,
                                                  double averageQuantity,
                                                  double standardDeviation,
                                                  boolean isCardBlockedActive,
                                                  String blockReason) {
        FraudAnalysisResult result = new FraudAnalysisResult();
        Family family = transaction.family;
        result.isCardBlocked = isCardBlockedActive;

        // 1. Check active card lockout in ration_blocks
        if (isCardBlockedActive) {
            result.addReason("ACTIVE LOCKOUT VIOLATION: Ration card is locked (" +
                    (blockReason != null && !blockReason.isEmpty() ? blockReason : "28-day mandatory cycle") + ").", 100);
        }

        // 2. Duplicate claim in same calendar month
        if (isDuplicateClaim(transaction, history)) {
            result.addReason("DUPLICATE CLAIM: Same ration card already recorded a claim in " + YearMonth.from(transaction.date) + ".", 40);
        }

        // 3. Ghost Beneficiary
        if (family == null || family.isGhostBeneficiary()) {
            result.addReason("GHOST BENEFICIARY: Zero active family members recorded in household registry.", 80);
        } else if (family.hasInactiveMember()) {
            result.addReason("REVIEW: Family contains inactive beneficiary member(s); physical audit recommended.", 10);
        }

        // 4. Government Employee
        if (family != null && family.hasGovernmentEmployee()) {
            result.addReason("INELIGIBLE BENEFICIARY: Government employee recorded in household (Automatic 100/100 High Risk).", 100);
        }

        // 5. Quantity mismatch
        double expected = expectedQuantity(family);
        if (!weighingMachine.checkWeight(expected, transaction.quantity, quantityToleranceKg)) {
            result.addReason(String.format(
                    "QUANTITY MISMATCH: Expected %.2f kg for %d active member(s), recorded %.2f kg.",
                    expected, (family != null ? family.getActiveMemberCount() : 0), transaction.quantity), 30);
        }

        // 6. Statistical Outlier (Z-score)
        if (standardDeviation > 0) {
            double zScore = Math.abs(transaction.quantity - averageQuantity) / standardDeviation;
            result.zScore = Math.round(zScore * 100.0) / 100.0;
            if (zScore >= 2.0) {
                result.addReason(String.format(
                        "STATISTICAL OUTLIER: Quantity %.2f kg vs baseline average %.2f kg (|Z|=%.2f >= 2.0).",
                        transaction.quantity, averageQuantity, zScore), 20);
            }
        } else if (!history.isEmpty() && Math.abs(transaction.quantity - averageQuantity) > quantityToleranceKg) {
            result.addReason("STATISTICAL REVIEW: Baseline has zero variance; transaction differs from average.", 10);
        }

        return result;
    }

    public FraudAnalysisResult analyzeTransaction(Transaction transaction,
                                                  ArrayList<Transaction> history,
                                                  double averageQuantity,
                                                  double standardDeviation) {
        return analyzeTransaction(transaction, history, averageQuantity, standardDeviation, false, null);
    }

    public boolean checkEligibility(Family family) {
        if (family == null) return false;
        return family.isActive() && !family.hasGovernmentEmployee();
    }

    public void printAnalysis(Transaction transaction, FraudAnalysisResult result) {
        System.out.println("\nFRAUD ANALYSIS");
        System.out.println("Transaction ID : " + transaction.transactionId);
        System.out.println("Family ID      : " + (transaction.family != null ? transaction.family.familyId : "N/A"));
        System.out.println("Ration Card    : " + (transaction.family != null ? transaction.family.rationCardNumber : "N/A"));
        System.out.printf("Quantity       : %.2f kg%n", transaction.quantity);
        System.out.println("Risk Score     : " + result.score + "/100");
        System.out.println("Risk Level     : " + result.getRiskLevel());
        if (result.reasons.isEmpty()) {
            System.out.println("Status         : NORMAL - no configured rule triggered.");
        } else {
            System.out.println("Status         : FLAGGED FOR REVIEW");
            for (String reason : result.reasons) System.out.println(" - " + reason);
        }
    }

    public void generateFraudReport(ArrayList<Transaction> transactions) {
        if (transactions.isEmpty()) {
            System.out.println("No transactions available for analysis.");
            return;
        }

        double average = calculateAverageQuantity(transactions);
        double standardDeviation = calculateStandardDeviation(transactions, average);
        System.out.println("\nSTATISTICAL ANOMALY REPORT");
        System.out.println("Total transactions : " + transactions.size());
        System.out.printf("Average quantity   : %.2f kg%n", average);
        System.out.printf("Std. deviation     : %.2f kg%n", standardDeviation);
        System.out.println("Statistical rule   : |Z-score| >= 2 (secondary signal)");
        System.out.println("Quantity rule      : active members x configured kg/person, with tolerance");

        int flaggedCount = 0;
        for (Transaction transaction : transactions) {
            FraudAnalysisResult result = analyzeTransaction(transaction, transactions, average, standardDeviation);
            printAnalysis(transaction, result);
            if (result.score > 0) flaggedCount++;
        }

        System.out.println("\nFINAL SUMMARY");
        System.out.println("Total transactions : " + transactions.size());
        System.out.println("Flagged for review : " + flaggedCount);
        System.out.println("No rule triggered  : " + (transactions.size() - flaggedCount));
        System.out.println("Note: A flag indicates a need for verification, not proven fraud.");
    }
}

