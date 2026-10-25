package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;

import java.util.ArrayList;
import java.util.List;

/**
 * What a placement stage needs to know about one kind of fort: its footprint, its works, its gates, the buildings that
 * house its aspects and the structure at its centre. Pure geometry in the fort's own cardinally rotatable frame; each
 * kind authors its own, so a kind is a distinct layout and never another's with a part swapped.
 */
public interface FortPlan {

    /** What a gatehouse piece is: the leaves of the gate over its open passage (its towers are rooms, {@link #towers()}). */
    enum PieceKind { GATE }

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

    /**
     * A sally port: a short section of the works' wall, two cells along it and as deep as the wall, that stands closed
     * like the wall round it until the fort's holder unbars it (a fort's ports are its sally doors; see
     * {@code SallyDoors}). It is authored on a face of the works that has no gate, so the garrison does not walk round
     * the works to the belt beyond that face. Its cells are
     * wall cells ({@link #isWall}) at stamping; the mouth is the open cell just outside the first of them, and the inside
     * cell the open yard cell just behind it.
     *
     * @param cells the port's wall cells in the footprint frame
     * @param mouthX the open cell just outside the port
     * @param outX the direction outward from the port, a unit step along one axis
     */
    record SallyPort(List<int[]> cells, int mouthX, int mouthY, int outX, int outY) {
        public SallyPort {
            cells = List.copyOf(cells);
        }

        /** The yard cell just inside the port, as deep behind the mouth as the wall is thick plus one. */
        public int[] inside() {
            int depth = 0;
            for (int[] c : cells) {
                depth = Math.max(depth, Math.abs((c[0] - mouthX) * outX) + Math.abs((c[1] - mouthY) * outY));
            }
            return new int[]{mouthX - outX * (depth + 1), mouthY - outY * (depth + 1)};
        }

        /**
         * A port two cells along a face of the works: {@code alongX, alongY} is the first of the two cells on the
         * wall's outer layer, {@code outX, outY} the outward step and {@code depth} how many cells thick the wall is.
         */
        public static SallyPort onFace(int alongX, int alongY, int outX, int outY, int depth) {
            List<int[]> cells = new ArrayList<>();
            int stepX = outY != 0 ? 1 : 0;
            int stepY = outX != 0 ? 1 : 0;
            for (int k = 0; k < depth; k++) {
                for (int s = 0; s < 2; s++) {
                    cells.add(new int[]{alongX - outX * k + stepX * s, alongY - outY * k + stepY * s});
                }
            }
            return new SallyPort(cells, alongX + outX, alongY + outY, outX, outY);
        }
    }

    /**
     * The sally ports in the works, none by default. A port's cells are part of the wall until unbarred; it is not one of
     * the {@link #gates()}, so it is no assault approach and the belt does not open in front of it.
     */
    default List<SallyPort> sallyPorts() {
        return List.of();
    }

    FortKind kind();

    /** Local width and height of the footprint, belt and fence included. */
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

    /**
     * The ways in for an attacker, the plan's own assault data: each gate as an approach with the nests that cover it,
     * and a breach section of wall. Derived from the kind's gates, nests and wall unless the kind overrides it.
     */
    default List<AssaultApproach> approaches() {
        return FortApproaches.derive(this);
    }

    /** The cells of the wall left open as gates. */
    List<int[]> gateCells();

    /** Wall-nest cells, one in each bastion, each in an embrasure on the wall's outer face so it can fire out of the works. */
    List<int[]> nests();

    /** Paved vehicle lanes (x, y, width, height) in the footprint frame. */
    int[][] lanes();

    /** The outer belt's cells in drawing order. */
    List<FortBelt.Cell> belt();

    /**
     * Every gun an assault on this fort answers to: the bastion nests ({@link #nests()}, in that order) and then the nests
     * of the outer belt, which stand off the works across open ground and reach the approaches as far as the bastions do.
     * Indices into this list are what an {@link AssaultApproach} names as covering its mouth.
     */
    default List<int[]> defenceNests() {
        List<int[]> out = new ArrayList<>(nests());
        out.addAll(FortBelt.nests(belt()));
        return out;
    }

    /**
     * Where this kind stands its optional anti-air yard, or {@code null} for a kind that has none. Present in the plan
     * whether or not a given fort asks for it: whether to stamp it is the declaration's, not the plan's.
     */
    default AirDefenceYard airDefence() {
        return null;
    }

    /**
     * The middles of the three-by-three Locust missile batteries a fort carrying the anti-air aspect stands out on its
     * bastions, in the footprint frame (empty for a kind with no yard). Spread across the works so the airspace they
     * cover is as wide as the fort; the yard ({@link #airDefence()}) holds only the console that commands them.
     */
    default List<int[]> airBatteries() {
        return List.of();
    }

    /**
     * The middles of the three-by-three Swarmer missile towers a fort carrying the anti-air aspect stands on the bastions
     * its Locust batteries do not hold, in the footprint frame (empty for a kind with no yard). With the batteries they put
     * anti-air at every corner of the works.
     */
    default List<int[]> airTowers() {
        return List.of();
    }

    /**
     * Every room of every aspect's suite, in the order the layout stamps its buildings. An aspect is housed in a suite and
     * not a point: its main room carries the core and the roof art, and an ante-room or two stand in front of it.
     */
    List<FortRoom> rooms();

    /**
     * The floors that together are the place of {@code aspect}: every room of its suite, so holding the aspect means
     * holding all of them. An aspect with no rooms of the plan's own (the anti-air control room is stamped on request)
     * is its {@link #floor} alone, and an aspect housed in no building at all (the emitter) has an empty suite.
     */
    default List<int[]> suite(FortAspect aspect) {
        List<int[]> floors = new ArrayList<>();
        for (FortRoom room : rooms()) if (room.aspect() == aspect) floors.add(room.floor());
        if (floors.isEmpty() && aspect == FortAspect.AIR_DEFENCE) floors.add(floor(aspect));
        return floors;
    }

    /** The bounding box of {@code aspect}'s suite floors: x, y, width, height in the footprint frame. */
    default int[] suiteBounds(FortAspect aspect) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] f : suite(aspect)) {
            minX = Math.min(minX, f[0]);
            minY = Math.min(minY, f[1]);
            maxX = Math.max(maxX, f[0] + f[2]);
            maxY = Math.max(maxY, f[1] + f[3]);
        }
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }

    /**
     * The gatehouse towers, two flanking each gate ({@link GateTowers}): rooms of no aspect that the garrison holds and
     * fights from, stamped after {@link #rooms()} in the layout. None by default.
     */
    default List<FortRoom> towers() {
        return List.of();
    }

    /** Every room the layout stamps, in its order: the aspects' suites, then the gatehouse towers. */
    default List<FortRoom> buildings() {
        List<FortRoom> out = new ArrayList<>(rooms());
        out.addAll(towers());
        return out;
    }

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

    /**
     * How far the centrepiece is from the farthest cell of the main works, in cells and rounded up: the least reach a
     * dome standing on the centrepiece needs to cover the fort's own works, wall included.
     */
    default float worksRadius() {
        int[] centre = centrepiece();
        double farthest = 0;
        for (int x = 0; x < width(); x++) {
            for (int y = 0; y < height(); y++) {
                if (isWorks(x, y)) farthest = Math.max(farthest, Math.hypot(x - centre[0], y - centre[1]));
            }
        }
        return (float) Math.ceil(farthest + 1);
    }
}
