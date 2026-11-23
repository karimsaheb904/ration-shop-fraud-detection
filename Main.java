import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

class Person {
    String name;
    int age;
    boolean governmentEmployee;
    boolean active;

    Person(String name, int age, boolean governmentEmployee, boolean active) {
        this.name = name;
        this.age = age;
        this.governmentEmployee = governmentEmployee;
        this.active = active;
    }
}

class Family {
    String familyId;
    String rationCardNumber;
    ArrayList<Person> members = new ArrayList<>();

    Family(String familyId, String rationCardNumber) {
        this.familyId = familyId;
        this.rationCardNumber = rationCardNumber;
    }

    void addMember(Person person) { members.add(person); }
    int getMemberCount() { return members.size(); }

    int getActiveMemberCount() {
        int count = 0;
        for (Person person : members) if (person.active) count++;
        return count;
    }

    boolean hasGovernmentEmployee() {
        for (Person person : members) if (person.governmentEmployee) return true;
        return false;
    }

    // A family is active when at least one member is active.
    boolean isActive() { return getActiveMemberCount() > 0; }

    boolean hasInactiveMember() {
        for (Person person : members) if (!person.active) return true;
        return false;
    }

    boolean isGhostBeneficiary() {
        return !members.isEmpty() && getActiveMemberCount() == 0;
    }
}

class RationCard {
    String cardNumber;
    boolean blocked;
    long blockedUntil;

    RationCard(String cardNumber) {
        this.cardNumber = cardNumber;
        this.blocked = false;
        this.blockedUntil = 0;
    }

    void blockCard() {
        blocked = true;
        blockedUntil = System.currentTimeMillis() + (28L * 24 * 60 * 60 * 1000);
        System.out.println("Ration card marked blocked for 28 days.");
    }

    boolean canReceiveRation() {
        if (blocked && System.currentTimeMillis() < blockedUntil) return false;
        blocked = false;
        return true;
    }
}

class RationShop {
    String shopId;
    String shopName;
    ArrayList<Family> families = new ArrayList<>();

    RationShop(String shopId, String shopName) {
        this.shopId = shopId;
        this.shopName = shopName;
    }

    void addFamily(Family family) { families.add(family); }
    int getFamilyCount() { return families.size(); }

    double calculateMonthlyRice(double ricePerActivePerson) {
        double total = 0;
        for (Family family : families) total += family.getActiveMemberCount() * ricePerActivePerson;
        return total;
    }
}

class WeighingMachine {
    // Small scale differences are allowed; this is a demo tolerance only.
    boolean checkWeight(double expectedWeight, double actualWeight, double toleranceKg) {
        return Math.abs(actualWeight - expectedWeight) <= toleranceKg;
    }
}

class Transaction {
    String transactionId;
    Family family;
    RationShop shop;
    double quantity;
    double pricePerKg;
    double totalAmount;
    LocalDate date;

    Transaction(String transactionId, Family family, RationShop shop,
                double quantity, double pricePerKg, LocalDate date) {
        this.transactionId = transactionId;
        this.family = family;
        this.shop = shop;
        this.quantity = quantity;
        this.pricePerKg = pricePerKg;
        this.totalAmount = quantity * pricePerKg;
        this.date = date;
    }

    Transaction(String transactionId, Family family, RationShop shop,
                double quantity, double pricePerKg) {
        this(transactionId, family, shop, quantity, pricePerKg, LocalDate.now());
    }

    void printBill() {
        System.out.println("\nRATION BILL");
        System.out.println("Transaction ID : " + transactionId);
        System.out.println("Date           : " + date);
        System.out.println("Family ID      : " + family.familyId);
        System.out.println("Ration Card    : " + family.rationCardNumber);
        System.out.println("Ration Shop    : " + shop.shopName);
        System.out.println("Active Members : " + family.getActiveMemberCount());
        System.out.printf("Rice Quantity  : %.2f kg%n", quantity);
        System.out.printf("Price per Kg   : Rs.%.2f%n", pricePerKg);
        System.out.printf("Total Amount   : Rs.%.2f%n", totalAmount);
    }
}

class FraudAnalysisResult {
    int score;
    ArrayList<String> reasons = new ArrayList<>();

    void addReason(String reason, int points) {
        reasons.add(reason);
        score = Math.min(100, score + points);
    }

    String getRiskLevel() {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        if (score > 0) return "LOW";
        return "NORMAL";
    }
}

class FraudDetector {
    private final double ricePerActivePerson;
    private final double quantityToleranceKg;
    private final WeighingMachine weighingMachine = new WeighingMachine();

    FraudDetector(double ricePerActivePerson, double quantityToleranceKg) {
        this.ricePerActivePerson = ricePerActivePerson;
        this.quantityToleranceKg = quantityToleranceKg;
    }

    // Demonstration rule only. Real eligibility must follow applicable PDS rules.
    boolean checkEligibility(Family family) {
        return family.isActive() && !family.hasGovernmentEmployee();
    }

