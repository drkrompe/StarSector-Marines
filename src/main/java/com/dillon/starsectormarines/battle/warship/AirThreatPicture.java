package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirLocomotion;
import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.HullStrike;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A read-only snapshot, taken once per doctrine pass, of what threatens one
 * ship and what it might deny: hostile warships, hostile armed craft in the
 * air, the ship's own aircraft, a threat a mission has declared, and the
 * hostile loaded shuttles still coming in to land.
 *
 * <p>It adds no simulation rule and no registry. It walks the ships and the air
 * entity ids the battle already tracks, in a stable order (ships as added,
 * aircraft by id), and writes nothing. A hostile shuttle is never a threat to a
 * ship; it is an inbound drop, listed apart, which is what interception is for.
 * "Armed craft" is a strike sortie the air layer flies (a committed wing flies as
 * one) whose ordnance gets through this ship's armour: a craft that cannot hurt the
 * hull is no threat to it.
 */
public final class AirThreatPicture {

    /** Weight of one hostile warship at full hull; a battered one counts down to half. */
    static final float HOSTILE_SHIP_WEIGHT = 1f;
    /** Weight of one hostile armed craft in the air. */
    static final float ARMED_CRAFT_WEIGHT = 0.5f;
    /**
     * Weight of one hostile armed craft aimed at the lead of an escort: twice an ordinary armed craft, so one is
     * enough to turn an escort to the air fight (the entry level is 1.0) whatever its own class.
     */
    static final float LEAD_CRAFT_WEIGHT = 1f;
    /** Most the armed craft can add to the score, so a swarm of strike craft cannot outweigh a capital. */
    static final float ARMED_CRAFT_CAP = 2f;
    /**
     * Seconds added to a boat's remaining run to reach touchdown. Measured, not
     * derived: the run in ends with the boat braking onto the zone, and from
     * entering its settle (PAD_DESCENT, about 0.9 s) to the first tick it is down
     * the whole approach estimate lands within a second of the true touchdown
     * ({@code warship-intercept} asserts it).
     */
    public static final float SETTLE_SECONDS = 0.5f;
    /** Seconds a boat takes to sink onto its pad once over it. */
    static final float PAD_DESCENT_SECONDS = 0.9f;

    /** A hostile loaded shuttle that has not yet put everyone on the ground. */
    public record InboundDrop(long craftId, Faction faction, float lzX, float lzY, float etaSeconds,
                              int marines, boolean mech, ShuttleState state) {
        /** How much landing this drop is worth stopping: people and machines, sooner counting more. */
        public float value() {
            return (marines + (mech ? 6f : 0f)) / Math.max(1f, etaSeconds);
        }
    }

    private final float declared;
    private final int hostileShips;
    private final float shipWeight;
    private final int armedCraft;
    private final int ownAir;
    private final List<InboundDrop> drops;
    private final ShipClass shipClass;
    private final int leadCraft;
    private final float axisX;
    private final float axisY;
    private final int axisCount;

    AirThreatPicture(float declared, int hostileShips, float shipWeight, int armedCraft, int ownAir,
                     List<InboundDrop> drops, ShipClass shipClass, int leadCraft, float axisX, float axisY,
                     int axisCount) {
        this.leadCraft = leadCraft;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisCount = axisCount;
        this.declared = declared;
        this.hostileShips = hostileShips;
        this.shipWeight = shipWeight;
        this.armedCraft = armedCraft;
        this.ownAir = ownAir;
        this.drops = Collections.unmodifiableList(drops);
        this.shipClass = shipClass;
    }

    /** What threatens {@code ship} now. Reads the sim and the ships; changes neither. */
    public static AirThreatPicture take(BattleSimulation sim, Warship ship, List<Warship> ships) {
        int hostile = 0;
        float weight = 0f;
        Warship lead = WarshipEscort.liveLead(ship);
        float leadRadius = lead == null ? 0f : 0.5f * Math.max(lead.lengthCells, lead.widthCells);
        int leadCraft = 0;
        float axisX = 0f;
        float axisY = 0f;
        int axisCount = 0;
        for (Warship other : ships) {
            if (other == ship || other.destroyed() || other.departing() || !ship.faction.hostileTo(other.faction)) continue;
            hostile++;
            weight += HOSTILE_SHIP_WEIGHT * (0.5f + 0.5f * other.hullFraction());
            if (lead != null) {
                axisX += other.centerX();
                axisY += other.centerY();
                axisCount++;
            }
        }
        World world = sim.world();
        long[] ids = sim.getAirEntityIds().clone();
        Arrays.sort(ids);
        int armed = 0;
        int own = 0;
        List<InboundDrop> drops = new ArrayList<>();
        for (long id : ids) {
            ShuttleMission mission = world.mission(id);
            Faction faction = world.airFaction(id);
            if (mission == null || faction == null || mission.state == ShuttleState.GONE) continue;
            boolean airborne = AirLocomotion.of(mission.state).airborne();
            if (ship.faction.friendlyTo(faction)) {
                if (airborne) own++;
                continue;
            }
            if (!ship.faction.hostileTo(faction)) continue;
            if (mission.strikeSortie) {
                if (!airborne) continue;
                if (lead != null && HullStrike.aimedAt(mission, lead.centerX(), lead.centerY(), leadRadius)
                        && canHurt(world, id, mission, lead)) {
                    leadCraft++;
                    AirBody body = world.kinematics(id);
                    if (body != null) {
                        axisX += body.x;
                        axisY += body.y;
                        axisCount++;
                    }
                } else if (canHurt(world, id, mission, ship)) {
                    armed++;
                }
                continue;
            }
            InboundDrop drop = dropOf(world, id, mission, faction);
            if (drop != null) drops.add(drop);
        }
        return new AirThreatPicture(ship.declaredAirThreat(), hostile, weight, armed, own, drops, ship.shipClass(),
                leadCraft, axisX, axisY, axisCount);
    }

