package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The starter scenarios. They share one skeleton — attackers walk in from the west edge, defenders hold the east —
 * and differ in what the defenders hold: bare ground, a trench, or a building. Nothing else is varied, so a
 * difference between scenarios for one matchup is a difference the ground made.
 */
public final class MatchupScenarios {

    private MatchupScenarios() {}

    static final int FIELD_WIDTH = 150;
    static final int FIELD_HEIGHT = 56;
    static final int MID_Y = FIELD_HEIGHT / 2;
    static final int ATTACKER_X = 10;
    /** Where the defenders stand: far enough that the attackers cross open ground for about a hundred cells. */
    static final int DEFENDER_X = 118;
    static final int TRENCH_CELLS = 12;
    private static final int TICK_LIMIT = 9000;

    public static List<MatchupScenario> all() {
        return List.of(new OpenField(), new Trench(false), new Trench(true), new Building());
    }

    public static MatchupScenario byId(String id) {
        for (MatchupScenario scenario : all()) if (scenario.id().equals(id)) return scenario;
        throw new IllegalArgumentException("No matchup scenario '" + id + "'; known: "
                + all().stream().map(MatchupScenario::id).toList());
    }

    private static SceneBuilder arena(long seed) {
        return SceneBuilder.openGround(FIELD_WIDTH, FIELD_HEIGHT).missionCompletion(false).seed(seed);
    }

    private static void attackers(SceneBuilder builder, MarineLoadout[] kit, int targetX, int targetY) {
        builder.squad(MatchupScenario.ATTACKERS)
                .faction(Faction.MARINE).type(UnitType.MARINE)
                .loadouts(kit)
                .at(ATTACKER_X, MID_Y)
                .assigned(id -> ObjectiveAssignment.attackMove(id, targetX, targetY))
                .done();
    }

    private static void defenders(SceneBuilder builder, MarineLoadout[] kit, int x, int y) {
        builder.squad(MatchupScenario.DEFENDERS)
                .faction(Faction.DEFENDER).type(UnitType.MARINE_RED)
                .loadouts(kit)
                .at(x, y)
                .assigned(id -> ObjectiveAssignment.defendArea(id, x, y))
                .done();
    }

    /** Scenario 1: twelve marines cross open ground to a target another twelve are standing on. */
    static final class OpenField implements MatchupScenario {
        @Override public String id() { return "open-field"; }

        @Override public String label() {
            return "Open field: attackers attack-move ~108 cells across bare ground onto defenders holding the target";
        }

        @Override public int maxTicks() { return TICK_LIMIT; }

        @Override public SceneWorld build(MarineLoadout[] attackerKit, MarineLoadout[] defenderKit, long seed) {
            SceneBuilder builder = arena(seed);
            attackers(builder, attackerKit, DEFENDER_X, MID_Y);
            defenders(builder, defenderKit, DEFENDER_X, MID_Y);
            return builder.build();
        }
    }

    /**
     * Scenario 2: the same crossing, with the defenders dug in along a twelve-cell trench. The trench runs north to
     * south, square across the attackers' line of advance and facing them. Laid east to west it points straight down
     * the attackers' axis, which is an enfilade: they would be firing along its length and the defenders would be
     * flanked by the geometry before any loadout mattered.
     */
    static final class Trench implements MatchupScenario {
        /**
         * Whether a Vulcan emplacement stands at each end of the trench: the heavily defended version of the line,
         * where the squad in the trench is covered by two automatic guns firing across its front.
         */
        private final boolean fortified;

        Trench(boolean fortified) {
            this.fortified = fortified;
        }

        @Override public String id() { return fortified ? "trench-vulcans" : "trench"; }

        @Override public String label() {
            return fortified
                    ? "Trench with Vulcans: the trench line with a Vulcan emplacement at its north and south ends"
                    : "Trench: the open-field crossing onto defenders holding a twelve-cell trench square across the approach";
        }

        @Override public int maxTicks() { return TICK_LIMIT; }

        @Override public SceneWorld build(MarineLoadout[] attackerKit, MarineLoadout[] defenderKit, long seed) {
            SceneBuilder builder = arena(seed);
            int y0 = MID_Y - TRENCH_CELLS / 2;
            if (fortified) {
                // On the run's line with one clear cell past each end: the guns flank the squad rather than replace any
                // of its positions, and a cell gap keeps the run itself twelve cells long.
                builder.posts(List.of(vulcan(DEFENDER_X, y0 - 2), vulcan(DEFENDER_X, y0 + TRENCH_CELLS + 1)));
            }
            attackers(builder, attackerKit, DEFENDER_X, MID_Y);
            builder.squad(MatchupScenario.DEFENDERS)
                    .faction(Faction.DEFENDER).type(UnitType.MARINE_RED)
                    .loadouts(defenderKit)
                    .inFile(DEFENDER_X, y0, 0, 1)
                    .done();
            SceneWorld world = builder.build();
            int run = dig(world, DEFENDER_X, y0, y0 + TRENCH_CELLS - 1);
            // The commander's order to man a garrisonable: an area defence whose claims stay on this run. A plain
            // area defence spreads the squad round the centre, half of it on the open ground either side.
            world.squad(MatchupScenario.DEFENDERS).assignedObjective = ObjectiveAssignment.defendRun(
                    world.squadId(MatchupScenario.DEFENDERS), DEFENDER_X, MID_Y, TRENCH_CELLS / 2 + 1, run);
            return world;
        }

        private static DefensePost vulcan(int x, int y) {
            return new DefensePost(DefensePostKind.LIGHT, x, y,
                    List.of(new DefensePost.TurretSpec(TurretCatalogRegistry.VULCAN_STRUCTURE_ID, x, y)));
        }

