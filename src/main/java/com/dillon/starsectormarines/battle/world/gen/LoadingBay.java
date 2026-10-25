package com.dillon.starsectormarines.battle.world.gen;

/**
 * Where a set piece means a hauler to stand while it is loaded ({@code set-piece-blocks.md}): the
 * stand's middle in world cells, the heading it faces to drive out (a maths angle, 0° along +x), the yard cell in front of the
 * loading door the crew carry through, the street cell outside the gate it leaves by, and the cell straight out from the
 * gate where the set piece proved a hauler can turn either way.
 *
 * <p>The turn is part of the bay because a hauler leaving a walled yard drives clear of the gate before it turns: the
 * cheapest path bends at the gate's mouth, hugging the wall, which no hauler can follow.
 */
public record LoadingBay(float standX, float standY, float headingDegrees,
                         int doorX, int doorY, int gateX, int gateY, int turnX, int turnY) {
}
