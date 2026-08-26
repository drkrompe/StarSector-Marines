package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarracksViewModelTest {

    @Test
    void selectionBrowsesSquadsAndWoundedPersonnelLeaveTheRoomButKeepTheirBillet() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY * 2);
        BarracksViewModel viewModel = new BarracksViewModel(new Reactor(), roster, () -> 100d);

        assertEquals(2, viewModel.squadRows().get().size());
        assertEquals(MarineSquad.CAPACITY, viewModel.sceneMarines().size());

        MarineSquad selected = roster.squads().stream()
                .filter(squad -> !squad.reserve()).findFirst().orElseThrow();
        roster.applySoldierOutcome(Map.of(selected.memberIds().get(0),
                MarineSoldierStatus.WIA), 0, 100f, 1.25f);
        viewModel.refresh();

        assertEquals(MarineSquad.CAPACITY - 1, viewModel.sceneMarines().size());
        assertEquals(MarineSquad.CAPACITY, viewModel.musterRows().get().size());
        assertTrue(viewModel.musterRows().get().get(0).classes().contains("wounded"));
        assertTrue(viewModel.quartersStatus().get().contains("1 in recovery"));

        viewModel.squadRows().get().get(1).select().run();
        assertTrue(viewModel.selectedSquadName().get().contains("02"));
        assertEquals(MarineSquad.CAPACITY, viewModel.sceneMarines().size());
    }
}
