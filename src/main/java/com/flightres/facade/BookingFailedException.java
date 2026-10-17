package com.flightres.facade;

public class BookingFailedException extends RuntimeException {
    private final BookingChain booking;

    public BookingFailedException(BookingChain booking) {
        super("Booking " + booking.getBookingRef() + " " + booking.getStatus() + ": step " + booking.getFailedStep()
                + " failed - " + booking.getFailureReason());
        this.booking = booking;
    }

    public BookingChain getBooking() {
        return booking;
    }
}
