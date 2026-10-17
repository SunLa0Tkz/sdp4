package com.flightres.seating;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SeatMapStandaloneTest {
    @TempDir
    Path folder;
    SeatRepository repository;
    SeatMap seatMap;

    @BeforeEach
    void setUp() {
        repository = new SeatRepository(folder.resolve("seating.db"));
        seatMap = new SeatMap(repository, new FareCalculator());
        seatMap.addFlight(new Flight("KC901", "ALA", "FRA", LocalDate.of(2027, 3, 15), 5100, 10, 400));
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void holding_a_free_seat_saves_it_with_its_price() {
        SeatHold hold = seatMap.holdSeat("BK001", "KC901", "8A", "Aliya Nurlanova");

        SeatHold saved = seatMap.getHold(hold.getId());
        assertEquals(SeatHold.Status.HELD, saved.getStatus());
        assertEquals("ECONOMY", saved.getCabin());
        assertEquals(400, saved.getPrice());
        assertEquals(hold.getId(), seatMap.findHoldByBooking("BK001").getId());
        assertFalse(seatMap.freeSeats("KC901").contains("8A"));
    }

    @Test
    void the_same_seat_cannot_be_held_twice() {
        seatMap.holdSeat("BK001", "KC901", "1A", "Aliya");
        assertThrows(SeatingException.class, () -> seatMap.holdSeat("BK002", "KC901", "1A", "Daniyar"));
    }

    @Test
    void the_database_also_refuses_a_second_hold_on_the_same_seat() {
        repository.insertHold("BK001", "KC901", "3C", "ECONOMY", "Aliya", 480);
        assertThrows(SeatingException.class,
                () -> repository.insertHold("BK002", "KC901", "3C", "ECONOMY", "Daniyar", 480));
    }

    @Test
    void releasing_frees_the_seat_and_can_be_repeated() {
        SeatHold hold = seatMap.holdSeat("BK001", "KC901", "5B", "Aliya");
        seatMap.release(hold.getId());
        seatMap.release(hold.getId());
        assertTrue(seatMap.freeSeats("KC901").contains("5B"));
        assertEquals(60, seatMap.freeSeats("KC901").size());
    }

    @Test
    void confirming_can_be_repeated_but_a_released_seat_cannot_be_confirmed() {
        SeatHold hold = seatMap.holdSeat("BK001", "KC901", "1C", "Aliya");
        seatMap.confirm(hold.getId());
        assertEquals(SeatHold.Status.CONFIRMED, seatMap.confirm(hold.getId()).getStatus());

        SeatHold other = seatMap.holdSeat("BK002", "KC901", "1D", "Daniyar");
        seatMap.release(other.getId());
        assertThrows(IllegalStateException.class, () -> seatMap.confirm(other.getId()));
    }

    @Test
    void holds_are_still_there_after_the_database_is_closed_and_opened_again() {
        SeatHold hold = seatMap.holdSeat("BK001", "KC901", "2F", "Aliya");
        repository.close();

        repository = new SeatRepository(folder.resolve("seating.db"));
        seatMap = new SeatMap(repository, new FareCalculator());

        assertEquals(SeatHold.Status.HELD, seatMap.getHold(hold.getId()).getStatus());
        assertEquals(1, seatMap.allFlights().size());
        assertEquals(1, seatMap.holdsOnFlight("KC901").size());
        assertEquals(1000, seatMap.priceOf("KC901", "2F"));
    }

    @Test
    void wrong_seats_and_flights_are_refused() {
        assertThrows(SeatingException.class, () -> seatMap.holdSeat("BK001", "KC901", "8G", "Aliya"));
        assertThrows(SeatingException.class, () -> seatMap.holdSeat("BK001", "KC901", "11A", "Aliya"));
        assertThrows(SeatingException.class, () -> seatMap.holdSeat("BK001", "XX000", "1A", "Aliya"));
        assertThrows(SeatingException.class, () -> seatMap.holdSeat("BK001", "KC901", null, "Aliya"));
        assertThrows(SeatingException.class, () -> seatMap.getHold(99));
        assertNull(seatMap.findHoldByBooking("BK999"));
    }
}
