package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a treatment gives off at the patient, as plain numbers. */
public class TreatmentParticlesTest {

    private record P(TreatmentParticles.Kind kind, float dx, float dy, float size, float alpha) { }

    private static List<P> emit(float elapsed, boolean health, boolean armor) {
        List<P> out = new ArrayList<>();
        TreatmentParticles.emit(elapsed, 42L, health, armor, 1f, 0f, 1f, 0.5f,
                (kind, dx, dy, size, alpha) -> out.add(new P(kind, dx, dy, size, alpha)));
        return out;
    }

    @Test
    public void theSameMomentDrawsTheSamePicture() {
        assertEquals(emit(1.7f, true, true), emit(1.7f, true, true));
    }

    @Test
    public void nothingBeingRestoredGivesOffNothing() {
        assertTrue(emit(1f, false, false).isEmpty());
    }

    @Test
    public void eachRestorationHasItsOwnParticle() {
        assertTrue(emit(1f, true, false).stream().allMatch(p -> p.kind() == TreatmentParticles.Kind.DRESSING));
        assertTrue(emit(1f, false, true).stream().allMatch(p -> p.kind() == TreatmentParticles.Kind.SEALANT));
        List<P> both = emit(1f, true, true);
        assertTrue(both.stream().anyMatch(p -> p.kind() == TreatmentParticles.Kind.DRESSING));
        assertTrue(both.stream().anyMatch(p -> p.kind() == TreatmentParticles.Kind.SEALANT));
    }

    @Test
    public void sealantLandsOnTheMedicsSideOfThePatient() {
        for (float t = 0f; t < 5f; t += 0.37f) {
            for (P p : emit(t, false, true)) {
                assertTrue(p.dx() > 0f, "medic is toward +x; sealant at " + p);
                assertTrue(p.alpha() >= 0f && p.alpha() <= 1f);
            }
        }
    }
}
