package com.flightres.passengerservices;

import java.util.ArrayList;
import java.util.List;

public class LoyaltyProgram {
    public record Member(String number, String name, String tier, boolean active) {
    }

    private final PassengerServicesRepository repository;
    private final MilesCalculator calculator;

    public LoyaltyProgram(PassengerServicesRepository repository, MilesCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public void addMember(String memberNo, String name, String tier, boolean active) {
        repository.saveMember(new Member(memberNo, name, tier, active));
    }

    public boolean isMember(String memberNo) {
        return repository.findMember(memberNo) != null;
    }

    public int creditMiles(String memberNo, String bookingRef, int distanceKm, String cabin) {
        int alreadyCredited = repository.milesForBooking(bookingRef);
        if (alreadyCredited > 0) {
            return alreadyCredited;
        }
        Member member = repository.findMember(memberNo);
        if (member == null) {
            throw new PassengerServicesException("Unknown frequent-flyer number " + memberNo);
        }
        if (!member.active()) {
            throw new PassengerServicesException("Frequent-flyer account " + memberNo
                    + " is suspended - miles cannot be credited");
        }
        int miles = calculator.miles(distanceKm, cabin, member.tier());
        repository.addMilesRow(memberNo, bookingRef, miles);
        return miles;
    }

    public int reverseMiles(String bookingRef) {
        int miles = repository.milesForBooking(bookingRef);
        if (miles <= 0) {
            return 0;
        }
        repository.addMilesRow(repository.memberOfBooking(bookingRef), bookingRef, -miles);
        return miles;
    }

    public int milesForBooking(String bookingRef) {
        return repository.milesForBooking(bookingRef);
    }

    public int balance(String memberNo) {
        return repository.balance(memberNo);
    }

    public List<String> describeMembers() {
        List<String> lines = new ArrayList<>();
        for (Member m : repository.allMembers()) {
            lines.add(String.format("%-8s %-18s %-6s %-9s %d miles", m.number(), m.name(), m.tier(),
                    m.active() ? "active" : "SUSPENDED", balance(m.number())));
        }
        return lines;
    }
}
