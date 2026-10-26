package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BriefingFieldSquadsTest {

    @Test
    void pastNineSquadsTheWholeOnesStackAndTheDamagedStandAlone() {
        List<BriefingViewModel.SquadPicture> squads = squads(10);
        squads.set(3, squad("short", 10));
        squads.set(7, squad("hurt", 11));
        List<BriefingViewModel.SquadPicture> tiles = BriefingViewModel.stackSquads(squads);
        assertEquals(List.of(8, 1, 1), tiles.stream().map(BriefingViewModel.SquadPicture::count).toList());
        assertEquals(List.of("short", "hurt"), tiles.subList(1, 3).stream().map(BriefingViewModel.SquadPicture::id).toList());
    }

    @Test
    void nineSquadsOrALoneWholeOneStayAsTheyAre() {
        assertEquals(9, BriefingViewModel.stackSquads(squads(9)).size());
        List<BriefingViewModel.SquadPicture> mostlyHurt = squads(10);
        for (int i = 1; i < 10; i++) mostlyHurt.set(i, squad("hurt-" + i, 11));
        assertEquals(10, BriefingViewModel.stackSquads(mostlyHurt).size());
    }

    private static List<BriefingViewModel.SquadPicture> squads(int count) {
        List<BriefingViewModel.SquadPicture> squads = new ArrayList<>();
        for (int i = 0; i < count; i++) squads.add(squad("whole-" + i, MarineSquad.CAPACITY));
        return squads;
    }

    private static BriefingViewModel.SquadPicture squad(String id, int ready) {
        return new BriefingViewModel.SquadPicture(id, BriefingViewModel.previewMuster(id, id, ready), null);
    }
}
