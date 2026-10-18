package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Where an armor matchup is fought. Every scenario shares one skeleton — attackers start at the west edge and
 * advance, defenders hold eighty cells east, farther apart than any machine's gun reaches — and differ only in the
 * ground between and around them, so a difference between scenarios for one pairing is a difference the ground made.
 *
 * <p>{@code open-ground} is bare. {@code hull-down} gives the defenders a low masonry berm in front of every slot (a
 * see-through parapet that hides a mech's legs and a vehicle's hull, which the section cover rows make matter) and
 * scatters berms and short solid walls over the middle ground. {@code street} lines an eighteen-cell street with
 * solid building blocks, broken by two cross streets, with building fronts jutting into it from alternate sides
 * and a low barricade in the lee of two of them; its defenders stand behind the last two corners.
 */
public final class ArmorScenarios {

    private ArmorScenarios() {}

    static final int WIDTH = 120;
    static final int HEIGHT = 48;
    static final int MID_Y = HEIGHT / 2;
    static final int ATTACKER_X = 12;
    static final int DEFENDER_X = 92;
    private static final int TICK_LIMIT = 5400;
    /** Unit slots: three abreast, then further back. */
    private static final int[] ROW_OFFSETS = {0, -6, 6};
    private static final int COLUMN_SPACING = 8;
    /** Wall hit points: berms can be shot away in a long fight, building walls practically cannot. */
    private static final int BERM_HP = 400;
    private static final int BUILDING_HP = 5000;
    static final int STREET_LO = MID_Y - 9;
    static final int STREET_HI = MID_Y + 8;

    /** A piece of ground, fresh for each fight. */
    public interface ArmorScenario {
        String id();

        String label();

        default int maxTicks() {
            return TICK_LIMIT;
        }

        default ReviewAnnotations annotations() {
            return ReviewAnnotations.NONE;
        }

        /** Lays the ground on an all-floor grid. */
        void lay(NavigationGrid grid, CellTopology topology);

        /** Cell of the {@code index}th machine of a side. */
        default int[] slot(boolean attacker, int index) {
            return ArmorScenarios.slot(attacker, index);
        }
    }

    public static List<ArmorScenario> all() {
        return List.of(new OpenGround(), new HullDown(), new Street());
    }

    public static ArmorScenario byId(String id) {
        for (ArmorScenario scenario : all()) if (scenario.id().equals(id)) return scenario;
        throw new IllegalArgumentException("No armor scenario '" + id + "'; known: "
                + all().stream().map(ArmorScenario::id).toList());
    }

    /** Cell of the {@code index}th machine of a side: three abreast, each further rank set back from the enemy. */
    static int[] slot(boolean attacker, int index) {
        int rank = index / ROW_OFFSETS.length;
        int x = attacker ? ATTACKER_X - rank * COLUMN_SPACING : DEFENDER_X + rank * COLUMN_SPACING;
        return new int[] {x, MID_Y + ROW_OFFSETS[index % ROW_OFFSETS.length]};
    }

