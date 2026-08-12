package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.ChainAdvancementSystem;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimantFactionDataTest {

    @Test
    void predeclaredFactionMatchesCivilWarResultIdentity() throws Exception {
        Path specPath = Path.of("mod", "data", "world", "factions",
                "starsector_marines_claimants.faction");
        JSONObject spec = new JSONObject(Files.readString(specPath,
                StandardCharsets.UTF_8));
        String csv = Files.readString(Path.of("mod", "data", "world", "factions",
                "factions.csv"), StandardCharsets.UTF_8);

        assertEquals(ChainAdvancementSystem.CLAIMANT_FACTION_ID,
                spec.getString("id"));
        assertTrue(csv.contains("data/world/factions/"
                + ChainAdvancementSystem.CLAIMANT_FACTION_ID + ".faction"));
        assertTrue(spec.getBoolean("showInIntelTab"));
        assertTrue(spec.getJSONObject("knownShips").getJSONArray("tags").length() > 0);
    }
}
