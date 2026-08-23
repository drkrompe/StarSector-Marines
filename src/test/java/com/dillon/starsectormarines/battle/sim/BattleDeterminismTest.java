package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The battle is reproducible from its seed.
 *
 * <p>This is the guard on the property the whole sim rests on: two battles built with
 * the same seed and given the same inputs produce the same outcome, every run, on every
 * machine. Before it existed, every roll came from {@code ThreadLocalRandom} — a bug
 * report could not be reproduced, a battle could not be replayed, and combat tests had
 * to saturate their inputs until the roll stopped mattering.
 *
 * <p>If one of these fails, something has reintroduced an unseeded source. The fix is to
 * route it through {@code BattleSimulation.random()}, never to loosen the assertion.
 */
public class BattleDeterminismTest {

    private static final int W = 40;
    private static final int H = 20;
    private static final int ROUNDS = 40;

    @Test
    public void theSameSeedFightsTheSameBattle() {
        List<Float> first = fireAndRecord(20260823L);
        List<Float> second = fireAndRecord(20260823L);

        assertEquals(first, second,
                "same seed, same inputs, same outcome — a battle must replay exactly");
    }

    @Test
    public void differentSeedsFightDifferentBattles() {
        List<Float> first = fireAndRecord(20260823L);
        List<Float> other = fireAndRecord(99999999L);

        // Guards the other direction: a harness that returned a constant, or a stream
        // that ignored its seed, would satisfy the test above and prove nothing.
        assertNotEquals(first, other,
                "a different seed must actually change the rolls");
    }

    @Test
    public void theNoSeedConstructorIsDeterministicToo() {
        // ~40 test fixtures build a sim without a seed. They get DEFAULT_SEED rather
        // than a fresh nondeterministic one, so those tests are reproducible by
        // construction instead of by luck.
        assertEquals(record(openArena(BattleSimulation.DEFAULT_SEED)),
                record(openArenaNoSeed()));
    }

    @Test
    public void everyDrawComesFromTheOneStream() {
        BattleSimulation sim = openArena(1L);

        // The view the AI tier holds and the sim itself must hand out the same stream.
        // Two streams would be individually seeded and jointly unreproducible.
        BattleView view = sim;
        assertSame(sim.random(), view.random());
    }

    @Test
    public void aFreshStreamOnTheSameSeedRepeatsItself() {
        // Sanity on the primitive the whole thing rests on, so a failure above points
        // at the sim's wiring rather than leaving java.util.Random in question.
        Random a = new Random(7L);
        Random b = new Random(7L);
        for (int i = 0; i < 16; i++) {
            assertEquals(a.nextFloat(), b.nextFloat(), 0f);
        }
    }

    // ---------- fixtures ----------

    /**
     * Fires a fixed burst at a target that cannot die, and records the target's HP after
     * every tick. HP is the readout because it folds in the hit roll, the damage roll,
     * and the flight clock — anything nondeterministic in the firing pipeline shows up
     * as a divergent trace.
     */
    private static List<Float> fireAndRecord(long seed) {
        return record(openArena(seed));
    }

    private static List<Float> record(BattleSimulation sim) {
        long shooter = sim.spawn(new EntitySpec(
                "shooter", Faction.MARINE, UnitType.MARINE, 5, 5));
        long target = sim.spawn(new EntitySpec(
                "target", Faction.DEFENDER, UnitType.MILITIA, 9, 5));
        sim.combat().setPrimaryWeapon(shooter, MarineWeapon.PULSE_RIFLE);
        // A nominal accuracy that genuinely rolls — the point is to exercise the roll,
        // not to avoid it.
        sim.combat().setAccuracy(shooter, 0.5f);
        sim.world().setMaxHp(target, 1_000_000f);
        sim.world().setHp(target, 1_000_000f);

        for (int i = 0; i < ROUNDS; i++) {
            sim.fireShot(shooter, target, FireStance.STANCED);
        }

        List<Float> trace = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            sim.advance(BattleSimulation.TICK_DT);
            trace.add(sim.world().hp(target));
        }
        return trace;
    }

    private static BattleSimulation openArena(long seed) {
        return new BattleSimulation(walkableGrid(), new CellTopology(W, H), seed);
    }

    private static BattleSimulation openArenaNoSeed() {
        return new BattleSimulation(walkableGrid(), new CellTopology(W, H));
    }

    private static NavigationGrid walkableGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
