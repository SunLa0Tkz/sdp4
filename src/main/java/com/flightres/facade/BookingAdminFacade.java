package com.flightres.facade;

import com.flightres.passengerservices.LoyaltyProgram;
import com.flightres.passengerservices.MealPreferenceService;
import com.flightres.passengerservices.MealRequest;
import com.flightres.seating.SeatHold;
import com.flightres.seating.SeatMap;
import com.flightres.ticketing.ETicket;
import com.flightres.ticketing.ETicketIssuer;

import java.util.ArrayList;
import java.util.List;

public class BookingAdminFacade {
    private final SeatMap seatMap;
    private final ETicketIssuer ticketIssuer;
    private final MealPreferenceService meals;
    private final LoyaltyProgram loyalty;
    private final BookingChainRepository bookings;
    private final Compensator compensator;

    public BookingAdminFacade(SeatMap seatMap, ETicketIssuer ticketIssuer, MealPreferenceService meals,
                              LoyaltyProgram loyalty, BookingChainRepository bookings) {
        this.seatMap = seatMap;
        this.ticketIssuer = ticketIssuer;
        this.meals = meals;
        this.loyalty = loyalty;
        this.bookings = bookings;
        this.compensator = new Compensator(seatMap, ticketIssuer, meals, loyalty, bookings);
    }

    public List<BookingChain> allBookings() {
        return bookings.findAll();
    }

    public List<BookingChain> unfinishedBookings() {
        return bookings.findUnfinished();
    }

    public String inspect(String bookingRef) {
        BookingChain booking = load(bookingRef);
        SeatHold hold = seatMap.findHoldByBooking(bookingRef);
        ETicket ticket = ticketIssuer.findTicketByBooking(bookingRef);
        MealRequest meal = meals.findMealByBooking(bookingRef);

        return "Booking " + bookingRef + ": " + booking.getStatus()
                + (booking.getFailedStep() == null ? "" : " (step " + booking.getFailedStep() + " failed: "
                + booking.getFailureReason() + ")") + "\n"
                + "  A seat   : " + (hold == null ? "-" : hold.getFlightNo() + " " + hold.getSeatNo() + "  " + hold.getStatus()) + "\n"
                + "  B ticket : " + (ticket == null ? "-" : ticket.getNumber() + "  " + ticket.getStatus()) + "\n"
                + "  C meal   : " + (meal == null ? "-" : meal.getMealCode() + "  " + meal.getStatus()) + "\n"
                + "  C miles  : " + loyalty.milesForBooking(bookingRef) + "\n"
                + "  undone   : " + (booking.getUndoneSteps().isEmpty() ? "-" : booking.getUndoneSteps());
    }

    public BookingChain cancelBooking(String bookingRef, String reason) {
        BookingChain booking = load(bookingRef);
        if (booking.getStatus() == ChainStatus.COMPENSATED || booking.getStatus() == ChainStatus.REJECTED) {
            throw new IllegalStateException("Booking " + bookingRef + " is already " + booking.getStatus());
        }
        ETicket ticket = ticketIssuer.findTicketByBooking(bookingRef);
        if (ticket != null && ticket.getStatus() == ETicket.Status.CHECKED_IN) {
            throw new IllegalStateException("The passenger of " + bookingRef + " is already checked in - cannot cancel");
        }
        compensator.undo(booking, "ADMIN", reason);
        return booking;
    }

    public List<BookingChain> rollBackUnfinished() {
        List<BookingChain> result = new ArrayList<>();
        for (BookingChain booking : bookings.findUnfinished()) {
            String failedStep = switch (booking.getStatus()) {
                case STARTED -> "A";
                case PENDING_B -> "B";
                case PENDING_C -> "C";
                default -> booking.getFailedStep();
            };
            String reason = booking.getFailureReason() != null ? booking.getFailureReason()
                    : "program stopped while the booking was " + booking.getStatus();
            compensator.undo(booking, failedStep, reason);
            result.add(booking);
        }
        return result;
    }

    public void checkIn(String bookingRef) {
        BookingChain booking = load(bookingRef);
        if (booking.getStatus() != ChainStatus.COMPLETED) {
            throw new IllegalStateException("Only completed bookings can be checked in, " + bookingRef
                    + " is " + booking.getStatus());
        }
        ticketIssuer.checkIn(booking.getTicketId());
    }

    public List<String> loyaltyAccounts() {
        return loyalty.describeMembers();
    }

    private BookingChain load(String bookingRef) {
        BookingChain booking = bookings.find(bookingRef);
        if (booking == null) {
            throw new IllegalArgumentException("No booking " + bookingRef);
        }
        return booking;
    }
}
