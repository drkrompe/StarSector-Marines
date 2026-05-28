package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-2 coverage for {@link RecaptureTargetService}: node bucketing by
 * biome, the open/held derivation from squad assignment, the debounced
 * contested-slice (frontline) detection, and the dispatch-dedup / reopen
 * lifecycle.
 *
 * <p>Slices are derived from {@link BiomeMap#biomeAt} rather than hard-coded,
 * so the tests stay robust to the map's per-column boundary jitter — they only
 * assume the three band-center anchors land in three distinct biomes.
 */
public class RecaptureTargetServiceTest {

    private static final int W = 20;
    private static final int H = 100;
    private static final float TICK = ReinforcementService.REINFORCEMENT_TICK_PERIOD;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static BiomeMap biomeMap() {
        return new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42));
    }

    private static TacticalNode node(TacticalNode.Kind kind, int x, int y, Faction guard, int garrison) {
        return new TacticalNode(kind, x, y, x - 1, y - 1, x + 1, y + 1, guard, 50, garrison);
    }

    /** A defender garrison squad assigned to {@code node} with {@code alive} live members. The leader unit is killed so it never counts toward biome presence. */
    private static Squad garrison(BattleSimulation sim, TacticalNode node, int alive, int original) {
        Unit leader = new Unit("garr-" + node.anchorX + "-" + node.anchorY,
                Faction.DEFENDER, UnitType.MILITIA, node.anchorX, node.anchorY);
        int sid = sim.mintSquad(Faction.DEFENDER, leader);
        Squad squad = sim.getSquad(sid);
        squad.assignedNode = node;
        squad.originalSize = original;
        squad.aliveMembers = alive;
        sim.addUnit(leader);
        TestUnits.kill(sim, leader);
        return squad;
    }

    /** A lone alive defender at {@code (x,y)} — contributes biome presence only (no squad/node assignment). */
    private static Unit presence(BattleSimulation sim, String id, int x, int y) {
        Unit u = new Unit(id, Faction.DEFENDER, UnitType.MILITIA, x, y);
        sim.addUnit(u);
        return u;
    }

    private static RecaptureTarget targetFor(RecaptureTargetService reg, TacticalNode node) {
        for (RecaptureTarget t : reg.allTargets()) {
            if (t.node == node) return t;
        }
        return null;
    }

    @Test
    public void bucketsEligibleDefenderNodesByBiome() {
        BiomeMap biomes = biomeMap();
        TacticalNode port = node(TacticalNode.Kind.GUARDPOST, 10, 25, Faction.DEFENDER, 4);
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);

        BiomeKind ps = biomes.biomeAt(port.anchorX, port.anchorY);
        BiomeKind cs = biomes.biomeAt(city.anchorX, city.anchorY);
        BiomeKind fs = biomes.biomeAt(fort.anchorX, fort.anchorY);
        assertNotEquals(ps, cs, "precondition: port and city anchors are in distinct biomes");
        assertNotEquals(cs, fs, "precondition: city and fortress anchors are in distinct biomes");
        assertNotEquals(ps, fs, "precondition: port and fortress anchors are in distinct biomes");

        TacticalMap tmap = new TacticalMap(List.of(port, city, fort));
        RecaptureTargetService reg = new RecaptureTargetService(tmap, biomes);

        assertEquals(3, reg.allTargets().size());
        assertEquals(1, reg.targetsInSlice(ps).size());
        assertEquals(1, reg.targetsInSlice(cs).size());
        assertEquals(1, reg.targetsInSlice(fs).size());
        assertEquals(port, reg.targetsInSlice(ps).get(0).node);
    }

    @Test
    public void excludesIneligibleNodes() {
        BiomeMap biomes = biomeMap();
        TacticalNode garrisoned = node(TacticalNode.Kind.GUARDPOST, 10, 55, Faction.DEFENDER, 4);
        TacticalNode airbase    = node(TacticalNode.Kind.AIRBASE, 10, 56, Faction.DEFENDER, 4);
        TacticalNode noGarrison = node(TacticalNode.Kind.GATE, 10, 57, Faction.DEFENDER, 0);
        TacticalNode marine     = node(TacticalNode.Kind.BEACHHEAD, 10, 10, Faction.MARINE, 4);

        TacticalMap tmap = new TacticalMap(List.of(garrisoned, airbase, noGarrison, marine));
        RecaptureTargetService reg = new RecaptureTargetService(tmap, biomes);

        assertEquals(1, reg.allTargets().size(), "only the garrisoned defender node is a recapture target");
        assertEquals(garrisoned, reg.allTargets().get(0).node);
    }

    @Test
    public void contestedSeedsThenDebouncesConceding() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        BiomeKind cs = biomes.biomeAt(city.anchorX, city.anchorY);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), biomes);

        Unit defender = presence(sim, "city-def", 10, 55);
        reg.tick(TICK, sim);
        assertTrue(reg.isContested(cs), "slice seeds contested with a defender present");

        TestUnits.kill(sim, defender);
        reg.tick(TICK, sim);
        assertTrue(reg.isContested(cs), "still contested 1 tick after the last defender died (debounce)");
        reg.tick(TICK, sim);
        assertTrue(reg.isContested(cs), "still contested 2 ticks after");
        reg.tick(TICK, sim);
        assertFalse(reg.isContested(cs), "conceded after PRESENCE_DEBOUNCE_TICKS of absence");
    }

    @Test
    public void contestedDebouncesActivation() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        BiomeKind fs = biomes.biomeAt(fort.anchorX, fort.anchorY);
        BiomeKind cs = biomes.biomeAt(10, 55);
        assertNotEquals(fs, cs, "precondition: fortress and the seed-defender slice are distinct");
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(fort)), biomes);

        // A defender elsewhere on the seed tick so the registry locks its seed
        // with the fortress slice conceded — otherwise the first-ever defender
        // sighting would seed (not debounce) the slice it appears in.
        presence(sim, "city-def", 10, 55);
        reg.tick(TICK, sim);
        assertFalse(reg.isContested(fs), "fortress seeds conceded with no defender present");

        // A defender now enters the fortress slice post-seed — the activation
        // must ramp through the debounce, not flip on the first tick.
        presence(sim, "fort-def", 10, 87);
        reg.tick(TICK, sim);
        assertFalse(reg.isContested(fs), "not yet contested 1 tick after a defender entered");
        reg.tick(TICK, sim);
        assertFalse(reg.isContested(fs), "not yet contested 2 ticks after");
        reg.tick(TICK, sim);
        assertTrue(reg.isContested(fs), "contested after PRESENCE_DEBOUNCE_TICKS of presence");
    }

    @Test
    public void eligibleRequiresOpenAndContested() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        TacticalNode port = node(TacticalNode.Kind.GUARDPOST, 10, 25, Faction.DEFENDER, 4);
        BiomeKind cs = biomes.biomeAt(city.anchorX, city.anchorY);
        BiomeKind ps = biomes.biomeAt(port.anchorX, port.anchorY);
        assertNotEquals(cs, ps, "precondition: distinct slices");

        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city, port)), biomes);

        // Both garrisons wiped → both nodes open. City slice has a live
        // defender (contested); port slice has none (conceded).
        garrison(sim, city, 0, 4);
        garrison(sim, port, 0, 4);
        presence(sim, "city-def", 10, 55);

        reg.tick(TICK, sim);

        assertTrue(targetFor(reg, city).isOpen());
        assertTrue(targetFor(reg, port).isOpen());
        assertTrue(reg.isContested(cs));
        assertFalse(reg.isContested(ps));

        List<RecaptureTarget> eligible = reg.eligibleTargets();
        assertEquals(1, eligible.size(), "only the open target in a contested slice is eligible");
        assertEquals(city, eligible.get(0).node, "the conceded-slice target is filtered out");
    }

    @Test
    public void dispatchDedupAndReopenOnReplacementWipe() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), biomes);

        garrison(sim, city, 0, 4);                     // original garrison dead → open
        presence(sim, "city-def", 10, 53);             // keeps the slice contested throughout
        reg.tick(TICK, sim);

        RecaptureTarget t = targetFor(reg, city);
        assertNotNull(t);
        assertEquals(1, reg.eligibleTargets().size(), "open + contested → eligible");

        // Dispatch a reinforcement: dedup suppresses re-dispatch while en route.
        reg.markDispatched(t);
        reg.tick(TICK, sim);
        assertTrue(reg.eligibleTargets().isEmpty(), "dispatched target is no longer eligible");

        // A DISTINCT replacement squad spawns and is assigned to the node at
        // deboard (the real arrival path — not a revived corpse). The original
        // wiped squad stays at 0; aggregate alive on the node is now > 0.
        Squad replacement = garrison(sim, city, 3, 3);
        reg.tick(TICK, sim);
        assertFalse(t.isOpen(), "held once an alive squad is assigned");
        assertFalse(t.isDispatched(), "dispatch flag clears on arrival so a later wipe re-opens");
        assertTrue(reg.eligibleTargets().isEmpty(), "held target is not eligible");

        // Replacement is wiped (the en-route/at-post failure H1 guards) → the
        // node re-opens with no timer because its assigned-alive drops to 0.
        replacement.aliveMembers = 0;
        reg.tick(TICK, sim);
        assertTrue(t.isOpen(), "re-opens when the replacement is wiped");
        assertEquals(1, reg.eligibleTargets().size(), "eligible again");
    }

    @Test
    public void contestedSeedDefersUntilDefendersExist() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        BiomeKind cs = biomes.biomeAt(city.anchorX, city.anchorY);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), biomes);

        // Ticks during sim-init before any defender is placed must not lock the
        // front to "conceded".
        reg.tick(TICK, sim);
        reg.tick(TICK, sim);
        assertFalse(reg.isContested(cs));

        // First defender sighting seeds contested immediately — no debounce ramp.
        presence(sim, "late-def", 10, 55);
        reg.tick(TICK, sim);
        assertTrue(reg.isContested(cs),
                "seeds contested on first defender sighting, not after a debounce ramp");
    }
}
