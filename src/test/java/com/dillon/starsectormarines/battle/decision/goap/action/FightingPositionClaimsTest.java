package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.goap.action.FightingPositionClaims.Order;
import com.dillon.starsectormarines.battle.decision.goap.action.FightingPositionClaims.Probe;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cell-choice rules behind a defend-area squad filling a fighting position, on a hand-built trench. */
class FightingPositionClaimsTest {

    private static final int W = 40;
    private static final int H = 20;
    private static final int NO_THREAT = -1;

    /** An open field; cover comes from {@code coverRule(cellIndex, fromDy)}. */
    private static Probe field(IntBinaryOperator coverRule) {
        return new Probe() {
            @Override public boolean walkable(int x, int y) {
                return true;
            }

            @Override public int cover(int x, int y, int fromDx, int fromDy) {
                return coverRule.applyAsInt(y * W + x, fromDy);
            }
        };
    }

    private static final Probe NO_COVER = field((cell, fromDy) -> 0);

    private static FightingPositionIndex trench(int fromX, int toX, int y) {
        List<Doodad> props = new ArrayList<>();
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = fromX; x <= toX; x++) props.add(new Doodad(x, y, def));
        return FightingPositionIndex.build(W, H, props, (cx, cy) -> true);
    }

    private static int[] unassigned(int members) {
        int[] cells = new int[members];
        Arrays.fill(cells, -1);
        return cells;
    }

    private static int cell(int x, int y) {
        return y * W + x;
    }

    private static Set<Integer> columns(int[] cells) {
        Set<Integer> columns = new HashSet<>();
        for (int c : cells) if (c >= 0) columns.add(c % W);
        return columns;
    }

    private static FightingPositionIndex trenches(int[][] runs) {
        List<Doodad> props = new ArrayList<>();
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int[] run : runs) for (int x = run[0]; x <= run[1]; x++) props.add(new Doodad(x, run[2], def));
        return FightingPositionIndex.build(W, H, props, (cx, cy) -> true);
    }

    private static int[] twoTeams(int each) {
        int[] teams = new int[each * 2];
        for (int i = each; i < teams.length; i++) teams[i] = 1;
        return teams;
    }

    @Test
    void aLineDrawnAcrossTwoTrenchesSeatsOneFireTeamInEach() {
        // Two twelve-cell trenches end to end with a gap, and a drag along both.
        FightingPositionIndex index = trenches(new int[][]{{2, 13, 10}, {22, 33, 10}});
        FrontageLine line = FrontageLine.facing(2, 10, 33, 10, 18, 2, NO_THREAT, NO_THREAT);
        Order order = new Order(18, 10, 20, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(12);
        int[] teams = twoTeams(6);

        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, teams, new HashSet<>());

        for (int i = 0; i < 6; i++) {
            assertTrue(cells[i] % W <= 13, "team 0 holds the first trench: " + cells[i] % W);
            assertTrue(cells[i + 6] % W >= 22, "team 1 holds the second trench: " + cells[i + 6] % W);
        }
    }

    @Test
    void aFireTeamWhoseStretchFallsInTheGapStaysOnOneTrench() {
        // Three teams of four: the middle team's stretch is mostly the gap between the trenches.
        FightingPositionIndex index = trenches(new int[][]{{2, 13, 10}, {22, 33, 10}});
        FrontageLine line = FrontageLine.facing(2, 10, 33, 10, 18, 2, NO_THREAT, NO_THREAT);
        Order order = new Order(18, 10, 20, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(12);
        int[] teams = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2};

        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, teams, new HashSet<>());

        for (int team = 0; team < 3; team++) {
            Set<Boolean> sides = new HashSet<>();
            for (int i = team * 4; i < team * 4 + 4; i++) sides.add(cells[i] % W <= 13);
            assertEquals(1, sides.size(), "team " + team + " is split across both trenches");
        }
    }

    @Test
    void theLineIsCutForTheWholeSquadEvenBeforeEveryTeamCanBePlaced() {
        FightingPositionIndex index = trench(4, 35, 10);
        FrontageLine line = FrontageLine.facing(4, 10, 35, 10, 20, 2, NO_THREAT, NO_THREAT);
        Order order = new Order(20, 10, 20, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(4);

        // Only team 0 is ready; team 1 is still forming up but owns the far half.
        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, new int[4], List.of(0, 1),
                new HashSet<>());

        for (int c : cells) assertTrue(c % W < 20, "team 0 stays on its own half: " + c % W);
    }

    @Test
    void aLineAlongOneLongTrenchSpreadsTheTeamsOverItsLength() {
        FightingPositionIndex index = trench(4, 35, 10);
        FrontageLine line = FrontageLine.facing(4, 10, 35, 10, 20, 2, NO_THREAT, NO_THREAT);
        Order order = new Order(20, 10, 20, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(12);

        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, twoTeams(6), new HashSet<>());

        for (int i = 0; i < 6; i++) {
            assertTrue(cells[i] % W < 20, "team 0 on the near half: " + cells[i] % W);
            assertTrue(cells[i + 6] % W >= 20, "team 1 on the far half: " + cells[i + 6] % W);
        }
    }

    @Test
    void aLineTakesNothingOutsideItsBand() {
        FightingPositionIndex index = trench(4, 35, 10);
        // Drawn five cells off the trench: outside the band, so no cell is a post.
        FrontageLine line = FrontageLine.facing(4, 15, 35, 15, 20, 19, NO_THREAT, NO_THREAT);
        Order order = new Order(20, 15, 20, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(6);

        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, new int[6], new HashSet<>());

        for (int c : cells) assertEquals(-1, c, "overflow holds the line on ordinary ground");
    }

    @Test
    void aLineAcrossABuildingSeatsAFireTeamAtItsPostsWithNoTrenchAtAll() {
        FrontageLine line = FrontageLine.facing(4, 15, 35, 15, 20, 19, NO_THREAT, NO_THREAT);
        // One building's four window posts on the line, and one more outside the band.
        Map<Long, Integer> posts = new HashMap<>();
        for (int x = 10; x <= 13; x++) posts.put(FightingPositionClaims.key(x, 14), 0);
        posts.put(FightingPositionClaims.key(30, 25), 0);
        Order order = new Order(20, 15, 20, NO_THREAT, NO_THREAT, -1, line, posts);
        int[] cells = unassigned(8);
        int[] teams = {0, 0, 0, 0, 1, 1, 1, 1};

        FightingPositionClaims.fillLine(null, W, order, NO_COVER, cells, teams, new HashSet<>());

        Set<Integer> seated = new HashSet<>();
        for (int i = 0; i < 4; i++) seated.add(cells[i]);
        assertEquals(Set.of(14 * W + 10, 14 * W + 11, 14 * W + 12, 14 * W + 13), seated,
                "the first team stands at the building's posts on its stretch");
        for (int i = 4; i < 8; i++) assertEquals(-1, cells[i], "nothing left for the second team: it holds the line");
    }

    @Test
    void aTeamThatFillsItsBuildingDoesNotSpillIntoTheNextTeamsBuilding() {
        FrontageLine line = FrontageLine.facing(4, 15, 36, 15, 20, 19, NO_THREAT, NO_THREAT);
        Map<Long, Integer> posts = new HashMap<>();
        for (int x = 9; x <= 11; x++) posts.put(FightingPositionClaims.key(x, 14), 0);   // team 0's stretch
        for (int x = 27; x <= 28; x++) posts.put(FightingPositionClaims.key(x, 14), 1);  // team 1's stretch
        Order order = new Order(20, 15, 20, NO_THREAT, NO_THREAT, -1, line, posts);
        int[] cells = unassigned(6);
        int[] teams = {0, 0, 0, 0, 1, 1};

        FightingPositionClaims.fillLine(null, W, order, NO_COVER, cells, teams, new HashSet<>());

        Set<Integer> first = new HashSet<>();
        for (int i = 0; i < 4; i++) if (cells[i] >= 0) first.add(cells[i] % W);
        assertEquals(Set.of(9, 10, 11), first, "team 0 fills its own building and its fourth man holds the line");
        assertEquals(Set.of(27, 28), Set.of(cells[4] % W, cells[5] % W), "team 1 keeps both of its own posts");
    }

    @Test
    void aTeamWithNoRunNearItsStretchHoldsItInTheOpenRatherThanCrowdingAnother() {
        // A short trench under the first team's stretch, and open ground under the second's.
        FightingPositionIndex index = trench(4, 15, 10);
        FrontageLine line = FrontageLine.facing(4, 10, 40, 10, 20, 2, NO_THREAT, NO_THREAT);
        Order order = new Order(22, 10, 20, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(8);

        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, twoTeams(4), new HashSet<>());

        for (int i = 0; i < 4; i++) assertTrue(cells[i] >= 0 && cells[i] % W <= 15, "team 0 holds the trench");
        for (int i = 4; i < 8; i++) assertEquals(-1, cells[i], "team 1 holds its own stretch in the open");
    }

    @Test
    void cellsGoInTheOrderTheMenStandAlongTheLine() {
        FightingPositionIndex index = trench(4, 15, 10);
        FrontageLine line = FrontageLine.facing(4, 10, 15, 10, 10, 2, NO_THREAT, NO_THREAT);
        Order order = new Order(10, 10, 12, NO_THREAT, NO_THREAT, -1, line);
        int[] cells = unassigned(4);
        float[] along = {0.9f, 0.1f, 0.6f, 0.3f};

        FightingPositionClaims.fillLine(index, W, order, NO_COVER, cells, new int[4], List.of(0),
                new HashSet<>(), along);

        assertTrue(cells[1] % W < cells[3] % W && cells[3] % W < cells[2] % W && cells[2] % W < cells[0] % W,
                "the leftmost man takes the leftmost cell: " + Arrays.toString(cells));
    }

    @Test
    void aSquadTakesAContiguousBlockNearestTheOrderCentre() {
        FightingPositionIndex index = trench(5, 34, 10);
        int[] cells = unassigned(6);

        FightingPositionClaims.fill(index, W, new Order(20, 10, 12, NO_THREAT, NO_THREAT),
                NO_COVER, cells, new HashSet<>());

        Set<Integer> columns = columns(cells);
        assertEquals(6, columns.size());
        int low = columns.stream().min(Integer::compare).orElseThrow();
        int high = columns.stream().max(Integer::compare).orElseThrow();
        assertEquals(5, high - low, "six marines, six adjacent cells");
        assertTrue(low >= 17 && high <= 23, "the block sits on the centre, not along the long trench");
    }

    @Test
    void neighboursInTheChainAreAdjacentSoFireTeamsStayTogether() {
        FightingPositionIndex index = trench(5, 34, 10);
        int[] cells = unassigned(8);

        FightingPositionClaims.fill(index, W, new Order(20, 10, 12, NO_THREAT, NO_THREAT),
                NO_COVER, cells, new HashSet<>());

        for (int i = 1; i < cells.length; i++) {
            assertEquals(1, Math.abs(cells[i] % W - cells[i - 1] % W),
                    "member " + i + " stands beside member " + (i - 1));
        }
    }

    @Test
    void coverTowardTheBelievedThreatBeatsNearnessToTheCentre() {
        FightingPositionIndex index = trench(5, 34, 10);
        // Only the far-west end of the reachable trench shields against fire from the north.
        Probe shielded = field((cell, fromDy) -> fromDy < 0 && cell % W >= 10 && cell % W <= 13 ? 3 : 0);
        int[] cells = unassigned(4);

        FightingPositionClaims.fill(index, W, new Order(20, 10, 12, 20, 0),
                shielded, cells, new HashSet<>());

        assertEquals(Set.of(10, 11, 12, 13), columns(cells));
    }

    private static FightingPositionIndex twoTrenches() {
        List<Doodad> props = new ArrayList<>();
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = 10; x <= 21; x++) {
            props.add(new Doodad(x, 6, def));
            props.add(new Doodad(x, 12, def));
        }
        return FightingPositionIndex.build(W, H, props, (cx, cy) -> true);
    }

    @Test
    void anOccupyOrderTakesOnlyTheRunItWasAimedAtEvenWhenAnotherHasBetterCover() {
        FightingPositionIndex index = twoTrenches();
        int clicked = index.runIdAt(15, 6);
        // The other trench is the better-shielded one, which would win an unscoped order.
        Probe shielded = field((cell, fromDy) -> cell / W == 12 ? 3 : 0);

        int[] unscoped = unassigned(6);
        FightingPositionClaims.fill(index, W, new Order(15, 6, 20, NO_THREAT, NO_THREAT),
                shielded, unscoped, new HashSet<>());
        for (int c : unscoped) assertEquals(12, c / W, "control: cover pulls the squad to the other run");

        int[] scoped = unassigned(6);
        FightingPositionClaims.fill(index, W, new Order(15, 6, 20, NO_THREAT, NO_THREAT, clicked),
                shielded, scoped, new HashSet<>());
        for (int c : scoped) assertEquals(6, c / W, "the occupy order stays on the clicked run");
    }

    @Test
    void anOccupyOrderDoesNotRetainACellOnAnotherRun() {
        FightingPositionIndex index = twoTrenches();
        int clicked = index.runIdAt(15, 6);
        int[] cells = unassigned(2);
        int[] previous = {cell(15, 6), cell(15, 12)};

        FightingPositionClaims.retain(index, W, new Order(15, 6, 20, NO_THREAT, NO_THREAT, clicked),
                NO_COVER, previous, cells, new HashSet<>());

        assertEquals(cell(15, 6), cells[0]);
        assertEquals(-1, cells[1], "a cell on the other run is released");
    }

    @Test
    void aSquadLargerThanTheFreeCellsLeavesTheRestUnassigned() {
        FightingPositionIndex index = trench(5, 16, 10);
        int[] cells = unassigned(14);

        FightingPositionClaims.fill(index, W, new Order(10, 10, 10, NO_THREAT, NO_THREAT),
                NO_COVER, cells, new HashSet<>());

        long assigned = Arrays.stream(cells).filter(c -> c >= 0).count();
        assertEquals(12, assigned);
        assertEquals(12, Arrays.stream(cells).filter(c -> c >= 0).distinct().count());
        assertEquals(-1, cells[12]);
        assertEquals(-1, cells[13]);
    }

    @Test
    void cellsAlreadyClaimedAreNotHandedOutAgain() {
        FightingPositionIndex index = trench(5, 34, 10);
        Set<Integer> claimed = new HashSet<>();
        for (int x = 18; x <= 22; x++) claimed.add(cell(x, 10));
        int[] cells = unassigned(4);

        FightingPositionClaims.fill(index, W, new Order(20, 10, 12, NO_THREAT, NO_THREAT),
                NO_COVER, cells, claimed);

        for (int c : cells) {
            assertTrue(c >= 0);
            assertTrue(c % W < 18 || c % W > 22, "took " + c % W + " from the claimed block");
        }
    }

    @Test
    void twoSquadsOnOneTrenchTakeDisjointCells() {
        FightingPositionIndex index = trench(5, 34, 10);
        Order order = new Order(20, 10, 12, NO_THREAT, NO_THREAT);
        Set<Integer> claimed = new HashSet<>();
        int[] first = unassigned(12);
        int[] second = unassigned(12);

        FightingPositionClaims.fill(index, W, order, NO_COVER, first, claimed);
        FightingPositionClaims.fill(index, W, order, NO_COVER, second, claimed);

        Set<Integer> all = new HashSet<>();
        for (int c : first) assertTrue(all.add(c), "duplicate " + c);
        for (int c : second) assertTrue(all.add(c), "duplicate " + c);
        assertEquals(24, all.size());
    }

    @Test
    void aMarineKeepsAStillValidCellAndTheRestGrowFromIt() {
        FightingPositionIndex index = trench(5, 34, 10);
        Order order = new Order(20, 10, 12, NO_THREAT, NO_THREAT);
        int[] previous = {cell(8, 10), -1, -1};
        int[] cells = unassigned(3);
        Set<Integer> claimed = new HashSet<>();

        FightingPositionClaims.retain(index, W, order, NO_COVER, previous, cells, claimed);
        assertEquals(cell(8, 10), cells[0], "retained, though far from the centre");
        FightingPositionClaims.fill(index, W, order, NO_COVER, cells, claimed);

        assertEquals(cell(8, 10), cells[0]);
        assertTrue(Math.abs(cells[1] % W - 8) <= 2 && Math.abs(cells[2] % W - 8) <= 2,
                "newcomers join the block the retained marine already stands in");
    }

    @Test
    void aRetainedCellIsDroppedWhenItStopsBeingValid() {
        FightingPositionIndex index = trench(5, 34, 10);
        int[] previous = {cell(30, 10), cell(20, 11), cell(20, 10)};
        int[] cells = unassigned(3);
        Set<Integer> claimed = new HashSet<>();
        claimed.add(cell(20, 10));

        FightingPositionClaims.retain(index, W, new Order(20, 10, 4, NO_THREAT, NO_THREAT),
                NO_COVER, previous, cells, claimed);

        assertEquals(-1, cells[0], "outside the ordered circle");
        assertEquals(-1, cells[1], "no longer a position cell");
        assertEquals(-1, cells[2], "a squadmate or another squad holds it");
    }

    @Test
    void aRetainedCellWithNoCoverTowardANewThreatIsReleased() {
        FightingPositionIndex index = trench(5, 34, 10);
        Probe shielded = field((cell, fromDy) -> fromDy < 0 ? 3 : 0);
        int[] previous = {cell(20, 10)};
        int[] cells = unassigned(1);

        FightingPositionClaims.retain(index, W, new Order(20, 10, 8, 20, 0),
                shielded, previous, cells, new HashSet<>());
        assertEquals(cell(20, 10), cells[0], "threat to the north: covered");

        cells = unassigned(1);
        FightingPositionClaims.retain(index, W, new Order(20, 10, 8, 20, 19),
                shielded, previous, cells, new HashSet<>());
        assertEquals(-1, cells[0], "threat now to the south: this side no longer shields");
    }

    @Test
    void withNoPositionsInTheCircleNobodyIsAssigned() {
        FightingPositionIndex index = trench(5, 34, 10);
        int[] cells = unassigned(5);

        FightingPositionClaims.fill(index, W, new Order(20, 16, 3, NO_THREAT, NO_THREAT),
                NO_COVER, cells, new HashSet<>());
        assertEquals(-1, Arrays.stream(cells).max().orElseThrow());

        FightingPositionClaims.fill(FightingPositionIndex.empty(W, H), W,
                new Order(20, 10, 12, NO_THREAT, NO_THREAT), NO_COVER, cells, new HashSet<>());
        assertEquals(-1, Arrays.stream(cells).max().orElseThrow());
    }

    private static BattleSimulation trenchSimulation() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = 5; x <= 34; x++) sim.addDoodad(new Doodad(x, 10, def));
        return sim;
    }

    private static Squad defendingSquad(BattleSimulation sim, int members, long[] ids) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        for (int i = 0; i < members; i++) {
            ids[i] = sim.spawn(new EntitySpec("marine" + i, Faction.MARINE, UnitType.MARINE, 20, 14)
                    .squad(squadId));
        }
        squad.leaderId = ids[0];
        squad.aliveMembers = members;
        squad.originalSize = members;
        squad.assignedObjective = ObjectiveAssignment.defendArea(squadId, 20, 10, 8);
        squad.currentPlan = new SquadPlan(List.of(new SquadPlan.Step(new DefendArea(20, 10, 8))));
        return squad;
    }

    @Test
    void definedAreaSquadIsSentToItsClaimedTrenchCellsAndNoTwoShareOne() {
        BattleSimulation sim = trenchSimulation();
        long[] ids = new long[6];
        Squad squad = defendingSquad(sim, 6, ids);
        DefendArea action = new DefendArea(20, 10, 8);

        sim.fightingPositionClaims().tick(sim);
        Set<Integer> destinations = new HashSet<>();
        for (long id : ids) {
            action.execute(id, squad, sim);
            int[] path = sim.world().path(id);
            assertEquals(10, Paths.destY(path), "ordinary open ground was available; the trench was chosen");
            assertTrue(destinations.add(Paths.destX(path)), "two marines sent to one cell");
        }
    }

    @Test
    void withFightingPositionsSwitchedOffNoCellsAreClaimed() {
        BattleSimulation sim = trenchSimulation();
        long[] ids = new long[4];
        defendingSquad(sim, 4, ids);
        DigInSystem.setEnabledForEvidence(false);
        try {
            sim.fightingPositionClaims().tick(sim);
            assertTrue(sim.fightingPositionClaims().isEmpty());
        } finally {
            DigInSystem.setEnabledForEvidence(true);
        }
        sim.fightingPositionClaims().tick(sim);
        assertEquals(4, ids.length);
        assertTrue(!sim.fightingPositionClaims().isEmpty());
    }
}
