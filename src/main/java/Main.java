import model.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

/**
 * Main
 * Original console demo and verification entry point for Ration Distribution Fraud Detection.
 * Uses official model classes from package model.*.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("RATION DISTRIBUTION FRAUD DETECTOR");

        FamilyDAO familyDAO = new FamilyDAO();
        TransactionDAO transactionDAO = new TransactionDAO();

        RationShop shop;
        Map<String, Family> familyMap;
        ArrayList<Transaction> transactions;

        try {
            shop = familyDAO.loadShopWithFamilies("RS001");
            familyMap = familyDAO.getAllFamiliesMap();
            transactions = transactionDAO.getAllTransactions(familyMap, shop);
        } catch (SQLException e) {
            System.out.println("DATABASE ERROR: Failed to load data from SQLite: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        final double ricePerActivePerson = 4.0; // Configured entitlement per active person (kg)
        final double quantityToleranceKg = 0.25; // Scale tolerance (kg)

        System.out.println("Families loaded from SQLite: " + shop.getFamilyCount());
        System.out.printf("Expected shop allocation (configured rate): %.2f kg%n",
                shop.calculateMonthlyRice(ricePerActivePerson));

        System.out.println("Transactions loaded from SQLite: " + transactions.size());
        System.out.println("\nTRANSACTION HISTORY");
        for (Transaction transaction : transactions) {
            System.out.printf("%s | %s | Card: %s | %.2f kg | %s%n",
                    transaction.transactionId, transaction.family.familyId,
                    transaction.family.rationCardNumber, transaction.quantity, transaction.date);
        }

        FraudDetector detector = new FraudDetector(ricePerActivePerson, quantityToleranceKg);
        System.out.println("\nBASIC FAMILY CHECKS");
        for (Family family : shop.families) {
            System.out.printf("%s | Members: %d | Active: %d | Active family: %s | Demo eligibility: %s%n",
                    family.familyId, family.getMemberCount(), family.getActiveMemberCount(),
                    family.isActive(), detector.checkEligibility(family));
        }

        detector.generateFraudReport(transactions);

        // Update calculated risk scores in SQLite without modifying or deleting families/beneficiaries
        try {
            transactionDAO.updateRiskScores(transactions, detector);
            System.out.println("\nDATABASE: Transaction risk scores synchronized with SQLite.");
        } catch (SQLException e) {
            System.out.println("\nDATABASE RISK SCORE UPDATE FAILED: " + e.getMessage());
            e.printStackTrace();
        }

        if (!transactions.isEmpty()) {
            transactions.get(0).printBill();
        }
        System.out.println("\nProgram completed successfully.");
    }
}
