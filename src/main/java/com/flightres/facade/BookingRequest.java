package com.flightres.facade;

import java.time.LocalDate;
import java.util.List;

public record BookingRequest(String flightNo, String seatNo, String passengerName, LocalDate passportExpiry,
                             List<Integer> bagWeightsKg, String mealCode, String loyaltyNumber) {
    public BookingRequest {
        flightNo = flightNo == null ? null : flightNo.trim().toUpperCase();
        seatNo = seatNo == null ? null : seatNo.trim().toUpperCase();
        passengerName = passengerName == null ? null : passengerName.trim();
        bagWeightsKg = bagWeightsKg == null ? List.of() : List.copyOf(bagWeightsKg);
        mealCode = (mealCode == null || mealCode.isBlank()) ? "STANDARD" : mealCode.trim().toUpperCase();
        loyaltyNumber = (loyaltyNumber == null || loyaltyNumber.isBlank()) ? null : loyaltyNumber.trim().toUpperCase();
    }
}
