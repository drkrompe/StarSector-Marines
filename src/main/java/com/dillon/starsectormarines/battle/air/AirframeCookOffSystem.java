package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.combat.Detonations;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Death-event handler that sets off a parked airframe when it is destroyed on
 * its hardstand — the fireball, the wreck, and the blast that catches whoever
 * was standing next to it.
 *
 * <p>An aircraft on the ground is a full tank with a thin skin over it, which
 * is the entire reason burning one is worth a fire team's time. Killed
 * silently it was just a target with a lot of hit points; killed the way this
 * handles it, the raiders who walked onto the apron to do it have to think
 * about where they are standing when it goes up, and so does the ground crew
 * that came out to fly it.
 *
 * <p>Sibling to {@code TurretDemolitionSystem} and {@code HubDemolitionSystem}
 * and built the same way: subscribed to the {@link
 * com.dillon.starsectormarines.battle.unit.DeathDispatcher}, classifying the
 * dead unit by id, keeping only an id side-table as a double-fire guard, and
 * reading position off the event's snapshot because the unit is released by
 * the time the mailbox drains.
 *
 * <p><b>A second door, for a death with no {@code DeathEvent} to knock with.</b>
 * A parked airframe is an ordinary grid unit, so its death publishes one; a
 * taxiing airframe is an air entity with no grid or combat components, so its
 * death is a mission-state transition and nothing else — there is no unit for
 * a {@code DeathDispatcher} to have heard about. {@link #cookOff} is that
 * second door, called directly by {@code AirSystem} for a craft it kills in a
 * grounded phase. Both doors open onto the same fire: the radius, the damage,
 * the friendly-fire behaviour and the refusal to chain are one definition
 * either way, and both share the double-fire guard below — not because either
 * caller can plausibly ask twice for the same airframe id, but because a
 * blast this specific is worth exactly one definition of "already burned" too.
 *
 * <p><b>It does not chain, and that is a rule rather than a measurement.</b>
 * An airframe standing on a berth is left out of the blast outright, so one
 * satchel is worth one aircraft however large the aircraft on either side of
 * it happen to be; the attacker works down the line. The blast is still sized
 * to the stand and the apron around it and still stops short of the next
 * hardstand, which an authored field puts {@link
 * com.dillon.starsectormarines.battle.world.gen.AirbaseLot#BERTH_PITCH} cells
 * away — that is what keeps the fire off the ground crew over there — but the
 * aircraft's own survival no longer rests on it. It used to: the area sweep
 * catches a body at blast radius <em>plus that body's own radius</em>, and
 * once a parked hull started reporting its real drawn size instead of a flat
 * half cell, a transport of the largest tier was wide enough to reach across
 * the gap. Nothing on a field is based with one today, which is the only
 * reason it was never seen, and "no field ever bases a bus" is a coincidence
 * rather than a property.
 *
 * <p><b>Rolling is not standing.</b> The exclusion is for an aircraft on its
 * own berth, which is a target the raider chose to spend time on rather than
 * to spend it on the one already burning. A craft caught taxiing, holding
 * short or partway down a roll is on open ground next to a fire and takes it
 * like anything else out there — the same reason the crossing is dangerous at
 * all.
 */
public final class AirframeCookOffSystem {

    /**
     * Blast radius in cells — the stand, its apron, and no further.
     *
     * <p>Short of the spacing between hardstands, so nobody standing at the
     * next berth is in it. What the aircraft over there is spared by is the
     * exclusion, not this number; see the class note.
     */
    public static final float BLAST_RADIUS_CELLS = 4f;

    /**
     * Damage to everything in the blast, before cover and armor.
     *
     * <p>Between a marine rocket and a demolition satchel: it kills infantry
     * caught in the open beside the aircraft outright and hurts armor, which
     * is what a fuel fire in a confined lot should do. It is not a shaped
     * charge and does not pretend to be one.
     */
    private static final float BLAST_DAMAGE = 240f;

    /** Efficiency against armor. Burning fuel is not a penetrator; a little above a rocket's. */
    private static final float BLAST_PENETRATION = 22f;

    /** Wall HP knocked off structures in the fire, and how far that reaches. Enough to open the shed beside the stand. */
    private static final int WALL_DAMAGE = 30;
    private static final float WALL_DAMAGE_RADIUS_CELLS = 2.5f;

    private final EffectsService effects;
    private final Detonations detonations;
    private final UnitRosterService roster;
    /** Airframes already burned, so a death that somehow arrived twice only goes up once. */
    private final LongOpenHashSet burned = new LongOpenHashSet();

    public AirframeCookOffSystem(EffectsService effects, Detonations detonations,
                                 UnitRosterService roster) {
        this.effects = effects;
        this.detonations = detonations;
        this.roster = roster;
    }

    /**
     * Death-event callback. Ignores everything that is not an airframe on a
     * hardstand.
     *
     * <p>The blast is applied here rather than queued with a fuse because the
     * explosion the player sees and the damage it does have to be the same
     * event; the same reason {@code Detonations.detonateNow} exists for
     * callers whose flight time is already spent. Killing more units from
     * inside a death drain is ordinary — the dispatcher drains in waves so a
     * re-entrant death lands in the same one, which is how a drone hub already
     * takes its drones with it.
     */
    public void onDeath(DeathEvent event) {
        long airframe = event.unitId();
        if (!roster.identity().type(airframe).isBasedAircraft()) return;
        ignite(airframe, event.x(), event.y(), roster.identity().faction(airframe));
    }

    /**
     * Sets off the same blast for an airframe that died away from its pad —
     * taxiing, holding short, partway down a takeoff roll or a landing
     * rollout — where there is no grid unit and therefore no {@link
     * DeathEvent} to publish one through. See the class note on why this and
     * {@link #onDeath} are one fire rather than two.
     */
    public void cookOff(long airframe, float x, float y, Faction faction) {
        ignite(airframe, x, y, faction);
    }

    private void ignite(long airframe, float x, float y, Faction faction) {
        if (!burned.add(airframe)) return;
        effects.spawnHeavyImpact(x, y, BLAST_RADIUS_CELLS);
        effects.spawnSmokingWreck((int) Math.floor(x), (int) Math.floor(y));
        detonations.detonateNow(new PendingDetonation(
                // Nobody is credited: what kills anyone here is the aircraft's
                // own fuel, and the round that opened the tank was fired at
                // the aircraft. An attacker earns the airframe, not the burns.
                CombatTelemetryService.NO_ATTACKER,
                x, y, /*remainingTime*/ 0f,
                BLAST_RADIUS_CELLS, BLAST_DAMAGE, BLAST_PENETRATION,
                WALL_DAMAGE, faction, /*aerialDelivery*/ false,
                WALL_DAMAGE_RADIUS_CELLS, /*spawnDustOnWallBreak*/ true,
                // Friendly fire on. A fire does not check anybody's colours,
                // and the ground crew walking out to the aircraft is exactly
                // who is standing close enough to find that out.
                /*friendlyFireImmune*/ false,
                /*directTargetId*/ 0L, /*directDamage*/ 0f, /*directPenetration*/ 0f,
                /*authoredAftermath*/ false,
                airframesOnTheirBerths()));
    }

    /**
     * Every aircraft currently standing on a berth, which is the set this fire
     * refuses to touch.
     *
     * <p>Read off the unit type rather than off the field's berth list: an
     * airframe is a {@code BASED_AIRCRAFT} unit exactly while it is standing on
     * one, and asking the type keeps the fire from having to know which field
     * it is burning on. The dying airframe is in here too and is welcome to be
     * — it is already dead.
     */
    private long[] airframesOnTheirBerths() {
        LongArrayList berthed = new LongArrayList();
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long id = dense[i];
            if (roster.identity().type(id).isBasedAircraft()) berthed.add(id);
        }
        return berthed.toLongArray();
    }

    /** Whether {@code airframe} has already gone up. The double-fire guard's observable state. */
    public boolean hasBurned(long airframe) {
        return burned.contains(airframe);
    }
}
