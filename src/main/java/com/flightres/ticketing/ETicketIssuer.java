package com.flightres.ticketing;

import java.util.List;

public class ETicketIssuer {
    private final TicketRepository repository;
    private final BaggageRulesEngine baggageRules;

    public ETicketIssuer(TicketRepository repository, BaggageRulesEngine baggageRules) {
        this.repository = repository;
        this.baggageRules = baggageRules;
    }

    public ETicket issue(TicketRequest request) {
        // same booking ref -> return existing ticket (safe after crash)
        ETicket existing = repository.findTicketByBooking(request.bookingRef());
        if (existing != null && existing.isLive()) {
            return existing;
        }
        if (request.passportExpiry() == null) {
            throw new TicketingException("A passport expiry date is required");
        }
        if (request.passportExpiry().isBefore(request.flightDate())) {
            throw new TicketingException("Passport expires on " + request.passportExpiry()
                    + ", before the flight on " + request.flightDate());
        }
        int baggageFee = baggageRules.baggageFee(request.cabin(), request.bagWeightsKg());
        return repository.insertTicket(request.bookingRef(), request.passengerName(), request.flightNo(),
                request.seatNo(), request.price(), baggageFee);
    }

    public ETicket voidTicket(long ticketId) {
        ETicket ticket = getTicket(ticketId);
        if (ticket.getStatus() == ETicket.Status.CHECKED_IN) {
            throw new TicketingException("Ticket " + ticket.getNumber() + " is already checked in and cannot be voided");
        }
        if (ticket.getStatus() == ETicket.Status.ISSUED) {
            ticket.voidTicket();
            repository.updateStatus(ticket);
        }
        return ticket;
    }

    public ETicket checkIn(long ticketId) {
        ETicket ticket = getTicket(ticketId);
        if (ticket.getStatus() != ETicket.Status.ISSUED) {
            throw new TicketingException("Ticket " + ticket.getNumber() + " is " + ticket.getStatus()
                    + " and cannot be checked in");
        }
        ticket.checkIn();
        repository.updateStatus(ticket);
        return ticket;
    }

    public ETicket getTicket(long ticketId) {
        ETicket ticket = repository.findTicket(ticketId);
        if (ticket == null) {
            throw new TicketingException("No ticket with id " + ticketId);
        }
        return ticket;
    }

    public ETicket findTicketByBooking(String bookingRef) {
        return repository.findTicketByBooking(bookingRef);
    }

    public List<ETicket> allTickets() {
        return repository.allTickets();
    }
}
