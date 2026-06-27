package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * S3d descent foundation — the <b>{@code setShipAI} takeover brain</b>. Installed onto one real
 * vanilla carrier mid-combat (by {@link CarrierDescentPlugin}) to fly it from the fleet down to a
 * point in the ground band, overriding the side's admiral. This is the "schedule" phase of the S3d
 * landing handoff: get the ship to the touchdown point. The visible scale-down + the
 * {@code removeEntity}/owned-sprite swap + {@code sim.deliverSquad} are later S3d steps; this slice
 * de-risks only the load-bearing unknown — <em>can a custom {@link ShipAIPlugin} reliably steer a
 * live vanilla ship (real mass / turn rate / physics) to a chosen point, against the admiral?</em>
 *
 * <p><b>Grip tier 2</b> (per {@code roadmap/vanilla-combat-bridge/next-session.md}): we own the
 * brain but vanilla physics still flies the ship. Each frame the brain issues {@link ShipCommand}s —
 * turn toward the target, thrust only while roughly aligned, and bleed speed while turning or near
 * the target so it arrives instead of orbiting. (Tier 3, the per-frame {@code getLocation().set}
 * puppet, is what proxies do; it's deterministic but ignores physics, so it doesn't answer the
 * tier-2 question this probe exists to answer.)
 *
 * <p>The brain never issues {@link ShipCommand#FIRE}, so the taken-over carrier stops shooting while
 * it peels off to land — fine for a ship leaving the fight. {@link #getAIFlags()} / {@link
 * #getConfig()} return fresh self-owned instances; the rest of the interface is no-op plumbing.
 */
@DebugOnly
public final class CarrierDescentBrain implements ShipAIPlugin {

    private static final Logger LOG = Global.getLogger(CarrierDescentBrain.class);

    /** Within this many world units of the target counts as "over the landing point". */
    private static final float ARRIVE_RADIUS = 400f;
    /** Only thrust toward the target while heading error is inside this cone (else turn first). */
    private static final float ACCEL_CONE_DEG = 35f;
    /** Below this speed at the target, the descent is considered settled (handoff-ready). */
    private static final float SETTLED_SPEED = 30f;
    /**
     * Dwell fallback: latch arrived after this long inside {@link #ARRIVE_RADIUS} even if the ship
     * never drops below {@link #SETTLED_SPEED}. The vision's transport is a <em>lumbering</em> hull —
     * a heavy ship can orbit/oscillate around the point without its speed ever settling, which would
     * leave the drop un-launched forever. Once it's been loitering over the band this long, that's
     * "in orbit" enough to drop.
     */
    private static final float ARRIVE_DWELL_SEC = 6f;

    private final ShipAPI ship;
    private final Vector2f target = new Vector2f();
    private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAIConfig config = new ShipAIConfig();
    private boolean arrived;
    private float dwellInRadius;   // seconds continuously within ARRIVE_RADIUS (resets on leaving)

    public CarrierDescentBrain(ShipAPI ship, Vector2f target) {
        this.ship = ship;
        this.target.set(target);
    }

    /**
     * True once the carrier has settled over the handoff point. {@link CarrierDescentPlugin} polls
     * this as the cue to launch the drop-ship sortie (S3d D1b): the mothership is in orbit, so the
     * dropships fall.
     */
    public boolean hasArrived() {
        return arrived;
    }

    /**
     * Re-aim the descent mid-approach — the "land there instead" re-designation. Resets the arrival
     * latch + dwell clock so the carrier flies to and re-settles over the new point before the drop.
     */
    public void setTarget(Vector2f newTarget) {
        this.target.set(newTarget);
        this.arrived = false;
        this.dwellInRadius = 0f;
    }

    @Override
    public void advance(float amount) {
        if (ship == null || !ship.isAlive()) return;

        Vector2f loc = ship.getLocation();
        float dist = Misc.getDistance(loc, target);

        if (arrived) {
            ship.giveCommand(ShipCommand.DECELERATE, null, 0);   // hold station at the handoff point
            return;
        }

        // Overshoot-aware turn toward the target: vanilla's own helper issues the TURN commands
        // and damps the approach so the ship doesn't oscillate around the heading.
        float desiredFacing = Misc.getAngleInDegrees(loc, target);
        Misc.turnTowardsFacingV2(ship, desiredFacing, 0f);

        // getAngleDiff is the true [0,180] heading-error magnitude. normalizeAngle alone returns
        // [0,360), so a 10° clockwise error reads as 350° — which would brake instead of thrust.
        float headingErr = Misc.getAngleDiff(desiredFacing, ship.getFacing());
        if (dist > ARRIVE_RADIUS) {
            // Thrust only when roughly pointed at the target; brake while still turning so the ship
            // bleeds tangential speed and cuts toward the point instead of carving a wide orbit.
            dwellInRadius = 0f;   // left the arrival zone — restart the settle clock
            ship.giveCommand(headingErr < ACCEL_CONE_DEG
                    ? ShipCommand.ACCELERATE : ShipCommand.DECELERATE, null, 0);
        } else {
            ship.giveCommand(ShipCommand.DECELERATE, null, 0);
            dwellInRadius += amount;
            boolean settled = ship.getVelocity().length() < SETTLED_SPEED;
            if (settled || dwellInRadius > ARRIVE_DWELL_SEC) {
                arrived = true;
                LOG.info("ground-bridge(descent): " + ship.getHullSpec().getHullId()
                        + (settled ? " settled" : " dwell-timeout") + " over ground band at ("
                        + (int) loc.x + ", " + (int) loc.y + ") — handoff point reached.");
            }
        }
    }

    @Override
    public void setDoNotFireDelay(float amount) {
    }

    @Override
    public void forceCircumstanceEvaluation() {
    }

    @Override
    public boolean needsRefit() {
        return false;
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void cancelCurrentManeuver() {
    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
}
