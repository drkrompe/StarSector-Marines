package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

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
    static final int BUILDING_WEST_X = 104;
    static final int BUILDING_EAST_X = 130;
    static final int BUILDING_NORTH_Y = MID_Y - 10;
    static final int BUILDING_SOUTH_Y = MID_Y + 10;
    private static final int TICK_LIMIT = 9000;

    public static List<MatchupScenario> all() {
        return List.of(new OpenField(), new Trench(), new Building());
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

    /** Scenario 2: the same crossing, with the defenders dug in along a twelve-cell trench. */
    static final class Trench implements MatchupScenario {
        @Override public String id() { return "trench"; }

        @Override public String label() {
            return "Trench: the open-field crossing onto defenders holding a twelve-cell trench";
        }

        @Override public int maxTicks() { return TICK_LIMIT; }

        @Override public SceneWorld build(MarineLoadout[] attackerKit, MarineLoadout[] defenderKit, long seed) {
            SceneBuilder builder = arena(seed);
            attackers(builder, attackerKit, DEFENDER_X + TRENCH_CELLS / 2, MID_Y);
            builder.squad(MatchupScenario.DEFENDERS)
                    .faction(Faction.DEFENDER).type(UnitType.MARINE_RED)
                    .loadouts(defenderKit)
                    .inFile(DEFENDER_X, MID_Y, 1, 0)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, DEFENDER_X + TRENCH_CELLS / 2, MID_Y))
                    .done();
            SceneWorld world = builder.build();
            dig(world, DEFENDER_X, DEFENDER_X + TRENCH_CELLS - 1, MID_Y);
            return world;
        }

        /** A straight trench, each cell joined to its east and west neighbours. */
        private static void dig(SceneWorld world, int x0, int x1, int y) {
            TileRegistry registry = TileRegistry.installed();
            for (int x = x0; x <= x1; x++) {
                int mask = (x < x1 ? 2 : 0) | (x > x0 ? 8 : 0);
                DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
                if (def == null) throw new IllegalStateException("no trench piece for mask " + mask);
                world.sim().addDoodad(new Doodad(x, y, def));
            }
            FightingPositionIndex index = world.sim().fightingPositions();
            int run = index.runIdAt(x0, y);
            int cells = x1 - x0 + 1;
            if (run < 0 || index.runSize(run) != cells) {
                throw new IllegalStateException("The trench did not come out as one fighting position of " + cells);
            }
        }
    }

    /**
     * Scenario 3: the defenders hold a walled building with one doorway on its west face; the attackers are ordered
     * to clear it. The building is hand-cut walls and a marked doorway, not a generated compound: the question is
     * what a loadout does in a breach, and a compound's variety would make every matchup a different building.
     */
    static final class Building implements MatchupScenario {
        @Override public String id() { return "building"; }

        @Override public String label() {
            return "Building: attackers clear a walled building with one west doorway held by the defenders";
        }

        @Override public int maxTicks() { return TICK_LIMIT; }

        @Override public SceneWorld build(MarineLoadout[] attackerKit, MarineLoadout[] defenderKit, long seed) {
            SceneBuilder builder = arena(seed)
                    .wall(BUILDING_WEST_X, BUILDING_NORTH_Y, BUILDING_EAST_X, BUILDING_NORTH_Y)
                    .wall(BUILDING_WEST_X, BUILDING_SOUTH_Y, BUILDING_EAST_X, BUILDING_SOUTH_Y)
                    .wall(BUILDING_EAST_X, BUILDING_NORTH_Y, BUILDING_EAST_X, BUILDING_SOUTH_Y)
                    .wall(BUILDING_WEST_X, BUILDING_NORTH_Y, BUILDING_WEST_X, BUILDING_SOUTH_Y)
                    .doorway(BUILDING_WEST_X, MID_Y);
            int insideX = (BUILDING_WEST_X + BUILDING_EAST_X) / 2;
            builder.squad(MatchupScenario.ATTACKERS)
                    .faction(Faction.MARINE).type(UnitType.MARINE)
                    .loadouts(attackerKit)
                    .at(ATTACKER_X, MID_Y)
                    .done();
            defenders(builder, defenderKit, insideX, MID_Y);
            SceneWorld world = builder.build();
            int zone = world.zoneAt(insideX, MID_Y);
            if (zone < 0 || zone == world.zoneAt(ATTACKER_X, MID_Y)) {
                throw new IllegalStateException("The building is not a zone of its own (zone " + zone
                        + "), so a clear order would name the ground the attackers already stand on");
            }
            Squad squad = world.squad(MatchupScenario.ATTACKERS);
            squad.assignedObjective = ObjectiveAssignment.clearZone(
                    world.squadId(MatchupScenario.ATTACKERS), zone);
            return world;
        }
    }
}
