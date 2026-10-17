package com.flightres.facade;

import java.util.ArrayList;
import java.util.List;

public class BookingChain {
    private final String bookingRef;
    private final BookingRequest request;
    private ChainStatus status;
    private Long holdId;
    private Long ticketId;
    private Long mealId;
    private String ticketNumber;
    private int totalPrice;
    private int miles;
    private String failedStep;
    private String failureReason;
    private final List<String> undoneSteps = new ArrayList<>();

    public BookingChain(String bookingRef, BookingRequest request) {
        this.bookingRef = bookingRef;
        this.request = request;
        this.status = ChainStatus.STARTED;
    }

    public String getBookingRef() { return bookingRef; }
    public BookingRequest getRequest() { return request; }
    public ChainStatus getStatus() { return status; }
    public Long getHoldId() { return holdId; }
    public Long getTicketId() { return ticketId; }
    public Long getMealId() { return mealId; }
    public String getTicketNumber() { return ticketNumber; }
    public int getTotalPrice() { return totalPrice; }
    public int getMiles() { return miles; }
    public String getFailedStep() { return failedStep; }
    public String getFailureReason() { return failureReason; }
    public List<String> getUndoneSteps() { return List.copyOf(undoneSteps); }

    void setStatus(ChainStatus status) { this.status = status; }
    void setHoldId(Long holdId) { this.holdId = holdId; }
    void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    void setMealId(Long mealId) { this.mealId = mealId; }
    void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    void setTotalPrice(int totalPrice) { this.totalPrice = totalPrice; }
    void setMiles(int miles) { this.miles = miles; }
    void setFailure(String step, String reason) {
        this.failedStep = step;
        this.failureReason = reason;
    }
    void addUndoneStep(String step) {
        if (!undoneSteps.contains(step)) {
            undoneSteps.add(step);
        }
    }

    @Override
    public String toString() {
        String text = bookingRef + " " + status + " - " + request.passengerName() + ", " + request.flightNo()
                + " seat " + request.seatNo();
        if (status == ChainStatus.COMPLETED) {
            text += ", ticket " + ticketNumber + ", total $" + totalPrice + ", miles +" + miles;
        }
        if (failedStep != null) {
            text += ", failed at step " + failedStep + ": " + failureReason;
        }
        if (!undoneSteps.isEmpty()) {
            text += ", undone: " + undoneSteps;
        }
        return text;
    }
}
