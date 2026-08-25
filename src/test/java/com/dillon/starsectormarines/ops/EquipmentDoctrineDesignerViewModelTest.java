package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
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

        Set<MarineSecondary> seen = cycleSpecials(designer, 12);
        assertFalse(seen.contains(MarineSecondary.FRAG_GRENADE));

        roster.armory().unlockSecondary(MarineSecondary.FRAG_GRENADE);
        seen = cycleSpecials(designer, 12);
        assertTrue(seen.contains(MarineSecondary.FRAG_GRENADE));
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
        assertEquals(List.of("POOL", "RATING", "MOVE", "EVA"), armor.stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::label).toList());
        assertTrue(armor.flavor().contains("Patchwork protection"));
    }

    private static EquipmentDoctrineDesignerViewModel designer(MarineRoster roster) {
        return new EquipmentDoctrineDesignerViewModel(
                new Reactor(), roster, roster.squads().get(0).id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
    }

    private static Set<MarineSecondary> cycleSpecials(
            EquipmentDoctrineDesignerViewModel designer, int count) {
        Set<MarineSecondary> seen = new HashSet<>();
        for (int index = 0; index < count; index++) {
            designer.billets().get().get(0).cycleSpecial().run();
            seen.add(designer.viewerBilletAt(0).secondary());
        }
        return seen;
    }
}
