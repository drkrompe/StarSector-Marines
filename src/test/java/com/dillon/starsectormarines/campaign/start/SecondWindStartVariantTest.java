package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.nexerelin.SecondWindNexStart;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Second Wind starts as a stripped wreck: a Cerberus (D), its d-mods, and only two Vulcans, on both start paths. */
class SecondWindStartVariantTest {

    private static final Path VARIANT = Path.of("mod/data/variants/" + CerberusCompanyStart.START_VARIANT_ID + ".variant");
    /** Vanilla's damage hullmods, tagged {@code dmod} in its hull_mods.csv. */
    private static final Set<String> VANILLA_DMODS = Set.of("comp_armor", "comp_hull", "degraded_engines",
            "degraded_drive_field", "faulty_grid", "comp_structure", "glitched_sensors", "malfunctioning_comms",
            "fragile_subsystems", "comp_storage", "increased_maintenance", "erratic_injector",
            "damaged_mounts", "degraded_life_support", "degraded_shields", "destroyed_mounts", "ill_advised");

    @Test
    void theStartShipIsADamagedCerberusWithOnlyTwoVulcans() throws Exception {
        JSONObject variant = new JSONObject(Files.readString(VARIANT));
        assertEquals(CerberusCompanyStart.START_VARIANT_ID, variant.getString("variantId"));
        assertEquals("cerberus_d", variant.getString("hullId"), "the (D) skin: damaged sprite, armour and power grid");

        List<String> weapons = new ArrayList<>();
        JSONArray groups = variant.getJSONArray("weaponGroups");
        for (int g = 0; g < groups.length(); g++) {
            JSONObject mounted = groups.getJSONObject(g).getJSONObject("weapons");
            JSONArray slots = mounted.names();
            for (int s = 0; s < slots.length(); s++) weapons.add(mounted.getString(slots.getString(s)));
        }
        assertEquals(List.of("vulcan", "vulcan"), weapons);

        JSONArray perma = variant.getJSONArray("permaMods");
        assertEquals(3, perma.length(), "with the skin's two built-ins, the five d-mods of a wreck");
        for (int i = 0; i < perma.length(); i++)
            assertTrue(VANILLA_DMODS.contains(perma.getString(i)), perma.getString(i) + " is a vanilla d-mod");
    }

    @Test
    void bothStartPathsHandOutTheSameShip() throws Exception {
        assertEquals(CerberusCompanyStart.START_VARIANT_ID, SecondWindNexStart.VARIANT_ID);
        assertTrue(Files.readString(Path.of("mod/data/campaign/rules.csv"))
                .contains("NGCAddShip " + CerberusCompanyStart.START_VARIANT_ID));
    }
}
