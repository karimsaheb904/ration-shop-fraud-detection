package model;

import java.util.ArrayList;

public class RationShop {
    public String shopId;
    public String shopName;
    public ArrayList<Family> families = new ArrayList<>();

    public RationShop(String shopId, String shopName) {
        this.shopId = shopId;
        this.shopName = shopName;
    }

    public void addFamily(Family family) {
        families.add(family);
    }

    public int getFamilyCount() {
        return families.size();
    }

    public double calculateMonthlyRice(double ratePerActivePerson) {
        double total = 0;
        for (Family family : families) {
            total += family.getActiveMemberCount() * ratePerActivePerson;
        }
        return total;
    }
}

