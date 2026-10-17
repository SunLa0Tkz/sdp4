package com.flightres.app;

import com.flightres.facade.BookingChain;
import com.flightres.facade.BookingFailedException;
import com.flightres.facade.BookingRequest;
import com.flightres.facade.InvalidBookingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ConsoleApp {
    private static final LocalDate VALID_PASSPORT = LocalDate.of(2032, 1, 1);

    private final Path dataDir;
    private final BufferedReader in;
    private final PrintStream out;
    private ReservationSystem system;

    public ConsoleApp(Path dataDir, BufferedReader in, PrintStream out) {
        this.dataDir = dataDir;
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of("data");
        if (args.length > 0 && args[0].equals("--reset")) {
            ReservationSystem.deleteAllData(dataDir);
            System.out.println("All saved data was deleted.");
        }
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        new ConsoleApp(dataDir, keyboard, System.out).run();
    }

    public void run() throws IOException {
        system = new ReservationSystem(dataDir);
        out.println("=== FLIGHT RESERVATION: facade over Seating (A) -> Ticketing (B) -> Passenger services (C) ===");
        out.println("Data folder: " + dataDir.toAbsolutePath());
        warnAboutUnfinishedBookings();

        while (true) {
            printMenu();
            String choice = ask("Your choice");
            if (choice == null || choice.equals("0")) {
                break;
            }
            try {
                handle(choice.trim());
            } catch (RuntimeException e) {
                out.println("  ERROR: " + e.getMessage());
            }
        }
        system.close();
        out.println("Bye. Everything is saved - start again to continue.");
    }

    private void printMenu() {
        out.println();
        out.println("--- Client (BookingFacade) ---");
        out.println(" 1  List flights");
        out.println(" 2  Show free seats of a flight");
        out.println(" 3  Book a flight (type your own data)");
        out.println("--- Ready-made scenarios ---");
        out.println(" 4  Successful booking                          A ok, B ok, C ok");
        out.println(" 5  B fails after A  (a bag of 33 kg)           -> undo A");
        out.println(" 6  C fails after A and B (kosher meal on a short flight) -> undo B, then A");
        out.println(" 7  C fails in loyalty (suspended account)      -> undo C, B, then A");
        out.println(" 8  Crash the program in the middle of a booking");
        out.println("--- Admin (BookingAdminFacade) ---");
        out.println(" 9  List all bookings");
        out.println("10  Inspect a booking (what A, B and C saved)");
        out.println("11  Finish unfinished bookings (resume or roll back)");
        out.println("12  Cancel a booking");
        out.println("13  Check a passenger in");
        out.println("14  Loyalty accounts");
        out.println("15  Reset all data");
        out.println(" 0  Exit");
    }

    private void handle(String choice) throws IOException {
        switch (choice) {
            case "1" -> system.booking().listFlights().forEach(line -> out.println("  " + line));
            case "2" -> out.println("  " + String.join(" ", system.booking().freeSeats(askOr("Flight", "KC901"))));
            case "3" -> bookWithOwnData();
            case "4" -> runScenario("KC901", "Aliya Nurlanova", List.of(23), "VGML", "FF-1001");
            case "5" -> runScenario("KC901", "Daniyar Seitkali", List.of(23, 33), "STANDARD", "FF-1002");
            case "6" -> runScenario("KC233", "Aliya Nurlanova", List.of(20), "KSML", "FF-1001");
            case "7" -> runScenario("KC925", "Timur Akhmetov", List.of(18), "MOML", "FF-1003");
            case "8" -> crashDemo();
            case "9" -> system.admin().allBookings().forEach(b -> out.println("  " + b));
            case "10" -> out.println(indent(system.admin().inspect(askBookingRef())));
            case "11" -> finishUnfinished();
            case "12" -> cancel();
            case "13" -> {
                String ref = askBookingRef();
                system.admin().checkIn(ref);
                out.println("  " + ref + " is checked in.");
            }
            case "14" -> system.admin().loyaltyAccounts().forEach(line -> out.println("  " + line));
            case "15" -> reset();
            default -> out.println("  Unknown option.");
        }
    }

    private void bookWithOwnData() throws IOException {
        String flight = askOr("Flight", "KC901").toUpperCase();
        String seat = askOr("Seat", firstEconomySeat(flight)).toUpperCase();
        out.println("  Price of seat " + seat + ": $" + system.booking().priceOf(flight, seat));
        String name = askOr("Passenger name", "Aliya Nurlanova");
        LocalDate passport = readDate(askOr("Passport valid until (yyyy-mm-dd)", VALID_PASSPORT.toString()));
        List<Integer> bags = readNumbers(askOr("Bag weights in kg, e.g. 23,18 ('-' = no bags)", "23"));
        String meal = askOr("Meal: STANDARD, VGML, KSML, MOML, DBML, GFML", "STANDARD");
        String loyalty = askOr("Frequent-flyer number: FF-1001, FF-1002, FF-1003 ('-' = none)", "-");
        book(new BookingRequest(flight, seat, name, passport, bags, meal, loyalty.equals("-") ? null : loyalty));
    }

    private void runScenario(String flight, String name, List<Integer> bags, String meal, String loyalty) {
        String seat = firstEconomySeat(flight);
        out.println("  Request: " + name + ", flight " + flight + ", seat " + seat + ", bags " + bags + " kg, meal "
                + meal + ", frequent flyer " + loyalty);
        book(new BookingRequest(flight, seat, name, VALID_PASSPORT, bags, meal, loyalty));
    }

    private void book(BookingRequest request) {
        try {
            BookingChain booking = system.booking().book(request);
            out.println("  SUCCESS: " + booking);
            out.println(indent(system.admin().inspect(booking.getBookingRef())));
        } catch (InvalidBookingException e) {
            out.println("  REFUSED, nothing was saved:");
            e.getProblems().forEach(p -> out.println("    - " + p));
        } catch (BookingFailedException e) {
            out.println("  FAILED: " + e.getMessage());
            out.println("  Undone steps, in this order: " + e.getBooking().getUndoneSteps());
            out.println(indent(system.admin().inspect(e.getBooking().getBookingRef())));
        }
    }

    private void crashDemo() {
        String step;
        try {
            step = askOr("Crash right after which step was saved: A, B or C", "B").toUpperCase();
        } catch (IOException e) {
            return;
        }
        system.booking().simulateCrashAfter(step, () -> {
            out.println();
            out.println("  !!! CRASH after step " + step + ". The program stops NOW (no clean-up).");
            out.println("  !!! Start it again: it will find the unfinished booking on disk.");
            out.flush();
            Runtime.getRuntime().halt(1);
        });
        runScenario("KC901", "Aliya Nurlanova", List.of(23), "VGML", "FF-1001");
        system.booking().simulateCrashAfter(null, null);
    }

    private void warnAboutUnfinishedBookings() {
        List<BookingChain> unfinished = system.admin().unfinishedBookings();
        if (unfinished.isEmpty()) {
            return;
        }
        out.println();
        out.println("WARNING: " + unfinished.size() + " booking(s) were not finished (the program stopped). Read from disk:");
        for (BookingChain b : unfinished) {
            out.println(indent(system.admin().inspect(b.getBookingRef())));
        }
        out.println("Use option 11 to resume them or roll them back.");
    }

    private void finishUnfinished() throws IOException {
        List<BookingChain> unfinished = system.admin().unfinishedBookings();
        if (unfinished.isEmpty()) {
            out.println("  There are no unfinished bookings.");
            return;
        }
        unfinished.forEach(b -> out.println("  " + b));
        String answer = askOr("r = resume (continue the booking), b = roll back (undo it)", "r");
        if (answer.equalsIgnoreCase("b")) {
            system.admin().rollBackUnfinished().forEach(b -> out.println("  Rolled back: " + b));
            return;
        }
        for (BookingChain b : unfinished) {
            try {
                out.println("  Resumed: " + system.booking().resume(b.getBookingRef()));
            } catch (BookingFailedException e) {
                out.println("  " + e.getMessage());
            }
        }
    }

    private void cancel() throws IOException {
        String ref = askBookingRef();
        BookingChain booking = system.admin().cancelBooking(ref, askOr("Reason", "cancelled by the passenger"));
        out.println("  Cancelled. Undone steps, in this order: " + booking.getUndoneSteps());
        out.println(indent(system.admin().inspect(ref)));
    }

    private void reset() throws IOException {
        if (askOr("Delete ALL bookings? (y/n)", "n").equalsIgnoreCase("y")) {
            system.close();
            ReservationSystem.deleteAllData(dataDir);
            system = new ReservationSystem(dataDir);
            out.println("  All data deleted, demo flights created again.");
        }
    }

    private String firstEconomySeat(String flight) {
        for (String seat : system.booking().freeSeats(flight)) {
            int row = Integer.parseInt(seat.substring(0, seat.length() - 1));
            if (row >= 5) {
                return seat;
            }
        }
        throw new IllegalStateException("Flight " + flight + " is full");
    }

    private String askBookingRef() throws IOException {
        return askOr("Booking reference", "BK001").toUpperCase();
    }

    private static LocalDate readDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Integer> readNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        if (text.equals("-")) {
            return numbers;
        }
        for (String part : text.split(",")) {
            try {
                numbers.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                numbers.add(-1);
            }
        }
        return numbers;
    }

    private static String indent(String text) {
        return "    " + text.replace("\n", "\n    ");
    }

    private String askOr(String question, String defaultAnswer) throws IOException {
        String answer = ask(question + " [" + defaultAnswer + "]");
        return (answer == null || answer.isBlank()) ? defaultAnswer : answer.trim();
    }

    private String ask(String question) throws IOException {
        out.print("> " + question + ": ");
        out.flush();
        return in.readLine();
    }
}
