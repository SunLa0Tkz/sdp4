package com.flightres.facade;

import java.util.List;

public class InvalidBookingException extends RuntimeException {
    private final List<String> problems;

    public InvalidBookingException(List<String> problems) {
        super("Invalid booking request: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