        /** A straight north-south trench, each cell joined to its neighbours above and below (mask N1 S4; north is +y). */
        private static int dig(SceneWorld world, int x, int y0, int y1) {
            TileRegistry registry = TileRegistry.installed();
            for (int y = y0; y <= y1; y++) {
                int mask = (y < y1 ? 1 : 0) | (y > y0 ? 4 : 0);
                DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
                if (def == null) throw new IllegalStateException("no trench piece for mask " + mask);
                world.sim().addDoodad(new Doodad(x, y, def));
            }
            FightingPositionIndex index = world.sim().fightingPositions();
            int run = index.runIdAt(x, y0);
            int cells = y1 - y0 + 1;
            if (run < 0 || index.runSize(run) != cells) {
                throw new IllegalStateException("The trench did not come out as one fighting position of " + cells);
            }
            return run;
        }
    }

    /**
     * Scenario 3: the defenders garrison a generated building and the attackers are ordered to secure it.
     *
     * <p>The building is a production stamp ({@code BuildingStamp.MARKET_HALL}), not hand-cut walls: real windows
     * the garrison can fire from, internal walls, doorways and its furniture for cover, placed through the same
     * stamper a city uses. It is turned a quarter so its two north windows face the attackers' approach and its
     * doors open south, off the axis. The garrison is set up as a generated garrison is (garrison role, a home cell
     * inside, the building as its tactical node, holding fire until the enemy is close); the attackers get the
     * compound order a commander gives.
     */
    static final class Building implements MatchupScenario {
        private static final BuildingStamp STAMP = BuildingStamp.MARKET_HALL;
        private static final int TURNS = 1;
        private static final int LEFT = DEFENDER_X - 4;

        @Override public String id() { return "building"; }

        @Override public String label() {
            return "Building: attackers secure a generated market hall (windows facing them, doors south) "
                    + "held by a garrison";
        }

        @Override public int maxTicks() { return TICK_LIMIT; }

        @Override public SceneWorld build(MarineLoadout[] attackerKit, MarineLoadout[] defenderKit, long seed) {
            GenContext ctx = new GenContext(new NavigationGrid(FIELD_WIDTH, FIELD_HEIGHT),
                    new CellTopology(FIELD_WIDTH, FIELD_HEIGHT), new Random(seed), FIELD_WIDTH, FIELD_HEIGHT, seed);
            new GenRecipe("Matchup building", List.of(
                    Building::openGround,
                    c -> STAMP.place(c, LEFT, MID_Y - 6, TURNS),
                    new FinalizeStage())).run(ctx);
            PointOfInterest room = ctx.pois.get(ctx.pois.size() - 1);
            TacticalNode node = new TacticalNode(TacticalNode.Kind.OBJECTIVE, room.interiorAnchorX,
                    room.interiorAnchorY, room.left, room.top, room.right, room.bottom, Faction.DEFENDER, 60,
                    defenderKit.length);
            List<int[]> inside = interiorCells(ctx.grid, room, defenderKit.length);

            SceneBuilder builder = SceneBuilder.over(ctx.grid, ctx.topology).missionCompletion(false).seed(seed);
            builder.squad(MatchupScenario.ATTACKERS)
                    .faction(Faction.MARINE).type(UnitType.MARINE)
                    .loadouts(attackerKit)
                    .at(ATTACKER_X, MID_Y)
                    .done();
            int[] seat = {0};
            builder.squad(MatchupScenario.DEFENDERS)
                    .faction(Faction.DEFENDER).type(UnitType.MARINE_RED)
                    .loadouts(defenderKit)
                    .at(room.interiorAnchorX, room.interiorAnchorY)
                    .each(spec -> {
                        int[] cell = inside.get(seat[0]++ % inside.size());
                        spec.atPosition(cell[0] + 0.5f, cell[1] + 0.5f);
                        spec.role(UnitRole.GARRISON).home(cell[0], cell[1]);
                    })
                    .done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.setTacticalMap(new TacticalMap(List.of(node)));
            for (Doodad doodad : ctx.doodads) sim.addDoodad(doodad);

            Squad garrison = world.squad(MatchupScenario.DEFENDERS);
            garrison.assignedNode = node;
            garrison.holdsFireUntilKillZone = true;
            Squad attackers = world.squad(MatchupScenario.ATTACKERS);
            attackers.assignedObjective = ObjectiveAssignment.secureCompound(
                    world.squadId(MatchupScenario.ATTACKERS), world.zoneAt(node.anchorX, node.anchorY), node);
            return world;
        }

        private static void openGround(GenContext c) {
            for (int y = 0; y < c.height; y++) {
                for (int x = 0; x < c.width; x++) {
                    c.grid.setWalkableFloor(x, y);
                    c.topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
                }
            }
        }

        /** Walkable cells inside the building, nearest its interior anchor first: where the garrison starts. */
        private static List<int[]> interiorCells(NavigationGrid grid, PointOfInterest room, int wanted) {
            List<int[]> cells = new ArrayList<>();
            int loX = Math.min(room.left, room.right), hiX = Math.max(room.left, room.right);
            int loY = Math.min(room.top, room.bottom), hiY = Math.max(room.top, room.bottom);
            for (int y = loY + 1; y < hiY; y++) {
                for (int x = loX + 1; x < hiX; x++) {
                    if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) cells.add(new int[]{x, y});
                }
            }
            cells.sort(Comparator.comparingInt(c -> Math.abs(c[0] - room.interiorAnchorX)
                    + Math.abs(c[1] - room.interiorAnchorY)));
            if (cells.size() < wanted) {
                throw new IllegalStateException("The building has " + cells.size() + " floor cells for "
                        + wanted + " defenders");
            }
            return cells;
        }
    }
}
