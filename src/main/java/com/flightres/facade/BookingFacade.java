package com.flightres.facade;

import com.flightres.passengerservices.LoyaltyProgram;
import com.flightres.passengerservices.MealPreferenceService;
import com.flightres.passengerservices.MealRequest;
import com.flightres.seating.Flight;
import com.flightres.seating.SeatHold;
import com.flightres.seating.SeatMap;
import com.flightres.seating.SeatingException;
import com.flightres.ticketing.ETicket;
import com.flightres.ticketing.ETicketIssuer;
import com.flightres.ticketing.TicketRequest;

import java.util.ArrayList;
import java.util.List;

public class BookingFacade {
    private final SeatMap seatMap;
    private final ETicketIssuer ticketIssuer;
    private final MealPreferenceService meals;
    private final LoyaltyProgram loyalty;
    private final BookingChainRepository bookings;
    private final Compensator compensator;

    private String crashAfterStep;
    private Runnable crashAction;

    public BookingFacade(SeatMap seatMap, ETicketIssuer ticketIssuer, MealPreferenceService meals,
                         LoyaltyProgram loyalty, BookingChainRepository bookings) {
        this.seatMap = seatMap;
        this.ticketIssuer = ticketIssuer;
        this.meals = meals;
        this.loyalty = loyalty;
        this.bookings = bookings;
        this.compensator = new Compensator(seatMap, ticketIssuer, meals, loyalty, bookings);
    }

    public List<String> listFlights() {
        List<String> lines = new ArrayList<>();
        for (Flight f : seatMap.allFlights()) {
            lines.add(String.format("%-6s %s -> %s  %s  %5d km  %2d free seats", f.flightNo(), f.from(), f.to(),
                    f.date(), f.distanceKm(), seatMap.freeSeats(f.flightNo()).size()));
        }
        return lines;
    }

    public List<String> freeSeats(String flightNo) {
        return seatMap.freeSeats(flightNo);
    }

    public int priceOf(String flightNo, String seatNo) {
        return seatMap.priceOf(flightNo, seatNo);
    }

    public BookingChain book(BookingRequest request) {
        checkRequest(request);
        BookingChain booking = new BookingChain(bookings.nextBookingRef(), request);
        bookings.save(booking);
        return runChain(booking);
    }

    public BookingChain resume(String bookingRef) {
        BookingChain booking = bookings.find(bookingRef);
        if (booking == null) {
            throw new IllegalArgumentException("No booking " + bookingRef);
        }
        return runChain(booking);
    }

    private BookingChain runChain(BookingChain booking) {
        // read saved status, do next step, repeat until finished
        while (true) {
            switch (booking.getStatus()) {
                case STARTED -> stepA_holdSeat(booking);
                case PENDING_B -> stepB_issueTicket(booking);
                case PENDING_C -> stepC_mealMilesAndConfirm(booking);
                case COMPENSATING -> compensator.undo(booking, booking.getFailedStep(), booking.getFailureReason());
                case COMPLETED -> {
                    return booking;
                }
                default -> throw new BookingFailedException(booking);
            }
        }
    }

    private void stepA_holdSeat(BookingChain booking) {
        BookingRequest r = booking.getRequest();
        try {
            // seat may already be held if we crashed after A
            SeatHold hold = seatMap.findHoldByBooking(booking.getBookingRef());
            if (hold == null || !hold.isActive()) {
                hold = seatMap.holdSeat(booking.getBookingRef(), r.flightNo(), r.seatNo(), r.passengerName());
            }
            crashPointAfter("A");
            booking.setHoldId(hold.getId());
            booking.setStatus(ChainStatus.PENDING_B);
        } catch (SeatingException e) {
            booking.setFailure("A", e.getMessage());
            booking.setStatus(ChainStatus.REJECTED);
        }
        bookings.save(booking);
    }

    private void stepB_issueTicket(BookingChain booking) {
        BookingRequest r = booking.getRequest();
        try {
            SeatHold hold = seatMap.getHold(booking.getHoldId());
            Flight flight = seatMap.getFlight(hold.getFlightNo());
            ETicket ticket = ticketIssuer.issue(new TicketRequest(booking.getBookingRef(), r.passengerName(),
                    r.passportExpiry(), flight.flightNo(), hold.getSeatNo(), flight.date(), hold.getCabin(),
                    hold.getPrice(), r.bagWeightsKg()));
            crashPointAfter("B");
            booking.setTicketId(ticket.getId());
            booking.setTicketNumber(ticket.getNumber());
            booking.setTotalPrice(ticket.getTotal());
            booking.setStatus(ChainStatus.PENDING_C);
            bookings.save(booking);
        } catch (RuntimeException e) {
            compensator.undo(booking, "B", e.getMessage());
        }
    }

    private void stepC_mealMilesAndConfirm(BookingChain booking) {
        BookingRequest r = booking.getRequest();
        try {
            SeatHold hold = seatMap.getHold(booking.getHoldId());
            Flight flight = seatMap.getFlight(hold.getFlightNo());

            MealRequest meal = meals.requestMeal(booking.getBookingRef(), flight.flightNo(), r.mealCode(),
                    flight.distanceKm());
            booking.setMealId(meal.getId());
            bookings.save(booking);

            if (r.loyaltyNumber() != null) {
                int miles = loyalty.creditMiles(r.loyaltyNumber(), booking.getBookingRef(), flight.distanceKm(),
                        hold.getCabin());
                booking.setMiles(miles);
                bookings.save(booking);
            }
            crashPointAfter("C");

            seatMap.confirm(hold.getId());
            meals.confirm(meal.getId());
            booking.setStatus(ChainStatus.COMPLETED);
            bookings.save(booking);
        } catch (RuntimeException e) {
            compensator.undo(booking, "C", e.getMessage());
        }
    }

    private void checkRequest(BookingRequest r) {
        List<String> problems = new ArrayList<>();
        if (r.flightNo() == null || r.flightNo().isEmpty()) {
            problems.add("flight number is missing");
        }
        if (r.seatNo() == null || r.seatNo().isEmpty()) {
            problems.add("seat is missing");
        }
        if (r.passengerName() == null || !r.passengerName().matches("[A-Za-z .'-]{2,60}")) {
            problems.add("passenger name must be 2-60 letters");
        }
        if (r.passportExpiry() == null) {
            problems.add("passport expiry date is missing");
        }
        for (Integer kg : r.bagWeightsKg()) {
            if (kg == null || kg <= 0) {
                problems.add("bag weights must be positive numbers");
            }
        }
        if (!meals.isKnownMeal(r.mealCode())) {
            problems.add("unknown meal code " + r.mealCode());
        }
        if (r.loyaltyNumber() != null && !loyalty.isMember(r.loyaltyNumber())) {
            problems.add("unknown frequent-flyer number " + r.loyaltyNumber());
        }
        if (!problems.isEmpty()) {
            throw new InvalidBookingException(problems);
        }
    }

    public void simulateCrashAfter(String step, Runnable crashAction) {
        this.crashAfterStep = step;
        this.crashAction = crashAction;
    }

    // only for demo/tests: simulate a crash after a step
    private void crashPointAfter(String step) {
        if (step.equals(crashAfterStep)) {
            crashAction.run();
        }
    }
}
