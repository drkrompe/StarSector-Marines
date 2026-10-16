package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.modifier.UnitModifiers;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a body part does to a hit: its damage multiplier, and the limb wound a legs or arms hit
 * that reaches the man leaves, read through the unit modifiers.
 */
class BodyPartConsequencesTest {

    private static final int SIZE = 16;
    private static final float DAMAGE = 4f;
    private static final float FULL_PENETRATION = 100f;

    @Test
    void theMultipliersAverageToOneOnAnUncoveredBody() {
        float mean = 0f;
        float shares = 0f;
        for (BodyPart part : BodyPlan.INFANTRY.parts()) {
            mean += part.share * part.damageMultiplier;
            shares += part.share;
        }
        assertEquals(1f, shares, 1e-5f);
        assertEquals(1f, mean, 0.02f, "in the open a man takes what he always did on average");
        assertTrue(BodyPart.HEAD.damageMultiplier > BodyPart.TORSO.damageMultiplier);
        assertTrue(BodyPart.TORSO.damageMultiplier > BodyPart.LEGS.damageMultiplier);
        assertTrue(BodyPart.LEGS.damageMultiplier > BodyPart.ARMS.damageMultiplier);
    }

    @Test
    void theSamePayloadIsWorthMoreOnTheHeadThanOnTheLegs() {
        float head = structureLost(BodyPart.HEAD);
        float legs = structureLost(BodyPart.LEGS);
        assertEquals(DAMAGE * BodyPart.HEAD.damageMultiplier, head, 1e-4f);
        assertEquals(DAMAGE * BodyPart.LEGS.damageMultiplier, legs, 1e-4f);
    }

    @Test
    void aPayloadWithNoPartIsUnchanged() {
        Arena a = new Arena();
        long target = a.target();
        float before = a.sim.world().hp(target);
        a.sim.applyDamage(target, a.shooter, DAMAGE, FULL_PENETRATION, 0f);
        assertEquals(DAMAGE, before - a.sim.world().hp(target), 1e-4f);
        assertFalse(a.sim.wounds().isWounded(target));
    }

    @Test
    void aLegHitThatGetsThroughSlowsTheManAndIsNamed() {
        Arena a = new Arena();
        long target = a.target();
        UnitModifiers modifiers = a.sim.getRoster().modifiers();
        float base = a.sim.movement().moveSpeed(target);
        a.hit(target, BodyPart.LEGS);
        assertEquals(1, a.sim.wounds().wounds(target, BodyPart.LEGS));
        assertEquals(base * WoundService.WOUND_MULTIPLIER, a.sim.movement().moveSpeed(target), 1e-4f);
        assertEquals(base * WoundService.WOUND_MULTIPLIER,
                modifiers.resolve(target, UnitStat.MOVE_SPEED, base), 1e-4f);
        List<UnitModifiers.Applied> applied = modifiers.explain(target, UnitStat.MOVE_SPEED);
        assertEquals(1, applied.size());
        assertEquals("unitModifierLegWound", applied.get(0).name());
        assertTrue(modifiers.explain(target, UnitStat.ACCURACY).isEmpty(), "a leg costs no aim");
    }

    @Test
    void anArmHitThatGetsThroughCostsAccuracy() {
        Arena a = new Arena();
        long target = a.target();
        float base = a.sim.combat().accuracy(target);
        float speed = a.sim.movement().moveSpeed(target);
        a.hit(target, BodyPart.ARMS);
        assertEquals(base * WoundService.WOUND_MULTIPLIER, a.sim.combat().accuracy(target), 1e-4f);
        assertEquals(speed, a.sim.movement().moveSpeed(target), 1e-6f, "an arm does not slow him");
        assertEquals("unitModifierArmWound",
                a.sim.getRoster().modifiers().explain(target, UnitStat.ACCURACY).get(0).name());
    }

