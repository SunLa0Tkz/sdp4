package com.flightres.seating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeatHoldTest {
    private SeatHold holdWithStatus(SeatHold.Status status) {
        return new SeatHold(1, "BK001", "KC901", "8A", "ECONOMY", "Aliya", 420, status);
    }

    @Test
    void a_held_seat_can_be_confirmed() {
        SeatHold hold = holdWithStatus(SeatHold.Status.HELD);
        hold.confirm();
        assertEquals(SeatHold.Status.CONFIRMED, hold.getStatus());
    }

    @Test
    void a_held_or_confirmed_seat_can_be_released() {
        SeatHold held = holdWithStatus(SeatHold.Status.HELD);
        held.release();
        assertFalse(held.isActive());

        SeatHold confirmed = holdWithStatus(SeatHold.Status.CONFIRMED);
        confirmed.release();
        assertEquals(SeatHold.Status.RELEASED, confirmed.getStatus());
    }

    @Test
    void a_released_seat_cannot_change_any_more() {
        SeatHold hold = holdWithStatus(SeatHold.Status.RELEASED);
        assertThrows(IllegalStateException.class, hold::confirm);
        assertThrows(IllegalStateException.class, hold::release);
    }

    @Test
    void a_confirmed_seat_cannot_be_confirmed_again() {
        SeatHold hold = holdWithStatus(SeatHold.Status.CONFIRMED);
        assertThrows(IllegalStateException.class, hold::confirm);
        assertTrue(hold.toString().contains("CONFIRMED"));
    }
}
