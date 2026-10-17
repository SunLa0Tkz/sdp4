package com.flightres.ticketing;

public class ETicket {
    public enum Status { ISSUED, CHECKED_IN, VOIDED }

    private final long id;
    private final String bookingRef;
    private final String passengerName;
    private final String flightNo;
    private final String seatNo;
    private final int price;
    private final int baggageFee;
    private Status status;

    public ETicket(long id, String bookingRef, String passengerName, String flightNo, String seatNo,
                   int price, int baggageFee, Status status) {
        this.id = id;
        this.bookingRef = bookingRef;
        this.passengerName = passengerName;
        this.flightNo = flightNo;
        this.seatNo = seatNo;
        this.price = price;
        this.baggageFee = baggageFee;
        this.status = status;
    }

    public void checkIn() {
        if (status != Status.ISSUED) {
            throw new IllegalStateException("Ticket " + getNumber() + " is " + status + " and cannot be checked in");
        }
        status = Status.CHECKED_IN;
    }

    public void voidTicket() {
        if (status != Status.ISSUED) {
            throw new IllegalStateException("Ticket " + getNumber() + " is " + status + " and cannot be voided");
        }
        status = Status.VOIDED;
    }

    public boolean isLive() {
        return status != Status.VOIDED;
    }

    public String getNumber() {
        return String.format("555-%06d", id);
    }

    public int getTotal() {
        return price + baggageFee;
    }

    public long getId() { return id; }
    public String getBookingRef() { return bookingRef; }
    public String getPassengerName() { return passengerName; }
    public String getFlightNo() { return flightNo; }
    public String getSeatNo() { return seatNo; }
    public int getPrice() { return price; }
    public int getBaggageFee() { return baggageFee; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "Ticket " + getNumber() + " (" + passengerName + ", " + flightNo + " " + seatNo + ", $" + price
                + " + bags $" + baggageFee + ", " + status + ")";
    }
}
