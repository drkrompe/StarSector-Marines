package com.dillon.starsectormarines.battle.modifier;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitModifiersTest {

    /** Applies to unit 1 only, adding {@code flat} and multiplying by {@code multiplier} on its stats. */
    private record Fixed(String name, Set<UnitStat> stats, float flat, float multiplier) implements UnitModifier {
        @Override public String name(long unit) { return name; }
        @Override public boolean appliesTo(long unit) { return unit == 1L; }
        @Override public float flat(long unit, UnitStat stat) { return flat; }
        @Override public float multiplier(long unit, UnitStat stat) { return multiplier; }
    }

    @Test
    void flatsAddBeforeMultipliersAndOnlyForTheStatsDeclared() {
        UnitModifiers modifiers = new UnitModifiers();
        modifiers.register(new Fixed("reach", EnumSet.of(UnitStat.ATTACK_RANGE), 0.5f, 1f));
        modifiers.register(new Fixed("steady", EnumSet.of(UnitStat.ATTACK_RANGE, UnitStat.ACCURACY), 0f, 2f));

        assertEquals((10f + 0.5f) * 2f, modifiers.resolve(1L, UnitStat.ATTACK_RANGE, 10f), 1e-6f);
        assertEquals(0.4f * 2f, modifiers.resolve(1L, UnitStat.ACCURACY, 0.4f), 1e-6f);
        assertEquals(3f, modifiers.resolve(1L, UnitStat.MOVE_SPEED, 3f), 1e-6f, "an undeclared stat is untouched");
        assertEquals(10f, modifiers.resolve(2L, UnitStat.ATTACK_RANGE, 10f), 1e-6f, "nor a unit none applies to");
    }

    @Test
    void explainNamesWhatAppliesAndNothingElse() {
        UnitModifiers modifiers = new UnitModifiers();
        modifiers.register(new Fixed("reach", EnumSet.of(UnitStat.ATTACK_RANGE), 0.5f, 1f));
        modifiers.register(new Fixed("steady", EnumSet.of(UnitStat.ACCURACY), 0f, 2f));

        List<UnitModifiers.Applied> range = modifiers.explain(1L, UnitStat.ATTACK_RANGE);
        assertEquals(List.of(new UnitModifiers.Applied("reach", 0.5f, 1f)), range);
        assertTrue(modifiers.explain(2L, UnitStat.ATTACK_RANGE).isEmpty());
    }
}
