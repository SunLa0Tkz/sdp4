package com.flightres.passengerservices;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MilesCalculatorTest {
    private final MilesCalculator calculator = new MilesCalculator();

    @Test
    void economy_basic_earns_the_distance_in_miles() {
        assertEquals(3162, calculator.miles(5100, "ECONOMY", "BASIC"));
    }

    @Test
    void business_and_higher_tiers_earn_more() {
        assertEquals(4743, calculator.miles(5100, "BUSINESS", "BASIC"));
        assertEquals(3953, calculator.miles(5100, "ECONOMY", "SILVER"));
        assertEquals(4743, calculator.miles(5100, "ECONOMY", "GOLD"));
    }

    @Test
    void a_short_flight_earns_at_least_the_minimum() {
        assertEquals(250, calculator.miles(300, "ECONOMY", "BASIC"));
        assertThrows(IllegalArgumentException.class, () -> calculator.miles(0, "ECONOMY", "BASIC"));
    }
}
