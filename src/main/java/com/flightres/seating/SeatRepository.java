package com.flightres.seating;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SeatRepository implements AutoCloseable {
    private final Connection db;

    public SeatRepository(Path file) {
        try {
            file.toAbsolutePath().getParent().toFile().mkdirs();
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS flights (flight_no TEXT PRIMARY KEY, origin TEXT, "
                        + "destination TEXT, flight_date TEXT, distance_km INTEGER, seat_rows INTEGER, base_fare INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS seat_holds (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "booking_ref TEXT, flight_no TEXT, seat_no TEXT, cabin TEXT, passenger TEXT, "
                        + "price INTEGER, status TEXT)");

                // db also blocks two active holds on one seat
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS one_hold_per_seat "
                        + "ON seat_holds(flight_no, seat_no) WHERE status <> 'RELEASED'");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open " + file, e);
        }
    }

    public void saveFlight(Flight f) {
        update("INSERT OR REPLACE INTO flights VALUES (?, ?, ?, ?, ?, ?, ?)",
                f.flightNo(), f.from(), f.to(), f.date().toString(), f.distanceKm(), f.rows(), f.baseFare());
    }

    public Flight findFlight(String flightNo) {
        List<Flight> found = queryFlights("SELECT * FROM flights WHERE flight_no = ?", flightNo);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Flight> allFlights() {
        return queryFlights("SELECT * FROM flights ORDER BY flight_date");
    }

    private List<Flight> queryFlights(String sql, Object... params) {
        List<Flight> result = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Flight(rs.getString("flight_no"), rs.getString("origin"), rs.getString("destination"),
                        LocalDate.parse(rs.getString("flight_date")), rs.getInt("distance_km"),
                        rs.getInt("seat_rows"), rs.getInt("base_fare")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("seating.db: " + e.getMessage(), e);
        }
        return result;
    }

    public SeatHold insertHold(String bookingRef, String flightNo, String seatNo, String cabin,
                               String passenger, int price) {
        String sql = "INSERT INTO seat_holds (booking_ref, flight_no, seat_no, cabin, passenger, price, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'HELD')";
        try (PreparedStatement ps = prepare(sql, bookingRef, flightNo, seatNo, cabin, passenger, price)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new SeatHold(keys.getLong(1), bookingRef, flightNo, seatNo, cabin, passenger, price,
                        SeatHold.Status.HELD);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                throw new SeatingException("Seat " + seatNo + " on " + flightNo + " is already taken");
            }
            throw new IllegalStateException("seating.db: " + e.getMessage(), e);
        }
    }

    public void updateStatus(SeatHold hold) {
        update("UPDATE seat_holds SET status = ? WHERE id = ?", hold.getStatus().name(), hold.getId());
    }

    public SeatHold findHold(long id) {
        List<SeatHold> found = queryHolds("SELECT * FROM seat_holds WHERE id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    public SeatHold findHoldByBooking(String bookingRef) {
        List<SeatHold> found = queryHolds("SELECT * FROM seat_holds WHERE booking_ref = ? ORDER BY id DESC", bookingRef);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<SeatHold> holdsOnFlight(String flightNo) {
        return queryHolds("SELECT * FROM seat_holds WHERE flight_no = ? ORDER BY id", flightNo);
    }

    private List<SeatHold> queryHolds(String sql, Object... params) {
        List<SeatHold> result = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new SeatHold(rs.getLong("id"), rs.getString("booking_ref"), rs.getString("flight_no"),
                        rs.getString("seat_no"), rs.getString("cabin"), rs.getString("passenger"),
                        rs.getInt("price"), SeatHold.Status.valueOf(rs.getString("status"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("seating.db: " + e.getMessage(), e);
        }
        return result;
    }

    private void update(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("seating.db: " + e.getMessage(), e);
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
