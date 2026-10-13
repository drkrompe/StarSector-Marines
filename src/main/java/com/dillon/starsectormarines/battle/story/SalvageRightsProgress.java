package com.dillon.starsectormarines.battle.story;

/** Referee state, separate from orders: company presence earns gains and safe work earns the claim. */
public final class SalvageRightsProgress {
    public enum Phase { RECEIVING, WORKSHOP, CRADLE, INSPECTION, SECURED, FAILED }
    public static final float CAPTURE_SECONDS = 4f, INSPECTION_SECONDS = 60f;
    private Phase phase = Phase.RECEIVING;
    private float capture, inspection;
    private boolean contested;
    private boolean companyAtTarget, engineerPresent, responseCommitted;

    public Phase phase() { return phase; }
    public float inspection() { return inspection; }
    public float capture() { return capture; }
    public boolean companyAtTarget() { return companyAtTarget; }
    public boolean engineerPresent() { return engineerPresent; }
    public boolean responseCommitted() { return responseCommitted; }
    public boolean contested() { return contested; }
    public boolean terminal() { return phase == Phase.SECURED || phase == Phase.FAILED; }

    /** Refresh the public gates when capture changes which physical area is being judged. */
    public void observeTarget(boolean companyAtTarget, boolean enemyAtTarget) {
        this.companyAtTarget = companyAtTarget;
        this.contested = enemyAtTarget;
    }

    public void advance(float dt, boolean companyAtTarget, boolean enemyAtTarget,
                        boolean engineerPresent, boolean engineersAlive, boolean responseCommitted) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        this.companyAtTarget = companyAtTarget;
        this.engineerPresent = engineerPresent;
        this.responseCommitted = responseCommitted;
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
