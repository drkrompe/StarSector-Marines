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
    HEART_FRONT("heart-front");

    /** The pool file's name and the {@code map} it declares. */
    public final String id;

    StoryMap(String id) { this.id = id; }

    /** The pool file, relative to the mod folder. */
    public String path() { return "data/world/pools/" + id + ".pool.json"; }
}
