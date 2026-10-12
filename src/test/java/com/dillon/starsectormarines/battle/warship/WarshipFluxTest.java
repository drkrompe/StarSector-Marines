package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
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

/** The flux pool that governs a warship's rate of fire, asked directly and through a fitted hull. */
class WarshipFluxTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"weaponSlots":[
              {"id":"WS 001","type":"TURRET","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,100]},
              {"id":"WS 002","type":"TURRET","size":"SMALL","mount":"TURRET","angle":0,"arc":360,"locations":[0,-100]}]}""";
    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[{"mode":"LINKED","weapons":{"WS 001":"gun_a","WS 002":"pd_b"}}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static WarshipFlux pool() {
        WarshipFlux f = new WarshipFlux(1000f, 100f);
        f.enabled(true);
        return f;
    }

    private static Warship ship(boolean hullHasFlux) throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.endsWith("weapon_data.csv")) {
                    return List.of(
                            Map.of("id", "gun_a", "damage/shot", "80", "range", "1000", "energy/shot", "150"),
                            Map.of("id", "pd_b", "damage/shot", "5", "range", "400", "energy/shot", "10",
                                    "burst size", "3", "hints", "PD"));
                }
                return hullHasFlux
                        ? List.of(Map.of("id", "test", "hitpoints", "20000", "armor rating", "1750",
                        "max flux", "4000", "flux dissipation", "250"))
                        : List.of(Map.of("id", "test", "hitpoints", "20000", "armor rating", "1750"));
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 50f, 50f, 0f);
    }

    @Test
    void aDisabledPoolNeverFillsAndNeverHoldsFire() {
        WarshipFlux f = new WarshipFlux(1000f, 100f);
        f.spend(1_000_000f);
        f.update(1f);
        assertEquals(0f, f.flux());
        assertEquals(0f, f.fraction());
        assertTrue(f.canStartFire());
        assertTrue(f.canDefend());
        assertFalse(f.overloaded());
    }

    @Test
    void spendingAccumulatesAndDissipationDrainsAtTheHullsRate() {
        WarshipFlux f = pool();
        f.spend(300f);
        f.spend(200f);
        assertEquals(500f, f.flux(), 1e-4f);
        f.update(2f);
        assertEquals(300f, f.flux(), 1e-4f);
        f.update(10f);
        assertEquals(0f, f.flux(), "never below empty");
    }

    @Test
    void aboveTheSoftLimitNewFireWaitsUntilTheResumeLevel() {
        WarshipFlux f = pool();
        f.spend(WarshipFlux.SOFT_LIMIT * 1000f + 1f);
        f.update(0f);
        assertEquals(WarshipFlux.State.VENTING, f.state());
        assertFalse(f.canStartFire());
        assertTrue(f.canDefend(), "venting does not silence point defence");
        // Still above the resume level: still resting (hysteresis, not a single threshold).
        f.update(1f);
        assertFalse(f.canStartFire());
        f.update(3f);
        assertTrue(f.fraction() < WarshipFlux.SOFT_LIMIT);
        assertEquals(WarshipFlux.State.FIRING, f.state());
        assertTrue(f.canStartFire());
        assertEquals(0, f.overloads());
    }

    @Test
    void underTheSoftLimitASalvoThatDoesNotFitIsNotStarted() {
        WarshipFlux f = pool();
        f.spend(600f);
        assertTrue(f.canStart(399f), "leaves a sliver");
        assertFalse(f.canStart(400f), "would fill the pool, which is an overload");
        f.softLimit(false);
        assertTrue(f.canStart(401f), "with the limit off the ship fires regardless");
        f.softLimit(true);
        WarshipFlux empty = pool();
        assertTrue(empty.canStart(5000f), "a salvo bigger than the pool is started from empty");
    }

    @Test
    void reachingCapacityOverloadsForALengthThatGrowsWithTheOvershoot() {
        WarshipFlux small = pool();
        small.spend(1000f);
        assertTrue(small.overloaded());
        assertEquals(WarshipFlux.OVERLOAD_MIN_SECONDS, small.overloadSecondsRemaining(), 1e-4f);
        assertFalse(small.canDefend(), "overload silences point defence too");

        WarshipFlux big = pool();
        big.spend(2000f);
        assertEquals(WarshipFlux.OVERLOAD_MAX_SECONDS, big.overloadSecondsRemaining(), 1e-4f);

        WarshipFlux middling = pool();
        middling.spend(1050f);
        float s = middling.overloadSecondsRemaining();
        assertTrue(s > WarshipFlux.OVERLOAD_MIN_SECONDS && s < WarshipFlux.OVERLOAD_MAX_SECONDS, "was " + s);
    }

    @Test
    void anOverloadDissipatesThroughoutIgnoresFurtherSpendingAndEndsEmpty() {
        WarshipFlux f = pool();
        f.spend(1000f);
        f.update(2f);
        assertEquals(800f, f.flux(), 1e-3f, "still draining while overloaded");
        f.spend(500f);
        assertEquals(800f, f.flux(), 1e-3f, "nothing is fired, so nothing is added");
        f.update(1.9f);
        assertTrue(f.overloaded());
        f.update(0.2f);
        assertFalse(f.overloaded());
        assertEquals(0f, f.flux());
        assertEquals(WarshipFlux.State.FIRING, f.state());
        assertTrue(f.canStartFire());
        assertEquals(1, f.overloads());
    }

    @Test
    void withTheSoftLimitOffOnlyAnOverloadStopsTheShip() {
        WarshipFlux f = pool();
        f.softLimit(false);
        f.spend(990f);
        f.update(0f);
        assertTrue(f.canStartFire(), "99% and still firing");
        f.spend(20f);
        assertTrue(f.overloaded());
    }

    @Test
    void theHullTableSetsCapacityAndDissipationAndScaleMultipliesBoth() throws Exception {
        Warship s = ship(true);
        assertEquals(4000f, s.fluxCapacity(), 1e-3f);
        assertEquals(250f, s.fluxPool().dissipation(), 1e-3f);
        s.limitFlux();
        s.fluxScale(0.5f);
        assertEquals(2000f, s.fluxCapacity(), 1e-3f);
        assertEquals(125f, s.fluxPool().dissipation(), 1e-3f);
        assertEquals(0.5f, s.fluxScale());
        assertThrows(IllegalArgumentException.class, () -> s.fluxScale(0f));
    }

    @Test
    void aHullWithNoFluxColumnsHasAMiddlingCapitals() throws Exception {
        Warship s = ship(false);
        assertTrue(s.fluxCapacity() > 0f);
        assertTrue(s.fluxPool().dissipation() > 0f);
    }

    @Test
    void shipsAreOffFluxUntilTheyAreToldAndReadOffWhenTheyAre() throws Exception {
        Warship s = ship(true);
        assertFalse(s.fluxLimited());
        assertEquals("off", s.fluxState());
        s.fluxPool().spend(5000f);
        assertEquals(0f, s.fluxFraction());
        s.limitFlux();
        assertEquals("firing", s.fluxState());
        s.fluxPool().spend(1000f);
        assertEquals(0.25f, s.fluxFraction(), 1e-4f);
        s.fluxPool().spend(4000f);
        assertTrue(s.overloaded());
        assertEquals("overloaded", s.fluxState());
        assertTrue(s.overloadSecondsRemaining() > 0f);
        s.unlimitedFlux();
        assertFalse(s.overloaded());
        assertEquals(0f, s.fluxFraction());
    }

    @Test
    void weaponsReadTheirCostFromTheTableAndPointDefenceSharesThePool() throws Exception {
        Warship s = ship(true);
        VanillaWeapon gun = null;
        VanillaWeapon pd = null;
        for (Warship.Mount m : s.mounts()) {
            if (m.pointDefence()) pd = m.weapon;
            else gun = m.weapon;
        }
        assertEquals(150f, gun.fluxPerShot(), 1e-4f);
        assertEquals(30f, WarshipPointDefence.engagementFlux(pd), 1e-4f, "a three-round burst at ten each");
        s.limitFlux();
        s.fluxPool().spend(gun.fluxPerShot());
        s.fluxPool().spend(WarshipPointDefence.engagementFlux(pd));
        assertEquals(180f, s.flux(), 1e-3f, "one pool for area fire and point defence");
    }
}
