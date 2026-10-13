package com.dillon.starsectormarines.battle.story;

/** Reversible site control followed by protected inspection of surviving tooling. */
public final class YardOwnersProgress {
    public enum Phase { DISPATCH, TOOLING, ENTRANCE, INSPECTION, COMPLETE, FAILED }
    public static final float CAPTURE_SECONDS = 6, INSPECTION_SECONDS = 12;
    public record Report(boolean dispatchHeld, boolean toolingHeld, boolean entranceHeld, boolean inspected,
                         boolean companyPresent, boolean toolingLost, int techniciansLost, int constablesLost) {
        public boolean complete() { return dispatchHeld && toolingHeld && entranceHeld && inspected && companyPresent && !toolingLost && techniciansLost < 2; }
    }
    private final float[] capture = new float[3];
    private int held, everHeld;
    private float inspection;
    private boolean complete, failed, toolingLost, companyPresent;
    public boolean held(int site) { return (held & (1 << site)) != 0; }
    public boolean visited(int site) { return (everHeld & (1 << site)) != 0; }
    public float capture(int site) { return capture[site]; }
    public float inspection() { return inspection; }
    public boolean terminal() { return complete || failed; }
    public Phase phase() {
        if (complete) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        for (int i = 0; i < 3; i++) if (!held(i)) return Phase.values()[i];
        return Phase.INSPECTION;
    }
    public Report report(int techniciansLost, int constablesLost) {
        return new Report(held(0), held(1), held(2), complete, companyPresent, toolingLost, techniciansLost, constablesLost);
    }
    public void advance(float dt, boolean companyInPlay, boolean toolingAlive, boolean technicianAlive,
                        boolean technicianAtTools, int companySites, int friendlySites, int contestedSites) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        toolingLost = !toolingAlive;
        companyPresent = (companySites & 2) != 0;
        if (!companyInPlay || toolingLost || !technicianAlive) { failed = true; return; }
        for (int i = 0; i < 3; i++) {
            int bit = 1 << i;
            if ((contestedSites & bit) != 0) { held &= ~bit; capture[i] = 0; }
            else if (!held(i)) {
                capture[i] = (companySites & bit) != 0 ? capture[i] + dt : 0;
                if (capture[i] >= CAPTURE_SECONDS) { held |= bit; everHeld |= bit; }
            }
        }
        boolean safe = held == 7 && friendlySites == 7 && contestedSites == 0 && companyPresent && technicianAtTools;
        inspection = safe ? inspection + dt : 0;
        if (inspection >= INSPECTION_SECONDS) complete = true;
    }
}
