package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageServicePenetrationTest {

    @Test
    void soaGrowthPreservesEachHitsPenetrationColumn() {
        List<Float> appliedPenetration = new ArrayList<>();
        DamageService service = service(appliedPenetration);

        service.enterParallel();
        for (int i = 0; i < 80; i++) {
            service.applyDamage(100L + i, 1L, 10f, i * 0.25f, 1f);
        }
        service.exitParallel();
        service.flushPendingDamage();

        assertEquals(80, appliedPenetration.size());
        for (int i = 0; i < 80; i++) {
            assertEquals(i * 0.25f, appliedPenetration.get(i), 1e-6f);
        }
    }

    @Test
    void inlineAndQueuedPathsForwardTheSamePenetration() {
        List<Float> appliedPenetration = new ArrayList<>();
        DamageService service = service(appliedPenetration);

        service.applyDamage(1L, 2L, 3f, 7f, 1f);
        service.enterParallel();
        service.applyDamage(1L, 2L, 3f, 11f, 1f);
        service.exitParallel();
        service.flushPendingDamage();

        assertEquals(List.of(7f, 11f), appliedPenetration);
    }

    private static DamageService service(List<Float> appliedPenetration) {
        return new DamageService(
                (target, attacker, damage, penetration, morale) ->
                        appliedPenetration.add(penetration),
                (target, expected) -> { },
                (target, x, y) -> { },
                (unit, oldX, oldY, newX, newY) -> { },
                id -> true);
    }
}
