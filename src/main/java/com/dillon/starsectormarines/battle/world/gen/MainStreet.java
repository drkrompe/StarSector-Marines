package com.dillon.starsectormarines.battle.world.gen;

/**
 * The main street district ({@code set-piece-districts.md}): one straight street with shopfronts on both
 * sides, a plaza at one end and a civic hall closing the other. The spine is the street's centre line in
 * world cells, from the plaza end to the hall end, and {@code width} its carriageway; the plaza is the market
 * square's open extent where it stands, and the plain plaza's leaf otherwise, inclusive; {@code square} is the
 * market square's {@link OpenSquare} (also on {@code MapResult.openSquares}), or null; the landmark door is the hall's front door onto the street, or {@code -1, -1} where the hall
 * could not be carved. A fight down the street runs from the square to the hall's steps.
 */
public record MainStreet(float startX, float startY, float endX, float endY, int width,
                         int plazaLeft, int plazaTop, int plazaRight, int plazaBottom,
                         int landmarkDoorX, int landmarkDoorY, OpenSquare square) {
}
