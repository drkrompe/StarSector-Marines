package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.infantry.PatrolRoute;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the death-event seam: a {@link MapTurret} killed
 * through the real damage path publishes a {@code DeathEvent}, and
 * {@link TurretDemolitionSystem} (subscribed to the sim's dispatcher) flips the
 * mount cell to rubble + drops a smoking wreck when the mailbox drains.
 *
 * <p>Also pins the buffering contract — the demolition is NOT applied at the
 * moment of death (inline {@code applyDamage}), only when the per-tick
 * {@code DeathDispatcher.drain()} runs at the demolition phase.
 */
public class TurretDemolitionSystemTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void deadTurretIsDemolishedWhenTheMailboxDrains() {
        BattleSimulation sim = openArena(20, 20);
        MapTurret turret = new MapTurret("t0", Faction.DEFENDER, TurretKind.VULCAN, 10, 10);
        sim.addUnit(turret);
        int wrecksBefore = sim.getSmokingWrecks().size();

        // Lethal hit, routed through the production damage path so the death
        // cascade (and the DeathEvent publish) actually runs.
        sim.applyDamage(turret, 100_000f, 3.5f, 0f);

        assertFalse(sim.world().isAlive(turret.entityId), "the turret should be dead after a lethal hit");
        // Buffered: the handler has NOT run yet — death published, not drained.
        assertFalse(turret.demolished,
                "demolition must wait for the dispatcher drain, not fire inline at death");

        // One tick drains the mailbox at the demolition phase.
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(turret.demolished, "drain → turret-demolition handler flips the dead turret");
        assertEquals(CellTopology.GroundKind.RUBBLE, sim.getTopology().getGroundKind(10, 10),
                "the mount cell flips to walkable rubble");
        assertTrue(sim.getSmokingWrecks().size() > wrecksBefore,
                "a smoking wreck is dropped on the mount cell");
    }

    @Test
    public void guardpostSquadIsReleasedOnceEveryTurretOnThePostIsDown() {
        BattleSimulation sim = openArena(20, 20);
        // A two-turret defense post; a garrison squad orbiting it on a tight
        // patrol radius. When BOTH turrets die the post is "down" and the
        // squad should revert to the wide default radius + drop its post link.
        MapTurret a = new MapTurret("ta", Faction.DEFENDER, TurretKind.VULCAN, 10, 10);
        MapTurret b = new MapTurret("tb", Faction.DEFENDER, TurretKind.VULCAN, 11, 10);
        sim.addUnit(a);
        sim.addUnit(b);
        // A lone, far-off MARINE keeps the battle in progress across both ticks
        // — without a live unit on each side the win-check eliminates MARINE
        // after tick 1 and the second advance() would no-op before the drain.
        sim.addUnit(new Unit("m0", Faction.MARINE, UnitType.MARINE, 1, 1));
        DefensePost post = new DefensePost(DefensePostKind.LIGHT, 10, 10, List.of(
                new DefensePost.TurretSpec(TurretKind.VULCAN, 10, 10),
                new DefensePost.TurretSpec(TurretKind.VULCAN, 11, 10)));
        sim.setDefensePosts(List.of(post));

        int squadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad garrison = sim.getSquad(squadId);
        garrison.defensePost = post;
        garrison.patrolRadius = 4; // tight orbit while the post is live

        // Kill only the first turret — the post still has a live turret, so
        // the squad must stay pinned to it.
        sim.applyDamage(a, 100_000f, 3.5f, 0f);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(post, garrison.defensePost,
                "one turret still alive → guardpost squad stays pinned");
        assertEquals(4, garrison.patrolRadius, "patrol radius unchanged while the post holds");

        // Kill the second — now every turret on the post is down.
        sim.applyDamage(b, 100_000f, 3.5f, 0f);
        sim.advance(BattleSimulation.TICK_DT);
        assertNull(garrison.defensePost, "post fully down → squad released from the guardpost");
        assertEquals(PatrolRoute.DEFAULT_DISTRICT_RADIUS, garrison.patrolRadius,
                "released squad reverts to the wide default patrol radius");
    }

    @Test
    public void liveTurretIsLeftAlone() {
        BattleSimulation sim = openArena(20, 20);
        MapTurret turret = new MapTurret("t0", Faction.DEFENDER, TurretKind.VULCAN, 10, 10);
        sim.addUnit(turret);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.world().isAlive(turret.entityId), "no damage → still alive");
        assertFalse(turret.demolished, "a live turret is never demolished");
    }
}
