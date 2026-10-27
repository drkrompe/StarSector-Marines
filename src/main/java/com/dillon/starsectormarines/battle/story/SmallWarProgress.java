package com.dillon.starsectormarines.battle.story;

/**
 * Chapter-five referee at Black Sluice: reversible custody of three sites taken in order (customs,
 * fuel control, dispatch), the once-only split when fuel control first falls, and a protected
 * handover by a living custody party. There is no construction step: the holders occupy what the
 * company actually takes. Nothing here reads the hull duel overhead: ground victory never
 * depends on it.
 */
public final class SmallWarProgress {
    public enum Phase { CUSTOMS, FUEL_CONTROL, DISPATCH, HANDOVER, COMPLETE, FAILED }
    public static final int CUSTOMS = 0, FUEL_CONTROL = 1, DISPATCH = 2;
    public static final float CAPTURE_SECONDS = 6, HANDOVER_SECONDS = 15;
    /** Authored employer party sizes: the custody party and three four-person holding constable squads. */
    public static final int CUSTODIANS = 3, HOLDERS = 12;

    /** The frozen physical facts the chapter settles on. */
    public record Report(boolean customsHeld, boolean fuelControlHeld, boolean dispatchHeld,
                         boolean handedOver, boolean companyPresent, boolean infrastructureLost, boolean split,
                         int custodiansLost, int holdersLost, int greyhookWithdrawn, boolean houseCustody) {
        public boolean complete() {
            return customsHeld && fuelControlHeld && dispatchHeld && handedOver
                    && companyPresent && !infrastructureLost && custodiansLost < CUSTODIANS;
        }
    }

    private final float[] capture = new float[3];
    private int held, everHeld;
    private float handover;
    private boolean split, complete, failed, infrastructureLost, companyPresent, safeHandover;

    public boolean held(int site) { return (held & (1 << site)) != 0; }
    public boolean visited(int site) { return (everHeld & (1 << site)) != 0; }
    public float capture(int site) { return capture[site]; }
    public float handover() { return handover; }
    /** Fuel control has actually fallen once; Voss's withdrawal order follows from this fact alone. */
    public boolean split() { return split; }
    public boolean safeHandover() { return safeHandover; }
    public boolean terminal() { return complete || failed; }
    public void fail() { if (!terminal()) failed = true; }

    public Phase phase() {
        if (complete) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        if (!held(CUSTOMS)) return Phase.CUSTOMS;
        if (!held(FUEL_CONTROL)) return Phase.FUEL_CONTROL;
        if (!held(DISPATCH)) return Phase.DISPATCH;
        return Phase.HANDOVER;
    }

    /** @param houseCustody whose party walked the handover: Dormer's custodians, else the joint party */
    public Report report(int custodiansLost, int holdersLost, int withdrawn, boolean houseCustody) {
        return new Report(held(CUSTOMS), held(FUEL_CONTROL), held(DISPATCH), complete, companyPresent,
                infrastructureLost, split, Math.max(0, Math.min(CUSTODIANS, custodiansLost)),
                Math.max(0, Math.min(HOLDERS, holdersLost)), Math.max(0, withdrawn), houseCustody);
    }

    /**
     * @param companySites bit per site with living company infantry inside it
     * @param friendlySites bit per site with company or allied combatants inside it
     * @param contestedSites bit per site with a hostile combatant inside or close by
     * @return whether this tick produced the split
     */
    public boolean advance(float dt, boolean companyInPlay, boolean infrastructureIntact, boolean custodyAlive,
                           boolean custodyInControlRoom, int companySites, int friendlySites, int contestedSites) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return false;
        if (!infrastructureIntact) infrastructureLost = true;
        companyPresent = (companySites & (1 << FUEL_CONTROL)) != 0;
        if (!companyInPlay || infrastructureLost || !custodyAlive) { failed = true; return false; }
        for (int i = 0; i < 3; i++) {
            int bit = 1 << i;
            if ((contestedSites & bit) != 0) { held &= ~bit; capture[i] = 0; }
            else if (!held(i)) {
                capture[i] = (companySites & bit) != 0 ? capture[i] + dt : 0;
                if (capture[i] >= CAPTURE_SECONDS) { held |= bit; everHeld |= bit; }
            }
        }
        boolean splitNow = !split && visited(FUEL_CONTROL);
        if (splitNow) split = true;
        safeHandover = phase() == Phase.HANDOVER && friendlySites == 7 && contestedSites == 0
                && companyPresent && custodyInControlRoom;
        handover = safeHandover ? handover + dt : 0;
        if (handover >= HANDOVER_SECONDS) complete = true;
        return splitNow;
    }
}
