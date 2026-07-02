package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link HoldZone}'s per-member spread. The bug: when holding a
 * cleared compound, every member used to freeze on the first cell it crossed
 * into the zone (the doorway/anchor approach), so the squad bunched even in a
 * roomy compound. The fix gives each member a distinct hold cell via
 * {@link HoldZone#pickHoldCells} bound through {@link HoldZone#roles}.
 */
public class HoldZoneTest {

    private static final int W = 20;
    private static final int H = 12;

    /**
     * One sealed 3×3 room (interior x∈[4..6], y∈[4..6] = 9 cells) with a wall
     * ring and a doorway at (5,3) into the surrounding open ground. The room is
     * its own navigation zone.
     */
    private static BattleSimulation roomSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int x = 3; x <= 7; x++) { grid.setWalkable(x, 3, false); grid.setWalkable(x, 7, false); }
        for (int y = 3; y <= 7; y++) { grid.setWalkable(3, y, false); grid.setWalkable(7, y, false); }
        grid.setWalkableFloor(5, 3);
        grid.setDoorway(5, 3, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Compound node whose footprint covers the 3×3 room. */
    private static TacticalNode roomNode() {
        return new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 80, 4);
    }

    private static boolean inRoom(int x, int y) {
        return x >= 4 && x <= 6 && y >= 4 && y <= 6;
    }

    @Test
    public void pickHoldCellsAreDistinctAndInsideTheCompoundRoom() {
        BattleSimulation sim = roomSim();
        TacticalNode node = roomNode();
        int roomZone = sim.getZoneGraph().zoneIdAt(5, 5);

        int[][] cells = HoldZone.pickHoldCells(node, roomZone, 4, sim);
        int[] xs = cells[0];
        int[] ys = cells[1];

        assertEquals(4, xs.length, "four members → four hold cells (the room has 9)");
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < xs.length; i++) {
            assertTrue(inRoom(xs[i], ys[i]), "hold cell must sit inside the compound room");
            assertTrue(seen.add(((long) xs[i] << 32) | (ys[i] & 0xFFFFFFFFL)),
                    "hold cells must be distinct (no bunching)");
        }
    }

    @Test
    public void pickHoldCellsCapsAtAvailableCells() {
        BattleSimulation sim = roomSim();
        TacticalNode node = roomNode();
        int roomZone = sim.getZoneGraph().zoneIdAt(5, 5);

        // Ask for far more cells than the 9-cell room can offer.
        int[][] cells = HoldZone.pickHoldCells(node, roomZone, 50, sim);
        assertEquals(9, cells[0].length, "capped at the room's walkable cell count");
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < cells[0].length; i++) {
            assertTrue(seen.add(((long) cells[0][i] << 32) | (cells[1][i] & 0xFFFFFFFFL)),
                    "every picked cell is distinct");
        }
    }

    @Test
    public void rolesBindEachMemberToADistinctHoldCell() {
        BattleSimulation sim = roomSim();
        TacticalNode node = roomNode();
        int roomZone = sim.getZoneGraph().zoneIdAt(5, 5);

        int[][] cells = HoldZone.pickHoldCells(node, roomZone, 4, sim);
        HoldZone hold = new HoldZone(roomZone, node, cells[0], cells[1]);

        Squad squad = new Squad(7, Faction.MARINE);
        squad.aliveMembers = 4;
        // Four members clustered near the doorway — exactly the bunch the old
        // code produced; roles() should fan them out to the four hold cells.
        List<Entity> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Entity m = new Entity("m" + i, Faction.MARINE, UnitType.MARINE, 5, 4);
            sim.addUnit(m); // register so RoleAssigner's getCellX read routes through the registry
            members.add(m);
        }

        List<RoleAssigner.Slot<Entity>> slots = hold.roles(squad, sim);
        Map<String, List<Entity>> assigned = RoleAssigner.assign(members, slots);

        // Every member must land on a distinct "hold:i" cell; overflow stays empty.
        assertTrue(assigned.getOrDefault("hold:overflow", List.of()).isEmpty(),
                "with enough cells nobody falls through to the anchor overflow");
        Set<Integer> cellIdx = new HashSet<>();
        int placed = 0;
        for (Map.Entry<String, List<Entity>> e : assigned.entrySet()) {
            if (!e.getKey().startsWith("hold:") || e.getKey().equals("hold:overflow")) continue;
            for (Entity ignored : e.getValue()) {
                int idx = Integer.parseInt(e.getKey().substring("hold:".length()));
                assertTrue(cellIdx.add(idx), "no two members share a hold cell");
                placed++;
            }
        }
        assertEquals(4, placed, "all four members bound to a hold cell");
    }

    @Test
    public void engageInZoneWithCooldownPendingWritesIntentAndStillCreepsTowardFiringPosition() {
        // FiringSystem sweep: engageInZone now authors a fire intent
        // unconditionally when in range + LoS (FiringSystem owns the cooldown
        // gate for the shot itself), but the old control flow only RETURNED
        // on the cooldown-ready tick — on a cooldown-pending tick it must
        // still fall through to the creep-toward-a-firing-position movement
        // block below. This pins that preserved fallthrough.
        BattleSimulation sim = roomSim();
        TacticalNode node = roomNode();
        int roomZone = sim.getZoneGraph().zoneIdAt(5, 5);

        int[][] cells = HoldZone.pickHoldCells(node, roomZone, 1, sim);
        HoldZone hold = new HoldZone(roomZone, node, cells[0], cells[1]);

        Squad squad = new Squad(7, Faction.MARINE);
        squad.aliveMembers = 1;

        Entity member = new Entity("m", Faction.MARINE, UnitType.MARINE, 5, 4);
        sim.addUnit(member);
        Entity target = new Entity("d", Faction.DEFENDER, UnitType.MARINE, 6, 4);
        sim.addUnit(target);

        sim.world().setAttackRange(member.entityId, 10f);
        sim.world().setCooldownTimer(member.entityId, 0.6f); // cooldown pending

        ActionStatus status = hold.execute(member, squad, sim);

        assertEquals(ActionStatus.RUNNING, status);
        assertEquals(target.entityId, sim.combat().fireTargetId(member.entityId),
                "in range + LoS writes a fire intent even though the cooldown isn't ready yet");
        assertEquals(FireStance.STANCED.ordinal(),
                sim.getRoster().entityWorld().getInt(member.entityId, sim.getRoster().components().COMBAT,
                        BattleComponents.COMBAT_FIRE_STANCE));
        assertTrue(!Paths.isEmpty(sim.world().path(member.entityId)),
                "a cooldown-pending tick must still fall through to the creep-toward-firing-position movement block");
    }
}
