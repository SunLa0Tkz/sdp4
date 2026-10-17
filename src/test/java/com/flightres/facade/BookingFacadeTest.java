package com.flightres.facade;

import com.flightres.passengerservices.MealRequest;
import com.flightres.testing.ReservationWorld;
import com.flightres.testing.ReservationWorld.Attempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.flightres.facade.ChainStatus.*;
import static com.flightres.testing.ReservationWorld.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingFacadeTest {
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
    void a_full_booking_holds_the_seat_issues_the_ticket_orders_the_meal_and_credits_miles() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A")
                .withBags(23).withMeal("VGML").withLoyalty(GOLD_MEMBER).attempt();

        assertTrue(booking.succeeded());
        assertEquals("555-000001", booking.booking().getTicketNumber());
        assertEquals(420, booking.booking().getTotalPrice());
        assertEquals(4743, booking.booking().getMiles());

        world.expectBooking(booking).hasStatus(COMPLETED).undid();
        world.expectSeat(LONG_FLIGHT, "8A").isConfirmed();
        world.expectTicket(booking).isIssued();
        world.expectMeal(booking).hasStatus(MealRequest.Status.CONFIRMED);
        world.expectMilesOfMember(GOLD_MEMBER, 4743);
    }

    @Test
    void the_client_can_look_at_flights_seats_and_prices_through_the_facade() {
        BookingFacade facade = world.bookingFacade();
        assertEquals(3, facade.listFlights().size());
        assertTrue(facade.listFlights().get(0).contains("KC233"));
        assertEquals(48, facade.freeSeats(SHORT_FLIGHT).size());
        assertEquals(1050, facade.priceOf(LONG_FLIGHT, "1A"));
    }

    @Test
    void a_bag_over_32_kg_undoes_only_the_seat() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").withBags(23, 33).attempt();

        assertEquals(COMPENSATED, booking.failure().getBooking().getStatus());
        world.expectBooking(booking).hasStatus(COMPENSATED).failedAt("B").undid("A").because("33 kg");
        world.expectSeat(LONG_FLIGHT, "8A").isFree();
        world.expectTicket(booking).doesNotExist();
        world.expectMeal(booking).doesNotExist();
    }

    @Test
    void an_expired_passport_undoes_only_the_seat() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "9F")
                .withPassportValidUntil(LocalDate.of(2027, 1, 1)).attempt();

        world.expectBooking(booking).hasStatus(COMPENSATED).failedAt("B").undid("A").because("Passport");
        world.expectSeat(LONG_FLIGHT, "9F").isFree();
    }

    @Test
    void a_special_meal_on_a_short_flight_voids_the_ticket_and_then_frees_the_seat() {
        Attempt booking = world.passenger("Aliya Nurlanova").books(SHORT_FLIGHT, "5A").withMeal("KSML").attempt();

        world.expectBooking(booking).hasStatus(COMPENSATED).failedAt("C").undid("B", "A").because("short flight");
        world.expectTicket(booking).isVoided();
        world.expectSeat(SHORT_FLIGHT, "5A").isFree();
        world.expectMeal(booking).doesNotExist();
    }

    @Test
    void a_suspended_loyalty_account_also_cancels_the_meal_that_was_already_ordered() {
        Attempt booking = world.passenger("Timur Akhmetov").books(LONG_FLIGHT, "6D")
                .withMeal("VGML").withLoyalty(SUSPENDED_MEMBER).attempt();

        world.expectBooking(booking).hasStatus(COMPENSATED).failedAt("C").undid("C", "B", "A").because("suspended");
        world.expectMeal(booking).hasStatus(MealRequest.Status.CANCELLED);
        world.expectTicket(booking).isVoided();
        world.expectSeat(LONG_FLIGHT, "6D").isFree();
    }

    @Test
    void a_seat_that_is_already_taken_rejects_the_booking_with_nothing_to_undo() {
        Attempt first = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();
        Attempt second = world.passenger("Daniyar Seitkali").books(LONG_FLIGHT, "8A").attempt();

        world.expectBooking(second).hasStatus(REJECTED).failedAt("A").undid().because("already taken");
        world.expectTicket(second).doesNotExist();
        world.expectBooking(first).hasStatus(COMPLETED);
        world.expectSeat(LONG_FLIGHT, "8A").isConfirmed();
    }

    @Test
    void wrong_input_is_refused_before_anything_is_saved() {
        BookingRequest bad = new BookingRequest(LONG_FLIGHT, "", "R2D2", null, List.of(-5), "PIZZA", "FF-9999");

        InvalidBookingException e = assertThrows(InvalidBookingException.class, () -> world.bookingFacade().book(bad));

        assertEquals(6, e.getProblems().size(), e.getMessage());
        assertTrue(world.adminFacade().allBookings().isEmpty(), "nothing may be saved");
        assertEquals(72, world.bookingFacade().freeSeats(LONG_FLIGHT).size());
    }

    @Test
    void a_missing_flight_number_is_reported() {
        BookingRequest bad = new BookingRequest(null, "1A", "Aliya", LocalDate.of(2032, 1, 1), null, null, " ");
        InvalidBookingException e = assertThrows(InvalidBookingException.class, () -> world.bookingFacade().book(bad));
        assertEquals(List.of("flight number is missing"), e.getProblems());
    }

    @Test
    void resuming_a_finished_booking_gives_the_same_result_again() {
        Attempt ok = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8A").attempt();
        Attempt failed = world.passenger("Aliya Nurlanova").books(LONG_FLIGHT, "8B").withBags(40).attempt();

        assertEquals(COMPLETED, world.bookingFacade().resume(ok.ref()).getStatus());
        assertThrows(BookingFailedException.class, () -> world.bookingFacade().resume(failed.ref()));
        assertThrows(IllegalArgumentException.class, () -> world.bookingFacade().resume("BK999"));
    }
}
