
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseTest {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:ration.db";

        try (Connection connection =
                     DriverManager.getConnection(url)) {

            System.out.println("Database connected successfully!");

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                     "SELECT name FROM sqlite_master " +
                     "WHERE type = 'table' ORDER BY name")) {

                System.out.println("Tables in database:");

                while (result.next()) {
                    System.out.println("- " + result.getString("name"));
                }
            }

        } catch (Exception e) {
            System.out.println("Database connection failed!");
            e.printStackTrace();
        }
    }
}
