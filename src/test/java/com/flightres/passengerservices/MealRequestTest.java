package com.flightres.passengerservices;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MealRequestTest {
    private MealRequest mealWithStatus(MealRequest.Status status) {
        return new MealRequest(1, "BK001", "KC901", "VGML", status);
    }

    @Test
    void a_requested_meal_can_be_confirmed_and_then_cancelled() {
        MealRequest meal = mealWithStatus(MealRequest.Status.REQUESTED);
        meal.confirm();
        meal.cancel();
        assertEquals(MealRequest.Status.CANCELLED, meal.getStatus());
    }

    @Test
    void a_cancelled_meal_cannot_change() {
        MealRequest meal = mealWithStatus(MealRequest.Status.CANCELLED);
        assertThrows(IllegalStateException.class, meal::confirm);
        assertThrows(IllegalStateException.class, meal::cancel);
        assertTrue(meal.toString().contains("VGML"));
    }
}
