package com.dillon.starsectormarines.ops.intel;

import com.dillon.starsectormarines.ops.MissionType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema-validation coverage for the industry-mission content loader.
 * The real {@code loadOrFallback()} path needs SettingsAPI; these tests
 * target the pure {@link IndustryMissionTemplates#parse(JSONObject)}
 * step which has no I/O.
 */
public class IndustryMissionTemplatesTest {

    @Test
    public void validJsonRoundTripsAllEntries() throws Exception {
        JSONObject root = buildValidRoot();
        Map<String, List<MissionArchetype>> parsed = IndustryMissionTemplates.parse(root);

        assertEquals(2, parsed.size());
        List<MissionArchetype> heavy = parsed.get("heavyindustry");
        assertNotNull(heavy);
        assertEquals(2, heavy.size());
        assertEquals(MissionType.SABOTAGE, heavy.get(0).type);
        assertEquals("Cripple the Foundry", heavy.get(0).name);
        assertTrue(heavy.get(0).flavor.startsWith("Target floor"));
        assertEquals(MissionType.RAID, heavy.get(1).type);

        List<MissionArchetype> mining = parsed.get("mining");
        assertNotNull(mining);
        assertEquals(1, mining.size());
        assertEquals(MissionType.RAID, mining.get(0).type);
    }

    @Test
    public void nullRootIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(null));
        assertTrue(ex.getMessage().contains("null root"));
    }

    @Test
    public void missingMissionsObjectIsRejected() {
        JSONObject root = new JSONObject();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains("missions"));
    }

    @Test
    public void emptyArchetypeArrayIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("mining", new JSONArray());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains("mining"));
        assertTrue(ex.getMessage().contains("zero archetypes"));
    }

    @Test
    public void missingTypeFieldIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONObject bad = new JSONObject();
        bad.put(IndustryMissionTemplates.FIELD_NAME, "Some Name");
        bad.put(IndustryMissionTemplates.FIELD_FLAVOR, "Some flavor text.");
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("mining", new JSONArray().put(bad));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains("mining"));
        assertTrue(ex.getMessage().contains(IndustryMissionTemplates.FIELD_TYPE),
                "Error should name the missing field, got: " + ex.getMessage());
    }

    @Test
    public void missingNameFieldIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONObject bad = new JSONObject();
        bad.put(IndustryMissionTemplates.FIELD_TYPE, "RAID");
        bad.put(IndustryMissionTemplates.FIELD_FLAVOR, "Some flavor text.");
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("mining", new JSONArray().put(bad));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains(IndustryMissionTemplates.FIELD_NAME));
    }

    @Test
    public void missingFlavorFieldIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONObject bad = new JSONObject();
        bad.put(IndustryMissionTemplates.FIELD_TYPE, "RAID");
        bad.put(IndustryMissionTemplates.FIELD_NAME, "Some Name");
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("mining", new JSONArray().put(bad));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains(IndustryMissionTemplates.FIELD_FLAVOR));
    }

    @Test
    public void unknownMissionTypeIsRejectedLoudly() throws Exception {
        JSONObject root = buildValidRoot();
        JSONObject bad = new JSONObject();
        bad.put(IndustryMissionTemplates.FIELD_TYPE, "INVESTIGATE"); // not a MissionType
        bad.put(IndustryMissionTemplates.FIELD_NAME, "Probe the Site");
        bad.put(IndustryMissionTemplates.FIELD_FLAVOR, "Some flavor text.");
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("mining", new JSONArray().put(bad));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains("INVESTIGATE"),
                "Error should quote the bad type, got: " + ex.getMessage());
    }

    @Test
    public void emptyStringFieldIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONObject bad = new JSONObject();
        bad.put(IndustryMissionTemplates.FIELD_TYPE, "RAID");
        bad.put(IndustryMissionTemplates.FIELD_NAME, "");
        bad.put(IndustryMissionTemplates.FIELD_FLAVOR, "Some flavor text.");
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("mining", new JSONArray().put(bad));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> IndustryMissionTemplates.parse(root));
        assertTrue(ex.getMessage().contains(IndustryMissionTemplates.FIELD_NAME));
    }

    @Test
    public void unknownIndustryIdsAreKeptNotRejected() throws Exception {
        // Mods can ship industries this mod doesn't recognize; the parser
        // should accept their entries and the runtime catalog returns an
        // empty pool for any industryId not present. We just verify the
        // unknown id survives the parse step.
        JSONObject root = buildValidRoot();
        JSONObject ok = new JSONObject();
        ok.put(IndustryMissionTemplates.FIELD_TYPE, "RAID");
        ok.put(IndustryMissionTemplates.FIELD_NAME, "Strike the Hangar");
        ok.put(IndustryMissionTemplates.FIELD_FLAVOR, "Strike fast.");
        root.getJSONObject(IndustryMissionTemplates.MISSIONS_KEY)
                .put("someothermod_industry", new JSONArray().put(ok));
        Map<String, List<MissionArchetype>> parsed = IndustryMissionTemplates.parse(root);
        assertNotNull(parsed.get("someothermod_industry"));
        assertEquals(1, parsed.get("someothermod_industry").size());
    }

    /** Build a minimal valid JSON root with two industries, four archetypes total. */
    private static JSONObject buildValidRoot() throws Exception {
        JSONObject root = new JSONObject();
        JSONObject missions = new JSONObject();

        JSONObject heavy1 = new JSONObject();
        heavy1.put(IndustryMissionTemplates.FIELD_TYPE,   "SABOTAGE");
        heavy1.put(IndustryMissionTemplates.FIELD_NAME,   "Cripple the Foundry");
        heavy1.put(IndustryMissionTemplates.FIELD_FLAVOR, "Target floor is networked.");
        JSONObject heavy2 = new JSONObject();
        heavy2.put(IndustryMissionTemplates.FIELD_TYPE,   "RAID");
        heavy2.put(IndustryMissionTemplates.FIELD_NAME,   "Loot the Production Floor");
        heavy2.put(IndustryMissionTemplates.FIELD_FLAVOR, "Smash-and-grab.");
        missions.put("heavyindustry", new JSONArray().put(heavy1).put(heavy2));

        JSONObject mining1 = new JSONObject();
        mining1.put(IndustryMissionTemplates.FIELD_TYPE,   "RAID");
        mining1.put(IndustryMissionTemplates.FIELD_NAME,   "Strip the Mining Camp");
        mining1.put(IndustryMissionTemplates.FIELD_FLAVOR, "Outpost workforce is unarmed.");
        missions.put("mining", new JSONArray().put(mining1));

        root.put(IndustryMissionTemplates.MISSIONS_KEY, missions);
        return root;
    }
}
