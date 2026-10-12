package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The counterplay budget a warship carries: station time and ordnance, on the ship alone. */
class WarshipBudgetTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"weaponSlots":[
              {"id":"WS 001","type":"TURRET","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,100]},
              {"id":"WS 002","type":"TURRET","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,-100]}]}""";
    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[{"mode":"LINKED","weapons":{"WS 001":"gun_a","WS 002":"gun_b"}}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static Warship ship() throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.endsWith("weapon_data.csv")) {
                    return List.of(
                            Map.of("id", "gun_a", "damage/shot", "80", "range", "1000", "ammo", "3", "turn rate", "180", "proj speed", "1000"),
                            Map.of("id", "gun_b", "damage/shot", "80", "range", "1000", "ammo", "", "turn rate", "180", "proj speed", "1000"));
                }
                return List.of(Map.of("id", "test", "hitpoints", "20000", "armor rating", "1750"));
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 50f, 50f, 0f);
    }

    @Test
    void unlimitedIsTheDefault() throws Exception {
        Warship s = ship();
        assertEquals(Float.POSITIVE_INFINITY, s.stationSecondsRemaining());
        assertEquals(Warship.UNLIMITED, s.ordnanceRemaining());
        assertFalse(s.spendStationTime(1000f));
        for (Warship.Mount m : s.mounts()) {
            assertEquals(Warship.UNLIMITED, m.ammoRemaining());
            assertTrue(s.hasOrdnance(m));
        }
    }

    @Test
    void stationTimeCountsOnlyWhileOnStation() throws Exception {
        Warship s = ship();
        s.stayFor(10f);
        s.stationTo(80f, 50f);
        assertFalse(s.spendStationTime(5f), "still travelling");
        assertEquals(10f, s.stationSecondsRemaining());
        s.x = 80f;
        assertFalse(s.spendStationTime(4f));
        assertEquals(6f, s.stationSecondsRemaining(), 1e-4f);
        assertTrue(s.spendStationTime(6f), "spent");
        assertEquals(0f, s.stationSecondsRemaining());
    }

    @Test
    void onlyExplicitBombardmentShootsAnUnoccupiedStructure() throws Exception {
        var grid = new NavigationGrid(80, 80);
        var topology = new CellTopology(80, 80);
        for (int y = 0; y < 80; y++) for (int x = 0; x < 80; x++)
            if (x != 60 || y != 50) grid.setWalkableFloor(x, y);
        grid.setWallHp(60, 50, 100);
        topology.setWall(60, 50, true);
        try (var sim = new BattleSimulation(grid, topology)) {
            Warship ship = ship();
            sim.addWarship(ship);
            ship.order(new WarshipOrder.FireOnArea(60.5f, 50.5f, 2f, false));
            for (int i = 0; i < 240; i++) sim.warships().tick(BattleSimulation.TICK_DT, sim);
            assertEquals(0, ship.mounts().stream().mapToInt(m -> m.shotsFired).sum());
            ship.order(new WarshipOrder.FireOnArea(60.5f, 50.5f, 2f, false, true));
            for (int i = 0; i < 240; i++) sim.warships().tick(BattleSimulation.TICK_DT, sim);
            assertTrue(ship.mounts().stream().mapToInt(m -> m.shotsFired).sum() > 0);
            assertTrue(grid.getWallHp(60, 50) < 100, "actual projectile impacts damage the empty wall");
        }
    }

    @Test
    void holdFireCancelsUnlaunchedBurstRounds() throws Exception {
        Warship s = ship();
        s.order(new WarshipOrder.FireOnArea(60, 60, 5, true, true));
        for (var mount : s.mounts()) mount.burstRemaining = 5;
        s.order(WarshipOrder.HoldFire.INSTANCE);
        for (var mount : s.mounts()) assertEquals(0, mount.burstRemaining);
        assertEquals(WarshipOrder.HoldFire.INSTANCE, s.order());
    }

    @Test
    void departureSilencesTheGuns() throws Exception {
        Warship s = ship();
        s.stayFor(1f);
        s.direct(5f, true);
        s.beginDeparture();
        assertTrue(s.departing());
        assertFalse(s.isDirected());
        assertEquals(WarshipOrder.HoldFire.INSTANCE, s.order());
        for (Warship.Mount m : s.mounts()) assertFalse(s.hasOrdnance(m));
    }

    @Test
    void theSharedPoolIsSpentRoundByRound() throws Exception {
        Warship s = ship();
        s.limitOrdnance(3);
        Warship.Mount a = s.mounts().get(0);
        Warship.Mount b = s.mounts().get(1);
        s.spendOrdnance(a);
        s.spendOrdnance(b);
        assertEquals(1, s.ordnanceRemaining());
        assertTrue(s.hasOrdnance(a));
        s.spendOrdnance(a);
        assertEquals(0, s.ordnanceRemaining());
        assertFalse(s.hasOrdnance(a));
        assertFalse(s.hasOrdnance(b));
        s.spendOrdnance(a);
        assertEquals(0, s.ordnanceRemaining(), "never below zero");
    }

    @Test
    void aWeaponsOwnAmmoColumnIsItsMagazineAndBlankIsUnlimited() throws Exception {
        Warship s = ship();
        s.limitOrdnanceToWeaponAmmo();
        Warship.Mount a = s.mounts().get(0);
        Warship.Mount b = s.mounts().get(1);
        assertEquals(3, a.ammoRemaining());
        assertEquals(Warship.UNLIMITED, b.ammoRemaining());
        for (int i = 0; i < 3; i++) s.spendOrdnance(a);
        assertFalse(s.hasOrdnance(a));
        for (int i = 0; i < 50; i++) s.spendOrdnance(b);
        assertTrue(s.hasOrdnance(b), "a gun with no ammo column does not run out");
    }

    @Test
    void negativeBudgetsAreRefused() throws Exception {
        Warship s = ship();
        assertThrows(IllegalArgumentException.class, () -> s.stayFor(-1f));
        assertThrows(IllegalArgumentException.class, () -> s.limitOrdnance(-1));
    }
}
