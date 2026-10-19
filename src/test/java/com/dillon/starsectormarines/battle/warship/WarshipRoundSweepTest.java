package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A round between hulls meets the first hull on its way: footprint, shield arc, who can be met, and a clear line. */
class WarshipRoundSweepTest {

    /** A 100 x 400 hull (beam x keel), front shield of radius 260 centred on the hull, a 180-degree arc. */
    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"shieldRadius":260,"shieldCenter":[0,0],"weaponSlots":[]}""";

    @BeforeEach
    void source() {
        Map<String, String> row = new HashMap<>();
        row.put("id", "test");
        row.put("hitpoints", "20000");
        row.put("armor rating", "1750");
        row.put("max flux", "4000");
        row.put("flux dissipation", "250");
        row.put("shield type", "FRONT");
        row.put("shield arc", "180");
        row.put("shield upkeep", "0.4");
        row.put("shield efficiency", "1");
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) {
                    return new JSONObject("{\"hullId\":\"test\",\"weaponGroups\":[]}");
                }
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(row);
            }
        });
        VanillaWeapons.clearCache();
    }

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    /** A hull at {@code (x, y)}, nose along +y (facing 0), shield down. */
    private static Warship ship(Faction side, float x, float y) throws Exception {
        return Warship.fit("test", "test_Standard", side, x, y, 0f);
    }

    /** {@link #ship} with its front shield held up and fully risen. */
    private static Warship shielded(Faction side, float x, float y) throws Exception {
        Warship s = ship(side, x, y);
        s.limitFlux();
        s.raiseShield(true);
        for (int i = 0; i < 30; i++) s.shieldModel().update(0.05f, s.fluxPool(), false);
        assertTrue(s.shieldAbsorbing());
        return s;
    }

    @Test
    void aRoundStopsOnTheNearFaceOfTheFootprint() throws Exception {
        Warship s = ship(Faction.DEFENDER, 200f, 200f);
        WarshipRoundSweep.Contact c = WarshipRoundSweep.contact(s, 100f, 200f, 300f, 200f);
        assertNotNull(c);
        assertFalse(c.shield());
        assertEquals(s.centerX() - s.widthCells * 0.5f, c.x(), 1e-3f, "it meets the port side, beam on");
        assertNull(WarshipRoundSweep.contact(s, 100f, 200f + s.lengthCells, 300f, 200f + s.lengthCells),
                "a line past the bow meets nothing");
    }

    @Test
    void aSegmentShortOfTheHullMeetsNothingAndOneStartingInsideMeetsItAtOnce() throws Exception {
        Warship s = ship(Faction.DEFENDER, 200f, 200f);
        float face = s.centerX() - s.widthCells * 0.5f;
        assertNull(WarshipRoundSweep.contact(s, 100f, 200f, face - 0.5f, 200f), "this tick's travel ends short");
        WarshipRoundSweep.Contact inside = WarshipRoundSweep.contact(s, 200f, 200f, 300f, 200f);
        assertNotNull(inside);
        assertEquals(0f, inside.t(), 1e-6f);
    }

    @Test
    void theFirstHullOnTheLineTakesItFriendOrFoe() throws Exception {
        Warship shooter = ship(Faction.MARINE, 100f, 200f);
        Warship escort = ship(Faction.DEFENDER, 160f, 200f);
        Warship charge = ship(Faction.DEFENDER, 240f, 200f);
        Warship friend = ship(Faction.MARINE, 130f, 200f);
        List<Warship> ships = List.of(shooter, charge, escort);
        WarshipRoundSweep.Contact c = WarshipRoundSweep.first(ships, shooter, 100f, 200f, 300f, 200f);
        assertNotNull(c);
        assertSame(escort, c.ship(), "the escort on the line stops the round meant for its charge");
        c = WarshipRoundSweep.first(List.of(shooter, friend, escort, charge), shooter, 100f, 200f, 300f, 200f);
        assertSame(friend, c.ship(), "a friend in the line is met as any hull is");
    }

    @Test
    void aRoundNeverMeetsTheShipThatFiredIt() throws Exception {
        Warship shooter = ship(Faction.MARINE, 100f, 200f);
        assertNull(WarshipRoundSweep.first(List.of(shooter), shooter, 100f, 200f, 300f, 200f));
    }

    @Test
    void hullsOutOfContactAreFlownOver() throws Exception {
        Warship shooter = ship(Faction.MARINE, 100f, 200f);
        Warship berthed = ship(Faction.DEFENDER, 200f, 200f);
        berthed.berth(200f, 200f);
        assertFalse(WarshipRoundSweep.meets(berthed));
        assertNull(WarshipRoundSweep.first(List.of(shooter, berthed), shooter, 100f, 200f, 300f, 200f),
                "a berthed hull is under the fight, as it is out of soft contact");
    }

    @Test
    void aRaisedShieldTakesARoundFromInsideItsArcOnTheBubble() throws Exception {
        Warship s = shielded(Faction.DEFENDER, 200f, 200f);
        float r = s.shieldRadiusCells();
        // The nose points along +y: a round from ahead meets the bubble; one from astern goes on to the plate.
        WarshipRoundSweep.Contact ahead = WarshipRoundSweep.contact(s, 200f, 200f + 3f * r, 200f, 200f);
        assertNotNull(ahead);
        assertTrue(ahead.shield());
        assertEquals(s.shieldCentreY() + r, ahead.y(), 1e-2f);
        WarshipRoundSweep.Contact astern = WarshipRoundSweep.contact(s, 200f, 200f - 3f * r, 200f, 200f);
        assertNotNull(astern);
        assertFalse(astern.shield(), "the open half of a front shield lets a round from astern through");
        assertEquals(s.centerY() - s.lengthCells * 0.5f, astern.y(), 1e-2f);
    }

    @Test
    void aRoundAlreadyInsideTheBubbleIsNotStoppedByIt() throws Exception {
        Warship s = shielded(Faction.DEFENDER, 200f, 200f);
        // Inside the bubble, abeam and ahead of the hull, flying forward out through the covered arc.
        float x = s.centerX() + s.widthCells;
        assertNull(WarshipRoundSweep.contact(s, x, 200f, x, 200f + 3f * s.shieldRadiusCells()));
    }

    @Test
    void aFriendAcrossTheLineFoulsItAndAFoeOrAFriendBeyondDoesNot() throws Exception {
        Warship shooter = ship(Faction.MARINE, 100f, 200f);
        Warship target = ship(Faction.DEFENDER, 240f, 200f);
        Warship between = ship(Faction.MARINE, 170f, 200f);
        Warship beyond = ship(Faction.MARINE, 300f, 200f);
        Warship foe = ship(Faction.DEFENDER, 170f, 200f);
        assertFalse(WarshipRoundSweep.clearLine(shooter, target, 100f, 200f, 240f, 200f,
                List.of(shooter, target, between)));
        assertTrue(WarshipRoundSweep.clearLine(shooter, target, 100f, 200f, 240f, 200f,
                List.of(shooter, target, beyond)));
        assertTrue(WarshipRoundSweep.clearLine(shooter, target, 100f, 200f, 240f, 200f,
                List.of(shooter, target, foe)), "a hostile hull in the way is no reason to hold");
        // Off the line by more than the pad, a friend leaves it clear.
        Warship aside = ship(Faction.MARINE, 170f, 200f + between.lengthCells);
        assertTrue(WarshipRoundSweep.clearLine(shooter, target, 100f, 200f, 240f, 200f,
                List.of(shooter, target, aside)));
    }
}
