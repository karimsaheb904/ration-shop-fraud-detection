package model;

public class WeighingMachine {
    public boolean checkWeight(double expected, double actual, double tolerance) {
        return Math.abs(expected - actual) <= tolerance;
    }
}

