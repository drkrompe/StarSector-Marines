package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tactics layer's pure pieces: travel along the nose, an escort's interposed station, and fighter exemption. */
class WarshipTacticsTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void aShipOnAManoeuvreTravelsAlongItsNose() throws Exception {
        // Facing 0: the nose is along +y.
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        float v = wolf.maxSpeed;
        float[] ahead = WarshipTactics.alongNose(wolf, 0f, v);
        assertEquals(v, ahead[1], 1e-4f, "ahead at full speed");
        float[] across = WarshipTactics.alongNose(wolf, v, 0f);
        assertEquals(WarshipTactics.LATERAL_SHARE * v, Math.abs(across[0]), 1e-4f, "sideways only a share");
        float[] astern = WarshipTactics.alongNose(wolf, 0f, -v);
        assertEquals(-WarshipTactics.ASTERN_SHARE * v, astern[1], 1e-4f, "astern only a share");
    }

    @Test
    void anEscortInterposesBetweenItsChargeAndTheThreat() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 100f, 100f, 0f);
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 100f, 60f, 0f);
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 300f, 100f, 90f);
        float[] at = WarshipTactics.interpose(hound, venture, wolf, List.of(hound));
        float cx = at[0] + (hound.centerX() - hound.x);
        float cy = at[1] + (hound.centerY() - hound.y);
        // Due east of the Venture, toward the Wolf, clear of the Venture's beam.
        assertTrue(cx > venture.centerX() + 0.5f * venture.widthCells, "on the threat side, beyond the charge's beam");
        assertTrue(cx < wolf.centerX(), "short of the threat");
        float lx = wolf.centerX() - venture.centerX();
        float ly = wolf.centerY() - venture.centerY();
        float cross = (cx - venture.centerX()) * ly - (cy - venture.centerY()) * lx;
        assertEquals(0f, cross / (float) Math.hypot(lx, ly), 1e-2f, "on the line between them");
    }

    @Test
    void fighterSizedHullsTakeNoPartInContact() throws Exception {
        Warship broadsword = Warship.fit("broadsword", "broadsword_Fighter", Faction.ALLY, 0f, 0f, 0f);
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 0f, 0f, 0f);
        assertTrue(broadsword.fighterSize());
        assertFalse(wolf.fighterSize());
        assertFalse(WarshipSpacing.inContact(broadsword));
    }
}
