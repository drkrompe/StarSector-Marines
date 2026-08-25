package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentDoctrineDesignerViewModelTest {

    @Test
    void billetCardsExposeStableComparativeMetersThatReactToDraftChanges() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel viewModel = new EquipmentDoctrineDesignerViewModel(
                new Reactor(), roster, roster.squads().get(0).id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);

        EquipmentDoctrineDesignerViewModel.BilletCard weapon = viewModel.billets().get().get(0);
        assertEquals(List.of("DMG", "RNG", "ACC", "DPS"),
                weapon.stats().stream().map(EquipmentDoctrineDesignerViewModel.StatMeter::label).toList());
        assertTrue(weapon.flavor().contains("Fire-team lead"));
        List<String> before = weapon.stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::fillStyle).toList();

        weapon.cyclePrimary().run();

        List<String> after = viewModel.billets().get().get(0).stats().stream()
                .map(EquipmentDoctrineDesignerViewModel.StatMeter::fillStyle).toList();
        assertNotEquals(before, after);

        viewModel.showArmor().run();
        EquipmentDoctrineDesignerViewModel.BilletCard armor = viewModel.billets().get().get(0);
        assertEquals(List.of("POOL", "RATING", "MOVE", "EVA"),
                armor.stats().stream().map(EquipmentDoctrineDesignerViewModel.StatMeter::label).toList());
        assertTrue(armor.flavor().contains("Patchwork protection"));
    }
}
