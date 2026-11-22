package model;

import java.time.LocalDate;

public class Transaction {
    public String transactionId;
    public Family family;
    public RationShop shop;
    public double quantity; // Combined weight = riceQuantity + jowarQuantity
    public double riceQuantity = 0.0;
    public double jowarQuantity = 0.0;
    public double pricePerKg;
    public double totalAmount;
    public LocalDate date;

    // Enriched fields for API/UI
    public int riskScore = 0;
    public String riskLevel = "NORMAL";
    public double zScore = 0.0;
    public java.util.ArrayList<String> reasons = new java.util.ArrayList<>();

    public Transaction(String transactionId, Family family, RationShop shop,
                       double quantity, double pricePerKg, LocalDate date) {
        this.transactionId = transactionId;
        this.family = family;
        this.shop = shop;
        this.quantity = quantity;
        this.riceQuantity = quantity;
        this.jowarQuantity = 0.0;
        this.pricePerKg = pricePerKg;
        this.totalAmount = quantity * pricePerKg;
        this.date = date;
    }

    public Transaction(String transactionId, Family family, RationShop shop,
                       double riceQuantity, double jowarQuantity, double totalAmount, LocalDate date) {
        this.transactionId = transactionId;
        this.family = family;
        this.shop = shop;
        this.riceQuantity = riceQuantity;
        this.jowarQuantity = jowarQuantity;
        this.quantity = Math.round((riceQuantity + jowarQuantity) * 100.0) / 100.0;
        this.pricePerKg = this.quantity > 0 ? (totalAmount / this.quantity) : 5.0;
        this.totalAmount = totalAmount;
        this.date = date;
    }

    public void printBill() {
        System.out.println("\nSAMPLE BILL");
        System.out.println("Transaction ID : " + transactionId);
        System.out.println("Family ID      : " + (family != null ? family.familyId : "N/A"));
        System.out.println("Ration Card    : " + (family != null ? family.rationCardNumber : "N/A"));
        System.out.printf("Total Amount   : Rs. %.2f%n", totalAmount);
    }
}