    /** The ground as a fresh grid, with cover recomputed. */
    static Ground build(ArmorScenario scenario) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
            }
        }
        scenario.lay(grid, topology);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);
        return new Ground(grid, topology);
    }

    record Ground(NavigationGrid grid, CellTopology topology) {}

    /** A north-south run of low see-through parapet: hides what stands behind it from the knees down. */
    private static void berm(NavigationGrid grid, CellTopology topology, int x, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, true);
            grid.setWallHp(x, y, BERM_HP);
            topology.setWall(x, y, true);
            topology.setWindow(x, y, true);
        }
    }

    /** An inclusive rectangle of solid wall nothing sees or shoots through. */
    private static void solid(NavigationGrid grid, CellTopology topology, int x0, int y0, int x1, int y1, int hp) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                grid.setWallHp(x, y, hp);
                topology.setWall(x, y, true);
            }
        }
    }

    static final class OpenGround implements ArmorScenario {
        @Override public String id() { return "open-ground"; }

        @Override public String label() {
            return "Open ground: attackers advance ~80 cells over bare dirt onto defenders holding";
        }

        @Override public void lay(NavigationGrid grid, CellTopology topology) { }
    }

    static final class HullDown implements ArmorScenario {
        /** Berms out in the middle ground: x, centre y, half length. */
        private static final int[][] FIELD_BERMS = {
                {34, MID_Y - 9, 2}, {34, MID_Y + 9, 2}, {48, MID_Y, 3}, {62, MID_Y - 7, 2},
                {62, MID_Y + 8, 2}, {74, MID_Y + 1, 3}};
        /** Short solid walls (ruins) out in the middle ground: x0, y0, x1, y1. */
        private static final int[][] FIELD_WALLS = {
                {42, MID_Y - 15, 43, MID_Y - 11}, {55, MID_Y + 11, 56, MID_Y + 15}, {68, MID_Y - 2, 69, MID_Y + 1}};

        @Override public String id() { return "hull-down"; }

        @Override public String label() {
            return "Hull-down: defenders behind low berms (legs and hulls hidden), the middle ground broken by "
                    + "scattered berms and ruined walls";
        }

        @Override public void lay(NavigationGrid grid, CellTopology topology) {
            for (int i = 0; i < ROW_OFFSETS.length; i++) {
                int y = MID_Y + ROW_OFFSETS[i];
                berm(grid, topology, DEFENDER_X - 3, y - 2, y + 2);
            }
            for (int[] b : FIELD_BERMS) berm(grid, topology, b[0], b[1] - b[2], b[1] + b[2]);
            for (int[] w : FIELD_WALLS) solid(grid, topology, w[0], w[1], w[2], w[3], BUILDING_HP);
        }

        @Override public ReviewAnnotations annotations() {
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            for (int i = 0; i < ROW_OFFSETS.length; i++) {
                int y = MID_Y + ROW_OFFSETS[i];
                marks.box(DEFENDER_X - 3, y - 2, DEFENDER_X - 3, y + 2, i == 0 ? "BERM" : "", ReviewStyle.NOTE);
            }
            for (int[] b : FIELD_BERMS) marks.box(b[0], b[1] - b[2], b[0], b[1] + b[2], "", ReviewStyle.NOTE);
            for (int[] w : FIELD_WALLS) marks.box(w[0], w[1], w[2], w[3], "WALL", ReviewStyle.NOTE);
            return marks.build();
        }
    }

    static final class Street implements ArmorScenario {
        /** Building blocks run x 20..104, cut by cross streets at these inclusive x ranges. */
        private static final int[][] CROSS_STREETS = {{40, 46}, {70, 76}};
        private static final int BLOCK_X0 = 20;
        private static final int BLOCK_X1 = 104;
        /** The last pair of fronts, facing each other: the corners the defenders stand behind. */
        private static final int CORNER_X0 = DEFENDER_X - 8;
        /**
         * Building fronts jutting four cells into the street from alternate sides: x0, x1, y0, y1. They give a machine
         * a corner to stand behind and narrow the lane. Deeper, they would break the sightline along the street, but a
         * jog the heavier hulls must steer around closes it to them: their planner wants a near-straight lane.
         */
        private static final int[][] CHICANES = {
                {50, 55, STREET_HI - 3, STREET_HI}, {62, 67, STREET_LO, STREET_LO + 3},
                {CORNER_X0, CORNER_X0 + 3, STREET_LO, STREET_LO + 3}, {CORNER_X0, CORNER_X0 + 3, STREET_HI - 3, STREET_HI}};
        /**
         * Low barricades in the lee of the jutting buildings, against the kerb: x, y0, y1. Out in the open roadway a
         * barricade closes the street to the heavier hulls, whose planner wants a clear straight lane.
         */
        private static final int[][] BARRICADES = {{57, STREET_HI - 3, STREET_HI}, {69, STREET_LO, STREET_LO + 3}};

        @Override public String id() { return "street"; }

        /**
         * Defenders stand behind the last two building corners, first the north one, then the south, then the open
         * middle of the street; further ranks back. A straight street is otherwise a firing lane, and a fight down it
         * reads like the open ground.
         */
        @Override public int[] slot(boolean attacker, int index) {
            if (attacker) return ArmorScenarios.slot(true, index);
            int rank = index / 3;
            int x = DEFENDER_X - 2 + rank * 8;
            int y = switch (index % 3) {
                case 0 -> STREET_HI - 1;
                case 1 -> STREET_LO + 1;
                default -> MID_Y;
            };
            return new int[] {x, y};
        }

        @Override public String label() {
            return "Street: an eighteen-cell street between solid building blocks, two cross streets, buildings "
                    + "jutting into it from alternate sides, low barricades in their lee, defenders behind the last "
                    + "two corners";
        }

        @Override public void lay(NavigationGrid grid, CellTopology topology) {
            for (int[] block : blocks()) {
                solid(grid, topology, block[0], 0, block[1], STREET_LO - 1, BUILDING_HP);
                solid(grid, topology, block[0], STREET_HI + 1, block[1], HEIGHT - 1, BUILDING_HP);
            }
            for (int[] c : CHICANES) solid(grid, topology, c[0], c[2], c[1], c[3], BUILDING_HP);
            for (int[] b : BARRICADES) berm(grid, topology, b[0], b[1], b[2]);
            for (int y = STREET_LO; y <= STREET_HI; y++) {
                for (int x = 0; x < WIDTH; x++) topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }

        private static List<int[]> blocks() {
            List<int[]> out = new ArrayList<>();
            int start = BLOCK_X0;
            for (int[] gap : CROSS_STREETS) {
                out.add(new int[] {start, gap[0] - 1});
                start = gap[1] + 1;
            }
            out.add(new int[] {start, BLOCK_X1});
            return out;
        }

        @Override public ReviewAnnotations annotations() {
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            for (int[] block : blocks()) {
                marks.box(block[0], 0, block[1], STREET_LO - 1, "BUILDINGS", ReviewStyle.NOTE);
                marks.box(block[0], STREET_HI + 1, block[1], HEIGHT - 1, "", ReviewStyle.NOTE);
            }
            for (int[] c : CHICANES) marks.box(c[0], c[2], c[1], c[3], "", ReviewStyle.NOTE);
            for (int[] b : BARRICADES) marks.box(b[0], b[1], b[0], b[2], "", ReviewStyle.NOTE);
            return marks.build();
        }
    }
}
