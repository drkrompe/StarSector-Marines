package com.dillon.starsectormarines.battle.world.gen.pool;

/** A story map that plays from a pool rather than from any seed ({@code story-map-pools.md}). */
public enum StoryMap {
    CORDON_LINE("cordon-line"),
    CORDON_ROAD("cordon-road"),
    CORDON_LINE_TOWN("cordon-line-town"),
    CORDON_ROAD_TOWN("cordon-road-town"),
    CORDON_LINE_URBAN("cordon-line-urban"),
    CORDON_ROAD_URBAN("cordon-road-urban"),
    HEART_BURN("heart-burn"),
    HEART_FRONT("heart-front"),
    HEART_BURN_TOWN("heart-burn-town"),
    HEART_FRONT_TOWN("heart-front-town"),
    HEART_BURN_CITY("heart-burn-city"),
    HEART_FRONT_CITY("heart-front-city"),
    /** Colony Breach's colony, one map per biome while the mission is tuned (owner, 2026-10-26). */
    COLONY_BREACH("colony-breach");

    /** The pool file's name and the {@code map} it declares. */
    public final String id;

    StoryMap(String id) { this.id = id; }

    /** The pool file, relative to the mod folder. */
    public String path() { return "data/world/pools/" + id + ".pool.json"; }
}
