package model;

public class RationCard {
    public String cardNumber;
    public String familyId;
    public boolean blocked;
    public String blockedUntil;

    public RationCard(String cardNumber, String familyId) {
        this.cardNumber = cardNumber;
        this.familyId = familyId;
        this.blocked = false;
    }

    public RationCard(String cardNumber, String familyId, boolean blocked, String blockedUntil) {
        this.cardNumber = cardNumber;
        this.familyId = familyId;
        this.blocked = blocked;
        this.blockedUntil = blockedUntil;
    }
}

