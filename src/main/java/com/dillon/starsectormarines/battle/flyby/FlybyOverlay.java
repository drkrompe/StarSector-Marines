package com.dillon.starsectormarines.battle.flyby;

/**
 * The fighter weapon-sound ids, and nothing else.
 *
 * <p><b>What this used to be.</b> A 1,583-line overlay that flew fighters: its
 * own heading-and-weave flight integration, its own map-edge entry and cycling
 * re-entry, its own cluster scan and bank-back/run state machine, its own
 * tracer and missile fire resolving straight into
 * {@code BattleSimulation.applyExternalDamage}, and its own particle system for
 * the visuals of all of it. Every one of those was a second implementation of
 * something {@code battle.air} now owns properly —
 * {@link com.dillon.starsectormarines.battle.air.AirBody} and
 * {@link com.dillon.starsectormarines.battle.air.AirSteeringSystem} for the
 * flight, {@link com.dillon.starsectormarines.battle.air.ShuttleMission} for
 * the lifecycle, {@link com.dillon.starsectormarines.battle.air.AirCorridor}
 * for coming and going off the map, and
 * {@link com.dillon.starsectormarines.battle.air.AirOrdnance} released through
 * the detonation pipeline for the fire. The overlay's version of each was worse
 * in the same way: its rounds could not be stopped by a roof, its aircraft
 * could not be shot down, its damage bypassed armour and cover, and its
 * fighters were invisible to everything in the simulation that looks at air.
 *
 * <p>{@link com.dillon.starsectormarines.battle.air.AirCoverSystem} flies the
 * same {@link FlybyRoster} now, as real air entities.
 *
 * <p><b>What is left.</b> Six sound ids that
 * {@link FighterProfile} names. They are here rather than on the profile only
 * because moving them is an edit to that file; the class earns its keep until
 * then and not a line longer. See {@code fighter-air-entities.md}.
 */
public final class FlybyOverlay {

    // ---- Sound ids (declared in mod/data/config/sounds.json). -----------------
    public static final String SFX_GUN_HEAVY      = "marines_flyby_gun_heavy";
    public static final String SFX_GUN_LIGHT      = "marines_flyby_gun_light";
    public static final String SFX_GUN_ENERGY     = "marines_flyby_gun_energy";
    public static final String SFX_IMPACT         = "marines_flyby_impact";
    public static final String SFX_MISSILE_LAUNCH = "marines_flyby_missile";
    public static final String SFX_MISSILE_IMPACT = "marines_flyby_missile_impact";

    private FlybyOverlay() {}
}
