package com.dillon.starsectormarines.battle.story;

/** Referee state, separate from orders: company presence earns gains and safe work earns the claim. */
public final class SalvageRightsProgress {
    public enum Phase { RECEIVING, WORKSHOP, CRADLE, INSPECTION, SECURED, FAILED }
    public static final float CAPTURE_SECONDS = 4f, INSPECTION_SECONDS = 60f;
    private Phase phase = Phase.RECEIVING;
    private float capture, inspection;
    private boolean contested;

    public Phase phase() { return phase; }
    public float inspection() { return inspection; }
    public boolean contested() { return contested; }
    public boolean terminal() { return phase == Phase.SECURED || phase == Phase.FAILED; }

    public void advance(float dt, boolean companyAtTarget, boolean enemyAtTarget,
                        boolean engineerPresent, boolean engineersAlive, boolean responseCommitted) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        if (!engineersAlive) { phase=Phase.FAILED; return; }
        contested=enemyAtTarget;
        if (phase == Phase.INSPECTION) {
            if (companyAtTarget && !enemyAtTarget && engineerPresent)
                inspection=Math.min(INSPECTION_SECONDS,inspection+dt);
            if (inspection>=INSPECTION_SECONDS && companyAtTarget && !enemyAtTarget
                    && engineerPresent && responseCommitted) phase=Phase.SECURED;
        } else {
            capture=companyAtTarget && !enemyAtTarget ? capture+dt : 0;
            if (capture>=CAPTURE_SECONDS) { phase=Phase.values()[phase.ordinal()+1]; capture=0; }
        }
    }
}
