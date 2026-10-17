package com.flightres.seating;

public class SeatHold {
    public enum Status { HELD, CONFIRMED, RELEASED }

    private final long id;
    private final String bookingRef;
    private final String flightNo;
    private final String seatNo;
    private final String cabin;
    private final String passengerName;
    private final int price;
    private Status status;

    public SeatHold(long id, String bookingRef, String flightNo, String seatNo, String cabin,
                    String passengerName, int price, Status status) {
        this.id = id;
        this.bookingRef = bookingRef;
        this.flightNo = flightNo;
        this.seatNo = seatNo;
        this.cabin = cabin;
        this.passengerName = passengerName;
        this.price = price;
        this.status = status;
    }

    public void confirm() {
        if (status != Status.HELD) {
            throw new IllegalStateException("Seat hold " + id + " is " + status + " and cannot be confirmed");
        }
        status = Status.CONFIRMED;
    }

    public void release() {
        if (status == Status.RELEASED) {
            throw new IllegalStateException("Seat hold " + id + " is already released");
        }
        status = Status.RELEASED;
    }

    public boolean isActive() {
        return status != Status.RELEASED;
    }

    public long getId() { return id; }
    public String getBookingRef() { return bookingRef; }
    public String getFlightNo() { return flightNo; }
    public String getSeatNo() { return seatNo; }
    public String getCabin() { return cabin; }
    public String getPassengerName() { return passengerName; }
    public int getPrice() { return price; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "SeatHold " + id + " (" + flightNo + " " + seatNo + ", " + passengerName + ", $" + price + ", " + status + ")";
    }
}
