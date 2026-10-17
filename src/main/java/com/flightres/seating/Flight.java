package com.flightres.seating;

import java.time.LocalDate;

public record Flight(String flightNo, String from, String to, LocalDate date,
                     int distanceKm, int rows, int baseFare) {
}
