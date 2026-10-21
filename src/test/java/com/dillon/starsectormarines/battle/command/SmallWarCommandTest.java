package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.setup.SmallWarForces;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.Group;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallWarCommandTest {

    /** Only the groups whose posts stand well clear of customs, fuel control and dispatch are offered to the planner. */
    @Test void onlyPostsClearOfTheStagesAreOffered() {
        List<String> offered = SmallWarForces.GROUPS.stream().filter(g -> !g.reserve())
                .filter(g -> SmallWarCommand.clearOfStages(g.x(), g.y())).map(Group::id).toList();
        assertEquals(List.of("service-picket", "prize-crew-pier"), offered);
    }
}