    boolean isDuplicateClaim(Transaction transaction, ArrayList<Transaction> history) {
        for (Transaction old : history) {
            if (old == transaction) continue;
            if (old.transactionId.equals(transaction.transactionId)) continue;

            boolean sameCard = old.family.rationCardNumber.equals(transaction.family.rationCardNumber);
            boolean sameMonth = YearMonth.from(old.date).equals(YearMonth.from(transaction.date));
            boolean oldComesFirst = old.date.isBefore(transaction.date)
                    || (old.date.equals(transaction.date)
                    && old.transactionId.compareTo(transaction.transactionId) < 0);
            if (sameCard && sameMonth && oldComesFirst) return true;
        }
        return false;
    }

    double expectedQuantity(Family family) {
        return family.getActiveMemberCount() * ricePerActivePerson;
    }

    double calculateAverageQuantity(ArrayList<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;
        double total = 0;
        for (Transaction transaction : transactions) total += transaction.quantity;
        return total / transactions.size();
    }

    double calculateStandardDeviation(ArrayList<Transaction> transactions, double average) {
        if (transactions.isEmpty()) return 0;
        double sum = 0;
        for (Transaction transaction : transactions) {
            double difference = transaction.quantity - average;
            sum += difference * difference;
        }
        return Math.sqrt(sum / transactions.size());
    }

    FraudAnalysisResult analyzeTransaction(Transaction transaction,
            ArrayList<Transaction> history, double averageQuantity, double standardDeviation) {
        FraudAnalysisResult result = new FraudAnalysisResult();
        Family family = transaction.family;

        if (isDuplicateClaim(transaction, history)) {
            result.addReason("DUPLICATE CLAIM: Same ration card already has a claim this month.", 40);
        }
        if (family.isGhostBeneficiary()) {
            result.addReason("GHOST BENEFICIARY: No active family members are recorded.", 50);
        } else if (family.hasInactiveMember()) {
            // An inactive member is a review signal, not automatic proof of fraud.
            result.addReason("REVIEW: Family contains an inactive member; verify current records.", 10);
        }
        if (family.hasGovernmentEmployee()) {
            result.addReason("ELIGIBILITY REVIEW: Government employment recorded; verify applicable PDS rules.", 20);
        }

        double expected = expectedQuantity(family);
        if (!weighingMachine.checkWeight(expected, transaction.quantity, quantityToleranceKg)) {
            result.addReason(String.format(
                    "QUANTITY MISMATCH: Expected %.2f kg based on active members, recorded %.2f kg.",
                    expected, transaction.quantity), 30);
        }

        // Statistical signal. With a tiny/demo dataset this is only a secondary indicator.
        if (standardDeviation > 0) {
            double zScore = Math.abs(transaction.quantity - averageQuantity) / standardDeviation;
            if (zScore >= 2.0) {
                result.addReason(String.format(
                        "STATISTICAL OUTLIER: Quantity %.2f kg; average %.2f kg; |Z|=%.2f.",
                        transaction.quantity, averageQuantity, zScore), 20);
            }
        } else if (!history.isEmpty() && Math.abs(transaction.quantity - averageQuantity) > quantityToleranceKg) {
            result.addReason("STATISTICAL REVIEW: No quantity variation in baseline; this record differs from the average.", 10);
        }

        return result;
    }

