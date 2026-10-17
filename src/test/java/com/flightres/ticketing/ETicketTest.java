package com.flightres.ticketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ETicketTest {
    private ETicket ticketWithStatus(ETicket.Status status) {
        return new ETicket(7, "BK001", "Aliya", "KC901", "8A", 420, 75, status);
    }

    @Test
    void an_issued_ticket_can_be_checked_in() {
        ETicket ticket = ticketWithStatus(ETicket.Status.ISSUED);
        ticket.checkIn();
        assertEquals(ETicket.Status.CHECKED_IN, ticket.getStatus());
        assertTrue(ticket.isLive());
    }

    @Test
    void an_issued_ticket_can_be_voided() {
        ETicket ticket = ticketWithStatus(ETicket.Status.ISSUED);
        ticket.voidTicket();
        assertFalse(ticket.isLive());
    }

    @Test
    void checked_in_and_voided_tickets_cannot_change() {
        assertThrows(IllegalStateException.class, ticketWithStatus(ETicket.Status.CHECKED_IN)::voidTicket);
        assertThrows(IllegalStateException.class, ticketWithStatus(ETicket.Status.VOIDED)::checkIn);
    }

    @Test
    void number_and_total_are_calculated() {
        ETicket ticket = ticketWithStatus(ETicket.Status.ISSUED);
        assertEquals("555-000007", ticket.getNumber());
        assertEquals(495, ticket.getTotal());
        assertTrue(ticket.toString().contains("555-000007"));
    }
}
