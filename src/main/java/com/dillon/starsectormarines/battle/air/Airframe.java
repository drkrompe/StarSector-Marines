package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;

/**
 * A kind of aircraft that can stand on a berth: what it looks like, which hull
 * sizes it, and how much of it there is to shoot.
 *
 * <p>The berth is the thing with identity on a field, and until now it could
 * only hold a {@link ShuttleType} — which made every aircraft on every airbase
 * a transport, whatever the base was for. A field that bases fighters needs its
 * berths to hold
 * {@link com.dillon.starsectormarines.battle.flyby.FighterProfile}s instead,
 * and those are already factional: the game ships five fighter hulls and
 * {@code FighterProfile.poolForFaction} already knows which faction flies
 * which. Copying them into {@link ShuttleType} so a berth could name one would
 * have put the same five aircraft in two enums.
 *
 * <p>Deliberately narrow: this is what standing on the ground requires, not
 * everything an aircraft is. A transport's capacity, a fighter's guns, and the
 * handling either one flies with are asked of the concrete type by whoever
 * needs them, because nothing that puts an airframe on a hardstand cares.
 */
public interface Airframe {

    /**
     * Sprite drawn for this airframe, resolved against the loaded game the
     * same way every other hull sprite is — the mod ships none of them.
     */
    String spritePath();

    /**
     * Vanilla hull id whose {@code .ship} geometry gives this airframe its
     * drawn size and pivot. Also what keys the engine-FX scrape.
     */
    String renderHullId();

    /**
     * What this aircraft delivers on a run, or {@code null} for one that
     * carries nothing.
     *
     * <p>Distinct from {@link #hardpoints}, which is about turrets — a mount
     * that traverses and picks its own target. A transport with a door gun has
     * hardpoints and no ordnance; a fighter is the other way round, because
     * the airframe is the mount and aiming it is flying it.
     */
    AirOrdnance ordnance();

    /**
     * Turret mounts this aircraft carries. Zero means it cannot shoot at all.
     *
     * <p>How many, not where: where each one sits comes from the hull's real
     * {@code weaponSlots} through
     * {@link com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver},
     * so an armed craft's guns are drawn and fired from the places the hull
     * actually has them.
     */
    int hardpoints();

    /**
     * How this aircraft flies.
     *
     * <p>Asked rather than implemented, because the two kinds of airframe know
     * it differently. A transport carries a hand-authored tier — a bus flies
     * like a bus by decision. A fighter's comes off its own hull spec through
     * {@code HullKinematicsResolver}, which is what makes an interceptor and a
     * bomber feel different without anybody tuning either.
     */
    AirHandling flight();

    /**
     * Structure on an undamaged hull.
     *
     * <p>What a berth starts with, what a turnaround repairs toward, and what
     * an attacker has to get through on the ground. A hull carries its own
     * remaining HP across a sortie; this is only the ceiling.
     */
    float maxHp();

    /**
     * Fraction of a hull's drawn length that stands in for its body circle.
     *
     * <p>A single radius has to speak for a shape that is long and narrow, and
     * the two obvious answers are both wrong: half the length circumscribes the
     * hull and claims wingspans of empty air either side of it, while the
     * roster's authored half-cell describes a machine that is plainly twelve
     * cells of aircraft on the map. A vanilla hull is drawn roughly twice as
     * long as it is wide, so half its length approximates its span and half of
     * that is the radius of the circle inscribed in it.
     */
    float RADIUS_PER_DRAWN_LENGTH = 0.25f;

    /**
     * How much of this aircraft there is to hit, in cells.
     *
     * <p>Asked here rather than computed by whoever holds the aircraft, because
     * an aircraft is two representations of one thing and both of them are
     * shot at. Rolling, it is an air body and {@code AirTargetService} answers
     * for it; parked, it is an ordinary roster unit and
     * {@code UnitRosterService.radius} answers instead. Those are two call
     * sites for one fact, and while each derived its own the same Valkyrie was
     * four and a half cells of aircraft taxiing and half a cell on its
     * hardstand — it changed size the instant it launched or recovered, which
     * is the drawn-scale pop one seam over.
     *
     * <p>Deliberately independent of the 3x3 navigation footprint a parked
     * aircraft stamps: how much deck an aircraft denies people is a gameplay
     * fact, not a measurement of the art.
     */
    default float targetRadiusCells() {
        return HullFootprintResolver.visualLengthCells(renderHullId())
                * AirAppearance.GROUND_SCALE * RADIUS_PER_DRAWN_LENGTH;
    }
}
