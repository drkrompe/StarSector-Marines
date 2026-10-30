package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.MechFitSpace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Stock frame against refit frame, every chassis, under the same fixed fire: <b>how long does each
 * frame last, and how much does it put out while it lasts?</b> The instrument for the stock-frame and
 * internals balance pass ({@code heavy-equipment-progression.md}).
 *
 * <p>Each play stands one mech, planted, on open ground with its factory weapons and one frame:
 * {@code stock} ({@link MechFrame#stock}), {@code refit} (the strongest frame
 * {@link MechFitSpace#bestRefitAroundStockWeapons} can actually build under the engine budget around
 * those weapons) and {@code pre-cut} (where the chassis stood before the 2026-10-16 cut, the
 * reference a refit is read against). The fire is fixed and outlasts it:
 * <ul>
 *   <li>{@code rifles-amr}: ten field rifles with one anti-materiel rifle among them, sixteen cells off;</li>
 *   <li>{@code hostile-mech}: one stock Bulwark sixteen cells off.</li>
 * </ul>
 * The opposition is given health enough to outlast every frame, so a reading is how the frame comes
 * apart rather than who wins. Planted means speed is not exercised: it is reported, not measured.
 *
 * <p>A third loop, {@code hound-duty}, puts the stock Hound against an inert, durable target for a
 * minute at a range of reservoirs and dissipation rates, which is the Hound's dissipation ceiling as a
 * production-fire-path reading rather than arithmetic.
 *
 * <p>Verdicts assert the instrument (the frame reached the battle, every play took fire); every
 * outcome is a metric. Plays run side by side ({@code -Dbattle.scene.frameEndurance.parallelism=N}, default half
 * the processors).
 */
public final class MechFrameEnduranceScene implements BehaviorScene {

    /** Seeds 1..N; {@code -Dbattle.scene.frameEndurance.seeds=N} widens the sample (default 4). */
    static final long[] SEEDS = seeds(Integer.getInteger("battle.scene.frameEndurance.seeds", 4));
    private static final int WIDTH = 44;
    private static final int HEIGHT = 27;
    private static final int MECH_X = 12;
    private static final int MECH_Y = 13;
    private static final int RANGE_CELLS = 16;
    private static final int MAX_TICKS = 4500;
    private static final int DUTY_TICKS = 1800;
    private static final float DURABLE = 100_000f;

    private static final String SUBJECT = "subject";
    private static final String FIRE = "fire";

    enum Opposition { RIFLES_AMR, HOSTILE_MECH }

    enum FrameKind { STOCK, REFIT, PRE_CUT, STUDY }

    @Override public String id() { return "mech-frame-endurance"; }

    @Override
    public String label() {
        return "Mech frame endurance: stock, refit and pre-cut frames of every chassis under the same fixed fire";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        int threads = Integer.getInteger("battle.scene.frameEndurance.parallelism",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Play> plays = new ArrayList<>();
            List<Future<Play>> futures = new ArrayList<>();
            for (Opposition opposition : Opposition.values()) {
                for (MechVariant variant : MechVariant.values()) {
                    for (FrameKind kind : MAIN_KINDS) {
                        for (long seed : SEEDS) {
                            Play p = new Play(opposition, variant, kind, frame(variant, kind), seed);
                            plays.add(p);
                            futures.add(pool.submit(() -> endure(p)));
                        }
                    }
                }
                for (Study study : studies()) {
                    for (long seed : SEEDS) {
                        Play p = new Play(opposition, study.variant, FrameKind.STUDY, study.frame, seed);
                        plays.add(p);
                        futures.add(pool.submit(() -> endure(p)));
                    }
                }
            }
            List<Duty> duties = new ArrayList<>();
            List<Future<Duty>> dutyFutures = new ArrayList<>();
            for (float[] f : new float[][]{{100f, 6f}, {100f, 7f}, {100f, 8f}, {120f, 7f}, {120f, 7.5f}, {120f, 8f}}) {
                for (long seed : SEEDS) {
                    Duty d = new Duty(f[0], f[1], seed);
                    duties.add(d);
                    dutyFutures.add(pool.submit(() -> duty(d)));
                }
            }
            for (Future<Play> f : futures) f.get();
            for (Future<Duty> f : dutyFutures) f.get();

            List<SceneReport> reports = new ArrayList<>();
            for (Opposition opposition : Opposition.values()) {
                for (MechVariant variant : MechVariant.values()) {
                    for (FrameKind kind : MAIN_KINDS) {
                        List<Play> group = new ArrayList<>();
                        for (Play p : plays) {
                            if (p.opposition == opposition && p.variant == variant && p.kind == kind) group.add(p);
                        }
                        reports.add(report(group));
                    }
                }
                for (Study study : studies()) {
                    List<Play> group = new ArrayList<>();
                    for (Play p : plays) {
                        if (p.opposition == opposition && p.kind == FrameKind.STUDY
                                && p.variant == study.variant && p.frame.equals(study.frame)) group.add(p);
                    }
                    reports.add(report(group));
                }
                reports.add(order(opposition, plays));
            }
            reports.add(dutyReport(duties));
            return reports;
        } finally {
            pool.shutdownNow();
        }
    }

    private static final FrameKind[] MAIN_KINDS = {FrameKind.STOCK, FrameKind.REFIT, FrameKind.PRE_CUT};

    /** An extra frame to stand under the same fire: {@code variant:armor:reservoir:dissipation[:speed]}. */
    record Study(MechVariant variant, MechFrame frame) {
        String label() {
            return String.format(Locale.ROOT, "study-a%.0f-h%.0f-c%.1f", frame.armorCapacity(),
                    frame.fluxCapacity(), frame.dissipationPerSecond());
        }
    }

    /**
     * {@code -Dbattle.scene.frameEndurance.study=hound:500:120:8;hound:500:120:7.5} adds those frames
     * as their own loops, for a tuning question the three standing frames do not ask.
     */
    static List<Study> studies() {
        String spec = System.getProperty("battle.scene.frameEndurance.study", "").trim();
        List<Study> out = new ArrayList<>();
        if (spec.isEmpty()) return out;
        for (String part : spec.split(";")) {
            String[] f = part.trim().split(":");
            MechVariant variant = MechVariant.fromId(f[0]);
            float speed = f.length > 4 ? Float.parseFloat(f[4]) : variant.moveSpeed;
            out.add(new Study(variant, new MechFrame(Float.parseFloat(f[1]), speed,
                    Float.parseFloat(f[2]), Float.parseFloat(f[3]))));
        }
        return out;
    }

    private static long[] seeds(int n) {
        long[] out = new long[Math.max(1, n)];
        for (int i = 0; i < out.length; i++) out[i] = i + 1L;
        return out;
    }

    static MechFrame frame(MechVariant variant, FrameKind kind) {
        return switch (kind) {
            case STOCK -> MechFrame.stock(variant);
            case REFIT -> MechFitSpace.bestRefitAroundStockWeapons(variant).frame();
            case PRE_CUT -> MechFitSpace.preCut(variant);
            case STUDY -> throw new IllegalArgumentException("a study frame is given, not derived");
        };
    }

    // ---- plays ----

    static final class Play {
        final Opposition opposition;
        final MechVariant variant;
        final FrameKind kind;
        final MechFrame frame;
        final long seed;
        boolean frameApplied;
        int firstDamaged = -1;
        int armorHalf = -1;
        int armorBreak = -1;
        int firstSectionLost = -1;
        int death = -1;
        int ticks;
        float damageDealt;
        int rounds;
        int dissipationTicks;

        Play(Opposition opposition, MechVariant variant, FrameKind kind, MechFrame frame, long seed) {
            this.opposition = opposition;
            this.variant = variant;
            this.kind = kind;
            this.frame = frame;
            this.seed = seed;
        }

        /** Death, or the cap for a frame still standing: a censored reading, counted separately. */
        int endurance() {
            return death >= 0 ? death : MAX_TICKS;
        }
    }

    private static Play endure(Play p) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(p.seed);
        builder.squad(SUBJECT).faction(Faction.MARINE).size(1).at(MECH_X + 2, MECH_Y)
                .mech(p.variant, p.variant.defaultRole)
                .stationary()
                .armor(p.frame.armorCapacity(), p.variant.armorRating)
                .assigned(id -> ObjectiveAssignment.defendArea(id, MECH_X, MECH_Y))
                .done();
        int fireX = MECH_X + RANGE_CELLS;
        if (p.opposition == Opposition.RIFLES_AMR) {
            builder.squad(FIRE).faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(10)
                    .primary("weapon.field-rifle")
                    .special("special.anti-materiel-rifle", 1, 999)
                    .inFile(fireX, MECH_Y - 5, 0, 1)
                    .health(DURABLE)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, fireX, MECH_Y))
                    .done();
        } else {
            builder.squad(FIRE).faction(Faction.DEFENDER).size(1).at(fireX + 2, MECH_Y)
                    .mech(MechVariant.BULWARK, MechVariant.BULWARK.defaultRole)
                    .stationary()
                    .health(DURABLE)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, fireX, MECH_Y))
                    .done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long mech = world.members(SUBJECT)[0];
            MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
            loadout.applyFrame(p.frame);
            p.frameApplied = loadout.flux.capacity() == p.frame.fluxCapacity()
                    && loadout.flux.dissipationPerSecond() == p.frame.dissipationPerSecond()
                    && Math.abs(sim.world().maxArmor(mech) - p.frame.armorCapacity()) < 1e-3f;
            float maxArmor = sim.world().maxArmor(mech);
            while (sim.getSimTickIndex() < MAX_TICKS) {
                sim.advance(BattleSimulation.TICK_DT);
                int tick = sim.getSimTickIndex();
                if (!sim.getRoster().isAliveById(mech)) {
                    p.death = tick;
                    break;
                }
                p.damageDealt = sim.telemetry().damageDealt(mech);
                p.rounds = sim.telemetry().roundsFired(mech);
                if (loadout.flux.aiDissipating()) p.dissipationTicks++;
                float armor = sim.world().armor(mech);
                boolean damaged = armor < maxArmor || sim.world().hp(mech) < sim.world().maxHp(mech);
                if (damaged && p.firstDamaged < 0) p.firstDamaged = tick;
                if (p.armorHalf < 0 && armor <= 0.5f * maxArmor) p.armorHalf = tick;
                MechSections sections = loadout.sections();
                boolean breached = sections != null ? anyArmorGone(sections) : armor <= 1f;
                if (p.armorBreak < 0 && breached) p.armorBreak = tick;
                if (p.firstSectionLost < 0 && sections != null && anySectionLost(sections)) p.firstSectionLost = tick;
            }
            p.ticks = sim.getSimTickIndex();
        }
        return p;
    }

    private static boolean anyArmorGone(MechSections sections) {
        for (MechSection section : MechSection.values()) {
            if (sections.fitted(section) && sections.maxArmor(section) > 0f && sections.armor(section) <= 0f) return true;
        }
        return false;
    }

    private static boolean anySectionLost(MechSections sections) {
        for (MechSection section : MechSection.values()) {
            if (sections.destroyed(section)) return true;
        }
        return false;
    }

    // ---- the Hound's duty cycle ----

    static final class Duty {
        final float capacity;
        final float dissipation;
        final long seed;
        int rounds;
        float damage;
        int dissipationTicks;
        int firstGate = -1;

        Duty(float capacity, float dissipation, long seed) {
            this.capacity = capacity;
            this.dissipation = dissipation;
            this.seed = seed;
        }
    }

    private static Duty duty(Duty d) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(d.seed);
        builder.squad(SUBJECT).faction(Faction.MARINE).size(1).at(MECH_X + 2, MECH_Y)
                .mech(MechVariant.HOUND, MechVariant.HOUND.defaultRole)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, MECH_X, MECH_Y))
                .done();
        builder.unit("range-target", Faction.DEFENDER, UnitType.MARINE, MECH_X + 12, MECH_Y);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long mech = world.members(SUBJECT)[0];
            long target = world.unit("range-target");
            sim.world().setMaxHp(target, DURABLE);
            sim.world().setHp(target, DURABLE);
            MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
            loadout.applyFrame(new MechFrame(MechVariant.HOUND.armorCapacity, MechVariant.HOUND.moveSpeed,
                    d.capacity, d.dissipation));
            while (sim.getSimTickIndex() < DUTY_TICKS) {
                sim.advance(BattleSimulation.TICK_DT);
                if (loadout.flux.aiDissipating()) {
                    d.dissipationTicks++;
                    if (d.firstGate < 0) d.firstGate = sim.getSimTickIndex();
                }
            }
            d.rounds = sim.telemetry().roundsFired(mech);
            d.damage = DURABLE - sim.world().hp(target)
                    + (sim.world().hasArmor(target) ? sim.world().maxArmor(target) - sim.world().armor(target) : 0f);
        }
        return d;
    }

    // ---- reports ----

    private SceneReport report(List<Play> group) {
        Play first = group.get(0);
        String loop = loopName(first.opposition) + "." + first.variant.id + "."
                + (first.kind == FrameKind.STUDY ? new Study(first.variant, first.frame).label() : kindName(first.kind));
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("frame.armorCapacity", first.frame.armorCapacity());
        m.put("frame.moveSpeed", first.frame.moveSpeed());
        m.put("frame.fluxCapacity", first.frame.fluxCapacity());
        m.put("frame.dissipationPerSecond", first.frame.dissipationPerSecond());
        stats(m, "firstDamagedS", group, p -> p.firstDamaged);
        stats(m, "armorHalfS", group, p -> p.armorHalf);
        stats(m, "armorBreakS", group, p -> p.armorBreak);
        stats(m, "firstSectionLostS", group, p -> p.firstSectionLost);
        stats(m, "enduranceS", group, Play::endurance);
        int survived = 0;
        int applied = 0;
        int tookFire = 0;
        int ticks = 0;
        double dealt = 0;
        double dealtPerMin = 0;
        double rounds = 0;
        double dissipation = 0;
        for (Play p : group) {
            if (p.death < 0) survived++;
            if (p.frameApplied) applied++;
            if (p.firstDamaged >= 0) tookFire++;
            ticks += p.ticks;
            dealt += p.damageDealt;
            dealtPerMin += p.damageDealt / (p.ticks / 30.0) * 60.0;
            rounds += p.rounds;
            dissipation += p.dissipationTicks / (double) Math.max(1, p.ticks);
        }
        m.put("survivedCap", survived);
        m.put("damageDealt.mean", dealt / group.size());
        m.put("damageDealtPerMinute.mean", dealtPerMin / group.size());
        spread(m, "damageDealtPerMinute", group, p -> p.damageDealt / (p.ticks / 30.0) * 60.0);
        spread(m, "enduranceS", group, p -> p.endurance() / 30.0);
        m.put("roundsFired.mean", rounds / group.size());
        m.put("aiDissipatingShare.mean", dissipation / group.size());
        List<Verdict> verdicts = List.of(
                Verdict.of("frame-applied", applied == group.size(),
                        applied + " of " + group.size() + " plays carried the frame's armor, reservoir and dissipation into battle"),
                Verdict.of("took-fire", tookFire == group.size(),
                        tookFire + " of " + group.size() + " plays were hit (wanted every one: the fire is the instrument)"));
        return new SceneReport(id(), loop, ticks, verdicts, m);
    }

    /** The durability order at each equal-tier comparison, as readings (1 holds, 0 does not). */
    private SceneReport order(Opposition opposition, List<Play> plays) {
        Map<String, Number> m = new LinkedHashMap<>();
        for (FrameKind kind : MAIN_KINDS) {
            double b = meanEndurance(plays, opposition, MechVariant.BULWARK, kind);
            double h = meanEndurance(plays, opposition, MechVariant.HOUND, kind);
            double s = meanEndurance(plays, opposition, MechVariant.WARDEN, kind);
            m.put(kindName(kind) + ".bulwarkS", b);
            m.put(kindName(kind) + ".houndS", h);
            m.put(kindName(kind) + ".wardenS", s);
            m.put(kindName(kind) + ".orderHolds", b > h && h > s ? 1 : 0);
        }
        double refitHound = meanEndurance(plays, opposition, MechVariant.HOUND, FrameKind.REFIT);
        double stockBulwark = meanEndurance(plays, opposition, MechVariant.BULWARK, FrameKind.STOCK);
        double refitWarden = meanEndurance(plays, opposition, MechVariant.WARDEN, FrameKind.REFIT);
        double stockHound = meanEndurance(plays, opposition, MechVariant.HOUND, FrameKind.STOCK);
        m.put("refitHoundUnderStockBulwark", refitHound < stockBulwark ? 1 : 0);
        m.put("refitWardenUnderStockHound", refitWarden < stockHound ? 1 : 0);
        for (MechVariant v : MechVariant.values()) {
            double stock = meanEndurance(plays, opposition, v, FrameKind.STOCK);
            double refit = meanEndurance(plays, opposition, v, FrameKind.REFIT);
            double pre = meanEndurance(plays, opposition, v, FrameKind.PRE_CUT);
            m.put(v.id + ".refitOverPreCut", pre > 0 ? refit / pre : -1);
            m.put(v.id + ".stockOverPreCut", pre > 0 ? stock / pre : -1);
        }
        return new SceneReport(id(), loopName(opposition) + ".order", 0, List.of(
                Verdict.pass("readings", "durability order is a reading here, not a verdict")), m);
    }

    private SceneReport dutyReport(List<Duty> duties) {
        Map<String, Number> m = new LinkedHashMap<>();
        for (Duty d : duties) {
            String key = String.format(Locale.ROOT, "cap%.0f.diss%.1f.", d.capacity, d.dissipation);
            add(m, key + "rounds.mean", d.rounds / (double) SEEDS.length);
            add(m, key + "damage.mean", d.damage / (double) SEEDS.length);
            add(m, key + "aiDissipatingShare.mean", d.dissipationTicks / (double) DUTY_TICKS / SEEDS.length);
            add(m, key + "firstGateS.mean", d.firstGate / 30.0 / SEEDS.length);
        }
        return new SceneReport(id(), "hound-duty", DUTY_TICKS * duties.size(),
                List.of(Verdict.of("fired", duties.stream().allMatch(d -> d.rounds > 0),
                        "every duty play fired at the range target")), m);
    }

    private static void add(Map<String, Number> m, String key, double value) {
        m.put(key, m.getOrDefault(key, 0.0).doubleValue() + value);
    }

    private static double meanEndurance(List<Play> plays, Opposition o, MechVariant v, FrameKind k) {
        double sum = 0;
        int n = 0;
        for (Play p : plays) {
            if (p.opposition == o && p.variant == v && p.kind == k) {
                sum += p.endurance() / 30.0;
                n++;
            }
        }
        return n == 0 ? -1 : sum / n;
    }

    private interface TickOf {
        int of(Play p);
    }

    private interface ValueOf {
        double of(Play p);
    }

    /** Sample standard deviation and standard error of the mean over every play in the group. */
    private static void spread(Map<String, Number> m, String key, List<Play> group, ValueOf of) {
        int n = group.size();
        double sum = 0;
        for (Play p : group) sum += of.of(p);
        double mean = sum / n;
        double sq = 0;
        for (Play p : group) {
            double d = of.of(p) - mean;
            sq += d * d;
        }
        double sd = n > 1 ? Math.sqrt(sq / (n - 1)) : 0;
        m.put(key + ".sd", sd);
        m.put(key + ".se", sd / Math.sqrt(n));
    }

    /** Mean, min and max in seconds over the plays that reached the event; {@code .n} is how many did. */
    private static void stats(Map<String, Number> m, String key, List<Play> group, TickOf of) {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -1;
        int n = 0;
        for (Play p : group) {
            int tick = of.of(p);
            if (tick < 0) continue;
            double s = tick / 30.0;
            sum += s;
            min = Math.min(min, s);
            max = Math.max(max, s);
            n++;
        }
        m.put(key + ".n", n);
        m.put(key + ".mean", n == 0 ? -1 : sum / n);
        m.put(key + ".min", n == 0 ? -1 : min);
        m.put(key + ".max", max);
    }

    private static String loopName(Opposition o) {
        return o == Opposition.RIFLES_AMR ? "rifles-amr" : "hostile-mech";
    }

    private static String kindName(FrameKind k) {
        return switch (k) {
            case STOCK -> "stock";
            case REFIT -> "refit";
            case PRE_CUT -> "pre-cut";
            case STUDY -> "study";
        };
    }
}
