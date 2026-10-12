package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shield model: its figures, its arc, what it does to the pool, and when an automatic one rises and drops. */
class WarshipShieldTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"shieldRadius":200,"shieldCenter":[20,0],"weaponSlots":[
              {"id":"WS 001","type":"TURRET","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,100]}]}""";
    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[{"mode":"LINKED","weapons":{"WS 001":"gun_a"}}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static Map<String, String> row(String type, String arc, String efficiency) {
        Map<String, String> row = new HashMap<>();
        row.put("id", "test");
        row.put("hitpoints", "20000");
        row.put("armor rating", "1750");
        row.put("max flux", "4000");
        row.put("flux dissipation", "250");
        row.put("shield type", type);
        row.put("shield arc", arc);
        row.put("shield upkeep", "0.4");
        row.put("shield efficiency", efficiency);
        return row;
    }

    private static Warship ship(Map<String, String> hullRow) throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.endsWith("weapon_data.csv")) {
                    return List.of(Map.of("id", "gun_a", "damage/shot", "80", "range", "1000", "energy/shot", "150"));
                }
                return List.of(hullRow);
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 50f, 50f, 0f);
    }

    /** A ship on flux whose shield is held up and fully risen. */
    private static Warship raised(Map<String, String> hullRow) throws Exception {
        Warship s = ship(hullRow);
        s.limitFlux();
        s.raiseShield(true);
        settle(s);
        return s;
    }

    private static void settle(Warship s) {
        for (int i = 0; i < 30; i++) s.shieldModel().update(0.05f, s.fluxPool(), false);
    }

    private static PendingDetonation hit(float x, float y, float damage) {
        return new PendingDetonation(0L, x, y, 0f, 2f, damage, 60f, 0, Faction.DEFENDER,
                false, 0f, false, false, false);
    }

    private static WarshipSystem system(Warship s) {
        WarshipSystem system = new WarshipSystem();
        system.add(s);
        return system;
    }

    @Test
    void readsTheHullsShieldFiguresAndCentre() throws Exception {
        Warship s = ship(row("FRONT", "180", "0.6"));
        assertEquals("front", s.shieldType());
        assertEquals(180f, s.shieldArcDegrees());
        assertEquals(0.6f, s.shieldModel().efficiency(), 1e-6f);
        assertEquals(0.4f, s.shieldModel().upkeep(), 1e-6f);
        assertEquals(WarshipScale.cells(200f), s.shieldRadiusCells(), 1e-4f);
        // The first centre figure runs along the nose: the bubble sits ahead of the origin.
        assertEquals(50f, s.shieldCentreX(), 1e-4f);
        assertEquals(50f + WarshipScale.cells(20f), s.shieldCentreY(), 1e-4f);
        assertEquals("none", ship(row("NONE", "", "1")).shieldType());
        assertEquals("none", ship(row("PHASE", "", "1")).shieldType(), "a cloak is not a shield");
        assertEquals("none", ship(row("", "", "")).shieldType());
    }

    @Test
    void aFrontArcCoversTheNoseAndNotTheStern() throws Exception {
        Warship s = ship(row("FRONT", "180", "1"));
        float cy = s.shieldCentreY();
        assertTrue(s.arcCovers(50f, cy + 10f), "dead ahead");
        assertTrue(s.arcCovers(60f, cy + 1f), "forward of the beam on one side");
        assertTrue(s.arcCovers(40f, cy + 1f), "forward of the beam on the other");
        assertFalse(s.arcCovers(60f, cy - 1f), "abaft the beam");
        assertFalse(s.arcCovers(50f, cy - 10f), "dead astern");
        s.facingDegrees = 90f;
        // Turned a quarter turn, the nose points along -x.
        assertTrue(s.arcCovers(s.shieldCentreX() - 10f, s.shieldCentreY()));
        assertFalse(s.arcCovers(s.shieldCentreX() + 10f, s.shieldCentreY()));
    }

    @Test
    void anOmniShieldCoversEveryBearing() throws Exception {
        Warship s = ship(row("OMNI", "45", "1"));
        float cx = s.shieldCentreX();
        float cy = s.shieldCentreY();
        assertTrue(s.arcCovers(cx, cy + 10f));
        assertTrue(s.arcCovers(cx, cy - 10f));
        assertTrue(s.arcCovers(cx + 10f, cy));
        assertTrue(s.arcCovers(cx - 10f, cy));
    }

    @Test
    void aShieldInsideItsArcAbsorbsTheHitAsFluxAndTheHullTakesNothing() throws Exception {
        Warship s = raised(row("FRONT", "180", "0.6"));
        float before = s.hitPoints();
        float flux = s.flux();
        system(s).onDetonation(hit(50f, s.shieldCentreY() + 4f, 600f), null);
        assertEquals(before, s.hitPoints(), "an absorbed hit costs the hull nothing");
        assertEquals(flux + 600f * 0.6f, s.flux(), 1e-3f, "damage times efficiency becomes flux");
        assertEquals(1, s.shieldHits());
        assertEquals(600f, s.shieldAbsorbedDamage());
        assertTrue(s.shieldHitFlash() > 0.9f);
    }

    @Test
    void aHitFromBehindAFrontShieldGoesToTheArmour() throws Exception {
        Warship s = raised(row("FRONT", "180", "1"));
        float flux = s.flux();
        system(s).onDetonation(hit(50f, s.shieldCentreY() - 4f, 600f), null);
        assertTrue(s.hitPoints() < s.maxHitPoints, "outside the arc the hull is hit");
        assertEquals(flux, s.flux(), 1e-6f, "and the shield takes nothing");
        assertEquals(0, s.shieldHits());
    }

    @Test
    void aShieldThatIsDownOrStillRisingDoesNotAbsorb() throws Exception {
        Warship down = ship(row("FRONT", "180", "1"));
        down.limitFlux();
        system(down).onDetonation(hit(50f, down.shieldCentreY() + 4f, 600f), null);
        assertTrue(down.hitPoints() < down.maxHitPoints);

        Warship rising = ship(row("FRONT", "180", "1"));
        rising.limitFlux();
        rising.raiseShield(true);
        rising.shieldModel().update(0.05f, rising.fluxPool(), false);
        assertEquals("rising", rising.shieldState());
        system(rising).onDetonation(hit(50f, rising.shieldCentreY() + 4f, 600f), null);
        assertTrue(rising.hitPoints() < rising.maxHitPoints);
    }

    @Test
    void aShipNotOnFluxHasNoShieldToRaise() throws Exception {
        Warship s = ship(row("FRONT", "180", "1"));
        s.raiseShield(true);
        settle(s);
        assertFalse(s.hasShield());
        assertFalse(s.shieldUp());
        assertEquals("off", s.shieldState());
        system(s).onDetonation(hit(50f, s.shieldCentreY() + 4f, 600f), null);
        assertTrue(s.hitPoints() < s.maxHitPoints, "armour answers as it always has");
    }

    @Test
    void aRaisedShieldDrawsUpkeepAndStopsTheFluxVenting() throws Exception {
        WarshipFlux f = new WarshipFlux(4000f, 250f);
        f.enabled(true);
        f.spend(2000f);
        WarshipShield shield = new WarshipShield(WarshipShield.Type.FRONT, 180f, 10f, 0f, 0f, 0.4f, 1f);
        shield.raise(true);
        for (int i = 0; i < 20; i++) {
            shield.update(0.05f, f, false);
            f.update(0.05f);
        }
        // One second at 0.4 of 250 a second, and not a unit vented.
        assertEquals(2000f + 100f, f.flux(), 1f);
        shield.raise(false);
        for (int i = 0; i < 20; i++) {
            shield.update(0.05f, f, false);
            f.update(0.05f);
        }
        assertEquals(2100f - 250f, f.flux(), 1f, "dropped, the pool vents at the hull's rate again");
    }

    @Test
    void aShieldThatFillsThePoolCollapsesAndTheShipOverloads() throws Exception {
        Warship s = raised(row("FRONT", "180", "1"));
        assertTrue(s.shieldUp());
        // 4000 capacity; a single 5000-damage hit fills it.
        system(s).onDetonation(hit(50f, s.shieldCentreY() + 4f, 5000f), null);
        assertTrue(s.overloaded());
        assertFalse(s.shieldUp(), "the shield drops with the overload");
        assertEquals("down", s.shieldState());
        assertEquals(1, s.shieldCollapses());
        assertTrue(s.shieldDropFlash() > 0.9f);
        // It stays down while overloaded even though the override still asks for it, and the pool vents.
        float peak = s.flux();
        for (int i = 0; i < 20; i++) {
            s.shieldModel().update(0.05f, s.fluxPool(), false);
            s.fluxPool().update(0.05f);
        }
        assertFalse(s.shieldUp());
        assertTrue(s.flux() < peak, "an overloaded ship dissipates");
        // The hit that came while overloaded goes to the armour.
        system(s).onDetonation(hit(50f, s.shieldCentreY() + 4f, 600f), null);
        assertTrue(s.hitPoints() < s.maxHitPoints);
    }

    @Test
    void upkeepAloneCanOverloadAShipWhoseShieldIsHeldUp() throws Exception {
        WarshipFlux f = new WarshipFlux(100f, 250f);
        f.enabled(true);
        WarshipShield shield = new WarshipShield(WarshipShield.Type.OMNI, 360f, 10f, 0f, 0f, 0.4f, 1f);
        shield.raise(true);
        for (int i = 0; i < 40 && !f.overloaded(); i++) {
            shield.update(0.05f, f, false);
            f.update(0.05f);
        }
        assertTrue(f.overloaded(), "100 a second of upkeep against 100 capacity, and no venting");
        assertFalse(shield.raised());
    }

    @Test
    void anAutomaticShieldRisesOnAThreatAndDropsOnceItHasPassed() {
        WarshipFlux f = new WarshipFlux(4000f, 250f);
        f.enabled(true);
        WarshipShield shield = new WarshipShield(WarshipShield.Type.FRONT, 180f, 10f, 0f, 0f, 0.4f, 1f);
        shield.update(0.05f, f, false);
        assertFalse(shield.raised(), "nothing threatens it");
        for (int i = 0; i < 10; i++) shield.update(0.05f, f, true);
        assertTrue(shield.raised());
        assertTrue(shield.absorbing());
        // The threat has passed but the shield holds a few seconds against the next one.
        for (int i = 0; i < 20; i++) shield.update(0.1f, f, false);
        assertTrue(shield.raised(), "2 seconds is inside the hold");
        for (int i = 0; i < 20; i++) shield.update(0.1f, f, false);
        assertFalse(shield.raised(), "4 quiet seconds later it has dropped");
        assertEquals(0f, shield.level(), 1e-6f);
    }

    @Test
    void anAutomaticShieldDropsAboveTheDropLevelAndOnlyRisesAgainOnceTheFluxHasVented() {
        WarshipFlux f = new WarshipFlux(4000f, 250f);
        f.enabled(true);
        WarshipShield shield = new WarshipShield(WarshipShield.Type.FRONT, 180f, 10f, 0f, 0f, 0.4f, 1f);
        shield.update(0.05f, f, true);
        assertTrue(shield.raised());
        f.spend(WarshipShield.DROP_LEVEL * 4000f + 10f);
        shield.update(0.05f, f, true);
        assertFalse(shield.raised(), "under fire but too full: it drops so the ship vents rather than overloads");
        // Still threatened, still full of flux: it stays down.
        shield.update(0.05f, f, true);
        assertFalse(shield.raised());
        f.holdDissipation(false);
        for (int i = 0; i < 400 && f.fraction() > WarshipFlux.RESUME_LEVEL; i++) {
            shield.update(0.05f, f, true);
            f.update(0.05f);
        }
        assertTrue(f.fraction() <= WarshipFlux.RESUME_LEVEL + 0.01f);
        shield.update(0.05f, f, true);
        assertTrue(shield.raised(), "vented to the resume level, the threat raises it again");
    }

    @Test
    void aManualOverrideHoldsTheShieldWhateverTheThreatAndAutoReturnsControl() {
        WarshipFlux f = new WarshipFlux(4000f, 250f);
        f.enabled(true);
        WarshipShield shield = new WarshipShield(WarshipShield.Type.FRONT, 180f, 10f, 0f, 0f, 0.4f, 1f);
        shield.raise(true);
        assertFalse(shield.auto());
        for (int i = 0; i < 100; i++) shield.update(0.1f, f, false);
        assertTrue(shield.raised(), "held up with no threat at all");
        shield.raise(false);
        shield.update(0.05f, f, true);
        assertFalse(shield.raised(), "held down under threat");
        shield.auto(true);
        shield.update(0.05f, f, true);
        assertTrue(shield.raised());
    }
}
