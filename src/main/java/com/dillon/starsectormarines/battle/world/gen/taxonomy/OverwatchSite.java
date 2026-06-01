package com.dillon.starsectormarines.battle.world.gen.taxonomy;

/**
 * One scored overwatch-corner position — the <em>positional</em> read that
 * complements {@link TacticalRegion}'s region-level <em>membership</em> read.
 * Where a high-{@link TacticalRegion#enclosure enclosure} region answers
 * "where do I garrison / fall back", an overwatch site answers "where do I
 * mount a gun": a cell with cover at its back and a long field of fire
 * <em>out</em> over low-cover ground.
 *
 * <p>See {@code roadmap/mapgen/stories/structural-taxonomy.md} §
 * "Membership vs. positional — the corner-tower correction": the walls that
 * make a pocket holdable are the same walls that box in a turret's arc, so the
 * two reads pick opposite cells. Built by {@link OverwatchScorer}.
 *
 * @param x      cell x of the firing position
 * @param y      cell y of the firing position
 * @param dirX   firing-direction x component ({@code -1/0/+1}); the wall is at
 *               {@code (x - dirX, y - dirY)}, the field of fire extends toward
 *               {@code (x + dirX, y + dirY)}
 * @param dirY   firing-direction y component
 * @param score  ranking score — capped outward open-run length, boosted when
 *               the position is a {@link #corner}
 * @param corner true when a cell perpendicular to the firing axis is also
 *               walled (an L-shaped corner, not a flat wall) — a sturdier mount
 */
public record OverwatchSite(int x, int y, int dirX, int dirY, float score, boolean corner) {}
