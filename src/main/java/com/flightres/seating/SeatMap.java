package com.flightres.seating;

import java.util.ArrayList;
import java.util.List;

public class SeatMap {
    private static final String SEAT_LETTERS = "ABCDEF";

    private final SeatRepository repository;
    private final FareCalculator fareCalculator;

    public SeatMap(SeatRepository repository, FareCalculator fareCalculator) {
        this.repository = repository;
        this.fareCalculator = fareCalculator;
    }

    public void addFlight(Flight flight) {
        repository.saveFlight(flight);
    }

    public List<Flight> allFlights() {
        return repository.allFlights();
    }

    public Flight getFlight(String flightNo) {
        Flight flight = repository.findFlight(flightNo);
        if (flight == null) {
            throw new SeatingException("Unknown flight " + flightNo);
        }
        return flight;
    }

    public List<String> freeSeats(String flightNo) {
        Flight flight = getFlight(flightNo);
        List<String> taken = new ArrayList<>();
        for (SeatHold hold : repository.holdsOnFlight(flightNo)) {
            if (hold.isActive()) {
                taken.add(hold.getSeatNo());
            }
        }
        List<String> free = new ArrayList<>();
        for (int row = 1; row <= flight.rows(); row++) {
            for (char letter : SEAT_LETTERS.toCharArray()) {
                String seat = row + "" + letter;
                if (!taken.contains(seat)) {
                    free.add(seat);
                }
            }
        }
        return free;
    }

    public int priceOf(String flightNo, String seatNo) {
        Flight flight = getFlight(flightNo);
        return fareCalculator.price(flight.baseFare(), rowOf(flight, seatNo));
    }

    public SeatHold holdSeat(String bookingRef, String flightNo, String seatNo, String passengerName) {
        Flight flight = getFlight(flightNo);
        int row = rowOf(flight, seatNo);
        if (!freeSeats(flightNo).contains(seatNo)) {
            throw new SeatingException("Seat " + seatNo + " on " + flightNo + " is already taken");
        }
        String cabin = fareCalculator.cabinOf(row);
        int price = fareCalculator.price(flight.baseFare(), row);
        return repository.insertHold(bookingRef, flightNo, seatNo, cabin, passengerName, price);
    }

    public SeatHold confirm(long holdId) {
        SeatHold hold = getHold(holdId);
        if (hold.getStatus() != SeatHold.Status.CONFIRMED) {
            hold.confirm();
            repository.updateStatus(hold);
        }
        return hold;
    }

    public SeatHold release(long holdId) {
        SeatHold hold = getHold(holdId);
        if (hold.isActive()) {
            hold.release();
            repository.updateStatus(hold);
        }
        return hold;
    }

    public SeatHold getHold(long holdId) {
        SeatHold hold = repository.findHold(holdId);
        if (hold == null) {
            throw new SeatingException("No seat hold with id " + holdId);
        }
        return hold;
    }

    public SeatHold findHoldByBooking(String bookingRef) {
        return repository.findHoldByBooking(bookingRef);
    }

    public List<SeatHold> holdsOnFlight(String flightNo) {
        return repository.holdsOnFlight(flightNo);
    }

    private int rowOf(Flight flight, String seatNo) {
        if (seatNo == null || !seatNo.matches("[0-9]{1,2}[A-F]")) {
            throw new SeatingException("'" + seatNo + "' is not a seat (example: 12C)");
        }
        int row = Integer.parseInt(seatNo.substring(0, seatNo.length() - 1));
        if (row < 1 || row > flight.rows()) {
            throw new SeatingException("Row " + row + " does not exist on " + flight.flightNo());
        }
        return row;
    }
}
