package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartObjectivesTest {

    @Test
    void aBeatIsRolledOnlyWhereItsPlaceWasLaid() {
        LaidLayout onlyHoldout = new LaidLayout(List.of(new Laid(HeartLayout.HOLDOUT, Laid.Kind.BLOCK,
                Laid.Status.LAID, "", false, List.of(new int[]{10, 10}), List.of(), List.of())));
        assertEquals(Set.of(HeartObjectives.LOST_COLUMN, HeartObjectives.PLUG, HeartObjectives.MATRIARCH),
                keys(ObjectiveRoll.pick(HeartObjectives.pool(Form.BROAD_FRONT), onlyHoldout, 7L, 9, 9)));
        assertEquals(Set.of(HeartObjectives.CLEAR, HeartObjectives.LAST),
                keys(ObjectiveRoll.pick(HeartObjectives.pool(Form.THE_BURN), onlyHoldout, 7L, 9, 9)));
    }

    private static Set<String> keys(List<OptionalObjective> rolled) {
        Set<String> out = new HashSet<>();
        for (OptionalObjective o : rolled) out.add(o.key());
        return out;
    }
}
