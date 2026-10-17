package com.flightres.ticketing;

import java.time.LocalDate;
import java.util.List;

public record TicketRequest(String bookingRef, String passengerName, LocalDate passportExpiry,
                            String flightNo, String seatNo, LocalDate flightDate, String cabin,
                            int price, List<Integer> bagWeightsKg) {
}