    @Test
    void headAndTorsoHitsAreDamageNotWounds() {
        Arena a = new Arena();
        long target = a.target();
        a.hit(target, BodyPart.HEAD);
        a.hit(target, BodyPart.TORSO);
        assertFalse(a.sim.wounds().isWounded(target));
    }

    @Test
    void woundsStackToAFloor() {
        Arena a = new Arena();
        EntitySpec spec = new EntitySpec("tough", Faction.DEFENDER, UnitType.MARINE, 8, 8);
        spec.hp = spec.maxHp = 10_000f;
        long target = a.sim.spawn(spec);
        float base = a.sim.movement().moveSpeed(target);
        a.hit(target, BodyPart.LEGS);
        a.hit(target, BodyPart.LEGS);
        assertEquals(base * WoundService.WOUND_MULTIPLIER * WoundService.WOUND_MULTIPLIER,
                a.sim.movement().moveSpeed(target), 1e-4f, "two wounds stack");
        for (int i = 0; i < 10; i++) a.hit(target, BodyPart.LEGS);
        assertEquals(base * WoundService.WOUND_FLOOR, a.sim.movement().moveSpeed(target), 1e-4f);
    }

    @Test
    void aHitTheArmourAbsorbsWholeLeavesNoWound() {
        Arena a = new Arena();
        EntitySpec spec = new EntitySpec("plated", Faction.DEFENDER, UnitType.MARINE, 8, 8);
        spec.currentArmor = spec.maxArmor = 50f;
        spec.armorRating = 10f;
        long target = a.sim.spawn(spec);
        float hp = a.sim.world().hp(target);
        a.sim.getRoster().damageService().applyDamage(target, a.shooter, DAMAGE, 0f, 0f, BodyPart.LEGS);
        assertEquals(hp, a.sim.world().hp(target), 1e-6f, "the plate took it");
        assertFalse(a.sim.wounds().isWounded(target));
    }

    @Test
    void dressingEndsTheWound() {
        Arena a = new Arena();
        long target = a.target();
        float base = a.sim.movement().moveSpeed(target);
        a.hit(target, BodyPart.LEGS);
        a.sim.wounds().dress(target);
        assertEquals(base, a.sim.movement().moveSpeed(target), 1e-6f);
        assertTrue(a.sim.getRoster().modifiers().explain(target, UnitStat.MOVE_SPEED).isEmpty());
    }

    @Test
    void telemetryTalliesPartHitsAndWoundsByTheStruckSide() {
        Arena a = new Arena();
        long target = a.target();
        a.hit(target, BodyPart.HEAD);
        a.hit(target, BodyPart.LEGS);
        assertEquals(1, a.sim.telemetry().partHits(Faction.DEFENDER, BodyPart.HEAD));
        assertEquals(1, a.sim.telemetry().partHits(Faction.DEFENDER, BodyPart.LEGS));
        assertEquals(1, a.sim.telemetry().partWounds(Faction.DEFENDER, BodyPart.LEGS));
        assertEquals(0, a.sim.telemetry().partWounds(Faction.DEFENDER, BodyPart.HEAD));
        assertEquals(0, a.sim.telemetry().partHits(Faction.MARINE, BodyPart.HEAD));
    }

    private static float structureLost(BodyPart part) {
        Arena a = new Arena();
        long target = a.target();
        float before = a.sim.world().hp(target);
        a.hit(target, part);
        return before - a.sim.world().hp(target);
    }

    /** An open floor with one shooter, so nothing but the part decides what a hit is worth. */
    private static final class Arena {
        final BattleSimulation sim;
        final long shooter;

        Arena() {
            NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
            }
            sim = new BattleSimulation(grid, new CellTopology(SIZE, SIZE));
            shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, 2));
        }

        long target() {
            return sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 8, 8));
        }

        void hit(long target, BodyPart part) {
            sim.getRoster().damageService().applyDamage(target, shooter, DAMAGE, FULL_PENETRATION, 0f, part);
        }
    }
}
