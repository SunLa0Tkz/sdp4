package com.flightres.facade;

import com.flightres.passengerservices.LoyaltyProgram;
import com.flightres.passengerservices.MealPreferenceService;
import com.flightres.passengerservices.MealRequest;
import com.flightres.seating.SeatHold;
import com.flightres.seating.SeatMap;
import com.flightres.ticketing.ETicket;
import com.flightres.ticketing.ETicketIssuer;

class Compensator {
    private final SeatMap seatMap;
    private final ETicketIssuer ticketIssuer;
    private final MealPreferenceService meals;
    private final LoyaltyProgram loyalty;
    private final BookingChainRepository bookings;

    Compensator(SeatMap seatMap, ETicketIssuer ticketIssuer, MealPreferenceService meals,
                LoyaltyProgram loyalty, BookingChainRepository bookings) {
        this.seatMap = seatMap;
        this.ticketIssuer = ticketIssuer;
        this.meals = meals;
        this.loyalty = loyalty;
        this.bookings = bookings;
    }

    void undo(BookingChain booking, String failedStep, String reason) {
        String ref = booking.getBookingRef();

        if (booking.getStatus() != ChainStatus.COMPENSATING) {
            booking.setFailure(failedStep, reason);
            booking.setStatus(ChainStatus.COMPENSATING);
            bookings.save(booking);
        }

        // undo C -> B -> A, based on what each subsystem actually saved
        boolean undidC = false;
        MealRequest meal = meals.findMealByBooking(ref);
        if (meal != null && meal.getStatus() != MealRequest.Status.CANCELLED) {
            meals.cancel(meal.getId());
            undidC = true;
        }
        if (loyalty.reverseMiles(ref) > 0) {
            undidC = true;
        }
        if (undidC) {
            booking.addUndoneStep("C");
            bookings.save(booking);
        }

        ETicket ticket = ticketIssuer.findTicketByBooking(ref);
        if (ticket != null && ticket.isLive()) {
            ticketIssuer.voidTicket(ticket.getId());
            booking.addUndoneStep("B");
            bookings.save(booking);
        }

        SeatHold hold = seatMap.findHoldByBooking(ref);
        if (hold != null && hold.isActive()) {
            seatMap.release(hold.getId());
            booking.addUndoneStep("A");
            bookings.save(booking);
        }

        booking.setStatus(ChainStatus.COMPENSATED);
        bookings.save(booking);
    }
}
