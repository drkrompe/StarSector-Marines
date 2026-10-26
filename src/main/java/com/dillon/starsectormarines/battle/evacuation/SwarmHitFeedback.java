package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.unit.SwarmFootprint;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * What a round looks like landing on a plated Rav ({@code colony-breach-nouns.md}, Matriarch and Brute): in place of a
 * flash, each round sends up a small puff off the body where it struck, sized by its weapon, and a round the plate
 * turns <b>glances</b>: a spark and a chip of chitin skip off it.
 *
 * <p>A glance is the armour law read, not a rule of its own ({@code combat-durability-nouns.md}, Resolution law): while
 * a body has plate, a round spends its damage on it at an efficiency that falls as its penetration falls under the
 * plate's rating, so the share of a weapon's rounds drawn glancing is the share of the rating its penetration does not
 * reach. Which rounds those are is hashed from the body, the tick and the round, never drawn from the battle's random
 * stream. A round that lands is a puff of chitin while plate is left after it and of ichor once it reaches the flesh,
 * which is every round on a stripped body.
 *
 * <p>Presentation only: it reads the hit the damage path already resolved and emits into the effects service's
 * per-frame queue ({@link EffectsService#emitSwarmHit}), which the live screen and the headless review feed drain.
 */
public final class SwarmHitFeedback {

    /** What a landed round sends up: see {@link #kind}. */
    public static final int GLANCE = 0;
    public static final int CHITIN = 1;
    public static final int ICHOR = 2;

    /** The damage of the round a puff is sized against, a field rifle's, and the bounds of that size. */
    static final float REFERENCE_DAMAGE = 14f;
    static final float MIN_SCALE = 0.55f;
    static final float MAX_SCALE = 2.8f;
    /** How far in from the drawn rim a round strikes, as a share of the footprint's half extents. */
    static final float RIM_IN = 0.85f;

    private SwarmHitFeedback() {}

    /**
     * Whether {@code type} shows its hits this way: the plated strains, the Brute and the Matriarch, and the Bombard,
     * which has no plate, so every round on it is an ichor puff off its split shell.
     */
    public static boolean shows(UnitType type) {
        return type == UnitType.SWARM_BRUTE || type == UnitType.SWARM_MATRIARCH || type == UnitType.SWARM_BOMBARD;
    }

    /** The share of rounds at {@code penetration} that glance off {@code armor} points of plate rated {@code rating}. */
    static float glanceShare(float penetration, float armor, float rating) {
        if (armor <= 0f || rating <= 0f) return 0f;
        return 1f - Math.max(0f, Math.min(1f, penetration / rating));
    }

    /**
     * What a round that met {@code armorBefore} points of plate and left {@code armorAfter} sends up: a
     * {@link #GLANCE} when {@code roll} (uniform in [0, 1)) falls in the {@link #glanceShare}, else {@link #CHITIN}
     * while plate is left, else {@link #ICHOR}.
     */
    static int kind(float penetration, float armorBefore, float armorAfter, float rating, float roll) {
        if (roll < glanceShare(penetration, armorBefore, rating)) return GLANCE;
        return armorAfter > 0f ? CHITIN : ICHOR;
    }

    /** A puff's size against a rifle round's, from the round's damage: a heavy round throws up more. */
    static float scale(float damage) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, (float) Math.sqrt(Math.max(0f, damage) / REFERENCE_DAMAGE)));
    }

    /**
     * Where on a body's drawn footprint a round from {@code (fromX, fromY)} strikes: the point of its ellipse
     * (centred {@code forward} cells ahead of the sim position along the facing, {@code halfLength} by
     * {@code halfWidth}) facing the shooter, a little in from the rim. Writes {@code out[0..1]}.
     */
    static void strikePoint(float bodyX, float bodyY, float facingDeg, float forward, float halfLength,
                            float halfWidth, float fromX, float fromY, float[] out) {
        double rad = Math.toRadians(facingDeg);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float cx = bodyX + fx * forward, cy = bodyY + fy * forward;
        float dx = fromX - cx, dy = fromY - cy;
        float along = dx * fx + dy * fy, across = dx * fy - dy * fx;
        float k = (float) Math.hypot(along / Math.max(1e-3f, halfLength), across / Math.max(1e-3f, halfWidth));
        if (k < 1e-4f) {
            out[0] = cx;
            out[1] = cy;
            return;
        }
        float s = RIM_IN / k;
        out[0] = cx + dx * s;
        out[1] = cy + dy * s;
    }

    /** Whether {@code (x, y)} lies on the footprint ellipse {@link #strikePoint} describes; false for NaN. */
    static boolean onFootprint(float bodyX, float bodyY, float facingDeg, float forward, float halfLength,
                               float halfWidth, float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return false;
        double rad = Math.toRadians(facingDeg);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float dx = x - (bodyX + fx * forward), dy = y - (bodyY + fy * forward);
        float along = (dx * fx + dy * fy) / Math.max(1e-3f, halfLength);
        float across = (dx * fy - dy * fx) / Math.max(1e-3f, halfWidth);
        return along * along + across * across <= 1f;
    }

    /** A uniform value in [0, 1) for this body, tick and round, from no random stream. */
    static float roll(long victimId, int tick, int round) {
        long z = victimId * 0x9E3779B97F4A7C15L ^ ((long) tick << 21) ^ (round + 1L) * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1L << 24);
    }

    /**
     * Emits the round that just landed on {@code victim} at {@code (bodyX, bodyY)} facing {@code facingDeg}, fired from
     * {@code (fromX, fromY)}: where it struck, its direction of travel there, its size and kind. It struck where the
     * round met the body ({@code hitX, hitY}) when that lies on the drawn footprint, else on the footprint's rim facing
     * the gun (a body's sim contact is a circle, which a long body overhangs). {@code round} tells apart several rounds
     * landing on one body in one tick.
     */
    public static void emit(EffectsService fx, long victim, UnitType type, SwarmStage stage, float bodyX, float bodyY,
                            float facingDeg, float fromX, float fromY, float hitX, float hitY, int tick, int round,
                            float damage, float penetration, float armorBefore, float armorAfter, float rating) {
        float[] at = new float[2];
        float forwardOffset = SwarmFootprint.forwardOffset(type, stage);
        float halfLength = SwarmFootprint.length(type, stage) * 0.5f, halfWidth = SwarmFootprint.width(type, stage) * 0.5f;
        if (onFootprint(bodyX, bodyY, facingDeg, forwardOffset, halfLength, halfWidth, hitX, hitY)) {
            at[0] = hitX;
            at[1] = hitY;
        } else {
            strikePoint(bodyX, bodyY, facingDeg, forwardOffset, halfLength, halfWidth, fromX, fromY, at);
        }
        float dx = at[0] - fromX, dy = at[1] - fromY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            dx = 0f;
            dy = 1f;
        } else {
            dx /= len;
            dy /= len;
        }
        // The rim faces out from the footprint's middle: a glance leaves it mirrored about that normal.
        double rad = Math.toRadians(facingDeg);
        float forward = SwarmFootprint.forwardOffset(type, stage);
        float nx = at[0] - (bodyX + (float) -Math.sin(rad) * forward);
        float ny = at[1] - (bodyY + (float) Math.cos(rad) * forward);
        float nl = (float) Math.hypot(nx, ny);
        if (nl > 1e-4f) {
            nx /= nl;
            ny /= nl;
        } else {
            nx = -dx;
            ny = -dy;
        }
        int kind = kind(penetration, armorBefore, armorAfter, rating, roll(victim, tick, round));
        fx.emitSwarmHit(at[0], at[1], dx, dy, nx, ny, scale(damage), kind);
    }
}
