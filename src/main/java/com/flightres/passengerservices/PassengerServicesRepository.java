package com.flightres.passengerservices;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PassengerServicesRepository implements AutoCloseable {
    private final Connection db;

    public PassengerServicesRepository(Path file) {
        try {
            file.toAbsolutePath().getParent().toFile().mkdirs();
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS members (member_no TEXT PRIMARY KEY, name TEXT, "
                        + "tier TEXT, active INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS miles (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "member_no TEXT, booking_ref TEXT, miles INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS meals (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "booking_ref TEXT, flight_no TEXT, meal_code TEXT, status TEXT)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open " + file, e);
        }
    }

    public void saveMember(LoyaltyProgram.Member m) {
        update("INSERT OR REPLACE INTO members VALUES (?, ?, ?, ?)", m.number(), m.name(), m.tier(), m.active() ? 1 : 0);
    }

    public LoyaltyProgram.Member findMember(String memberNo) {
        List<LoyaltyProgram.Member> found = queryMembers("SELECT * FROM members WHERE member_no = ?", memberNo);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<LoyaltyProgram.Member> allMembers() {
        return queryMembers("SELECT * FROM members ORDER BY member_no");
    }

    private List<LoyaltyProgram.Member> queryMembers(String sql, Object... params) {
        List<LoyaltyProgram.Member> result = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new LoyaltyProgram.Member(rs.getString("member_no"), rs.getString("name"),
                        rs.getString("tier"), rs.getInt("active") == 1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("passenger-services.db: " + e.getMessage(), e);
        }
        return result;
    }

    public void addMilesRow(String memberNo, String bookingRef, int miles) {
        update("INSERT INTO miles (member_no, booking_ref, miles) VALUES (?, ?, ?)", memberNo, bookingRef, miles);
    }

    // credits are positive rows, reversals negative
    public int milesForBooking(String bookingRef) {
        return sum("SELECT COALESCE(SUM(miles), 0) FROM miles WHERE booking_ref = ?", bookingRef);
    }

    public int balance(String memberNo) {
        return sum("SELECT COALESCE(SUM(miles), 0) FROM miles WHERE member_no = ?", memberNo);
    }

    public String memberOfBooking(String bookingRef) {
        try (PreparedStatement ps = prepare("SELECT member_no FROM miles WHERE booking_ref = ?", bookingRef);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("passenger-services.db: " + e.getMessage(), e);
        }
    }

    private int sum(String sql, String param) {
        try (PreparedStatement ps = prepare(sql, param); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("passenger-services.db: " + e.getMessage(), e);
        }
    }

    public MealRequest insertMeal(String bookingRef, String flightNo, String mealCode) {
        String sql = "INSERT INTO meals (booking_ref, flight_no, meal_code, status) VALUES (?, ?, ?, 'REQUESTED')";
        try (PreparedStatement ps = prepare(sql, bookingRef, flightNo, mealCode)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new MealRequest(keys.getLong(1), bookingRef, flightNo, mealCode, MealRequest.Status.REQUESTED);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("passenger-services.db: " + e.getMessage(), e);
        }
    }

    public void updateMealStatus(MealRequest meal) {
        update("UPDATE meals SET status = ? WHERE id = ?", meal.getStatus().name(), meal.getId());
    }

    public MealRequest findMeal(long id) {
        List<MealRequest> found = queryMeals("SELECT * FROM meals WHERE id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    public MealRequest findMealByBooking(String bookingRef) {
        List<MealRequest> found = queryMeals("SELECT * FROM meals WHERE booking_ref = ? ORDER BY id DESC", bookingRef);
        return found.isEmpty() ? null : found.get(0);
    }

    private List<MealRequest> queryMeals(String sql, Object... params) {
        List<MealRequest> result = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new MealRequest(rs.getLong("id"), rs.getString("booking_ref"), rs.getString("flight_no"),
                        rs.getString("meal_code"), MealRequest.Status.valueOf(rs.getString("status"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("passenger-services.db: " + e.getMessage(), e);
        }
        return result;
    }

    private void update(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("passenger-services.db: " + e.getMessage(), e);
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
