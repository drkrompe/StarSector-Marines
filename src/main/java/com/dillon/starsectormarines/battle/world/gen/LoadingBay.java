package com.dillon.starsectormarines.battle.world.gen;

/**
 * Where a set piece means a hauler to stand while it is loaded ({@code set-piece-blocks.md}): the
 * stand's middle in world cells, the heading it faces to drive out (a maths angle, 0° along +x), the yard cell in front of the
 * loading door the crew carry through, and the street cell outside the gate it leaves by.
 */
public record LoadingBay(float standX, float standY, float headingDegrees,
                         int doorX, int doorY, int gateX, int gateY) {
}
