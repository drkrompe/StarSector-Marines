package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.turret.StructureDef;

/**
 * Which per-instance thing decides how big a body is, and in what order.
 *
 * <p>A body's radius is read two ways. {@link UnitRosterService#radius(long)}
 * asks by id — selection, ballistics, blast catch, the spatial index — a handful
 * of times per shot. {@code SeparationSystem} asks columnar, walking archetype
 * tables once a tick and taking the same fact off arrays it already has open.
 * That split is deliberate: turning the columnar walk into by-id probes would
 * cost one lookup per live unit per tick. It is the <em>access</em> that
 * differs, never the answer, so the order lives here and each caller supplies
 * the four values it already has in hand — four lookups on one side, four array
 * reads on the other.
 *
 * <p>Both derivations were written out for a while, and they drifted the moment
 * one of them grew a case: the airframe branch landed on the by-id accessor
 * only, so a parked Valkyrie shoved people around with the archetype's half cell
 * while every round fired at it aimed at four and a half. One object, two sizes,
 * nine-fold apart, and a marine could stand in the middle of the hull.
 *
 * <p><b>A carried body is resolved before this and not by it.</b> A convoy
 * chassis and an airborne craft answer through their {@link BodyCarrier}, and
 * they carry no {@code IDENTITY} at all — carrying no {@code POSITION},
 * {@code COMBAT}, {@code MOVEMENT} or {@code ROLE} is exactly what keeps
 * occupancy, separation, the mover and the planner off them. So a carried body
 * cannot even feed the parameters below, and it is never a row in the
 * {@code gridOccupants} query separation walks. That is a dispatch between
 * storage families rather than a step of this precedence, which is why it sits
 * at the by-id call site: adding a carrier lookup to a hot columnar loop would
 * buy nothing and cost a probe per unit per tick.
 */
public final class BodyRadius {

    private BodyRadius() {
    }

    /**
     * The one precedence for a body that lives in the ground roster's
     * {@code IDENTITY} columns: a turret answers from its structure, an
     * aircraft from its airframe, a mech from its variant, and only a type
     * whose whole archetype is one size falls through to
     * {@link UnitType#radius}.
     */
    public static float resolve(StructureDef turretStructure,
                                Airframe airframe,
                                MechVariant variant,
                                UnitType type) {
        if (turretStructure != null) return turretStructure.radius;
        if (airframe != null) return airframe.targetRadiusCells();
        if (variant != null) return variant.radius;
        return type.radius;
    }
}
