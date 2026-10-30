package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Kit Ladder loader on tiny inline documents: what it builds, and that it refuses bad data by file and entry. */
class KitLadderCatalogTest {

    private static final String RIFLE = "weapon.field-rifle";
    private static final String SMOKE = "special.smoke-grenade";
    private static final String SUIT = "armor.field-fatigues";
    private static final String KEY = "squadDoctrineFieldSecurityWeaponsName";

    /** Only the handful of ids these documents name exist. */
    private static final KitLadderCatalog.Known KNOWN = new KitLadderCatalog.Known() {
        @Override public boolean primary(String id) { return RIFLE.equals(id); }
        @Override public boolean special(String id) { return SMOKE.equals(id); }
        @Override public boolean thrown(String id) { return SMOKE.equals(id); }
        @Override public boolean armor(String id) { return SUIT.equals(id); }
        @Override public boolean pattern(String id) { return "mech-pattern:chassis:hound".equals(id); }
        @Override public boolean string(String key) { return KEY.equals(key); }
    };

    private static JSONObject ladder() throws Exception {
        return new JSONObject("""
                {"stations": [
                  {"id": "start", "reach": {"type": "always"}},
                  {"id": "relief", "reach": {"type": "storyMission", "mission": "m1"}},
                  {"id": "junction", "reach": {"type": "arcBeat", "arc": "broken-supply-route", "beat": "junction-won"}}],
                 "placements": [
                  {"item": "loadout-card:weapon:weapons:a", "station": "relief"},
                  {"item": "loadout-card:sheet:armor:b", "station": "none"},
                  {"item": "mech-pattern:chassis:hound", "station": "junction"}]}""");
    }

    private static JSONObject cards() throws Exception {
        JSONArray seats = new JSONArray();
        for (int i = 0; i < MarineSquad.CAPACITY; i++) {
            seats.put(new JSONObject().put("role", "Rifleman").put("primary", RIFLE).put("grade", "SURPLUS")
                    .put("special", i == 0 ? SMOKE : null));
        }
        JSONObject weapon = new JSONObject().put("id", "weapons:a").put("name", "@" + KEY)
                .put("description", "@" + KEY).put("members", seats);
        JSONObject sheet = new JSONObject().put("id", "armor:b").put("name", "@" + KEY).put("description", "@" + KEY)
                .put("tradition", "INDEPENDENT").put("mix", "IRREGULAR").put("suits", new JSONArray().put(SUIT));
        return new JSONObject().put("weaponCards", new JSONArray().put(weapon))
                .put("tacticSheets", new JSONArray().put(sheet));
    }

    private static KitLadderCatalog parse(JSONObject ladder, JSONObject cards) {
        return KitLadderCatalog.parse(ladder, cards, KNOWN, Set.of());
    }

    private static String refusal(JSONObject ladder, JSONObject cards) {
        return assertThrows(IllegalStateException.class, () -> parse(ladder, cards)).getMessage();
    }

    @Test
    void buildsStationsPlacementsCardsAndReachFromData() throws Exception {
        KitLadderCatalog catalog = parse(ladder(), cards());
        assertEquals(UnlockStation.START, catalog.stations().get(0));
        assertEquals("relief", catalog.placements().get("loadout-card:weapon:weapons:a").id);
        assertEquals(UnlockStation.NONE, catalog.placements().get("loadout-card:sheet:armor:b"));
        assertEquals(SMOKE, catalog.weaponCard("weapons:a").issue(0).specialEquipmentId());
        assertEquals(List.of(SUIT), catalog.suits("armor:b"));

        UnlockStation relief = catalog.station("relief");
        UnlockStation junction = catalog.station("junction");
        CampaignState state = new CampaignState();
        assertTrue(relief.reach().test("m1"::equals, state));
        assertFalse(relief.reach().test(id -> false, state));
        assertFalse(junction.reach().test(id -> true, state), "an arc beat reads the arc's ledger, not the story list");
        assertEquals("arcBeat broken-supply-route junction-won", junction.reach().toString());
    }

    @Test
    void refusesUnknownIdsStationsAndDuplicatesNamingFileAndEntry() throws Exception {
        JSONObject ladder = ladder();
        ladder.getJSONArray("placements")
                .put(new JSONObject().put("item", "loadout-card:weapon:weapons:a").put("station", "start"))
                .put(new JSONObject().put("item", "mech-pattern:chassis:nothing").put("station", "start"))
                .put(new JSONObject().put("item", "mech-pattern:chassis:hound").put("station", "later"));
        JSONObject cards = cards();
        JSONObject seat = cards.getJSONArray("weaponCards").getJSONObject(0).getJSONArray("members").getJSONObject(1);
        seat.put("special", "special.nothing").put("primary", "weapon.nothing");
        String message = refusal(ladder, cards);
        assertTrue(message.contains("kit-ladder.json: placements[3] (loadout-card:weapon:weapons:a): placed twice"), message);
        assertTrue(message.contains("placements[4] (mech-pattern:chassis:nothing): names no machine pattern"), message);
        assertTrue(message.contains("placements[5] (mech-pattern:chassis:hound): station 'later'"), message);
        assertTrue(message.contains("loadout-cards.json: weaponCards[0] (weapons:a) seat 2: special 'special.nothing'"), message);
        assertTrue(message.contains("seat 2: primary 'weapon.nothing'"), message);
    }

    @Test
    void refusesShortSquadsEmptySheetsMissingKeysAndUnknownReach() throws Exception {
        JSONObject cards = cards();
        cards.getJSONArray("weaponCards").getJSONObject(0).getJSONArray("members").remove(0);
        JSONObject sheet = cards.getJSONArray("tacticSheets").getJSONObject(0);
        sheet.put("suits", new JSONArray()).put("name", "@noSuchKey");
        JSONObject ladder = ladder();
        ladder.getJSONArray("stations").getJSONObject(2).put("reach", new JSONObject().put("type", "moonPhase"));
        String message = refusal(ladder, cards);
        assertTrue(message.contains("weaponCards[0] (weapons:a): has 11 seats; a squad has 12"), message);
        assertTrue(message.contains("tacticSheets[0] (armor:b): has no suits"), message);
        assertTrue(message.contains("tacticSheets[0] (armor:b): name key @noSuchKey is not in strings.json"), message);
        assertTrue(message.contains("stations[2] (junction): reach type 'moonPhase'"), message);
    }

    @Test
    void refusesAFreePickTheWaylineDoesNotNameAndAnUnplacedCard() throws Exception {
        JSONObject cards = cards();
        cards.getJSONArray("weaponCards").getJSONObject(0).put("grant", "free-pick");
        JSONObject ladder = ladder();
        ladder.getJSONArray("placements").remove(1);
        String message = refusal(ladder, cards);
        assertTrue(message.contains("weapons:a is marked free-pick but is no Wayline path's card"), message);
        assertTrue(message.contains("card loadout-card:sheet:armor:b has no placement"), message);
    }
}
