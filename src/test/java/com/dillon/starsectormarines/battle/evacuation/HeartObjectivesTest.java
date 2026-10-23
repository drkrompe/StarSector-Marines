package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartObjectivesTest {

    @Test
    void aBeatIsRolledOnlyWhereItsPlaceWasLaid() {
        LaidLayout onlyHoldout = new LaidLayout(List.of(new Laid(HeartLayout.HOLDOUT, Laid.Kind.BLOCK,
                Laid.Status.LAID, "", false, List.of(new int[]{10, 10}), List.of(), List.of())));
        List<OptionalObjective> rolled = ObjectiveRoll.pick(HeartObjectives.pool(Form.BROAD_FRONT), onlyHoldout, 7L, 1, 3);
        assertEquals(1, rolled.size());
        assertEquals(HeartObjectives.LOST_COLUMN, rolled.get(0).key());
        assertEquals(0, ObjectiveRoll.pick(HeartObjectives.pool(Form.THE_BURN), onlyHoldout, 7L, 1, 3).size());
    }
}
