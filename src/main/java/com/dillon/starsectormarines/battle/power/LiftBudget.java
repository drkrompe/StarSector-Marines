package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How much armor the company can still put on the ground this mission, carrier by carrier.
 *
 * <p>Two things limit the armour that comes down, and nothing else (owner): the carrier must
 * lift the machine's weight class ({@link LiftAdmission}: a Heavy machine needs Heavy lift),
 * and no more machines land than the bay holds. Each carrier, the cradled company boat and
 * any heavy lift a mission supplies, therefore brings a lift for every machine in the bay at
 * its {@link LiftRating}. A machine spends one lift, so a machine called once stays called;
 * a drop cancelled while it still waits on the lift queue gives its lift back.
 *
 * <p>The budget says how much may come down; the ferry craft on the lift queue say when.
 * Which craft flies a machine is the queue's business, not the budget's.
 *
 * <p>Each machine goes to the lowest-rated carrier that still admits it. Ratings are
 * nested (a Heavy carrier takes anything a Light one does), so that keeps heavy lift for
 * heavy machines.
 */
public final class LiftBudget {

    /** Why a machine cannot be ordered down. */
    public enum Refusal {
        /** No carrier this mission lifts a machine of its class. */
        NO_LIFT,
        /** Carriers that could lift it have spent their lift this mission. */
        LIFT_SPENT,
        /** Lift is left, but every ferry craft that could fly it is lost. */
        NO_CRAFT
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
     * supplied heavy lift when there is one, which lifts as many machines as the bay holds.
     */
    public static LiftBudget forMission(FittedBoat companyBoat, boolean heavyLift,
                                        ShuttleType heavy, int manifestSize) {
        List<Carrier> carriers = new ArrayList<>(2);
        if (companyBoat != null && companyBoat.liftRating() != LiftRating.NONE) {
            // Weight class and the bay's slots are the only limits: the boat lifts every machine it can carry.
            carriers.add(new Carrier(companyBoat, true, Math.max(MechSupport.LANCE_SIZE, manifestSize)));
        }
        if (heavyLift && heavy != null) {
            carriers.add(new Carrier(heavy, false, Math.max(MechSupport.LANCE_SIZE, manifestSize)));
        }
        return new LiftBudget(carriers);
    }

    public List<Carrier> carriers() { return carriers; }

    /** Machine lifts left across every carrier. */
    public int remaining() {
        int total = 0;
        for (Carrier carrier : carriers) total += carrier.remaining();
        return total;
    }

    /**
     * The carrier whose lift one machine of {@code payload}'s class would spend: the
     * lowest-rated one with lift left that admits it, or {@code null} when none does.
     */
    public Carrier allocate(LiftClass payload) {
        if (payload == null) return null;
        Carrier best = null;
        for (Carrier carrier : carriers) {
            if (carrier.remaining() <= 0) continue;
            if (!LiftAdmission.check(payload, carrier.frame).admitted()) continue;
            if (best == null || carrier.rating().ordinal() < best.rating().ordinal()) best = carrier;
        }
        return best;
    }

    /** Why one machine of {@code payload}'s class cannot be lifted, or {@code null} when it can. */
    public Refusal refusal(LiftClass payload) {
        if (allocate(payload) != null) return null;
        for (Carrier carrier : carriers) {
            if (LiftAdmission.check(payload, carrier.frame).admitted()) return Refusal.LIFT_SPENT;
        }
        return Refusal.NO_LIFT;
    }

    /** Spends one lift from {@code carrier}. */
    public void spend(Carrier carrier) {
        carrier.spent++;
    }

    /** Gives back one lift a cancelled drop had spent from {@code carrier}. */
    void refund(Carrier carrier) {
        if (carrier.spent > 0) carrier.spent--;
    }
}
