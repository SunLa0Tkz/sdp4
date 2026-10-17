package com.flightres.app;

import com.flightres.facade.BookingAdminFacade;
import com.flightres.facade.BookingChainRepository;
import com.flightres.facade.BookingFacade;
import com.flightres.passengerservices.LoyaltyProgram;
import com.flightres.passengerservices.MealPreferenceService;
import com.flightres.passengerservices.MilesCalculator;
import com.flightres.passengerservices.PassengerServicesRepository;
import com.flightres.seating.FareCalculator;
import com.flightres.seating.Flight;
import com.flightres.seating.SeatMap;
import com.flightres.seating.SeatRepository;
import com.flightres.ticketing.BaggageRulesEngine;
import com.flightres.ticketing.ETicketIssuer;
import com.flightres.ticketing.TicketRepository;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;

public class ReservationSystem implements AutoCloseable {
    public static final String[] DATA_FILES = {"seating.db", "ticketing.db", "passenger-services.db", "bookings.db"};

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final PassengerServicesRepository servicesRepository;
    private final BookingChainRepository bookingRepository;

    private final SeatMap seatMap;
    private final ETicketIssuer ticketIssuer;
    private final MealPreferenceService meals;
    private final LoyaltyProgram loyalty;
    private final BookingFacade bookingFacade;
    private final BookingAdminFacade adminFacade;

    public ReservationSystem(Path dataDir) {
        seatRepository = new SeatRepository(dataDir.resolve("seating.db"));
        ticketRepository = new TicketRepository(dataDir.resolve("ticketing.db"));
        servicesRepository = new PassengerServicesRepository(dataDir.resolve("passenger-services.db"));
        bookingRepository = new BookingChainRepository(dataDir.resolve("bookings.db"));

        seatMap = new SeatMap(seatRepository, new FareCalculator());
        ticketIssuer = new ETicketIssuer(ticketRepository, new BaggageRulesEngine());
        meals = new MealPreferenceService(servicesRepository);
        loyalty = new LoyaltyProgram(servicesRepository, new MilesCalculator());

        bookingFacade = new BookingFacade(seatMap, ticketIssuer, meals, loyalty, bookingRepository);
        adminFacade = new BookingAdminFacade(seatMap, ticketIssuer, meals, loyalty, bookingRepository);

        if (seatMap.allFlights().isEmpty()) {
            addDemoData();
        }
    }

    private void addDemoData() {
        seatMap.addFlight(new Flight("KC901", "ALA", "FRA", LocalDate.of(2027, 3, 15), 5100, 12, 420));
        seatMap.addFlight(new Flight("KC925", "ALA", "IST", LocalDate.of(2027, 3, 20), 4050, 10, 310));
        seatMap.addFlight(new Flight("KC233", "ALA", "CIT", LocalDate.of(2027, 3, 10), 620, 8, 55));
        loyalty.addMember("FF-1001", "Aliya Nurlanova", "GOLD", true);
        loyalty.addMember("FF-1002", "Daniyar Seitkali", "SILVER", true);
        loyalty.addMember("FF-1003", "Timur Akhmetov", "BASIC", false);
    }

    public BookingFacade booking() {
        return bookingFacade;
    }

    public BookingAdminFacade admin() {
        return adminFacade;
    }

    public SeatMap seatMap() { return seatMap; }
    public ETicketIssuer ticketIssuer() { return ticketIssuer; }
    public MealPreferenceService meals() { return meals; }
    public LoyaltyProgram loyalty() { return loyalty; }

    public static void deleteAllData(Path dataDir) {
        for (String name : DATA_FILES) {
            new File(dataDir.toFile(), name).delete();
        }
    }

    @Override
    public void close() {
        seatRepository.close();
        ticketRepository.close();
        servicesRepository.close();
        bookingRepository.close();
    }
}
