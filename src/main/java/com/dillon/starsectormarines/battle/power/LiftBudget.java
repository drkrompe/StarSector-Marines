package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * How much armor the company can still put on the ground this mission, carrier by carrier.
 *
 * <p>Each carrier brings a number of machine lifts at its {@link LiftRating}: a cradled
 * company boat {@link #liftsPerCompanyBoat} (two on a light cradle, three on a heavy one),
 * the synthetic heavy carrier a hull, ship or employer supplies enough for the whole bay.
 * A machine spends one lift from a carrier whose rating admits it ({@link LiftAdmission}), so a Heavy machine spends Heavy lift and
 * a budget with none refuses it. Spent lift stays spent for the mission whether or not
 * the carrier came home: better boats mean more armor on the ground, not faster calls.
 *
 * <p>Allocation is heaviest first, each machine to the lowest-rated carrier that still
 * admits it. Ratings are nested (a Heavy carrier takes anything a Light one does), so that
 * order fits every load that can be fitted at all, and keeps heavy lift for heavy machines.
 */
public final class LiftBudget {

    /** Machine lifts a light-cradled company boat flies per mission: a trip each. */
    public static final int LIGHT_CRADLE_LIFTS = 2;

    /**
     * Machine lifts a heavy-cradled company boat flies per mission. One more than the light cradle: a Medium
     * mech (Warden) carries less structure and armor than the Light one (Hound), so a better cradle that
     * only changed the class would not put more armor on the ground, and the heavy cradle costs about twice
     * the light one's bill.
     */
    public static final int MEDIUM_CRADLE_LIFTS = 3;

    /** Machine lifts a cradled company boat of {@code rating} flies per mission. */
    public static int liftsPerCompanyBoat(LiftRating rating) {
        return rating == LiftRating.LIGHT ? LIGHT_CRADLE_LIFTS : MEDIUM_CRADLE_LIFTS;
    }

    /** Why a machine cannot be added to a call. */
    public enum Refusal {
        /** No carrier this mission lifts a machine of its class. */
        NO_LIFT,
        /** Carriers that could lift it have spent their lift this mission. */
        LIFT_SPENT,
        /** Its only carrier is in the air or lost. */
        CARRIER_AWAY
    }

    /** One carrier's share of the budget. */
    public static final class Carrier {
        private final Airframe frame;
        private final boolean company;
        private final int lifts;
        private int spent;

        Carrier(Airframe frame, boolean company, int lifts) {
            this.frame = frame;
            this.company = company;
            this.lifts = lifts;
        }

        public Airframe frame() { return frame; }
        public boolean company() { return company; }
        public LiftRating rating() { return frame.liftRating(); }
        public int lifts() { return lifts; }
        public int remaining() { return lifts - spent; }
    }

    private final List<Carrier> carriers;

    private LiftBudget(List<Carrier> carriers) {
        this.carriers = Collections.unmodifiableList(carriers);
    }

    /**
     * The mission's budget: the committed company boat, if it carries a cradle, and the
     * synthetic carrier when one is supplied, which lifts as many machines as the bay holds.
     */
    public static LiftBudget forMission(FittedBoat companyBoat, boolean syntheticLift,
                                        ShuttleType synthetic, int manifestSize) {
        List<Carrier> carriers = new ArrayList<>(2);
        if (companyBoat != null && companyBoat.liftRating() != LiftRating.NONE) {
            carriers.add(new Carrier(companyBoat, true, liftsPerCompanyBoat(companyBoat.liftRating())));
        }
        if (syntheticLift && synthetic != null) {
            carriers.add(new Carrier(synthetic, false,
                    Math.max(MechSupport.LANCE_SIZE, manifestSize)));
        }
        return new LiftBudget(carriers);
    }

    public List<Carrier> carriers() { return carriers; }

    /** Machine lifts left across every carrier, home or not. */
    public int remaining() {
        int total = 0;
        for (Carrier carrier : carriers) total += carrier.remaining();
        return total;
    }

    /**
     * Which carrier lifts each of {@code load}, in {@code load}'s order, or {@code null} when
     * the remaining budget cannot lift all of it. {@code companyAvailable} false withholds
     * the company boat (in the air, or lost).
     */
    public List<Carrier> allocate(List<LiftClass> load, boolean companyAvailable) {
        if (load == null || load.isEmpty()) return List.of();
        Integer[] order = new Integer[load.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> load.get(i).ordinal()).reversed());
        int[] used = new int[carriers.size()];
        Carrier[] chosen = new Carrier[load.size()];
        for (int index : order) {
            int best = -1;
            for (int c = 0; c < carriers.size(); c++) {
                Carrier carrier = carriers.get(c);
                if (carrier.company && !companyAvailable) continue;
                if (carrier.remaining() - used[c] <= 0) continue;
                if (!LiftAdmission.check(load.get(index), carrier.frame).admitted()) continue;
                if (best < 0 || carrier.rating().ordinal() < carriers.get(best).rating().ordinal()) {
                    best = c;
                }
            }
            if (best < 0) return null;
            used[best]++;
            chosen[index] = carriers.get(best);
        }
        return List.of(chosen);
    }

    /**
     * Why {@code candidate} cannot join a call already holding {@code picked}, or
     * {@code null} when it can.
     */
    public Refusal refusal(LiftClass candidate, List<LiftClass> picked, boolean companyAvailable) {
        List<LiftClass> load = new ArrayList<>(picked == null ? List.of() : picked);
        load.add(candidate);
        if (allocate(load, companyAvailable) != null) return null;
        boolean anyAdmits = false;
        boolean anyHome = false;
        for (Carrier carrier : carriers) {
            if (!LiftAdmission.check(candidate, carrier.frame).admitted()) continue;
            anyAdmits = true;
            if (carrier.remaining() > 0 && (!carrier.company || companyAvailable)) anyHome = true;
        }
        if (!anyAdmits) return Refusal.NO_LIFT;
        boolean anyLiftLeft = false;
        for (Carrier carrier : carriers) {
            if (carrier.remaining() > 0
                    && LiftAdmission.check(candidate, carrier.frame).admitted()) anyLiftLeft = true;
        }
        if (anyLiftLeft && !anyHome) return Refusal.CARRIER_AWAY;
        return Refusal.LIFT_SPENT;
    }

    /** Spends one lift from each carrier in {@code allocation}. */
    void spend(List<Carrier> allocation) {
        for (Carrier carrier : allocation) carrier.spent++;
    }
}
