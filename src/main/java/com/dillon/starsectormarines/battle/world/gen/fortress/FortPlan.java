package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;

import java.util.List;

/**
 * What a placement stage needs to know about one kind of fort: its footprint, its works, its gates, the buildings that
 * house its aspects and the structure at its centre. Pure geometry in the fort's own cardinally rotatable frame; each
 * kind authors its own, so a kind is a distinct layout and never another's with a part swapped.
 */
public interface FortPlan {

    /** What a gatehouse piece is: a tower cap standing on solid wall, or the leaves of the gate over its open passage. */
    enum PieceKind { TOWER, GATE }

    /**
     * One piece of gatehouse art in the footprint frame: the cells it covers and the clockwise quarter turns its sprite
     * takes from its authored frame (outer flank on the left, passage along x). The sprite is stretched to the cells.
     */
    record Piece(PieceKind kind, int x, int y, int width, int height, int turns) { }

    /** The plan of a kind of fort. */
    static FortPlan of(FortKind kind) {
        return switch (kind) {
            case BATTERY_FORT -> BatteryFort.PLAN;
            case SHIELD_FORT -> ShieldFort.PLAN;
        };
    }

    /** Gatehouse pieces to draw art over; empty for a kind that has no authored gatehouse art yet. */
    default List<Piece> gatehouse() {
        return List.of();
    }

    /** One gate through the works: the open cell just outside its middle and the direction outward from it. */
    record Gate(int mouthX, int mouthY, int outX, int outY) { }

    FortKind kind();

    /** Local width and height of the footprint, belt and wire included. */
    int width();

    int height();

    /** The buildings of the fort; the host stamps walls, belt, lanes and dressing from this frame. */
    CompoundLayout layout();

    /** Whether the footprint cell is within the main works, wall included. */
    boolean isWorks(int x, int y);

    /** Whether the local cell is part of the works' wall (gatehouses, baffles and any inner ring included), not a gate. */
    boolean isWall(int x, int y);

    /** The cells that must be open ground for a fort to stand: the works and the glacis round them. */
    List<int[]> clearCells();

    /** Gates in order; the first is the main gate, which placement turns to face the map centre. */
    List<Gate> gates();

    /** The cells of the wall left open as gates. */
    List<int[]> gateCells();

    /** Wall-nest cells, one in each bastion. */
    List<int[]> nests();

    /** Paved vehicle lanes (x, y, width, height) in the footprint frame. */
    int[][] lanes();

    /** The outer belt's cells in drawing order. */
    List<FortBelt.Cell> belt();

    /** The aspects housed in buildings, in the order their cores are stamped. */
    List<FortAspect> aspects();

    /** The floor of the building housing {@code aspect}: x, y, width, height in the footprint frame. */
    int[] floor(FortAspect aspect);

    /** The door of the building housing {@code aspect}, as the footprint cell of its wall ring. */
    int[] door(FortAspect aspect);

    /** The middle of the three-by-three core of {@code aspect}, in the local frame. */
    int[] coreCentre(FortAspect aspect);

    /** The structure id of the core of {@code aspect}. */
    String coreStructureId(FortAspect aspect);

    /** The structure the fort is built around (the battery platform, the shield generator). */
    String centrepieceStructureId();

    /** The middle of the centrepiece's three-by-three footprint. */
    int[] centrepiece();

    /** The aspect the centrepiece is, or {@code null} when it is not one (the battery platform). */
    FortAspect centrepieceAspect();
}
