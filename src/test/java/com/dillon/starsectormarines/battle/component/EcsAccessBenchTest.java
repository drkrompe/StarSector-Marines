package com.dillon.starsectormarines.battle.component;

import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <b>ECS-migration Phase 0 — the measurement gate.</b> Compares the two ways a
 * per-unit tick can read/write component state at a representative N≈200 battle:
 *
 * <ul>
 *   <li><b>by-id</b> — what every mainline system does today:
 *       {@code world.getFloat(id, COMBAT, field)} per field per unit, each call a
 *       {@code Long2LongOpenHashMap} location probe + {@code tables.get} +
 *       column-object deref. This is the access the hot loop
 *       ({@code UnitUpdateSystem}) actually performs.</li>
 *   <li><b>column-walk</b> — what the storage migration was justified on:
 *       {@link EntityWorld#matched(Query)}, grab the raw column arrays once per
 *       matched table, then a tight {@code for (row)} over dense primitive arrays
 *       (the {@code DroneCrashSystem} idiom). Zero hashmap probes.</li>
 * </ul>
 *
 * <p>Both kernels run the same arithmetic over the same fields — the Phase-1
 * conversion candidates: the {@code AI_STATE} cadence countdowns
 * ({@code repositionCooldown}/{@code fallbackTimer}/{@code wanderDwellTimer}), the
 * {@code MOVEMENT} {@code moveProgress} advance, and the {@code COMBAT}
 * {@code cooldownTimer}/{@code burstTimer} decrements — over the real live
 * combatant archetype {@code {IDENTITY, POSITION, HEALTH, COMBAT, MOVEMENT,
 * AI_STATE}} using the real {@link BattleComponents} field layout. The decision the
 * epic is gated on: <i>at N=200, is the by-id path meaningfully slower than a
 * column walk, and is the difference large relative to a 33.3 ms frame budget?</i>
 *
 * <p>{@link #byIdAndColumnWalkAgree()} is the always-on regression guard (the two
 * access paths must compute bit-identical results). {@link #benchmark()} is the
 * timing run; it is gated behind {@code -Decs.bench=true} so the normal suite stays
 * fast, and it prints a markdown-ready results block. Run it with:
 * <pre>gradlew test --tests "*EcsAccessBenchTest" -Decs.bench=true</pre>
 */
public class EcsAccessBenchTest {

    /** Sim tick at 30 Hz. */
    private static final float DT = 1f / 30f;
    /** Wrap period for the timing loop so decrement-toward-zero never saturates across reps. */
    private static final float PERIOD = 5f;

    /** DCE sink — the timing loop accumulates kernel checksums here and the bench prints it. */
    private static double blackhole;

    // ---- the live combatant-mover archetype, as the game spawns it ----

    private static ComponentType[] liveArchetype(BattleComponents c) {
        return new ComponentType[]{c.IDENTITY, c.POSITION, c.HEALTH, c.COMBAT, c.MOVEMENT, c.AI_STATE};
    }

    /** A query for the live combatant-movers, excluding corpses — the column-walk's working set. */
    private static Query moverCombatantQuery(EntityWorld w, BattleComponents c) {
        return w.query(new ComponentType[]{c.COMBAT, c.MOVEMENT, c.AI_STATE},
                new ComponentType[]{c.CORPSE});
    }

    /** Deterministic per-unit seed so the arithmetic operates on varied, non-constant values. */
    private static void seed(EntityWorld w, BattleComponents c, long id, int i) {
        w.setFloat(id, c.HEALTH,   BattleComponents.HEALTH_HP,                 50f + (i % 50));
        w.setFloat(id, c.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN, 0.1f + (i % 7) * 0.13f);
        w.setFloat(id, c.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER,      0.2f + (i % 5) * 0.17f);
        w.setFloat(id, c.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER,  0.3f + (i % 3) * 0.21f);
        w.setFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS,       (i % 10) * 0.1f);
        w.setFloat(id, c.COMBAT,   BattleComponents.COMBAT_COOLDOWN_TIMER,        0.05f + (i % 9) * 0.11f);
        w.setFloat(id, c.COMBAT,   BattleComponents.COMBAT_BURST_TIMER,           0.07f + (i % 4) * 0.09f);
    }

    /**
     * Builds a world holding {@code n} live combatant-movers. When {@code churn},
     * over-allocates and destroys a random surplus so survivors' ids are scattered
     * across the location map and their table rows are shuffled by swap-pop — the
     * honest steady state. Returns the survivors' ids in shuffled (roster-like, not
     * row) order, which is the order the by-id kernel walks.
     */
    private static long[] buildLive(EntityWorld w, BattleComponents c, int n, boolean churn, Random rnd) {
        ComponentType[] arch = liveArchetype(c);
        List<Long> live = new ArrayList<>();
        if (churn) {
            int surplus = n * 3;
            int total = n + surplus;
            for (int i = 0; i < total; i++) {
                long e = w.createEntity(arch);
                seed(w, c, e, i);
                live.add(e);
            }
            Collections.shuffle(live, rnd);
            for (int k = 0; k < surplus; k++) {
                w.destroy(live.remove(live.size() - 1));
            }
            Collections.shuffle(live, rnd);
        } else {
            for (int i = 0; i < n; i++) {
                long e = w.createEntity(arch);
                seed(w, c, e, i);
                live.add(e);
            }
        }
        long[] ids = new long[live.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = live.get(i);
        return ids;
    }

    // ---- the two access kernels (identical arithmetic) ----

    /** By-id access: one location probe + indirection per field, per unit. */
    private static double kernelById(EntityWorld w, BattleComponents c, long[] ids) {
        double cs = 0;
        for (long id : ids) {
            if (!w.has(id, c.AI_STATE)) continue;   // mirrors UnitUpdateSystem's hasAiState gate
            float rc = w.getFloat(id, c.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN);
            rc -= DT; if (rc < 0f) rc += PERIOD;
            w.setFloat(id, c.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN, rc);

            float ft = w.getFloat(id, c.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER);
            ft -= DT; if (ft < 0f) ft += PERIOD;
            w.setFloat(id, c.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER, ft);

            float wd = w.getFloat(id, c.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER);
            wd -= DT; if (wd < 0f) wd += PERIOD;
            w.setFloat(id, c.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER, wd);

            float mp = w.getFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS);
            mp += DT; if (mp >= 1f) mp -= 1f;
            w.setFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS, mp);

            float cd = w.getFloat(id, c.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER);
            cd -= DT; if (cd < 0f) cd += PERIOD;
            w.setFloat(id, c.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER, cd);

            float bt = w.getFloat(id, c.COMBAT, BattleComponents.COMBAT_BURST_TIMER);
            bt -= DT; if (bt < 0f) bt += PERIOD;
            w.setFloat(id, c.COMBAT, BattleComponents.COMBAT_BURST_TIMER, bt);

            cs += w.getFloat(id, c.HEALTH, BattleComponents.HEALTH_HP) + rc + ft + wd + mp + cd + bt;
        }
        return cs;
    }

    /** Column-walk access: raw arrays captured once per matched table, dense row loop. */
    private static double kernelColumnWalk(EntityWorld w, BattleComponents c, Query q) {
        double cs = 0;
        for (ArchetypeTable t : w.matched(q)) {
            float[] rcA = t.floats(c.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN).array();
            float[] ftA = t.floats(c.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER).array();
            float[] wdA = t.floats(c.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER).array();
            float[] mpA = t.floats(c.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS).array();
            float[] cdA = t.floats(c.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER).array();
            float[] btA = t.floats(c.COMBAT, BattleComponents.COMBAT_BURST_TIMER).array();
            float[] hpA = t.floats(c.HEALTH, BattleComponents.HEALTH_HP).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                float rc = rcA[r]; rc -= DT; if (rc < 0f) rc += PERIOD; rcA[r] = rc;
                float ft = ftA[r]; ft -= DT; if (ft < 0f) ft += PERIOD; ftA[r] = ft;
                float wd = wdA[r]; wd -= DT; if (wd < 0f) wd += PERIOD; wdA[r] = wd;
                float mp = mpA[r]; mp += DT; if (mp >= 1f) mp -= 1f; mpA[r] = mp;
                float cd = cdA[r]; cd -= DT; if (cd < 0f) cd += PERIOD; cdA[r] = cd;
                float bt = btA[r]; bt -= DT; if (bt < 0f) bt += PERIOD; btA[r] = bt;
                cs += hpA[r] + rc + ft + wd + mp + cd + bt;
            }
        }
        return cs;
    }

    // ---- always-on correctness guard ----

    /**
     * The two access paths must compute bit-identical results: run each on its own
     * identically-seeded world for one tick, then compare every mutated field by id.
     * This keeps the bench honest (a column-walk that drifts from by-id is a bug) and
     * runs in every build.
     */
    @Test
    public void byIdAndColumnWalkAgree() {
        int n = 200;
        EntityWorld wa = new EntityWorld();
        BattleComponents ca = new BattleComponents(wa);
        long[] idsA = buildLive(wa, ca, n, false, null);

        EntityWorld wb = new EntityWorld();
        BattleComponents cb = new BattleComponents(wb);
        long[] idsB = buildLive(wb, cb, n, false, null);

        kernelById(wa, ca, idsA);
        kernelColumnWalk(wb, cb, moverCombatantQuery(wb, cb));

        // Both built deterministically with no churn → ids 1..n match across worlds.
        assertEquals(n, idsA.length);
        for (int k = 0; k < idsA.length; k++) {
            long id = idsA[k];
            assertEquals(wa.getFloat(id, ca.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN),
                         wb.getFloat(id, cb.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN),
                         "repositionCooldown @id=" + id);
            assertEquals(wa.getFloat(id, ca.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER),
                         wb.getFloat(id, cb.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER),
                         "fallbackTimer @id=" + id);
            assertEquals(wa.getFloat(id, ca.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER),
                         wb.getFloat(id, cb.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER),
                         "wanderDwellTimer @id=" + id);
            assertEquals(wa.getFloat(id, ca.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS),
                         wb.getFloat(id, cb.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS),
                         "moveProgress @id=" + id);
            assertEquals(wa.getFloat(id, ca.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER),
                         wb.getFloat(id, cb.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER),
                         "cooldownTimer @id=" + id);
            assertEquals(wa.getFloat(id, ca.COMBAT, BattleComponents.COMBAT_BURST_TIMER),
                         wb.getFloat(id, cb.COMBAT, BattleComponents.COMBAT_BURST_TIMER),
                         "burstTimer @id=" + id);
        }
    }

    // ---- timing run (opt-in) ----

    @Test
    public void benchmark() {
        if (!Boolean.getBoolean("ecs.bench")) {
            System.out.println("[ecs-bench] skipped (run with -Decs.bench=true)");
            return;
        }
        int[] sizes = {50, 200, 800};
        StringBuilder md = new StringBuilder();
        md.append("\n| N | population | by-id ns/tick | column ns/tick | speedup | by-id ns/unit | column ns/unit | delta µs/tick |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        System.out.println("[ecs-bench] JVM=" + System.getProperty("java.version")
                + " cpus=" + Runtime.getRuntime().availableProcessors());
        for (int n : sizes) {
            for (boolean churn : new boolean[]{false, true}) {
                runConfig(n, churn, md);
            }
        }
        System.out.println(md);
        System.out.println("[ecs-bench] blackhole=" + blackhole + " (ignore; DCE sink)");
    }

    private void runConfig(int n, boolean churn, StringBuilder md) {
        EntityWorld w = new EntityWorld();
        BattleComponents c = new BattleComponents(w);
        Random rnd = new Random(1234567L * n + (churn ? 1 : 0));
        long[] ids = buildLive(w, c, n, churn, rnd);
        Query q = moverCombatantQuery(w, c);

        DoubleSupplier byId = () -> kernelById(w, c, ids);
        DoubleSupplier col   = () -> kernelColumnWalk(w, c, q);

        // Warm up both paths together so JIT compiles them under the same conditions.
        for (int r = 0; r < 30_000; r++) { blackhole += byId.getAsDouble(); blackhole += col.getAsDouble(); }

        long byIdNs = minNanosPerTick(byId, 20_000, 7);
        long colNs  = minNanosPerTick(col,  20_000, 7);

        double speedup = colNs == 0 ? 0 : (double) byIdNs / colNs;
        double deltaUs = (byIdNs - colNs) / 1000.0;
        String pop = churn ? "churned" : "fresh";
        md.append(String.format("| %d | %s | %d | %d | %.2fx | %.1f | %.1f | %.2f |%n",
                n, pop, byIdNs, colNs, speedup,
                (double) byIdNs / ids.length, (double) colNs / ids.length, deltaUs));
    }

    /** Min over {@code trials} of (elapsed / reps) — min is the least-noisy estimator for a microbench. */
    private static long minNanosPerTick(DoubleSupplier kernel, int reps, int trials) {
        long best = Long.MAX_VALUE;
        for (int t = 0; t < trials; t++) {
            long s = System.nanoTime();
            double acc = 0;
            for (int r = 0; r < reps; r++) acc += kernel.getAsDouble();
            long elapsed = System.nanoTime() - s;
            blackhole += acc;
            best = Math.min(best, elapsed / reps);
        }
        return best;
    }
}
