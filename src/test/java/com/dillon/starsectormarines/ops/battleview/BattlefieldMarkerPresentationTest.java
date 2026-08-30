package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlefieldMarkerPresentationTest {

    @Test
    void captureIdentityMatchesKindOrdinalAndOnlyContestGetsProgressEmphasis() {
        BattlefieldMarkerPresentation.ObjectiveMarker hostile =
                BattlefieldMarkerPresentation.capture(TacticalNode.Kind.ARMORY, 2,
                        CompoundService.CompoundState.DEFENDER_HELD, 0.8f);
        BattlefieldMarkerPresentation.ObjectiveMarker contested =
                BattlefieldMarkerPresentation.capture(TacticalNode.Kind.BARRACKS, 1,
                        CompoundService.CompoundState.CONTESTED, 0.624f);
        BattlefieldMarkerPresentation.ObjectiveMarker secured =
                BattlefieldMarkerPresentation.capture(TacticalNode.Kind.COMMAND_POST, 1,
                        CompoundService.CompoundState.MARINE_HELD, 0.4f);

        assertEquals("A2", hostile.code());
        assertEquals(0f, hostile.progress());
        assertFalse(hostile.emphasized());
        assertEquals("B1", contested.code());
        assertEquals("62%", contested.status());
        assertEquals(0.624f, contested.progress());
        assertTrue(contested.emphasized());
        assertEquals("C1", secured.code());
        assertEquals("SECURED", secured.status());
        assertFalse(secured.emphasized());
    }

    @Test
    void sabotageCodeComesFromStableSiteIdentityAndOnlyLivePlantPulses() {
        BattlefieldMarkerPresentation.ObjectiveMarker quiet =
                BattlefieldMarkerPresentation.sabotage("SAB-03", false,
                        false, 0f);
        BattlefieldMarkerPresentation.ObjectiveMarker planting =
                BattlefieldMarkerPresentation.sabotage("SAB-02", false,
                        true, 0.7f);
        BattlefieldMarkerPresentation.ObjectiveMarker complete =
                BattlefieldMarkerPresentation.sabotage("SAB-01", true,
                        false, 1f);

        assertEquals("S3", quiet.code());
        assertEquals("S2", planting.code());
        assertEquals("70%", planting.status());
        assertTrue(planting.emphasized());
        assertEquals("S1", complete.code());
        assertEquals("ARMED", complete.status());
        assertFalse(complete.emphasized());
    }

    @Test
    void targetPreviewKeepsPowerFootprintAndMakesValidityExplicit() {
        BattlefieldMarkerPresentation.TargetMarker valid =
                BattlefieldMarkerPresentation.target("Orbital Barrage", true, 4f);
        BattlefieldMarkerPresentation.TargetMarker invalid =
                BattlefieldMarkerPresentation.target("Marine Drop", false, 1.5f);

        assertEquals("ORBITAL BARRAGE", valid.label());
        assertEquals("VALID", valid.status());
        assertEquals(4f, valid.radiusCells());
        assertSame(BattlefieldMarkerPresentation.VALID, valid.tone());
        assertEquals("BLOCKED", invalid.status());
        assertSame(BattlefieldMarkerPresentation.INVALID, invalid.tone());
    }

    @Test
    void objectiveRadiusIsPixelClampedAcrossBattleZooms() {
        assertEquals(11f, BattlefieldMarkerPresentation.objectiveRadius(4f));
        assertEquals(15.6f, BattlefieldMarkerPresentation.objectiveRadius(20f), 0.001f);
        assertEquals(20f, BattlefieldMarkerPresentation.objectiveRadius(80f));
        assertEquals(18f, BattlefieldMarkerPresentation.targetRadius(4f, 0f));
        assertEquals(48f, BattlefieldMarkerPresentation.targetRadius(12f, 4f));
    }
}
