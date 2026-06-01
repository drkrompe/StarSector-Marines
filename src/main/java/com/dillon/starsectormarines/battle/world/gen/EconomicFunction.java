package com.dillon.starsectormarines.battle.world.gen;

/**
 * The economic role a target world plays, distilled from its vanilla industry
 * mix to a stable, campaign-decoupled vocabulary. This is what lets the city a
 * battle is fought over <em>read and fight</em> like what it is — a mining world
 * unlike a farming colony unlike a trade hub.
 *
 * <p>Carried inward by {@link TargetProfile#functions()} (the campaign → battle
 * bridge): {@code TargetProfileResolver} maps vanilla {@code Industries.*} ids
 * onto these values at the launch boundary, so {@code battle.world.gen} stays
 * campaign-free and headless-testable — no {@code MarketAPI} reaches the core.
 *
 * <p>Presence-only for now (an {@code EnumSet} on the profile); per-function
 * weight from industry size / upgrade tier is a later refinement. The selection
 * layer consumes the set via {@link EconomicZoning}. See
 * {@code roadmap/economic-districts/overview.md}.
 */
public enum EconomicFunction {

    /** Population / urban living — the residential baseline every market carries. */
    HABITATION,

    /** Trade and markets — commerce industry; reads as a dense civic core. */
    COMMERCE,

    /** Heavy / light industry, orbital works — factory and yard terrain. */
    HEAVY_INDUSTRY,

    /** Spaceport / megaport — pad fields and landing structures (also drives {@link TargetProfile#spaceportTier()}). */
    SPACEPORT,

    /** Ore / tech mining — pit-and-headframe extraction terrain. */
    MINING,

    /** Refining / fuel production — tank-farm and pipe-run terrain. */
    REFINING,

    /** Farming / aquaculture — open fields and silos. */
    AGRICULTURE,

    /** Military base / high command / patrol HQ — garrison and fortification. */
    MILITARY,
}
