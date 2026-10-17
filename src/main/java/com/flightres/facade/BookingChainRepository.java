package com.flightres.facade;

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

public class BookingChainRepository implements AutoCloseable {
    private final Connection db;

    public BookingChainRepository(Path file) {
        try {
            file.toAbsolutePath().getParent().toFile().mkdirs();
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS bookings ("
                        + "booking_ref TEXT PRIMARY KEY, flight_no TEXT, seat_no TEXT, passenger TEXT, "
                        + "passport_expiry TEXT, bags TEXT, meal_code TEXT, loyalty_no TEXT, "
                        + "status TEXT, hold_id INTEGER, ticket_id INTEGER, meal_id INTEGER, ticket_number TEXT, "
                        + "total_price INTEGER, miles INTEGER, failed_step TEXT, failure_reason TEXT, undone_steps TEXT)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open " + file, e);
        }
    }

    public String nextBookingRef() {
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bookings")) {
            rs.next();
            return String.format("BK%03d", rs.getInt(1) + 1);
        } catch (SQLException e) {
            throw new IllegalStateException("bookings.db: " + e.getMessage(), e);
        }
    }

    public void save(BookingChain b) {
        BookingRequest r = b.getRequest();
        String sql = "INSERT OR REPLACE INTO bookings VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            Object[] values = {
                    b.getBookingRef(), r.flightNo(), r.seatNo(), r.passengerName(),
                    r.passportExpiry() == null ? null : r.passportExpiry().toString(),
                    joinNumbers(r.bagWeightsKg()), r.mealCode(), r.loyaltyNumber(),
                    b.getStatus().name(), b.getHoldId(), b.getTicketId(), b.getMealId(), b.getTicketNumber(),
                    b.getTotalPrice(), b.getMiles(), b.getFailedStep(), b.getFailureReason(),
                    String.join(",", b.getUndoneSteps())
            };
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("bookings.db: " + e.getMessage(), e);
        }
    }

    public BookingChain find(String bookingRef) {
        List<BookingChain> found = query("SELECT * FROM bookings WHERE booking_ref = ?", bookingRef);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<BookingChain> findAll() {
        return query("SELECT * FROM bookings ORDER BY booking_ref");
    }

    public List<BookingChain> findUnfinished() {
        return query("SELECT * FROM bookings WHERE status IN ('STARTED', 'PENDING_B', 'PENDING_C', 'COMPENSATING') "
                + "ORDER BY booking_ref");
    }

    private List<BookingChain> query(String sql, String... params) {
        List<BookingChain> result = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("bookings.db: " + e.getMessage(), e);
        }
        return result;
    }

    private BookingChain readRow(ResultSet rs) throws SQLException {
        String passport = rs.getString("passport_expiry");
        BookingRequest request = new BookingRequest(rs.getString("flight_no"), rs.getString("seat_no"),
                rs.getString("passenger"), passport == null ? null : LocalDate.parse(passport),
                splitNumbers(rs.getString("bags")), rs.getString("meal_code"), rs.getString("loyalty_no"));

        BookingChain b = new BookingChain(rs.getString("booking_ref"), request);
        b.setStatus(ChainStatus.valueOf(rs.getString("status")));
        b.setHoldId(readLong(rs, "hold_id"));
        b.setTicketId(readLong(rs, "ticket_id"));
        b.setMealId(readLong(rs, "meal_id"));
        b.setTicketNumber(rs.getString("ticket_number"));
        b.setTotalPrice(rs.getInt("total_price"));
        b.setMiles(rs.getInt("miles"));
        b.setFailure(rs.getString("failed_step"), rs.getString("failure_reason"));
        for (String step : rs.getString("undone_steps").split(",")) {
            if (!step.isEmpty()) {
                b.addUndoneStep(step);
            }
        }
        return b;
    }

    private static Long readLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String joinNumbers(List<Integer> numbers) {
        List<String> parts = new ArrayList<>();
        for (Integer n : numbers) {
            parts.add(String.valueOf(n));
        }
        return String.join(",", parts);
    }

    private static List<Integer> splitNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        for (String part : text.split(",")) {
            if (!part.isEmpty()) {
                numbers.add(Integer.parseInt(part));
            }
        }
        return numbers;
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
