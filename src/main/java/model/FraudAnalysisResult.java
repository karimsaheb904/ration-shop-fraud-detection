package model;

import java.util.ArrayList;

public class FraudAnalysisResult {
    public int score = 0;
    public ArrayList<String> reasons = new ArrayList<>();
    public double zScore = 0.0;
    public boolean isCardBlocked = false;

    public void addReason(String reason, int penalty) {
        reasons.add(reason);
        score = Math.min(100, score + penalty);
    }

    public String getRiskLevel() {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        if (score > 0) return "LOW";
        return "NORMAL";
    }
}

