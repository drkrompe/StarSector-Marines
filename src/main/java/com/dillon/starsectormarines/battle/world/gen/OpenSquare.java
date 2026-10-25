package com.dillon.starsectormarines.battle.world.gen;

import java.util.List;

/**
 * An open square a set piece frames ({@code set-piece-blocks.md}): its centre cell, its open extent in world
 * cells (inclusive), and the mouths a fight can enter it by. A mission that wants to hold, cross or send
 * pressure through the square reads this rather than searching the finished map.
 */
public record OpenSquare(int centreX, int centreY, int left, int bottom, int right, int top, List<Mouth> mouths) {

    public OpenSquare {
        mouths = List.copyOf(mouths);
    }

    /**
     * One way into the square: the cells of its opening on the square's edge, from {@code (fromX, fromY)} to
     * {@code (toX, toY)} inclusive along one row or column, and the world step {@code (outX, outY)} that leaves
     * the square through it onto the street. A wide mouth is a street; a narrow one is an alley.
     */
    public record Mouth(int fromX, int fromY, int toX, int toY, int outX, int outY) {

        /** Cells across the opening. */
        public int width() { return Math.abs(toX - fromX) + Math.abs(toY - fromY) + 1; }

        /** Whether a vehicle-width street enters here rather than an alley people use. */
        public boolean wide() { return width() > 2; }
    }
}
