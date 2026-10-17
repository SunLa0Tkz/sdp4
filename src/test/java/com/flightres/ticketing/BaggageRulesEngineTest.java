package com.flightres.ticketing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaggageRulesEngineTest {
    private final BaggageRulesEngine rules = new BaggageRulesEngine();

    @Test
    void no_bags_and_one_normal_economy_bag_are_free() {
        assertEquals(0, rules.baggageFee("ECONOMY", List.of()));
        assertEquals(0, rules.baggageFee("ECONOMY", List.of(23)));
    }

    @Test
    void a_heavy_economy_bag_costs_an_overweight_fee() {
        assertEquals(75, rules.baggageFee("ECONOMY", List.of(28)));
    }

    @Test
    void extra_bags_cost_an_extra_bag_fee() {
        assertEquals(60, rules.baggageFee("ECONOMY", List.of(20, 20)));
        assertEquals(135, rules.baggageFee("ECONOMY", List.of(20, 30)));
    }

    @Test
    void business_gets_two_free_bags_up_to_32_kg() {
        assertEquals(0, rules.baggageFee("BUSINESS", List.of(32, 30)));
        assertEquals(60, rules.baggageFee("BUSINESS", List.of(32, 30, 10)));
    }

    @Test
    void a_bag_over_32_kg_is_refused() {
        TicketingException e = assertThrows(TicketingException.class, () -> rules.baggageFee("BUSINESS", List.of(33)));
        assertTrue(e.getMessage().contains("cargo"));
    }

    @Test
    void too_many_bags_or_impossible_weights_are_refused() {
        assertThrows(TicketingException.class, () -> rules.baggageFee("ECONOMY", List.of(5, 5, 5, 5, 5, 5)));
        assertThrows(TicketingException.class, () -> rules.baggageFee("ECONOMY", List.of(0)));
    }
}
