package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepVegetationScene;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The nest's skitterers at each of their jobs (`colony-breach-nouns.md`, Swarm strain: Skitterer). Every loop opens
 * one bore, held asleep so no surge muddies the picture, with its whole brood standing round the mouth. Where a job
 * makes something happen sooner, the same loop is played again with {@code -Dbattle.swarm.workers=false} as its
 * control.
 * <ul>
 *   <li>{@code haul}: a Runner lies dead eight cells out. A skitterer drags it home and the bore is fed.</li>
 *   <li>{@code share}: four dead Runners round a mother maw's mouth and one far out. No more than its share of the
 *       brood hauls at once, the far one is left, and the near ones still go home; with
 *       {@code -Dbattle.swarm.haulShare=false} as the control, more haul and the far one is gone for.</li>
 *   <li>{@code shun}: a dead Runner lies beside a rifle pair. Skitterers set out for it fewer times than in the
 *       control, because a body one fled from is left a while.</li>
 *   <li>{@code repair}: a spore colony of the bore is shot down to two fifths. Skitterers mend it past four fifths.</li>
 *   <li>{@code spire}: a maw on old creep with its brood spire, which has just launched. Skitterers carry feeds to
 *       it and its wait shortens.</li>
 *   <li>{@code regrow}: a ring is lit in the bore's old creep and the fire runs through the nest. Skitterers end the
 *       burn's refusal and the creep stands on more of the burnt nest than in the control.</li>
 *   <li>{@code strip}: a grove beside a mother bore. Skitterers strip a creep-held tree, it dies before any does in
 *       the control, and its pulp is carried home.</li>
 *   <li>{@code outpost}: a bore on old creep waiting on its outposts. Skitterers stand on the pending cell and an
 *       outpost roots sooner than in the control.</li>
 *   <li>{@code flee}: a rifle squad stands five cells from the mouth. Every skitterer runs and none strikes.</li>
 * </ul>
 */
public final class SwarmWorkerScene implements BehaviorScene {

    static final int W = 100, H = 70;
    static final int BORE_X = 40, BORE_Y = 35;
    static final long SEED = 20261025L;

    @Override public String id() { return "swarm-workers"; }

    @Override public String label() {
        return "Skitterers haul the dead home, mend the nest, feed the spire, re-seed burns, strip trees, tend"
                + " outposts and run from people";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(haul(frames), share(), shun(), repair(), spire(), regrow(), strip(), outpost(), flee());
    }

    /** One loop's battle: open ground, one bore grown to {@code tier}, its creep matured when asked, held asleep. */
    private static BattleSimulation nest(SceneWorld world, BoreTier tier, boolean mother, boolean matured) {
        BattleSimulation sim = world.sim();
        SwarmBore bore = sim.swarmBores().open(sim, 1, mother, BORE_X, BORE_Y);
        if (bore == null) throw new IllegalStateException("open ground refused a bore");
        for (int guard = 0; bore.tier().ordinal() < tier.ordinal() && guard < 64; guard++) {
            sim.swarmBores().feed(sim, bore, 1f);
        }
        sim.swarmBores().hold(bore);
        sim.creepField();
        if (matured) sim.matureCreep();
        else sim.swarmWorkers().populate(sim);
        return sim;
    }

    private static SceneWorld ground() {
        return SceneBuilder.openGround(W, H).seed(SEED).missionCompletion(false).build();
    }

    private static SwarmBore bore(BattleSimulation sim) { return sim.swarmBores().bores().get(0); }

    private static void run(BattleSimulation sim, int ticks, Consumer<Integer> each) {
        for (int tick = 1; tick <= ticks; tick++) {
            each.accept(tick);
            sim.advance(BattleSimulation.TICK_DT);
        }
    }

    /** Plays {@code body} with skitterers switched off, restoring the switch after. */
    private static <T> T control(Callable<T> body) throws Exception {
        return switchedOff(SwarmWorkers.PROPERTY, body);
    }

    /** Plays {@code body} with {@code property} reading {@code false}, restoring it after. */
    private static <T> T switchedOff(String property, Callable<T> body) throws Exception {
        String before = System.getProperty(property);
        System.setProperty(property, "false");
        try {
            return body.call();
        } finally {
            if (before == null) System.clearProperty(property);
            else System.setProperty(property, before);
        }
    }

