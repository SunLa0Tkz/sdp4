package com.flightres.ticketing;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TicketRepository implements AutoCloseable {
    private final Connection db;

    public TicketRepository(Path file) {
        try {
            file.toAbsolutePath().getParent().toFile().mkdirs();
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS tickets (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "booking_ref TEXT, passenger TEXT, flight_no TEXT, seat_no TEXT, price INTEGER, "
                        + "baggage_fee INTEGER, status TEXT)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open " + file, e);
        }
    }

    public ETicket insertTicket(String bookingRef, String passenger, String flightNo, String seatNo,
                                int price, int baggageFee) {
        String sql = "INSERT INTO tickets (booking_ref, passenger, flight_no, seat_no, price, baggage_fee, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'ISSUED')";
        try (PreparedStatement ps = prepare(sql, bookingRef, passenger, flightNo, seatNo, price, baggageFee)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new ETicket(keys.getLong(1), bookingRef, passenger, flightNo, seatNo, price, baggageFee,
                        ETicket.Status.ISSUED);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ticketing.db: " + e.getMessage(), e);
        }
    }

    public void updateStatus(ETicket ticket) {
        try (PreparedStatement ps = prepare("UPDATE tickets SET status = ? WHERE id = ?",
                ticket.getStatus().name(), ticket.getId())) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("ticketing.db: " + e.getMessage(), e);
        }
    }

    public ETicket findTicket(long id) {
        List<ETicket> found = query("SELECT * FROM tickets WHERE id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    public ETicket findTicketByBooking(String bookingRef) {
        List<ETicket> found = query("SELECT * FROM tickets WHERE booking_ref = ? ORDER BY id DESC", bookingRef);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<ETicket> allTickets() {
        return query("SELECT * FROM tickets ORDER BY id");
    }

    private List<ETicket> query(String sql, Object... params) {
        List<ETicket> result = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ETicket(rs.getLong("id"), rs.getString("booking_ref"), rs.getString("passenger"),
                        rs.getString("flight_no"), rs.getString("seat_no"), rs.getInt("price"),
                        rs.getInt("baggage_fee"), ETicket.Status.valueOf(rs.getString("status"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ticketing.db: " + e.getMessage(), e);
        }
        return result;
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
