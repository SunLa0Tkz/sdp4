package com.flightres.passengerservices;

import java.util.List;

public class MealPreferenceService {
    public static final List<String> SPECIAL_MEALS = List.of("VGML", "KSML", "MOML", "DBML", "GFML");
    public static final int SHORT_FLIGHT_KM = 1000;

    private final PassengerServicesRepository repository;

    public MealPreferenceService(PassengerServicesRepository repository) {
        this.repository = repository;
    }

    public boolean isKnownMeal(String mealCode) {
        return "STANDARD".equals(mealCode) || SPECIAL_MEALS.contains(mealCode);
    }

    public MealRequest requestMeal(String bookingRef, String flightNo, String mealCode, int distanceKm) {
        MealRequest existing = repository.findMealByBooking(bookingRef);
        if (existing != null && existing.getStatus() != MealRequest.Status.CANCELLED) {
            return existing;
        }
        if (!isKnownMeal(mealCode)) {
            throw new PassengerServicesException("Unknown meal code " + mealCode);
        }
        if (SPECIAL_MEALS.contains(mealCode) && distanceKm < SHORT_FLIGHT_KM) {
            throw new PassengerServicesException("Flight " + flightNo + " is a short flight (" + distanceKm
                    + " km): special meal " + mealCode + " cannot be loaded, only a snack box");
        }
        return repository.insertMeal(bookingRef, flightNo, mealCode);
    }

    public MealRequest confirm(long mealId) {
        MealRequest meal = getMeal(mealId);
        if (meal.getStatus() != MealRequest.Status.CONFIRMED) {
            meal.confirm();
            repository.updateMealStatus(meal);
        }
        return meal;
    }

    public MealRequest cancel(long mealId) {
        MealRequest meal = getMeal(mealId);
        if (meal.getStatus() != MealRequest.Status.CANCELLED) {
            meal.cancel();
            repository.updateMealStatus(meal);
        }
        return meal;
    }

    public MealRequest getMeal(long mealId) {
        MealRequest meal = repository.findMeal(mealId);
        if (meal == null) {
            throw new PassengerServicesException("No meal request with id " + mealId);
        }
        return meal;
    }

    public MealRequest findMealByBooking(String bookingRef) {
        return repository.findMealByBooking(bookingRef);
    }
}