    /**
     * Whether a strike craft carries ordnance that gets through this ship's armour at all. A craft that cannot
     * hurt the hull is no threat to it, however armed it is against the ground; a craft with nothing to drop
     * never is.
     */
    static boolean canHurt(World world, long id, ShuttleMission mission, Warship ship) {
        AirOrdnance load = mission.ordnance;
        if (load == null) {
            Airframe frame = world.airframe(id);
            load = frame == null ? null : frame.ordnance();
        }
        return load != null && Warship.effectiveDamage(load.damage, load.penetration, ship.armour) > 0f;
    }

    private static InboundDrop dropOf(World world, long id, ShuttleMission mission, Faction faction) {
        boolean mech = mission.mechVariant != null || (mission.mechVariants != null && mission.mechVariants.length > 0);
        if (mission.marinesRemaining <= 0 && !mech) return null;
        float eta;
        switch (mission.state) {
            case INCOMING -> {
                AirBody body = world.kinematics(id);
                Airframe frame = world.airframe(id);
                if (body == null) return null;
                float cruise = frame == null ? 4f : Math.max(1f, frame.flight().maxSpeed());
                float speed = Math.max(body.speed(), 0.5f * cruise);
                eta = body.distanceTo(mission.lzX, mission.lzY) / speed + SETTLE_SECONDS;
            }
            case PAD_DESCENT -> eta = PAD_DESCENT_SECONDS;
            case LANDED -> eta = 0f;
            default -> {
                return null;
            }
        }
        return new InboundDrop(id, faction, mission.lzX, mission.lzY, eta, mission.marinesRemaining, mech, mission.state);
    }

    /**
     * The weighted threat to the ship: declared threat plus hostile ships plus a capped share for armed craft,
     * each scaled by the ship's class ({@link AirThreatWeights}).
     */
    public float score() {
        return AirThreatWeights.score(shipClass, declared, shipWeight, armedCraft, leadCraft);
    }

    public int hostileShips() {
        return hostileShips;
    }

    public int armedCraft() {
        return armedCraft;
    }

    /** Hostile armed craft aimed at this ship's lead (an escort only); zero for a ship that escorts nobody. */
    public int leadCraft() {
        return leadCraft;
    }

    /** Whether there is a direction the lead's threats come from: craft aimed at it, or hostile ships. Escorts only. */
    public boolean hasThreatAxis() {
        return axisCount > 0;
    }

    /** Centroid of what threatens the lead (craft aimed at it, hostile ships), in cells; meaningful when {@link #hasThreatAxis()}. */
    public float threatX() {
        return axisCount == 0 ? 0f : axisX / axisCount;
    }

    /** See {@link #threatX()}. */
    public float threatY() {
        return axisCount == 0 ? 0f : axisY / axisCount;
    }

    public int ownAir() {
        return ownAir;
    }

    public float declared() {
        return declared;
    }

    /** Hostile loaded shuttles still on their way down, in craft-id order. */
    public List<InboundDrop> drops() {
        return drops;
    }

    /** The threats in words, for the posture's reason. */
    public String describe() {
        StringBuilder out = new StringBuilder();
        if (declared > 0f) out.append(String.format(Locale.ROOT, "declared %.1f", declared));
        if (hostileShips > 0) {
            if (out.length() > 0) out.append(", ");
            out.append(hostileShips).append(hostileShips == 1 ? " hostile ship" : " hostile ships");
        }
        if (armedCraft > 0) {
            if (out.length() > 0) out.append(", ");
            out.append(armedCraft).append(" armed craft");
        }
        if (leadCraft > 0) {
            if (out.length() > 0) out.append(", ");
            out.append(leadCraft).append(" armed craft at the lead");
        }
        return out.length() == 0 ? "none" : out.toString();
    }
}