    /** How many of the bore's skitterers are hauling right now. */
    private static int hauling(BattleSimulation sim) {
        int n = 0;
        for (SwarmWorkers.Worker w : sim.swarmWorkers().workers()) if (w.job() == SwarmWorkers.Job.HAUL) n++;
        return n;
    }

    /** A dead Runner at ({@code x}, {@code y}). */
    private static long deadRunner(BattleSimulation sim, String name, int x, int y) {
        long dead = SwarmStrains.spawn(sim, name, UnitType.SWARM_RUNNER, x, y);
        sim.applyDamage(dead, 1e6f, 1e6f);
        return dead;
    }

    private SceneReport report(String loop, int ticks, List<Verdict> verdicts, Map<String, Number> metrics) {
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    // ---- haul ------------------------------------------------------------------------------------

    private SceneReport haul(FrameSink frames) {
        int ticks = 900;
        SceneWorld world = ground();
        try (BattleSimulation sim = nest(world, BoreTier.BORE, false, false)) {
            SwarmBore bore = bore(sim);
            long dead = SwarmStrains.spawn(sim, "Fallen Runner", UnitType.SWARM_RUNNER, BORE_X + 8, BORE_Y);
            sim.applyDamage(dead, 1e6f, 1e6f);
            float fedBefore = bore.biomassFed();
            run(sim, ticks, tick -> {
                if (tick % 15 == 0) {
                    frames.frame("haul", sim, tick, "skitterers / haul / tick " + tick + " / hauled "
                            + sim.swarmHarvest().hauledByWorkers());
                }
            });
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("a-skitterer-drags-the-dead-home", sim.swarmHarvest().hauledByWorkers() == 1
                            && sim.swarmHarvest().recycled() == 1,
                    sim.swarmHarvest().hauledByWorkers() + " hauled by skitterers, " + sim.swarmHarvest().recycled()
                            + " recycled"));
            verdicts.add(Verdict.of("the-bore-is-fed", bore.biomassFed() > fedBefore,
                    "biomass fed " + fedBefore + " -> " + bore.biomassFed()));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("skitterers", sim.swarmWorkers().workers().size());
            metrics.put("hauled", sim.swarmHarvest().hauledByWorkers());
            return report("haul", ticks, verdicts, metrics);
        }
    }

    // ---- share ------------------------------------------------------------------------------------

