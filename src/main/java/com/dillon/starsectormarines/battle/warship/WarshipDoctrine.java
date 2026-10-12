package com.dillon.starsectormarines.battle.warship;

import java.util.Locale;

/**
 * Chooses a warship's {@link WarshipPosture} from a {@link PostureInputs}
 * snapshot, one ship's doctrine at a time. The only memory it keeps is the
 * hysteresis a sticky choice needs, so a sequence of snapshots is all a test
 * has to supply.
 *
 * <p>The order is the design's: is the ship in trouble (WITHDRAW), is there a
 * fight in the air to win (AIR_SUPERIORITY), else what the mission wants done
 * about its objective (SUPPORT, INTERCEPT, or the default BOMBARD).
 *
 * <ul>
 *   <li><b>Air superiority</b> is entered when the threat score reaches
 *       {@link #AIR_ENTER} and left only when it has stayed under
 *       {@link #AIR_EXIT} (half the entry) for {@link #AIR_QUIET_SECONDS}
 *       without a break. A wing between sorties is a pause in the fight, not
 *       the end of it.</li>
 *   <li><b>Withdraw</b> is for the rest of the battle: once chosen it is never
 *       left (the ship departs through the existing station-time behaviour).
 *       A ship whose own aircraft are in the air is slower to give up.</li>
 *   <li>A change of <b>stance</b> is a mission's order, not sensing noise, so
 *       it takes effect at the next pass rather than after a dwell.</li>
 * </ul>
 *
 * It owns no random source and reads nothing but its argument.
 */
public final class WarshipDoctrine {

    /** Threat score at which the ship turns to the air fight. */
    public static final float AIR_ENTER = 1f;
    /** Threat score under which the fight counts as quiet; half the entry, so the thresholds differ 2:1. */
    public static final float AIR_EXIT = 0.5f;
    /** Seconds the score must stay under {@link #AIR_EXIT} before air superiority is achieved. */
    public static final float AIR_QUIET_SECONDS = 8f;
    /** Hull share under which a ship leaves, unless it has aircraft up. */
    public static final float WITHDRAW_HULL = 0.35f;
    /** The lower hull share at which a ship with its own aircraft in the air gives up. */
    public static final float WITHDRAW_HULL_WITH_COVER = 0.25f;
    /** Hull share under which an overloaded or shieldless ship that is threatened leaves. */
    public static final float WITHDRAW_HULL_THREATENED = 0.6f;

    /** A posture and the sentence that explains it. */
    public record Decision(WarshipPosture posture, String reason) {}

    private boolean airActive;
    private float airQuietSeconds;
    private String withdrawReason;

    /** Whether the ship is currently in the air fight. */
    public boolean airActive() {
        return airActive;
    }

    /** Whether the ship has decided to leave. */
    public boolean withdrawn() {
        return withdrawReason != null;
    }

    /**
     * The posture for this snapshot, {@code dt} seconds after the previous one.
     * Deterministic: the same sequence of snapshots always gives the same
     * sequence of decisions.
     */
    public Decision assess(PostureInputs in, float dt) {
        if (withdrawReason != null) return new Decision(WarshipPosture.WITHDRAW, withdrawReason);
        float hullLimit = in.ownAir() > 0 ? WITHDRAW_HULL_WITH_COVER : WITHDRAW_HULL;
        if (in.hullFraction() < hullLimit) {
            return withdraw(String.format(Locale.ROOT, "hull %d%% under %d%%",
                    pct(in.hullFraction()), pct(hullLimit)));
        }
        boolean threatened = in.airScore() >= AIR_EXIT;
        if (in.hullFraction() < WITHDRAW_HULL_THREATENED && threatened && (in.overloaded() || in.shieldDown())) {
            return withdraw(String.format(Locale.ROOT, "%s under threat at hull %d%%",
                    in.overloaded() ? "overloaded" : "shield down", pct(in.hullFraction())));
        }
        if (!airActive) {
            if (in.airScore() >= AIR_ENTER) {
                airActive = true;
                airQuietSeconds = 0f;
            }
        } else if (in.airScore() < AIR_EXIT) {
            airQuietSeconds += dt;
            if (airQuietSeconds >= AIR_QUIET_SECONDS) airActive = false;
        } else {
            airQuietSeconds = 0f;
        }
        if (airActive) {
            return new Decision(WarshipPosture.AIR_SUPERIORITY, String.format(Locale.ROOT,
                    "air threat %.1f: %s", in.airScore(), in.airDescription()));
        }
        return switch (in.stance()) {
            case ATTACK -> new Decision(WarshipPosture.SUPPORT, "attacking: fire where the assault is");
            case DEFEND -> new Decision(WarshipPosture.INTERCEPT, "defending: deny the drop, cover the held ground");
            case HOLD -> new Decision(WarshipPosture.BOMBARD, "no stance, no air threat");
        };
    }

    private Decision withdraw(String reason) {
        withdrawReason = reason;
        return new Decision(WarshipPosture.WITHDRAW, reason);
    }

    private static int pct(float share) {
        return Math.round(share * 100f);
    }
}