    void printAnalysis(Transaction transaction, FraudAnalysisResult result) {
        System.out.println("\nFRAUD ANALYSIS");
        System.out.println("Transaction ID : " + transaction.transactionId);
        System.out.println("Family ID      : " + transaction.family.familyId);
        System.out.println("Ration Card    : " + transaction.family.rationCardNumber);
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

    void generateFraudReport(ArrayList<Transaction> transactions) {
        if (transactions.isEmpty()) {
            System.out.println("No transactions available for analysis.");
            return;
        }

        // Validate unique transaction IDs before running analysis.
        Set<String> transactionIds = new HashSet<>();
        for (Transaction transaction : transactions) {
            if (!transactionIds.add(transaction.transactionId)) {
                System.out.println("DATA ERROR: Duplicate transaction ID: " + transaction.transactionId);
            }
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

public class Main {
    public static void main(String[] args) {
        System.out.println("RATION DISTRIBUTION FRAUD DETECTOR");
        RationShop shop = new RationShop("RS001", "Main Ration Shop");

        // Register 15 sample families.
        Family family1 = new Family("F001", "RC1001");
        family1.addMember(new Person("Karim", 20, false, true));
        family1.addMember(new Person("Person2", 45, false, true));
        family1.addMember(new Person("Person3", 40, false, true));
        family1.addMember(new Person("Person4", 15, false, true));
        shop.addFamily(family1);

        Family family2 = new Family("F002", "RC1002");
        family2.addMember(new Person("Ravi", 35, false, true));
        family2.addMember(new Person("Person5", 30, false, true));
        family2.addMember(new Person("Person6", 12, false, true));
        shop.addFamily(family2);

        Family family3 = new Family("F003", "RC1003");
        family3.addMember(new Person("Old Beneficiary", 70, false, false));
        family3.addMember(new Person("Inactive Member", 65, false, false));
        shop.addFamily(family3);

        Family family4 = new Family("F004", "RC1004");
        family4.addMember(new Person("Sita", 32, false, true));
        family4.addMember(new Person("Person7", 30, false, true));
        family4.addMember(new Person("Person8", 10, false, true));
        shop.addFamily(family4);

        Family family5 = new Family("F005", "RC1005");
        family5.addMember(new Person("Employee", 40, true, true));
        family5.addMember(new Person("Person9", 35, false, true));
        shop.addFamily(family5);

        Family family6 = new Family("F006", "RC1006");
        family6.addMember(new Person("Ramesh", 35, false, true));
        family6.addMember(new Person("Lakshmi", 32, false, true));
        family6.addMember(new Person("Child1", 10, false, true));
        shop.addFamily(family6);

        Family family7 = new Family("F007", "RC1007");
        family7.addMember(new Person("Suresh", 40, false, true));
        family7.addMember(new Person("Wife7", 36, false, true));
        shop.addFamily(family7);

        Family family8 = new Family("F008", "RC1008");
        family8.addMember(new Person("Anitha", 28, false, true));
        family8.addMember(new Person("Member8A", 30, false, true));
        family8.addMember(new Person("Member8B", 8, false, true));
        family8.addMember(new Person("Member8C", 5, false, true));
        shop.addFamily(family8);

        Family family9 = new Family("F009", "RC1009");
        family9.addMember(new Person("Kumar", 45, false, true));
        shop.addFamily(family9);

        Family family10 = new Family("F010", "RC1010");
        family10.addMember(new Person("Rani", 38, false, true));
        family10.addMember(new Person("Member10A", 35, false, true));
        shop.addFamily(family10);

        Family family11 = new Family("F011", "RC1011");
        family11.addMember(new Person("Mohan", 42, false, true));
        family11.addMember(new Person("Member11A", 39, false, true));
        family11.addMember(new Person("Member11B", 15, false, true));
        shop.addFamily(family11);

        Family family12 = new Family("F012", "RC1012");
        family12.addMember(new Person("Geetha", 33, false, true));
        family12.addMember(new Person("Member12A", 30, false, true));
        family12.addMember(new Person("Member12B", 12, false, true));
        shop.addFamily(family12);

        Family family13 = new Family("F013", "RC1013");
        family13.addMember(new Person("Raju", 50, false, true));
        family13.addMember(new Person("Member13A", 45, false, true));
        shop.addFamily(family13);

        Family family14 = new Family("F014", "RC1014");
        family14.addMember(new Person("Meena", 29, false, true));
        family14.addMember(new Person("Member14A", 31, false, true));
        family14.addMember(new Person("Member14B", 7, false, true));
        shop.addFamily(family14);

        Family family15 = new Family("F015", "RC1015");
        family15.addMember(new Person("Prasad", 40, false, true));
        family15.addMember(new Person("Member15A", 37, false, true));
        family15.addMember(new Person("Member15B", 14, false, true));
        family15.addMember(new Person("Member15C", 9, false, true));
        shop.addFamily(family15);

        final double ricePerActivePerson = 4.0; // Demonstration value; configure to applicable entitlement.
        final double quantityToleranceKg = 0.25; // Demonstration scale tolerance.
        final double pricePerKg = 5.0;
        LocalDate currentMonth = LocalDate.of(2026, 10, 2);

        System.out.println("Families registered: " + shop.getFamilyCount());
        System.out.printf("Expected shop allocation (demo rate): %.2f kg%n",
                shop.calculateMonthlyRice(ricePerActivePerson));

        ArrayList<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("T001", family1, shop, 16.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T002", family1, shop, 16.0, pricePerKg, currentMonth.plusDays(2))); // duplicate claim
        transactions.add(new Transaction("T003", family2, shop, 12.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T004", family3, shop, 8.0, pricePerKg, currentMonth)); // ghost test
        transactions.add(new Transaction("T005", family4, shop, 40.0, pricePerKg, currentMonth)); // quantity anomaly
        transactions.add(new Transaction("T006", family5, shop, 8.0, pricePerKg, currentMonth)); // eligibility review
        transactions.add(new Transaction("T007", family6, shop, 12.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T008", family7, shop, 8.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T009", family8, shop, 16.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T010", family9, shop, 4.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T011", family10, shop, 8.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T012", family11, shop, 12.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T013", family12, shop, 12.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T014", family13, shop, 8.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T015", family14, shop, 12.0, pricePerKg, currentMonth));
        transactions.add(new Transaction("T016", family15, shop, 16.0, pricePerKg, currentMonth));

        System.out.println("Transactions recorded: " + transactions.size());
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
        transactions.get(0).printBill();
        System.out.println("\nProgram completed successfully.");
    }
}
