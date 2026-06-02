package com.dillon.starsectormarines.battle.combat;

/**
 * Shooter posture at the moment a shot leaves the barrel. Threads through
 * {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#fireShot} as an accuracy multiplier — damage is
 * unchanged, the bullet just lands wider when the shooter is on the move.
 * Replaces the implicit "everything fires at base accuracy" baseline that
 * worked while the only firing path was a stationary engagement loop.
 *
 * <p>{@link #stanceFor(float)} is the convenience for callers that don't
 * already know — they pass the unit's {@code moveProgress} and let this enum
 * decide. Action implementations that <em>do</em> know (e.g. a cordon
 * holder explicitly at-post, an EngagePosture member dwelling between
 * bursts) should pass the stance directly so the strict-vs-lerping
 * heuristic doesn't second-guess them.
 */
public enum FireStance {

    /**
     * Stationary shooter. Default — preserves pre-stance accuracy math.
     * EngagePosture's dwell-and-fire loop, cordon holders on-post, turret
     * fire, mech chassis fire all sit here.
     */
    STANCED(1.0f),

    /**
     * Shooter is mid-step between cells (the sprite is visibly lerping).
     * Suppression-on-the-move: half-accuracy says "you can shoot while
     * walking but you won't kill people doing it." Drives EnterZone's
     * opportunistic fire, cordon-holder transit, kit-retriever transit.
     */
    MOVING(0.5f);

    /** Accuracy multiplier applied to {@code shooter.getAccuracy()} / weapon accuracy at fire time. */
    public final float accuracyMult;

    FireStance(float accuracyMult) {
        this.accuracyMult = accuracyMult;
    }

    /**
     * Heuristic stance for callers that don't track it explicitly — strict
     * rule: any non-zero {@code moveProgress} reads as {@link #MOVING}. The
     * unit is currently lerping in the renderer, so a stationary-fire
     * accuracy multiplier would look visually wrong. Burst-tick follow-ups
     * use this so a unit that walked off after the burst started gets
     * downgraded mid-burst. The caller passes the unit's {@code moveProgress}
     * (read by-id/by-index from the registry) rather than a {@link Unit} ref.
     */
    public static FireStance stanceFor(float moveProgress) {
        return moveProgress > 0f ? MOVING : STANCED;
    }
}
