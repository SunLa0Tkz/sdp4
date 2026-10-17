package com.flightres.ticketing;

import java.util.List;

public class BaggageRulesEngine {
    public static final int MAX_BAG_KG = 32;
    public static final int MAX_BAGS = 5;
    public static final int EXTRA_BAG_FEE = 60;
    public static final int OVERWEIGHT_FEE = 75;

    public int baggageFee(String cabin, List<Integer> bagWeightsKg) {
        boolean business = cabin.equals("BUSINESS");
        int freeBags = business ? 2 : 1;
        int freeWeightKg = business ? 32 : 23;

        if (bagWeightsKg.size() > MAX_BAGS) {
            throw new TicketingException(bagWeightsKg.size() + " bags is more than the limit of " + MAX_BAGS);
        }
        int fee = 0;
        for (int i = 0; i < bagWeightsKg.size(); i++) {
            int kg = bagWeightsKg.get(i);
            if (kg <= 0) {
                throw new TicketingException("Bag " + (i + 1) + " has an impossible weight: " + kg + " kg");
            }
            if (kg > MAX_BAG_KG) {
                throw new TicketingException("Bag " + (i + 1) + " weighs " + kg + " kg - more than "
                        + MAX_BAG_KG + " kg is not allowed, it must be sent as cargo");
            }
            if (kg > freeWeightKg) {
                fee += OVERWEIGHT_FEE;
            }
            if (i >= freeBags) {
                fee += EXTRA_BAG_FEE;
            }
        }
        return fee;
    }
}
