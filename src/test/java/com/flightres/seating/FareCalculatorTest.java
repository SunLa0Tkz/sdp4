package com.flightres.seating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FareCalculatorTest {
    private final FareCalculator calculator = new FareCalculator();

    @Test
    void rows_one_and_two_are_business_and_cost_two_and_a_half_times_the_base_fare() {
        assertEquals("BUSINESS", calculator.cabinOf(2));
        assertEquals(1000, calculator.price(400, 1));
    }

    @Test
    void rows_three_and_four_are_extra_legroom_and_cost_twenty_percent_more() {
        assertEquals("ECONOMY", calculator.cabinOf(3));
        assertEquals(480, calculator.price(400, 4));
    }

    @Test
    void other_rows_cost_the_base_fare() {
        assertEquals(400, calculator.price(400, 5));
    }

    @Test
    void impossible_input_is_refused() {
        assertThrows(IllegalArgumentException.class, () -> calculator.price(0, 5));
        assertThrows(IllegalArgumentException.class, () -> calculator.price(400, 0));
    }
}
