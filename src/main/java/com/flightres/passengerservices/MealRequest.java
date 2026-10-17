package com.flightres.passengerservices;

public class MealRequest {
    public enum Status { REQUESTED, CONFIRMED, CANCELLED }

    private final long id;
    private final String bookingRef;
    private final String flightNo;
    private final String mealCode;
    private Status status;

    public MealRequest(long id, String bookingRef, String flightNo, String mealCode, Status status) {
        this.id = id;
        this.bookingRef = bookingRef;
        this.flightNo = flightNo;
        this.mealCode = mealCode;
        this.status = status;
    }

    public void confirm() {
        if (status != Status.REQUESTED) {
            throw new IllegalStateException("Meal request " + id + " is " + status + " and cannot be confirmed");
        }
        status = Status.CONFIRMED;
    }

    public void cancel() {
        if (status == Status.CANCELLED) {
            throw new IllegalStateException("Meal request " + id + " is already cancelled");
        }
        status = Status.CANCELLED;
    }

    public long getId() { return id; }
    public String getBookingRef() { return bookingRef; }
    public String getFlightNo() { return flightNo; }
    public String getMealCode() { return mealCode; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "Meal " + id + " (" + flightNo + ", " + mealCode + ", " + status + ")";
    }
}
