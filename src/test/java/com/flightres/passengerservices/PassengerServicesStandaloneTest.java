package com.flightres.passengerservices;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PassengerServicesStandaloneTest {
    @TempDir
    Path folder;
    PassengerServicesRepository repository;
    LoyaltyProgram loyalty;
    MealPreferenceService meals;

    @BeforeEach
    void setUp() {
        repository = new PassengerServicesRepository(folder.resolve("passenger-services.db"));
        loyalty = new LoyaltyProgram(repository, new MilesCalculator());
        meals = new MealPreferenceService(repository);
        loyalty.addMember("FF-1001", "Aliya Nurlanova", "GOLD", true);
        loyalty.addMember("FF-1003", "Timur Akhmetov", "BASIC", false);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void crediting_miles_adds_them_to_the_balance_only_once() {
        assertEquals(4743, loyalty.creditMiles("FF-1001", "BK001", 5100, "ECONOMY"));
        assertEquals(4743, loyalty.creditMiles("FF-1001", "BK001", 5100, "ECONOMY"));
        assertEquals(4743, loyalty.balance("FF-1001"));
        assertEquals(4743, loyalty.milesForBooking("BK001"));
    }

    @Test
    void reversing_takes_the_miles_back_and_can_be_repeated() {
        loyalty.creditMiles("FF-1001", "BK001", 5100, "ECONOMY");
        assertEquals(4743, loyalty.reverseMiles("BK001"));
        assertEquals(0, loyalty.reverseMiles("BK001"));
        assertEquals(0, loyalty.balance("FF-1001"));
    }

    @Test
    void suspended_or_unknown_accounts_get_no_miles() {
        assertThrows(PassengerServicesException.class, () -> loyalty.creditMiles("FF-1003", "BK001", 5100, "ECONOMY"));
        assertThrows(PassengerServicesException.class, () -> loyalty.creditMiles("FF-9999", "BK001", 5100, "ECONOMY"));
        assertTrue(loyalty.isMember("FF-1003"));
        assertFalse(loyalty.isMember("FF-9999"));
        assertTrue(loyalty.describeMembers().get(1).contains("SUSPENDED"));
    }

    @Test
    void a_special_meal_on_a_long_flight_is_ordered() {
        MealRequest meal = meals.requestMeal("BK001", "KC901", "VGML", 5100);
        assertEquals(MealRequest.Status.REQUESTED, meals.getMeal(meal.getId()).getStatus());
        assertEquals(meal.getId(), meals.requestMeal("BK001", "KC901", "VGML", 5100).getId());
    }

    @Test
    void a_special_meal_on_a_short_flight_is_refused_but_a_standard_meal_is_fine() {
        assertThrows(PassengerServicesException.class, () -> meals.requestMeal("BK001", "KC233", "KSML", 620));
        assertNull(meals.findMealByBooking("BK001"));
        assertEquals("STANDARD", meals.requestMeal("BK002", "KC233", "STANDARD", 620).getMealCode());
        assertThrows(PassengerServicesException.class, () -> meals.requestMeal("BK003", "KC901", "PIZZA", 5100));
    }

    @Test
    void meals_keep_their_status_after_the_database_is_closed_and_opened_again() {
        MealRequest meal = meals.requestMeal("BK001", "KC901", "MOML", 5100);
        meals.confirm(meal.getId());
        meals.confirm(meal.getId());
        repository.close();

        repository = new PassengerServicesRepository(folder.resolve("passenger-services.db"));
        meals = new MealPreferenceService(repository);
        assertEquals(MealRequest.Status.CONFIRMED, meals.getMeal(meal.getId()).getStatus());
        meals.cancel(meal.getId());
        meals.cancel(meal.getId());
        assertEquals(MealRequest.Status.CANCELLED, meals.findMealByBooking("BK001").getStatus());
        assertThrows(PassengerServicesException.class, () -> meals.getMeal(99));
    }
}
