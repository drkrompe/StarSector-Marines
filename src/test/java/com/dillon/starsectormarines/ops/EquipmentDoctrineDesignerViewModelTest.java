package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
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
    void primaryAndGradePickersSkipUncollectedCards() {
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

    }

    /**
     * <b>The designer authors weapons and nothing else.</b> Armour used to be a
     * second page here, twelve concrete patterns picked a billet at a time and
     * frozen at the moment of saving — which is the artifact the plan model
     * exists to remove, and which no longer has a way in
     * ({@code role-and-access.md}). The marine is still drawn wearing something,
     * and the honest something is what the squad's assigned sheet issues.
     */
    @Test
    void theDesignerDrawsTheArmourTheAssignedSheetIssuesAndCannotChangeIt() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        EquipmentDoctrineDesignerViewModel designer = designer(roster);

        String sheetId = squad.armorDoctrineId() != null
                ? squad.armorDoctrineId() : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR;
        SquadArmorDoctrine issued = roster.armory().armorDoctrineById(sheetId);
        assertEquals(issued.issueIds().get(0), designer.viewerBilletAt(0).armorId(),
                "the preview wears what the squad's own sheet issues");

        String before = designer.viewerBilletAt(0).armorId();
        for (EquipmentDoctrineDesignerViewModel.BilletCard card : designer.billets().get()) {
            card.cycleRole().run();
            card.cyclePrimary().run();
            card.cycleGrade().run();
            card.cycleSpecial().run();
        }
        assertEquals(before, designer.viewerBilletAt(0).armorId(),
                "no control on this screen may change what a billet wears");
    }

    private static EquipmentDoctrineDesignerViewModel designer(MarineRoster roster) {
        return new EquipmentDoctrineDesignerViewModel(
                new Reactor(), roster, roster.squads().get(0).id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
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
