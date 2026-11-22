
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class FamilyRegistration {

    private static final String URL = DatabaseManager.getDatabaseUrl();
    private static final String SHOP_ID = "RS001";

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("================================");
        System.out.println("     FAMILY REGISTRATION");
        System.out.println("================================");

        System.out.print("Enter Family ID: ");
        String familyId = sc.nextLine().trim();

        System.out.print("Enter Ration Card Number: ");
        String cardNumber = sc.nextLine().trim();

        System.out.print("Enter number of beneficiaries: ");
        int count = Integer.parseInt(sc.nextLine().trim());

        if (familyId.isEmpty() || cardNumber.isEmpty() || count <= 0) {
            System.out.println("Invalid family details.");
            return;
        }

        try (Connection con = DriverManager.getConnection(URL)) {

            con.setAutoCommit(false);

            try {
                // Check whether the ration shop exists.
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT 1 FROM ration_shops WHERE shop_id = ?")) {

                    check.setString(1, SHOP_ID);

                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Ration shop RS001 does not exist.");
                        }
                    }
                }

                // Insert family information.
                String familySQL =
                    "INSERT INTO families " +
                    "(family_id, ration_card_number, shop_id) " +
                    "VALUES (?, ?, ?)";

                try (PreparedStatement ps =
                         con.prepareStatement(familySQL)) {

                    ps.setString(1, familyId);
                    ps.setString(2, cardNumber);
                    ps.setString(3, SHOP_ID);

                    ps.executeUpdate();
                }

                // Insert beneficiary information.
                String personSQL =
                    "INSERT INTO beneficiaries " +
                    "(family_id, name, age, government_employee, active) " +
                    "VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement ps =
                         con.prepareStatement(personSQL)) {

                    for (int i = 1; i <= count; i++) {

                        System.out.println(
                            "\nBeneficiary " + i);

                        System.out.print("Name: ");
                        String name = sc.nextLine().trim();

                        System.out.print("Age: ");
                        int age = Integer.parseInt(
                            sc.nextLine().trim());

                        System.out.print(
                            "Government employee? (yes/no): ");
                        String employeeInput =
                            sc.nextLine().trim();

                        System.out.print("Active? (yes/no): ");
                        String activeInput =
                            sc.nextLine().trim();

                        boolean employee =
                            employeeInput.equalsIgnoreCase("yes");

                        boolean active =
                            activeInput.equalsIgnoreCase("yes");

                        if (name.isEmpty() || age <= 0 ||
                            !(employeeInput.equalsIgnoreCase("yes")
                              || employeeInput.equalsIgnoreCase("no")) ||
                            !(activeInput.equalsIgnoreCase("yes")
                              || activeInput.equalsIgnoreCase("no"))) {

                            throw new IllegalArgumentException(
                                "Invalid beneficiary details.");
                        }

                        ps.setString(1, familyId);
                        ps.setString(2, name);
                        ps.setInt(3, age);
                        ps.setInt(4, employee ? 1 : 0);
                        ps.setInt(5, active ? 1 : 0);

                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                // Save all changes together.
                con.commit();

                System.out.println("\n================================");
                System.out.println("FAMILY REGISTERED SUCCESSFULLY!");
                System.out.println("Family ID: " + familyId);
                System.out.println("Ration Card: " + cardNumber);
                System.out.println("Beneficiaries: " + count);
                System.out.println("================================");

            } catch (Exception e) {

                con.rollback();

                System.out.println(
                    "\nRegistration failed: " + e.getMessage());

            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println(
                "Database connection error: " + e.getMessage());
        }
    }
}
