package model;

import java.util.ArrayList;

public final class Family {
    public String familyId;
    public String rationCardNumber;
    public String shopId = "RS001";
    public ArrayList<Person> members = new ArrayList<>();
    public boolean isBlocked = false;
    public boolean cardBlocked = false;
    public String blockedUntil = null;
    public String blockReason = null;
    public String latestRiskLevel = "NO TRANSACTION";
    public int latestRiskScore = 0;
    public String latestTransactionId = null;

    // Derived properties aligned with frontend expectations
    public int totalMembers = 0;
    public int activeMembers = 0;
    public int activeMemberCount = 0;
    public int memberCount = 0;
    public double expectedQuota = 0.0;
    public boolean hasGovernmentEmployee = false;
    public boolean isGhost = false;
    public boolean isActive = true;
    public boolean isEligible = true;

    public Family(String familyId, String rationCardNumber) {
        this.familyId = familyId;
        this.rationCardNumber = rationCardNumber;
        recalculateMetrics();
    }

    public Family(String familyId, String rationCardNumber, String shopId) {
        this.familyId = familyId;
        this.rationCardNumber = rationCardNumber;
        this.shopId = shopId;
        recalculateMetrics();
    }

    public void addMember(Person person) {
        members.add(person);
        recalculateMetrics();
    }

    public final void recalculateMetrics() {
        this.memberCount = getMemberCount();
        this.totalMembers = this.memberCount;
        this.activeMemberCount = getActiveMemberCount();
        this.activeMembers = this.activeMemberCount;
        this.expectedQuota = this.activeMembers * 4.0;
        this.cardBlocked = this.isBlocked;
        this.hasGovernmentEmployee = hasGovernmentEmployee();
        this.isGhost = isGhostBeneficiary();
        this.isActive = isActive();
        this.isEligible = isEligible();
    }

    public int getMemberCount() {
        return members.size();
    }

    public int getActiveMemberCount() {
        int count = 0;
        for (Person p : members) {
            if (p.active) count++;
        }
        return count;
    }

    public int getInactiveMemberCount() {
        int count = 0;
        for (Person p : members) {
            if (!p.active) count++;
        }
        return count;
    }

    public boolean hasGovernmentEmployee() {
        for (Person p : members) {
            if (p.governmentEmployee) return true;
        }
        return false;
    }

    public boolean hasInactiveMember() {
        for (Person p : members) {
            if (!p.active) return true;
        }
        return false;
    }

    public boolean isGhostBeneficiary() {
        return getActiveMemberCount() == 0;
    }

    public boolean isActive() {
        return getActiveMemberCount() > 0;
    }

    public boolean isEligible() {
        return isActive() && !hasGovernmentEmployee();
    }
}

