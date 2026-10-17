package com.flightres.seating;

public class FareCalculator {
    public static final int LAST_BUSINESS_ROW = 2;
    public static final int LAST_LEGROOM_ROW = 4;

    public String cabinOf(int row) {
        return row <= LAST_BUSINESS_ROW ? "BUSINESS" : "ECONOMY";
    }

    public int price(int baseFare, int row) {
        if (baseFare <= 0 || row <= 0) {
            throw new IllegalArgumentException("base fare and row must be positive");
        }
        if (row <= LAST_BUSINESS_ROW) {
            return baseFare * 5 / 2;
        }
        if (row <= LAST_LEGROOM_ROW) {
            return baseFare * 6 / 5;
        }
        return baseFare;
    }
}
