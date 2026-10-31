package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.mech.MechRefit;
import com.dillon.starsectormarines.marine.MechFieldRepair;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The Mech Lab's tracks: what each tier is worth to a company mech, and what it needs first. */
class MechLabRefitTest {
    private static final List<RefitTrack> MECH_TRACKS = List.of(RefitTrack.MECH_CALIBRATION,
            RefitTrack.MECH_COOLANT, RefitTrack.MECH_ARMOR, RefitTrack.MECH_REPAIR);

    @Test void nothingInTheMechLabStartsAtADeficit() {
        assertEquals(MechRefit.NEUTRAL, ShipCondition.NONE.mechRefit());
        assertEquals(MechRefit.NEUTRAL, ShipCondition.battered().mechRefit());
        assertEquals(MechFieldRepair.NEUTRAL, ShipCondition.battered().mechFieldRepair());
        var restored = ShipCondition.battered();
        for (RefitTrack track : MECH_TRACKS) restored = restored.with(track, RefitBand.RESTORED);
        assertEquals(MechRefit.NEUTRAL, restored.mechRefit(), "Restored is neutral");
        assertEquals(MechFieldRepair.NEUTRAL, restored.mechFieldRepair());
    }

    @Test void eachImprovedTierNamesItsFigure() {
        var start = ShipCondition.battered();
        assertEquals(new MechRefit(1.03f, 1f, 1f, 1f), start.with(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_I).mechRefit());
        assertEquals(new MechRefit(1.06f, 1f, 1f, 1.25f), start.with(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_II).mechRefit(),
                "the tactical core link adds the faster traverse");
        assertEquals(1.10f, start.with(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_I).mechRefit().dissipationMult());
        assertEquals(1.20f, start.with(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_II).mechRefit().dissipationMult());
        assertEquals(1.05f, start.with(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_I).mechRefit().armorMult());
        assertEquals(1.10f, start.with(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_II).mechRefit().armorMult());
        assertEquals(new MechFieldRepair(0.75f, 1f), start.with(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_I).mechFieldRepair());
        assertEquals(new MechFieldRepair(0.75f, 0.75f), start.with(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_II).mechFieldRepair(),
                "the spares rack keeps the faster repairs and cheapens a lost mount");
    }

    @Test void everyMechLabTierNeedsTwoCoresAndTheTopTierThree() {
        for (RefitOrder order : RefitOrder.values()) {
            if (!MECH_TRACKS.contains(order.track)) continue;
            assertEquals(order.band == RefitBand.IMPROVED_II ? 3 : 2, order.requires.size(), order.name());
            for (RefitTrack need : order.requires.keySet()) assertNotNull(need.core, order + " needs " + need);
            boolean aiCore = order.cores().keySet().stream().anyMatch(id -> id.endsWith("_core"));
            assertFalse(aiCore && order.band != RefitBand.IMPROVED_II, order + " takes an AI core below its top tier");
        }
        for (RefitTrack track : MECH_TRACKS) assertEquals(RefitBand.IMPROVED_II, RefitOrder.ceiling(track), track.name());
    }

    @Test void theSheetLineNamesOnlyWhatIsImproved() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> text)) {
            assertEquals("", RefitText.mechLine(ShipCondition.battered()));
            var improved = ShipCondition.battered().with(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_I)
                    .with(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_II).with(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_I);
            String line = RefitText.mechLine(improved);
            assertTrue(line.contains("aim +3%") && line.contains("plate +10%") && line.contains("repair time -25%"), line);
            assertFalse(line.contains("vents") || line.contains("traverse"), line);
            assertTrue(RefitText.briefingLine(improved).contains(line), "the briefing carries the same line");
            for (RefitTrack track : MECH_TRACKS) assertFalse(RefitText.lore(track).isBlank(), track.name());
        }
    }
}
