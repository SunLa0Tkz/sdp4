package com.flightres.passengerservices;

public class MilesCalculator {
    public static final int MINIMUM_MILES = 250;

    public int miles(int distanceKm, String cabin, String tier) {
        if (distanceKm <= 0) {
            throw new IllegalArgumentException("distance must be positive");
        }
        double miles = distanceKm * 0.62;
        if (cabin.equals("BUSINESS")) {
            miles = miles * 1.5;
        }
        if (tier.equals("SILVER")) {
            miles = miles * 1.25;
        } else if (tier.equals("GOLD")) {
            miles = miles * 1.5;
        }
        return Math.max(MINIMUM_MILES, (int) Math.round(miles));
    }
}
