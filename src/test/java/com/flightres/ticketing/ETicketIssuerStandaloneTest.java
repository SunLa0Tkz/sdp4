package com.flightres.ticketing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ETicketIssuerStandaloneTest {
    private static final LocalDate FLIGHT_DATE = LocalDate.of(2027, 3, 15);

    @TempDir
    Path folder;
    TicketRepository repository;
    ETicketIssuer issuer;

    @BeforeEach
    void setUp() {
        repository = new TicketRepository(folder.resolve("ticketing.db"));
        issuer = new ETicketIssuer(repository, new BaggageRulesEngine());
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private TicketRequest request(String bookingRef, LocalDate passportExpiry, Integer... bags) {
        return new TicketRequest(bookingRef, "Aliya Nurlanova", passportExpiry, "KC901", "8A", FLIGHT_DATE,
                "ECONOMY", 420, List.of(bags));
    }

    @Test
    void issuing_saves_a_ticket_with_its_baggage_fee() {
        ETicket ticket = issuer.issue(request("BK001", LocalDate.of(2030, 1, 1), 28));

        ETicket saved = issuer.getTicket(ticket.getId());
        assertEquals("555-000001", saved.getNumber());
        assertEquals(ETicket.Status.ISSUED, saved.getStatus());
        assertEquals(495, saved.getTotal());
    }

    @Test
    void issuing_again_for_the_same_booking_returns_the_same_ticket() {
        ETicket first = issuer.issue(request("BK001", LocalDate.of(2030, 1, 1)));
        ETicket second = issuer.issue(request("BK001", LocalDate.of(2030, 1, 1)));
        assertEquals(first.getId(), second.getId());
        assertEquals(1, issuer.allTickets().size());
    }

    @Test
    void a_bag_over_32_kg_means_no_ticket_is_saved() {
        assertThrows(TicketingException.class, () -> issuer.issue(request("BK001", LocalDate.of(2030, 1, 1), 23, 33)));
        assertNull(issuer.findTicketByBooking("BK001"));
    }

    @Test
    void a_passport_that_expires_before_the_flight_is_refused() {
        assertThrows(TicketingException.class, () -> issuer.issue(request("BK001", LocalDate.of(2027, 3, 1))));
        assertThrows(TicketingException.class, () -> issuer.issue(request("BK001", null)));
    }

    @Test
    void voiding_can_be_repeated_but_not_after_check_in() {
        ETicket a = issuer.issue(request("BK001", LocalDate.of(2030, 1, 1)));
        issuer.voidTicket(a.getId());
        assertEquals(ETicket.Status.VOIDED, issuer.voidTicket(a.getId()).getStatus());

        ETicket b = issuer.issue(request("BK002", LocalDate.of(2030, 1, 1)));
        issuer.checkIn(b.getId());
        assertThrows(TicketingException.class, () -> issuer.voidTicket(b.getId()));
        assertThrows(TicketingException.class, () -> issuer.checkIn(b.getId()));
        assertThrows(TicketingException.class, () -> issuer.getTicket(99));
    }

    @Test
    void tickets_are_still_there_after_the_database_is_closed_and_opened_again() {
        ETicket ticket = issuer.issue(request("BK001", LocalDate.of(2030, 1, 1)));
        repository.close();

        repository = new TicketRepository(folder.resolve("ticketing.db"));
        issuer = new ETicketIssuer(repository, new BaggageRulesEngine());

        assertEquals(ticket.getId(), issuer.findTicketByBooking("BK001").getId());
    }
}
