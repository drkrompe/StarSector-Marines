package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pick of what each redoubt and the base are built from ({@link CordonConstruction}). */
class CordonConstructionTest {

    @Test
    void openCountryIsAlwaysTheCompoundAndTheShed() {
        for (long seed = 0; seed < 50; seed++) {
            for (int i = 0; i < CordonPlan.REDOUBTS; i++) {
                assertEquals(CordonConstruction.COMPOUND, CordonConstruction.redoubt(CordonPlan.Density.RURAL, i, 1, seed));
            }
            assertEquals(CordonConstruction.SHED, CordonConstruction.base(CordonPlan.Density.RURAL, seed));
        }
    }

    @Test
    void eachDensityPicksOnlyItsOwnChoicesAndEveryOneOfThem() {
        for (CordonPlan.Density density : List.of(CordonPlan.Density.TOWN, CordonPlan.Density.URBAN)) {
            Set<CordonConstruction> seen = EnumSet.noneOf(CordonConstruction.class);
            for (long seed = 0; seed < 200; seed++) {
                for (int i = 0; i < CordonPlan.REDOUBTS; i++) seen.add(CordonConstruction.redoubt(density, i, 0, seed));
            }
            assertEquals(Set.copyOf(CordonConstruction.redoubtChoices(density)), seen, density.name());
        }
        for (CordonPlan.Density density : CordonPlan.Density.values()) {
            Set<CordonConstruction> bases = EnumSet.noneOf(CordonConstruction.class);
            for (long seed = 0; seed < 200; seed++) bases.add(CordonConstruction.base(density, seed));
            assertEquals(Set.copyOf(CordonConstruction.baseChoices(density)), bases, density.name());
        }
    }

    @Test
    void thePickIsAPureFunctionOfDensityPlaceAndSeed() {
        for (long seed = 0; seed < 40; seed++) {
            for (int i = 0; i < CordonPlan.REDOUBTS; i++) {
                assertEquals(CordonConstruction.redoubt(CordonPlan.Density.URBAN, i, 2, seed),
                        CordonConstruction.redoubt(CordonPlan.Density.URBAN, i, 2, seed));
            }
        }
    }

    @Test
    void aLineMixesItsKindsOnMostMaps() {
        int mixed = 0, maps = 200;
        for (long seed = 0; seed < maps; seed++) {
            Set<CordonConstruction> line = EnumSet.noneOf(CordonConstruction.class);
            for (int i = 0; i < CordonPlan.REDOUBTS; i++) line.add(CordonConstruction.redoubt(CordonPlan.Density.TOWN, i, 1, seed));
            if (line.size() > 1) mixed++;
        }
        assertTrue(mixed > maps * 8 / 10, mixed + " of " + maps + " town lines mixed");
    }

    @Test
    void theRedoubtNearestTheBaseIsTheCompoundMoreOften() {
        int nearest = 0, other = 0, maps = 3000;
        for (long seed = 0; seed < maps; seed++) {
            if (CordonConstruction.redoubt(CordonPlan.Density.URBAN, 1, 1, seed) == CordonConstruction.COMPOUND) nearest++;
            if (CordonConstruction.redoubt(CordonPlan.Density.URBAN, 2, 1, seed) == CordonConstruction.COMPOUND) other++;
        }
        // Two in four against one in three.
        assertTrue(nearest > maps * 0.45 && nearest < maps * 0.55, "nearest the base: " + nearest);
        assertTrue(other > maps * 0.28 && other < maps * 0.38, "elsewhere: " + other);
    }

    @Test
    void theLayoutRecordIsReadBackAndAbsenceMeansTheGeneratedWork() {
        LaidLayout layout = new LaidLayout(List.of(
                CordonConstructions.record(CordonConstructions.WORKS + 2, CordonConstruction.TENEMENT,
                        new int[]{10, 20, 40, 45}, List.of(new int[]{12, 22})),
                CordonConstructions.record(CordonConstructions.BASE_WORKS, CordonConstruction.CAR_PARK,
                        new int[]{100, 100, 150, 150}, List.of())));
        assertEquals(CordonConstruction.TENEMENT, CordonConstructions.redoubt(layout, 2));
        assertEquals(CordonConstruction.COMPOUND, CordonConstructions.redoubt(layout, 1));
        assertEquals(CordonConstruction.CAR_PARK, CordonConstructions.base(layout));
        assertEquals(CordonConstruction.SHED, CordonConstructions.base(LaidLayout.EMPTY));
        Laid tenement = layout.find(CordonConstructions.WORKS + 2);
        assertEquals(Laid.Kind.STRUCTURE, tenement.kind());
        assertEquals(1, tenement.nests().size());
        assertEquals(40, tenement.cells().get(1)[0]);
    }
}
