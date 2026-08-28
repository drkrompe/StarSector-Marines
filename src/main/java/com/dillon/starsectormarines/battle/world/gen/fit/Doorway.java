package com.dillon.starsectormarines.battle.world.gen.fit;

/**
 * One cell of doorway into a furnished room.
 *
 * <p>A record rather than an {@code int[]} because a record's own equality
 * compares its components, and an array component compares by identity — so a
 * room carrying arrays never equalled an identical room, and the determinism
 * check that was supposed to catch layout drift could only ever fail.
 */
public record Doorway(int x, int y) {}
