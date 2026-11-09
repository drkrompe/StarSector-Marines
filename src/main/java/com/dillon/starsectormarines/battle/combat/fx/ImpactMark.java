package com.dillon.starsectormarines.battle.combat.fx;

/**
 * The kind of mark a weapon's round leaves where it lands ({@code render.impactMark}): what calibre and what kind of
 * weapon struck, not which frame draws it. {@link ImpactDecals} turns a mark into decals for the ground or a wall.
 */
public enum ImpactMark {
    /** Leaves nothing: ion, point defence, spores. */
    NONE,
    /** Rifle, SMG and light machine-gun rounds: dust pocks in the ground, spalled pocks in a wall. */
    SMALL_ARMS,
    /** Anti-materiel, heavy machine-gun and autocannon rounds: a furrow along the line of flight, a punched wall crater. */
    HEAVY_ROUND,
    /** Pulse lasers: a fused scorch dot. */
    PULSE,
    /** Continuous beams: a seared line along the beam, laid now and then as it plays over a spot. */
    BEAM,
    /** Thermal lances: a glassy melt crater. */
    MELT,
    /** Fragmentation warheads and grenades: a blast spot peppered with fragment strikes. */
    FRAG,
    /** Flak bursting near the ground: a lighter fragment pepper. */
    FLAK,
    /** Rockets, missiles and HE grenades: a flat scorched blast star. */
    ROCKET,
    /** Light shells (mortars, light cannon): a crater and its thrown debris. */
    SHELL,
    /** Heavy shells (heavy mortars, heavy cannon): a larger crater over a burn, ringed with debris. */
    HEAVY_SHELL,
    /** Incendiary grenades: burned-out ground. */
    INCENDIARY,
    /** Flame throwers: a burn widening along the jet. */
    FLAME,
    /** Spitter acid: a spatter of venom. */
    ACID,
    /** Ground thrown up from below (spines): debris, no crater. */
    UPHEAVAL
}
