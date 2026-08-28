package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceService;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link FrontageDefense} — the stand-to posture between quiet
 * garrison patrol and the indoor fight. Same walled-compound fixture the
 * derivation test uses: solid perimeter, paired windows north and south, one
 * south doorway.
 */
class FrontageDefenseTest {

    private static final int W = 36;
    private static final int H = 24;
    private static final int LEFT = 10;
    private static final int TOP = 6;
    private static final int RIGHT = 24;
    private static final int BOTTOM = 18;
    private static final int DOOR_X = 17;

    @Test
    void aQuietGarrisonDoesNotStandTo() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);

        assertEquals(0f, FrontageDefense.INSTANCE.relevance(WorldState.EMPTY, garrison, sim),
                "no believed pressure on any aperture — the garrison keeps patrolling");
        assertNull(FrontageDefense.INSTANCE.customPlan(garrison, sim));
    }

    @Test
    void believedPressureMansTheApproachedWall() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 6);

        assertEquals(FrontageDefense.RELEVANCE,
                FrontageDefense.INSTANCE.relevance(WorldState.EMPTY, garrison, sim), 1e-6);
        assertEquals(Goal.Priority.MISSION, FrontageDefense.INSTANCE.priority());

        List<ApertureHold.Post> posts = postsOf(garrison, sim);
        assertFalse(posts.isEmpty());
        ApertureHold.Post first = posts.get(0);
        assertTrue(first.standY() >= BOTTOM - 1,
                "the most threatened post is on the approached wall, got " + first);
        for (ApertureHold.Post post : posts) {
            if (post.isReserve()) continue;
            assertTrue(sim.getGrid().isWalkable(post.standX(), post.standY()),
                    "every post stands on walkable ground");
        }
    }

    @Test
    void entrancesOutrankEquallyThreatenedWindows() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        // Contact centred on the doorway, so the door and the windows beside it
        // sit in the same influence block and separate only on kind.
        approachFrom(sim, garrison, 6, DOOR_X, BOTTOM + 3);

        List<ApertureHold.Post> posts = postsOf(garrison, sim);
        assertEquals(DefenseFrontage.Kind.ENTRANCE, posts.get(0).kind(),
                "an assault comes through the door before it comes through a window");
    }

    @Test
    void aReserveStaysOffTheWall() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 8);

        List<ApertureHold.Post> posts = postsOf(garrison, sim);
        long reserves = posts.stream().filter(ApertureHold.Post::isReserve).count();
        assertTrue(reserves >= 1,
                "one threatened facing must not strip the rest of the perimeter");
        assertTrue(posts.size() - reserves >= 1, "and the wall is still manned");
    }

    @Test
    void aSmallSquadCommitsEveryone() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 3);

        List<ApertureHold.Post> posts = postsOf(garrison, sim);
        assertTrue(posts.stream().noneMatch(ApertureHold.Post::isReserve),
                "a reserve of one out of three is a squad that defends nothing");
    }

    @Test
    void postsAreDistinctCells() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 10);

        List<ApertureHold.Post> posts = postsOf(garrison, sim);
        long distinct = posts.stream()
                .map(p -> p.standX() + "," + p.standY()).distinct().count();
        assertEquals(posts.size(), distinct, "two members must not be sent to one cell");
    }

    @Test
    void anAttackerHoldingTheCapturedCompoundMansTheSameWall() {
        BattleSimulation sim = compoundSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad holder = sim.getSquad(squadId);
        holder.assignedObjective = ObjectiveAssignment.holdNode(holder.id, node());
        spawnStill(sim, "holder", Faction.MARINE, UnitType.MARINE, DOOR_X, 12, squadId);
        // The counter-attack the marine garrison believes in is a defender one.
        long counter = spawnTough(sim, "counter", Faction.DEFENDER, UnitType.MARINE_RED,
                14, BOTTOM + 3);
        sim.postShot(new ShotEvent(counter, 14.5f, BOTTOM + 3.5f, DOOR_X + 0.5f, 12.5f,
                false, Faction.DEFENDER, 0.1f));
        advancePastInfluenceCadence(sim);
        holder.aliveMembers = 6;

        assertEquals(FrontageDefense.RELEVANCE,
                FrontageDefense.INSTANCE.relevance(WorldState.EMPTY, holder, sim), 1e-6,
                "frontage belongs to whoever holds the place, not to whoever is defending");
        assertFalse(postsOf(holder, sim).isEmpty());
    }

    @Test
    void aStandaloneStructureHasAFrontageWithNoCompoundAroundIt() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 6);

        // The fixture node's own bbox is its compound bbox, which is the
        // degenerate case an isolated post is always in.
        TacticalNode standalone = node();
        assertEquals(standalone.left, standalone.compoundLeft());
        assertFalse(DefenseFrontage.forStructure(standalone, sim).isEmpty());
        assertFalse(postsOf(garrison, sim).isEmpty());
    }

    @Test
    void anEnemyInsideHandsTheFightToTheRoomBehaviors() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 6);
        assertFalse(postsOf(garrison, sim).isEmpty());

        spawnStill(sim, "breacher", Faction.MARINE, UnitType.MARINE, DOOR_X, 12, Squad.NO_SQUAD);

        assertEquals(0f, FrontageDefense.INSTANCE.relevance(WorldState.EMPTY, garrison, sim),
                "standing at a window with enemies behind you is the failure this gate prevents");
    }

    @Test
    void guardPostYieldsWhileTheFrontageIsThreatened() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        assertEquals(1.0f, GuardPost.INSTANCE.relevance(WorldState.EMPTY, garrison, sim), 1e-6,
                "a quiet garrison still holds its post");

        approachFromTheSouth(sim, garrison, 6);
        assertEquals(0f, GuardPost.INSTANCE.relevance(WorldState.EMPTY, garrison, sim),
                "GuardPost's flat 1.0 would otherwise leash the squad away from the wall");
    }

    @Test
    void standingToSitsBetweenTheAmbushAndTheQuietPatrol() {
        BattleSimulation sim = compoundSim();
        Squad garrison = defenderGarrison(sim);
        approachFromTheSouth(sim, garrison, 6);

        float standTo = FrontageDefense.INSTANCE.relevance(WorldState.EMPTY, garrison, sim);
        float patrol = GarrisonCompound.INSTANCE.relevance(WorldState.EMPTY, garrison, sim);
        assertTrue(standTo > patrol,
                "standing to outranks walking the rooms while an assault forms up");
        assertTrue(standTo < 1.0f,
                "but a live chokepoint contact — GarrisonAmbush at 1.0 — still preempts");
    }

    private static List<ApertureHold.Post> postsOf(Squad squad, BattleSimulation sim) {
        SquadPlan plan = FrontageDefense.INSTANCE.customPlan(squad, sim);
        assertFalse(plan == null, "expected a stand-to plan");
        ApertureHold hold = assertInstanceOf(ApertureHold.class, plan.currentStep().action);
        return hold.posts();
    }

    /** A defender garrison squad pegged to the compound node, with a body inside it to carry belief. */
    private static Squad defenderGarrison(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        Squad squad = sim.getSquad(squadId);
        squad.holdsFireUntilKillZone = true;
        squad.assignedNode = node();
        spawnStill(sim, "garrison", Faction.DEFENDER, UnitType.MARINE_RED, DOOR_X, 12, squadId);
        return squad;
    }

    private static void approachFromTheSouth(BattleSimulation sim, Squad garrison, int members) {
        approachFrom(sim, garrison, members, 14, BOTTOM + 3);
    }

    /**
     * Put a believed marine contact at {@code (x, y)} by having it fire on the
     * garrison, then run past the influence field's fixed refresh cadence so
     * the belief has actually reached the snapshot the goal reads. Both bodies
     * are given a deep pool so the exchange during those ticks cannot remove
     * the contact this fixture exists to create.
     *
     * <p>{@code members} is applied last on purpose: a tick recomputes
     * {@link Squad#aliveMembers} from the live roster, so a squad strength set
     * before the advance is silently replaced by the one real body.
     */
    private static void approachFrom(BattleSimulation sim, Squad garrison, int members,
                                     int x, int y) {
        long attacker = spawnTough(sim, "attacker-" + x + "-" + y, Faction.MARINE,
                UnitType.MARINE, x, y);
        sim.postShot(new ShotEvent(attacker, x + 0.5f, y + 0.5f, DOOR_X + 0.5f, 12.5f,
                false, Faction.MARINE, 0.1f));
        advancePastInfluenceCadence(sim);
        garrison.aliveMembers = members;
    }

    private static void advancePastInfluenceCadence(BattleSimulation sim) {
        for (int i = 0; i <= CommanderInfluenceService.UPDATE_INTERVAL_TICKS; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
    }

    private static BattleSimulation compoundSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean onPerimeter = (x == LEFT || x == RIGHT) && y >= TOP && y <= BOTTOM
                        || (y == TOP || y == BOTTOM) && x >= LEFT && x <= RIGHT;
                if (!onPerimeter) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(DOOR_X, BOTTOM);
        grid.setDoorway(DOOR_X, BOTTOM, true);

        CellTopology topology = new CellTopology(W, H);
        for (int x = 14; x <= 15; x++) {
            for (int y : new int[]{TOP, BOTTOM}) {
                grid.setSeeThrough(x, y, true);
                topology.setWindow(x, y, true);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static TacticalNode node() {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST, DOOR_X, 12,
                LEFT, TOP, RIGHT, BOTTOM, Faction.DEFENDER, 90, 6);
    }

    private static long spawnStill(BattleSimulation sim, String name, Faction faction,
                                   UnitType type, int x, int y, int squadId) {
        EntitySpec spec = new EntitySpec(name, faction, type, x, y);
        spec.moveSpeed = 0f;
        if (squadId != Squad.NO_SQUAD) spec.squad(squadId);
        return sim.spawn(spec);
    }

    /** Unkillable within a fixture's measurement window — these bodies exist to carry belief, not to fight. */
    private static long spawnTough(BattleSimulation sim, String name, Faction faction,
                                   UnitType type, int x, int y) {
        EntitySpec spec = new EntitySpec(name, faction, type, x, y);
        spec.moveSpeed = 0f;
        spec.health(100_000f);
        return sim.spawn(spec);
    }
}