    static final int SHARE_TICKS = 1800;
    /** Dead Runners round the mouth, and one out past the near half of a maw's work range. */
    static final int[][] NEAR_DEAD = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}};
    static final int FAR_DEAD_DX = 45;

    /** The most skitterers hauling at once, whether the far Runner was ever gone for, bodies hauled, and the brood. */
    private static int[] shareOut(boolean share) throws Exception {
        Callable<int[]> body = () -> {
            SceneWorld world = ground();
            try (BattleSimulation sim = nest(world, BoreTier.MAW, true, false)) {
                for (int i = 0; i < NEAR_DEAD.length; i++) {
                    deadRunner(sim, "Fallen Runner " + i, BORE_X + NEAR_DEAD[i][0], BORE_Y + NEAR_DEAD[i][1]);
                }
                long far = deadRunner(sim, "Far Runner", BORE_X + FAR_DEAD_DX, BORE_Y);
                int brood = sim.swarmWorkers().workers().size();
                int[] peak = {0}, farClaimed = {0};
                run(sim, SHARE_TICKS, tick -> {
                    peak[0] = Math.max(peak[0], hauling(sim));
                    for (SwarmWorkers.Worker w : sim.swarmWorkers().workers()) {
                        if (w.job() == SwarmWorkers.Job.HAUL && w.subject == far) farClaimed[0] = 1;
                    }
                });
                return new int[]{peak[0], farClaimed[0], sim.swarmHarvest().hauledByWorkers(), brood};
            }
        };
        return share ? body.call() : switchedOff(SwarmWorkers.HAUL_SHARE_PROPERTY, body);
    }

    private SceneReport share() throws Exception {
        int[] with = shareOut(true);
        int[] without = shareOut(false);
        int cap = SwarmWorkers.haulersFor(with[3]);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("a-share-of-the-brood-hauls", with[0] >= 1 && with[0] <= cap,
                "at most " + with[0] + " of " + with[3] + " hauling at once (cap " + cap + "), " + without[0]
                        + " in the control"));
        verdicts.add(Verdict.of("the-cap-bites", without[0] > with[0],
                without[0] + " hauling at once without the share, " + with[0] + " with it"));
        verdicts.add(Verdict.of("its-own-dead-far-out-are-left", with[1] == 0 && without[1] == 1,
                "the far Runner gone for: " + (with[1] == 1) + " with the share, " + (without[1] == 1) + " without"));
        verdicts.add(Verdict.of("the-near-dead-still-go-home", with[2] >= NEAR_DEAD.length,
                with[2] + " of " + NEAR_DEAD.length + " near bodies hauled"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("peakHaulersWith", with[0]);
        metrics.put("peakHaulersWithout", without[0]);
        metrics.put("hauledWith", with[2]);
        metrics.put("hauledWithout", without[2]);
        return report("share", SHARE_TICKS, verdicts, metrics);
    }

    // ---- shun -------------------------------------------------------------------------------------

    static final int SHUN_TICKS = 1200;

    /** How many times a skitterer set out for a body lying under a rifle squad, and how often one fled. */
    private static int[] underTheGuns(boolean share) throws Exception {
        Callable<int[]> body = () -> {
            SceneBuilder builder = SceneBuilder.openGround(W, H).seed(SEED).missionCompletion(false);
            builder.squad("rifles").faction(Faction.MARINE).size(2).at(BORE_X + 16, BORE_Y)
                    .primary("weapon.field-rifle").stationary().done();
            SceneWorld world = builder.build();
            try (BattleSimulation sim = nest(world, BoreTier.MAW, true, false)) {
                long dead = deadRunner(sim, "Runner in the open", BORE_X + 10, BORE_Y);
                Map<Long, Long> going = new LinkedHashMap<>();
                int[] setOut = {0};
                run(sim, SHUN_TICKS, tick -> {
                    for (SwarmWorkers.Worker w : sim.swarmWorkers().workers()) {
                        long now = w.job() == SwarmWorkers.Job.HAUL ? w.subject : 0L;
                        Long was = going.put(w.id, now);
                        if (now == dead && (was == null || was != dead)) setOut[0]++;
                    }
                });
                return new int[]{setOut[0], sim.swarmWorkers().fled()};
            }
        };
        return share ? body.call() : switchedOff(SwarmWorkers.HAUL_SHARE_PROPERTY, body);
    }

    private SceneReport shun() throws Exception {
        int[] with = underTheGuns(true);
        int[] without = underTheGuns(false);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-nest-tries-for-it", with[0] >= 1, with[0] + " set out for the body"));
        verdicts.add(Verdict.of("a-body-fled-from-is-left-a-while", with[0] < without[0],
                with[0] + " trips toward the guns with the shun, " + without[0] + " without"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("setOutWith", with[0]);
        metrics.put("setOutWithout", without[0]);
        metrics.put("fledWith", with[1]);
        metrics.put("fledWithout", without[1]);
        return report("shun", SHUN_TICKS, verdicts, metrics);
    }

    // ---- repair ----------------------------------------------------------------------------------

    private SceneReport repair() {
        int ticks = 1800;
        SceneWorld world = ground();
        try (BattleSimulation sim = nest(world, BoreTier.BORE, false, false)) {
            long colony = bore(sim).colonies().get(0);
            float max = sim.world().maxHp(colony);
            sim.world().setHp(colony, max * 0.4f);
            int[] mendedAt = {-1};
            run(sim, ticks, tick -> {
                if (mendedAt[0] < 0 && sim.world().hp(colony) >= max * 0.8f) mendedAt[0] = tick;
            });
            float share = sim.world().hp(colony) / max;
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("a-wounded-colony-is-mended", mendedAt[0] > 0,
                    "colony at " + Math.round(share * 100) + "% of its structure, past 80% at tick " + mendedAt[0]));
            verdicts.add(Verdict.of("never-past-full", sim.world().hp(colony) <= max,
                    sim.world().hp(colony) + " of " + max));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("mendedAtTick", mendedAt[0]);
            metrics.put("repaired", sim.swarmWorkers().repaired());
            return report("repair", ticks, verdicts, metrics);
        }
    }

    // ---- spire -----------------------------------------------------------------------------------

    private SceneReport spire() {
        int ticks = 900;
        SceneWorld world = ground();
        try (BattleSimulation sim = nest(world, BoreTier.MAW, false, true)) {
            List<Verdict> verdicts = new ArrayList<>();
            if (sim.broodSpires().spires().isEmpty()) {
                verdicts.add(Verdict.of("a-spire-to-tend", false, "no spire grew"));
                return report("spire", 0, verdicts, Map.of());
            }
            BroodSpires.Spire spire = sim.broodSpires().spires().get(0);
            spire.lastReleaseTick = sim.getSimTickIndex();
            run(sim, ticks, tick -> { });
            int gap = BroodSpires.gapTicks(spire.tendedTicks());
            int full = BroodSpires.gapTicks(0);
            verdicts.add(Verdict.of("skitterers-feed-the-spire", sim.swarmWorkers().spireFeeds() >= 1,
                    sim.swarmWorkers().spireFeeds() + " feeds carried"));
            verdicts.add(Verdict.of("its-wait-shortens", gap < full,
                    "gap " + gap + " ticks against " + full + " untended"));
            verdicts.add(Verdict.of("never-under-the-floor", gap >= BroodSpires.gapTicks(Integer.MAX_VALUE / 2),
                    "gap " + gap + " ticks"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("feeds", sim.swarmWorkers().spireFeeds());
            metrics.put("gapTicks", gap);
            return report("spire", ticks, verdicts, metrics);
        }
    }

    // ---- regrow ----------------------------------------------------------------------------------

    /**
     * Burnt cells of the nest (within {@link #NEST_CELLS} of the mouth) the creep stands on again at the end, burnt
     * cells there, and cells skitterers freed, with skitterers or without. A ring is lit round the mouth and the fire
     * runs through the old creep as it does.
     */
    private static int[] burnRing(boolean workers) throws Exception {
        Callable<int[]> body = () -> {
            SceneWorld world = ground();
            try (BattleSimulation sim = nest(world, BoreTier.BORE, false, true)) {
                FireField fire = sim.fireField();
                run(sim, REGROW_TICKS, tick -> {
                    if (tick != 1) return;
                    for (int dy = -8; dy <= 8; dy++) {
                        for (int dx = -8; dx <= 8; dx++) {
                            int d2 = dx * dx + dy * dy;
                            if (d2 >= 36 && d2 <= 64) fire.addHeat(BORE_X + dx, BORE_Y + dy, 3000);
                        }
                    }
                });
                CreepField field = sim.creepField();
                int creeped = 0, burnt = 0;
                for (int dy = -NEST_CELLS; dy <= NEST_CELLS; dy++) {
                    for (int dx = -NEST_CELLS; dx <= NEST_CELLS; dx++) {
                        if (dx * dx + dy * dy > NEST_CELLS * NEST_CELLS) continue;
                        int x = BORE_X + dx, y = BORE_Y + dy;
                        if (!fire.isBurnt(x, y)) continue;
                        burnt++;
                        if (field.isCreeped(x, y)) creeped++;
                    }
                }
                return new int[]{creeped, burnt, sim.swarmWorkers().cellsRegrown()};
            }
        };
        return workers ? body.call() : control(body);
    }

    static final int NEST_CELLS = 12;
    static final int REGROW_TICKS = 3600;

    private SceneReport regrow() throws Exception {
        int[] with = burnRing(true);
        int[] without = burnRing(false);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-burn-was-real", with[1] > 0 && without[1] > 0,
                with[1] + " and " + without[1] + " nest cells burnt"));
        verdicts.add(Verdict.of("skitterers-re-seed-the-burn", with[2] > 0, with[2] + " cells freed by skitterers"));
        verdicts.add(Verdict.of("the-creep-comes-back-sooner", with[0] > without[0],
                with[0] + " burnt nest cells creeped again with skitterers, " + without[0] + " without"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("creepedWith", with[0]);
        metrics.put("creepedWithout", without[0]);
        metrics.put("regrown", with[2]);
        return report("regrow", REGROW_TICKS, verdicts, metrics);
    }

    // ---- strip -----------------------------------------------------------------------------------

    /** The tick the first tree dies, trees stripped and pulp carried home, beside a grove. */
    private static int[] grove(boolean workers) throws Exception {
        Callable<int[]> body = () -> {
            GenContext ctx = CreepVegetationScene.country();
            SceneWorld world = SceneBuilder.over(ctx.grid, ctx.topology).seed(SEED).missionCompletion(false).build();
            try (BattleSimulation sim = world.sim()) {
                for (Doodad doodad : ctx.doodads) sim.addDoodad(doodad);
                SwarmBore bore = sim.swarmBores().open(sim, 0, true, 26, 34);
                if (bore == null) throw new IllegalStateException("open grass refused the bore");
                sim.swarmBores().hold(bore);
                sim.creepField();
                sim.swarmWorkers().populate(sim);
                int[] firstDeath = {-1};
                run(sim, 5400, tick -> {
                    if (firstDeath[0] < 0 && !sim.creepVegetation().summary().startsWith("0 of")
                            && !sim.creepVegetation().summary().startsWith("no trees")) {
                        firstDeath[0] = tick;
                    }
                });
                return new int[]{firstDeath[0], sim.swarmWorkers().treesStripped(), sim.swarmWorkers().pulpDelivered()};
            }
        };
        return workers ? body.call() : control(body);
    }

    private SceneReport strip() throws Exception {
        int[] with = grove(true);
        int[] without = grove(false);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("skitterers-strip-a-tree-to-death", with[1] >= 1, with[1] + " trees stripped"));
        verdicts.add(Verdict.of("a-tree-dies-sooner", with[0] > 0 && (without[0] < 0 || with[0] < without[0]),
                "first tree dead at tick " + with[0] + " with skitterers, " + without[0] + " without"));
        verdicts.add(Verdict.of("its-pulp-goes-home", with[2] >= 1, with[2] + " loads of pulp fed"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstDeathWith", with[0]);
        metrics.put("firstDeathWithout", without[0]);
        metrics.put("stripped", with[1]);
        return report("strip", 5400, verdicts, metrics);
    }

    // ---- outpost ---------------------------------------------------------------------------------

    /** The tick the bore's first outpost roots, and the seconds skitterers stood on it. */
    private static int[] firstOutpost(boolean workers) throws Exception {
        Callable<int[]> body = () -> {
            SceneWorld world = ground();
            try (BattleSimulation sim = nest(world, BoreTier.BORE, false, true)) {
                SwarmBore bore = bore(sim);
                int[] rooted = {-1};
                run(sim, 1800, tick -> {
                    if (rooted[0] < 0 && !bore.outposts().isEmpty()) rooted[0] = tick;
                });
                return new int[]{rooted[0], sim.swarmWorkers().outpostTicks()};
            }
        };
        return workers ? body.call() : control(body);
    }

    private SceneReport outpost() throws Exception {
        int[] with = firstOutpost(true);
        int[] without = firstOutpost(false);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("skitterers-tend-the-pending-outpost", with[1] > 0,
                with[1] + " ticks stood on it"));
        verdicts.add(Verdict.of("it-roots-sooner", with[0] > 0 && without[0] > 0 && with[0] < without[0],
                "first outpost at tick " + with[0] + " with skitterers, " + without[0] + " without"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("rootedWith", with[0]);
        metrics.put("rootedWithout", without[0]);
        return report("outpost", 1800, verdicts, metrics);
    }

    // ---- flee ------------------------------------------------------------------------------------

    private SceneReport flee() {
        int ticks = 300;
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(SEED).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).size(4).at(BORE_X + 5, BORE_Y).primary("weapon.field-rifle")
                .stationary().done();
        SceneWorld world = builder.build();
        try (BattleSimulation sim = nest(world, BoreTier.MAW, true, false)) {
            int brood = sim.swarmWorkers().workers().size();
            float marineHp = marineHp(sim);
            run(sim, ticks, tick -> { });
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("every-skitterer-runs", sim.swarmWorkers().fled() >= brood && brood > 0,
                    sim.swarmWorkers().fled() + " scatters from a brood of " + brood));
            verdicts.add(Verdict.of("none-strikes", marineHp(sim) >= marineHp,
                    "marines' health " + marineHp + " -> " + marineHp(sim)));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("brood", brood);
            metrics.put("fled", sim.swarmWorkers().fled());
            metrics.put("left", sim.swarmWorkers().workers().size());
            return report("flee", ticks, verdicts, metrics);
        }
    }

    private static float marineHp(BattleSimulation sim) {
        float hp = 0f;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) == Faction.MARINE) hp += sim.world().hp(u);
        }
        return hp;
    }
}
