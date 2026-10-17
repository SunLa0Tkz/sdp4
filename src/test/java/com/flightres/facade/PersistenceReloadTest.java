package com.flightres.facade;

import com.flightres.passengerservices.MealRequest;
import com.flightres.testing.ReservationWorld;
import com.flightres.testing.ReservationWorld.Attempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.flightres.facade.ChainStatus.*;
import static com.flightres.testing.ReservationWorld.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceReloadTest {
    @TempDir
    Path folder;
    ReservationWorld world;

    @BeforeEach
    void setUp() {
        world = new ReservationWorld(folder);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void crash_after_A_then_resume_finishes_the_booking_with_the_same_seat_hold() {
        Attempt booking = world.crashAfter("A").passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();

        world.restart();
        world.expectBooking(booking).hasStatus(STARTED);
        world.expectSeat(LONG_FLIGHT, "8A").isHeld();

        world.bookingFacade().resume(booking.ref());

        world.expectBooking(booking).hasStatus(COMPLETED);
        world.expectSeat(LONG_FLIGHT, "8A").isConfirmed();
        assertEquals(1, world.system().seatMap().holdsOnFlight(LONG_FLIGHT).size(), "no second seat hold");
    }

    @Test
    void crash_after_B_then_resume_does_not_issue_a_second_ticket() {
        Attempt booking = world.crashAfter("B").passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();

        world.restart();
        world.expectBooking(booking).hasStatus(PENDING_B);
        world.expectTicket(booking).isIssued();
        assertEquals(1, world.adminFacade().unfinishedBookings().size());

        world.bookingFacade().resume(booking.ref());

        world.expectBooking(booking).hasStatus(COMPLETED);
        world.expectTicket(booking).existsOnlyOnce();
        assertTrue(world.adminFacade().unfinishedBookings().isEmpty());
    }

    @Test
    void crash_after_B_then_roll_back_undoes_B_and_A_by_looking_at_the_subsystems() {
        Attempt booking = world.crashAfter("B").passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();

        world.restart();
        world.adminFacade().rollBackUnfinished();

        world.expectBooking(booking).hasStatus(COMPENSATED).failedAt("B").undid("B", "A").because("program stopped");
        world.expectTicket(booking).isVoided();
        world.expectSeat(LONG_FLIGHT, "8A").isFree();
    }

    @Test
    void crash_after_C_then_resume_does_not_credit_miles_twice() {
        Attempt booking = world.crashAfter("C").passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A")
                .withMeal("VGML").withLoyalty(GOLD_MEMBER).attempt();

        world.restart();
        world.expectBooking(booking).hasStatus(PENDING_C);
        world.expectMeal(booking).hasStatus(MealRequest.Status.REQUESTED);

        world.bookingFacade().resume(booking.ref());

        world.expectBooking(booking).hasStatus(COMPLETED);
        world.expectMeal(booking).hasStatus(MealRequest.Status.CONFIRMED);
        world.expectMilesOfMember(GOLD_MEMBER, 4743);
    }

    @Test
    void a_completed_booking_is_still_complete_after_a_restart() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "1A").withLoyalty(GOLD_MEMBER).attempt();

        world.restart();

        String report = world.adminFacade().inspect(booking.ref());
        assertTrue(report.contains("COMPLETED"), report);
        assertTrue(report.contains("KC901 1A  CONFIRMED"), report);
        assertTrue(report.contains("C miles  : 7115"), report);
        assertEquals("BK002", world.bookingFacade().book(
                world.passenger("Daniyar Seitkali").books(LONG_FLIGHT, "1B").request()).getBookingRef());
    }
}
