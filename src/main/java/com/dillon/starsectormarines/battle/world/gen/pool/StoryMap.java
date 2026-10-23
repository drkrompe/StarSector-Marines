package com.dillon.starsectormarines.battle.world.gen.pool;

/** A story map that plays from a pool rather than from any seed ({@code story-map-pools.md}). */
public enum StoryMap {
    CORDON_LINE("cordon-line"),
    CORDON_ROAD("cordon-road"),
    HEART_BURN("heart-burn"),
    HEART_FRONT("heart-front");

    /** The pool file's name and the {@code map} it declares. */
    public final String id;

    StoryMap(String id) { this.id = id; }

    /** The pool file, relative to the mod folder. */
    public String path() { return "data/world/pools/" + id + ".pool.json"; }
}
