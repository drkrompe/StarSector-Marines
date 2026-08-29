package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentDoctrineDesignerViewModelTest {

    @Test
    void specialPickerCyclesOnlyCollectedTemplateCards() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel designer = designer(roster);

        Set<SpecialEquipmentDef> seen = cycleSpecials(designer, 12);
        assertFalse(seen.contains(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));

        roster.armory().unlockSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID));
        seen = cycleSpecials(designer, 12);
        assertTrue(seen.contains(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
    }

    @Test
    void primaryGradeAndArmorPickersSkipUncollectedCards() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel designer = designer(roster);

        Set<EquipmentGrade> grades = new HashSet<>();
        for (int index = 0; index < 8; index++) {
            designer.billets().get().get(0).cycleGrade().run();
            grades.add(designer.viewerBilletAt(0).grade());
        }
        assertFalse(grades.contains(EquipmentGrade.MILSPEC));
        assertFalse(grades.contains(EquipmentGrade.MASTERWORK));

        designer.showArmor().run();
        Set<MarineArmorPattern> armor = new HashSet<>();
        for (int index = 0; index < 10; index++) {
            designer.billets().get().get(0).cyclePrimary().run();
            armor.add(designer.viewerBilletAt(0).armor());
        }
        assertTrue(armor.contains(MarineArmorPattern.CHARCOAL));
        assertTrue(armor.contains(MarineArmorPattern.ARMY_GREEN));
        assertFalse(armor.contains(MarineArmorPattern.BLUE_SCOUT));
        assertFalse(armor.contains(MarineArmorPattern.RED_ELITE));
    }

    @Test
    void billetCardsExposeStableComparativeMetersThatReactToDraftChanges() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel viewModel = designer(roster);

        EquipmentDoctrineDesignerViewModel.BilletCard weapon =
                viewModel.billets().get().get(0);
        assertEquals(List.of("DMG", "RNG", "ACC", "DPS"), weapon.stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::label).toList());
        assertTrue(weapon.flavor().contains("Fire-team lead"));
        List<String> before = weapon.stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::fillStyle).toList();

        weapon.cyclePrimary().run();

        List<String> after = viewModel.billets().get().get(0).stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::fillStyle).toList();
        assertNotEquals(before, after);

        viewModel.showArmor().run();
        EquipmentDoctrineDesignerViewModel.BilletCard armor =
                viewModel.billets().get().get(0);
        assertEquals(List.of("ARMOR", "RESIST", "MOVE", "EVA"), armor.stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::label).toList());
        // The patchwork doctrine's first billet wears a ward vest, and a ward
        // vest carries a brace now. A card leads with the capability whenever
        // there is one; the descriptive prose is what a pattern with nothing to
        // say falls back to.
        assertTrue(armor.flavor().contains(IntegralSystemEffect.BRACE.displayName),
                "the issued pattern carries a brace, so its card says so: " + armor.flavor());
    }

    private static EquipmentDoctrineDesignerViewModel designer(MarineRoster roster) {
        return new EquipmentDoctrineDesignerViewModel(
                new Reactor(), roster, roster.squads().get(0).id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
    }

    private static Set<SpecialEquipmentDef> cycleSpecials(
            EquipmentDoctrineDesignerViewModel designer, int count) {
        Set<SpecialEquipmentDef> seen = new HashSet<>();
        for (int index = 0; index < count; index++) {
            designer.billets().get().get(0).cycleSpecial().run();
            seen.add(designer.viewerBilletAt(0).specialDef());
        }
        return seen;
    }
}
