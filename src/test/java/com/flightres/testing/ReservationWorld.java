package com.flightres.testing;

import com.flightres.app.ReservationSystem;
import com.flightres.facade.BookingAdminFacade;
import com.flightres.facade.BookingChain;
import com.flightres.facade.BookingFacade;
import com.flightres.facade.BookingFailedException;
import com.flightres.facade.BookingRequest;
import com.flightres.facade.ChainStatus;
import com.flightres.passengerservices.MealRequest;
import com.flightres.seating.SeatHold;
import com.flightres.ticketing.ETicket;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReservationWorld implements AutoCloseable {
    public static final String LONG_FLIGHT = "KC901";
    public static final String SHORT_FLIGHT = "KC233";
    public static final String GOLD_MEMBER = "FF-1001";
    public static final String SUSPENDED_MEMBER = "FF-1003";

    // Error, so the facade's catch(RuntimeException) doesn't catch it
    public static class SimulatedCrash extends Error {
        public SimulatedCrash(String step) {
            super("simulated crash after step " + step);
        }
    }

    private final Path folder;
    private ReservationSystem system;

    public ReservationWorld(Path folder) {
        this.folder = folder;
        this.system = new ReservationSystem(folder);
    }

    public void restart() {
        system.close();
        system = new ReservationSystem(folder);
    }

    public ReservationWorld crashAfter(String step) {
        system.booking().simulateCrashAfter(step, () -> {
            throw new SimulatedCrash(step);
        });
        return this;
    }

    public BookingFacade bookingFacade() { return system.booking(); }
    public BookingAdminFacade adminFacade() { return system.admin(); }
    public ReservationSystem system() { return system; }

    @Override
    public void close() {
        system.close();
    }

    public Draft passenger(String name) {
        return new Draft(name);
    }

    public class Draft {
        private final String name;
        private String flight;
        private String seat;
        private LocalDate passport = LocalDate.of(2032, 1, 1);
        private List<Integer> bags = List.of(20);
        private String meal = "STANDARD";
        private String loyaltyNo;

        Draft(String name) {
            this.name = name;
        }

        public Draft books(String flight, String seat) {
            this.flight = flight;
            this.seat = seat;
            return this;
        }

        public Draft withBags(Integer... kg) {
            this.bags = Arrays.asList(kg);
            return this;
        }

        public Draft withMeal(String mealCode) {
            this.meal = mealCode;
            return this;
        }

        public Draft withLoyalty(String memberNo) {
            this.loyaltyNo = memberNo;
            return this;
        }

        public Draft withPassportValidUntil(LocalDate date) {
            this.passport = date;
            return this;
        }

        public BookingRequest request() {
            return new BookingRequest(flight, seat, name, passport, bags, meal, loyaltyNo);
        }

        public Attempt attempt() {
            try {
                BookingChain booking = system.booking().book(request());
                return new Attempt(booking.getBookingRef(), booking, null);
            } catch (BookingFailedException e) {
                return new Attempt(e.getBooking().getBookingRef(), null, e);
            } catch (SimulatedCrash crash) {
                List<BookingChain> all = system.admin().allBookings();
                return new Attempt(all.get(all.size() - 1).getBookingRef(), null, null);
            }
        }
    }

    public record Attempt(String ref, BookingChain booking, BookingFailedException failure) {
        public boolean succeeded() {
            return booking != null;
        }
    }

    public BookingCheck expectBooking(Attempt attempt) {
        return new BookingCheck(system.admin().allBookings().stream()
                .filter(b -> b.getBookingRef().equals(attempt.ref())).findFirst().orElseThrow());
    }

    public static class BookingCheck {
        private final BookingChain booking;

        BookingCheck(BookingChain booking) {
            this.booking = booking;
        }

        public BookingCheck hasStatus(ChainStatus status) {
            assertEquals(status, booking.getStatus(), "status of " + booking.getBookingRef());
            return this;
        }

        public BookingCheck failedAt(String step) {
            assertEquals(step, booking.getFailedStep(), "failed step of " + booking.getBookingRef());
            return this;
        }

        public BookingCheck undid(String... steps) {
            assertEquals(List.of(steps), booking.getUndoneSteps(), "undone steps of " + booking.getBookingRef());
            return this;
        }

        public BookingCheck because(String textInReason) {
            assertTrue(booking.getFailureReason().contains(textInReason),
                    "reason '" + booking.getFailureReason() + "' should contain '" + textInReason + "'");
            return this;
        }
    }

    public SeatCheck expectSeat(String flight, String seat) {
        return new SeatCheck(flight, seat);
    }

    public class SeatCheck {
        private final String flight;
        private final String seat;

        SeatCheck(String flight, String seat) {
            this.flight = flight;
            this.seat = seat;
        }

        public void isFree() {
            assertTrue(system.seatMap().freeSeats(flight).contains(seat), seat + " should be free");
        }

        public void isConfirmed() {
            assertEquals(SeatHold.Status.CONFIRMED, activeHold().getStatus());
        }

        public void isHeld() {
            assertEquals(SeatHold.Status.HELD, activeHold().getStatus());
        }

        private SeatHold activeHold() {
            return system.seatMap().holdsOnFlight(flight).stream()
                    .filter(h -> h.getSeatNo().equals(seat) && h.isActive())
                    .findFirst().orElseThrow(() -> new AssertionError(seat + " is not taken"));
        }
    }

    public TicketCheck expectTicket(Attempt attempt) {
        return new TicketCheck(attempt.ref());
    }

    public class TicketCheck {
        private final String ref;

        TicketCheck(String ref) {
            this.ref = ref;
        }

        public void doesNotExist() {
            assertNull(system.ticketIssuer().findTicketByBooking(ref), "there should be no ticket for " + ref);
        }

        public void isIssued() {
            assertEquals(ETicket.Status.ISSUED, ticket().getStatus());
        }

        public void isVoided() {
            assertEquals(ETicket.Status.VOIDED, ticket().getStatus());
        }

        public void existsOnlyOnce() {
            long count = system.ticketIssuer().allTickets().stream().filter(t -> t.getBookingRef().equals(ref)).count();
            assertEquals(1, count, "number of tickets for " + ref);
        }

        private ETicket ticket() {
            ETicket ticket = system.ticketIssuer().findTicketByBooking(ref);
            assertNotNull(ticket, "there should be a ticket for " + ref);
            return ticket;
        }
    }

    public MealCheck expectMeal(Attempt attempt) {
        return new MealCheck(attempt.ref());
    }

    public class MealCheck {
        private final String ref;

        MealCheck(String ref) {
            this.ref = ref;
        }

        public void doesNotExist() {
            assertNull(system.meals().findMealByBooking(ref), "there should be no meal for " + ref);
        }

        public void hasStatus(MealRequest.Status status) {
            MealRequest meal = system.meals().findMealByBooking(ref);
            assertNotNull(meal, "there should be a meal for " + ref);
            assertEquals(status, meal.getStatus());
        }
    }

    public void expectMilesOfMember(String memberNo, int miles) {
        assertEquals(miles, system.loyalty().balance(memberNo), "miles of " + memberNo);
    }
}
