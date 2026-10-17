package com.flightres.facade;

import com.flightres.passengerservices.MealRequest;
import com.flightres.testing.ReservationWorld;
import com.flightres.testing.ReservationWorld.Attempt;
import com.flightres.ticketing.TicketingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.flightres.facade.ChainStatus.*;
import static com.flightres.testing.ReservationWorld.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingAdminFacadeTest {
    @TempDir
    Path folder;
    ReservationWorld world;
    BookingAdminFacade admin;

    @BeforeEach
    void setUp() {
        world = new ReservationWorld(folder);
        admin = world.adminFacade();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void inspect_shows_what_each_subsystem_saved() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(SHORT_FLIGHT, "5A").withMeal("KSML").attempt();

        String report = admin.inspect(booking.ref());

        assertTrue(report.contains("COMPENSATED (step C failed"), report);
        assertTrue(report.contains("A seat   : KC233 5A  RELEASED"), report);
        assertTrue(report.contains("B ticket : 555-000001  VOIDED"), report);
        assertTrue(report.contains("C meal   : -"), report);
    }

    @Test
    void an_admin_can_cancel_a_completed_booking_which_undoes_C_then_B_then_A() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A")
                .withMeal("GFML").withLoyalty(GOLD_MEMBER).attempt();

        admin.cancelBooking(booking.ref(), "passenger called");

        world.expectBooking(booking).hasStatus(COMPENSATED).failedAt("ADMIN").undid("C", "B", "A");
        world.expectMeal(booking).hasStatus(MealRequest.Status.CANCELLED);
        world.expectTicket(booking).isVoided();
        world.expectSeat(LONG_FLIGHT, "8A").isFree();
        world.expectMilesOfMember(GOLD_MEMBER, 0);
    }

    @Test
    void a_checked_in_passenger_cannot_be_cancelled() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();
        admin.checkIn(booking.ref());

        assertThrows(IllegalStateException.class, () -> admin.cancelBooking(booking.ref(), "too late"));
        world.expectBooking(booking).hasStatus(COMPLETED);
    }

    @Test
    void finished_failures_cannot_be_cancelled_or_checked_in() {
        Attempt failed = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").withBags(35).attempt();

        assertThrows(IllegalStateException.class, () -> admin.cancelBooking(failed.ref(), "again"));
        assertThrows(IllegalStateException.class, () -> admin.checkIn(failed.ref()));
        assertThrows(IllegalArgumentException.class, () -> admin.inspect("BK999"));
        assertEquals(1, admin.allBookings().size());
    }

    @Test
    void if_undo_itself_fails_the_booking_stays_compensating_and_the_seat_is_not_touched() {
        Attempt booking = world.crashAfter("B").passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();
        world.restart();
        var issuer = world.system().ticketIssuer();
        issuer.checkIn(issuer.findTicketByBooking(booking.ref()).getId());

        assertThrows(TicketingException.class, () -> world.adminFacade().rollBackUnfinished());

        world.expectBooking(booking).hasStatus(COMPENSATING).undid();
        world.expectSeat(LONG_FLIGHT, "8A").isHeld();
        assertThrows(TicketingException.class, () -> world.bookingFacade().resume(booking.ref()));
    }

    @Test
    void loyalty_accounts_are_listed_with_their_miles() {
        world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").withLoyalty(GOLD_MEMBER).attempt();
        assertTrue(admin.loyaltyAccounts().get(0).contains("4743 miles"));
        assertTrue(admin.loyaltyAccounts().get(2).contains("SUSPENDED"));
    }
}
