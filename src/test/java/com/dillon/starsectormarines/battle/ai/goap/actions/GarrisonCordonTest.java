package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link GarrisonCordon}'s execute semantics: slot count =
 * portal count, transit branch paths toward the guard cell, on-post members
 * fire on visible enemies (opportunistic — no portal-cell trigger required).
 */
public class GarrisonCordonTest {

    private static final int W = 14;
    private static final int H = 14;

    /** 14x14 room with two doorways at (6,3) and (3,6). Defender zone is the interior. */
    private static BattleSimulation multiPortalRoom() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 3; y <= 9; y++) {
            grid.setWalkable(3, y, false);
            grid.setWalkable(9, y, false);
        }
        for (int x = 3; x <= 9; x++) {
            grid.setWalkable(x, 3, false);
            grid.setWalkable(x, 9, false);
        }
        grid.setWalkableFloor(6, 3);
        grid.setDoorway(6, 3, true);
        grid.setWalkableFloor(3, 6);
        grid.setDoorway(3, 6, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad defenderSquad(int id, float cx, float cy, int alive) {
        Squad squad = new Squad(id, Faction.DEFENDER);
        squad.aliveMembers = alive;
        squad.centroidX = cx;
        squad.centroidY = cy;
        return squad;
    }

    /**
     * Build the guard posts the {@link com.dillon.starsectormarines.battle.ai.goap.goals.GarrisonAmbush}-
     * style customPlan would emit for this room — one per portal of the
     * defender (interior) zone, with the guard cell at the inside-zone
     * cardinal neighbor of each doorway.
     */
    private static List<HoldPortalCordon.GuardPost> guardPostsForRoom(BattleSimulation sim) {
        int roomZone = sim.getZoneGraph().zoneIdAt(6, 6);
        var portalIds = sim.getZoneGraph().zoneById(roomZone).getPortalIds();
        List<HoldPortalCordon.GuardPost> posts = new ArrayList<>(portalIds.size());
        int w = sim.getGrid().getWidth();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int portalId : portalIds) {
            var p = sim.getZoneGraph().portalById(portalId);
            int dwX = p.getDoorwayCellIdx() % w;
            int dwY = p.getDoorwayCellIdx() / w;
            int gx = dwX, gy = dwY;
            for (int[] d : dirs) {
                int nx = dwX + d[0], ny = dwY + d[1];
                if (sim.getZoneGraph().zoneIdAt(nx, ny) == roomZone) {
                    gx = nx; gy = ny;
                    break;
                }
            }
            posts.add(new HoldPortalCordon.GuardPost(portalId, gx, gy));
        }
        return posts;
    }

    @Test
    public void slotCountMatchesPortalCount() {
        BattleSimulation sim = multiPortalRoom();
        Squad squad = defenderSquad(1, 6f, 6f, 2);
        var posts = guardPostsForRoom(sim);
        assertEquals(2, posts.size(), "test prerequisite: room must have two portals");
        GarrisonCordon cordon = new GarrisonCordon(posts);
        var slots = cordon.roles(squad, sim);
        assertEquals(2, slots.size(), "one slot per portal");
        for (var slot : slots) {
            assertEquals(1, slot.count(), "each slot holds one member");
            assertTrue(slot.name().startsWith("portal:"),
                    "garrison cordon slots use the portal:<id> naming");
        }
    }

    @Test
    public void holderPathsToGuardCellInsideZone() {
        BattleSimulation sim = multiPortalRoom();
        Squad squad = defenderSquad(1, 6f, 6f, 1);
        var posts = guardPostsForRoom(sim);
        // Pick the first post and place the defender across the room from it.
        var post = posts.get(0);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 7, 7);
        d1.squadId = squad.id;
        sim.addUnit(d1);

        // Verify the guard cell is inside the defender zone (sanity for the test fixture).
        int roomZone = sim.getZoneGraph().zoneIdAt(6, 6);
        assertEquals(roomZone, sim.getZoneGraph().zoneIdAt(post.cellX, post.cellY),
                "test fixture: guard cell must be inside the defender zone");

        GarrisonCordon cordon = new GarrisonCordon(posts);
        // Attach a plan that puts d1 in the slot for the chosen post.
        SquadPlan.Step step = new SquadPlan.Step(cordon);
        List<Unit> bucket = new ArrayList<>(1);
        bucket.add(d1);
        step.assignments.put(post.slotName(), bucket);
        // Empty other slot.
        step.assignments.put(posts.get(1).slotName(), new ArrayList<>());
        squad.currentPlan = new SquadPlan(List.of(step));

        cordon.execute(d1, squad, sim);

        assertNotEquals(0, d1.pathCellCount(),
                "transit branch must queue a path toward the guard cell");
        // Final cell of the path should be the guard cell.
        int last = d1.pathCellCount() - 1;
        assertEquals(post.cellX, d1.pathCellX(last));
        assertEquals(post.cellY, d1.pathCellY(last));
    }

    @Test
    public void onPostHolderFiresOnVisibleEnemy() {
        BattleSimulation sim = multiPortalRoom();
        Squad squad = defenderSquad(1, 6f, 6f, 1);
        var posts = guardPostsForRoom(sim);
        var post = posts.get(0);

        // Defender already on the guard cell.
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, post.cellX, post.cellY);
        d1.squadId = squad.id;
        sim.addUnit(d1);

        // Place an attacker visible from the guard cell (just inside the room).
        // The guard cell is one cardinal step inside from the doorway, so a
        // sibling cell two off should usually be in LoS.
        Unit attacker = new Unit("a1", Faction.MARINE, UnitType.MARINE, post.cellX, post.cellY + 2);
        sim.addUnit(attacker);
        assertTrue(sim.getGrid().hasLineOfSight(d1.getCellX(), d1.getCellY(), attacker.getCellX(), attacker.getCellY()),
                "test prerequisite: attacker must be visible from the guard cell");

        GarrisonCordon cordon = new GarrisonCordon(posts);
        SquadPlan.Step step = new SquadPlan.Step(cordon);
        List<Unit> bucket = new ArrayList<>(1);
        bucket.add(d1);
        step.assignments.put(post.slotName(), bucket);
        step.assignments.put(posts.get(1).slotName(), new ArrayList<>());
        squad.currentPlan = new SquadPlan(List.of(step));

        cordon.execute(d1, squad, sim);

        assertTrue(d1.cooldownTimer > 0f,
                "on-post holder with visible enemy in range → must fire (opportunistic, no portal trigger required)");
        assertTrue(sim.getShotsThisFrame().size() > 0,
                "shot event must be emitted for the opportunistic fire");
    }
}
