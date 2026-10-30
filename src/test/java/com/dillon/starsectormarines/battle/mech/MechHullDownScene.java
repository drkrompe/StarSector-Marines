package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Hound hull-down: <b>does a berm hide a mech's legs while its weapons stay in the fight?</b> The
 * cover-by-section acceptance ({@code mechs-nouns.md}, Cover by section).
 *
 * <p>One Hound, planted, under the fire of a ten-strong rifle squad twenty cells east, played twice
 * over the same ground: once in the open and once behind a windowed masonry parapet one cell in
 * front of it, the cover a man would kneel behind. Nothing else differs. Every round is the production fire path
 * and every section roll the production one; the Hound shoots back with its own guns, which is not
 * the question but is why the squad is given health enough to keep shooting.
 *
 * <p>The reading is <b>what reached each section</b>: armor and structure taken off the legs, the
 * torso and the mounts over the window. A mech's cover rows read from the ground up, so a berm hides
 * most of the legs, a little of the torso and none of the shoulders or the head; behind it the legs
 * should take markedly less, and the pod should be no safer than in the open. That last half is the
 * point of hull-down — the machine keeps its guns in the fight — and also the control: if the pod
 * were hidden as well the berm would simply be a flat cut, which is the model this replaces.
 *
 * <p>This is a mechanic verdict, so several seeds are summed rather than one recorded: one seed's
 * readings are a roll. The first seed of each arm is recorded.
 */
public final class MechHullDownScene implements BehaviorScene {

    static final String OPEN_LOOP = "open";
    static final String HULL_DOWN_LOOP = "hull-down";
    static final int FRAME_EVERY_TICKS = 10;

    private static final int WIDTH = 40;
    private static final int HEIGHT = 21;
    private static final int WINDOW_TICKS = 1500;
    private static final int SEEDS = 4;

    private static final int HOUND_X = 10;
    private static final int HOUND_Y = 10;
    /** The parapet's column: the cell in front of the Hound, between it and every shooter. */
    private static final int BERM_X = HOUND_X + 1;
    private static final int BERM_HALF_LENGTH = 3;
    private static final int PARAPET_HP = 400;
    private static final int SQUAD_X = 30;
    private static final int SQUAD_Y = 5;
    private static final int SQUAD_SIZE = 10;
    private static final float SQUAD_HEALTH = 100_000f;
    private static final String RIFLE = "weapon.field-rifle";

    private static final String HOUND = "hound";
    private static final String SQUAD = "rifles";

    @Override public String id() { return "mech-hull-down"; }

    @Override
    public String label() {
        return "Mech hull-down: a Hound behind a berm takes its hits on the torso and weapons, not the legs";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<Trace> open = new ArrayList<>();
        List<Trace> hullDown = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            open.add(playOnce(seed == 1 ? frames : FrameSink.NONE, OPEN_LOOP, false, seed));
            hullDown.add(playOnce(seed == 1 ? frames : FrameSink.NONE, HULL_DOWN_LOOP, true, seed));
        }
        Totals o = Totals.of(open);
        Totals h = Totals.of(hullDown);
        return List.of(reportOpen(o), reportHullDown(h, o));
    }

    private Trace playOnce(FrameSink frames, String loopId, boolean berm, long seed) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        if (berm) {
            for (int y = HOUND_Y - BERM_HALF_LENGTH; y <= HOUND_Y + BERM_HALF_LENGTH; y++) {
                // A masonry parapet, seen and shot over. Masonry rather than sandbags because rifle
                // fire wears sandbags away (small-arms wear), and a berm that is gone halfway
                // through the window would measure the wear, not the cover.
                grid.setWalkable(BERM_X, y, false);
                grid.setSeeThrough(BERM_X, y, true);
                grid.setWallHp(BERM_X, y, PARAPET_HP);
                topology.setWall(BERM_X, y, true);
                topology.setWindow(BERM_X, y, true);
            }
        }
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);

        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false).seed(seed);
        // A one-mech lance forms up a cell and a half west of its spawn cell, so ask two east.
        builder.squad(HOUND).faction(Faction.MARINE).size(1).at(HOUND_X + 2, HOUND_Y)
                .mech(MechVariant.HOUND, MechRole.ASSAULT)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, HOUND_X, HOUND_Y))
                .done();
        builder.squad(SQUAD).faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(SQUAD_SIZE)
                .primary(RIFLE)
                .inFile(SQUAD_X, SQUAD_Y, 0, 1)
                .stationary()
                .health(SQUAD_HEALTH)
                .assigned(id -> ObjectiveAssignment.defendArea(id, SQUAD_X, SQUAD_Y + SQUAD_SIZE / 2))
                .done();
        SceneWorld world = builder.build();
        Trace trace = new Trace(world.members(HOUND)[0], seed, grid);
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && trace.deathTick < 0) run.step();
        } finally {
            world.sim().close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final long hound;
        final long seed;
        final NavigationGrid grid;
        /** Cover at the Hound's own cell toward the shooters, read where it actually stands. */
        int coverLevel = -1;
        float startX = Float.NaN;
        float startY = Float.NaN;
        /** Ticks the Hound's own cell had cover toward the shooters, and how far it ever stood from its start. */
        int coveredTicks;
        int aliveTicks;
        double maxDrift;
        int ticks;
        int deathTick = -1;
        MechSections sections;
        /** Each section's armor plus structure at the last observation, or NaN before the first hit. */
        final float[] last = new float[MechSection.values().length];
        /** Ticks on which a section lost armor or structure: a hit, or several in one tick. */
        final int[] hitTicks = new int[MechSection.values().length];
        /** Armor and structure taken off each section while it still had some. */
        final float[] taken = new float[MechSection.values().length];
        int shots;

        Trace(long hound, long seed, NavigationGrid grid) {
            this.hound = hound;
            this.seed = seed;
            this.grid = grid;
            Arrays.fill(last, Float.NaN);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (coverLevel < 0) {
                startX = sim.world().x(hound);
                startY = sim.world().y(hound);
                int cx = (int) Math.floor(startX);
                int cy = (int) Math.floor(startY);
                coverLevel = grid.getCoverAt(cx, cy, SQUAD_X - cx, 0);
            }
            if (sections == null && sim.world().hasMechLoadout(hound)) {
                MechLoadoutComponent loadout = sim.world().mechLoadout(hound);
                sections = loadout.sections();
            }
            if (sections != null) {
                for (MechSection section : MechSection.values()) {
                    int i = section.ordinal();
                    float now = sections.fitted(section) ? sections.armor(section) + sections.structure(section) : 0f;
                    float before = Float.isNaN(last[i])
                            ? (sections.fitted(section) ? sections.maxArmor(section) + sections.maxStructure(section) : 0f)
                            : last[i];
                    if (now < before - 1e-3f) {
                        hitTicks[i]++;
                        taken[i] += before - now;
                    }
                    last[i] = now;
                }
            }
            if (deathTick < 0 && !sim.getRoster().isAliveById(hound)) deathTick = tick;
            if (deathTick < 0) {
                float x = sim.world().x(hound);
                float y = sim.world().y(hound);
                int cx = (int) Math.floor(x);
                int cy = (int) Math.floor(y);
                aliveTicks++;
                if (grid.getCoverAt(cx, cy, SQUAD_X - cx, 0) > 0) coveredTicks++;
                maxDrift = Math.max(maxDrift, Math.hypot(x - startX, y - startY));
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) if (shot.shooterId == hound) shots++;
        }

        String caption(int tick) {
            String state = sections == null ? "untouched" : String.format(Locale.ROOT,
                    "legs -%.0f  torso -%.0f  pod -%.0f  gun -%.0f  head -%.0f",
                    taken[MechSection.LEGS.ordinal()], taken[MechSection.TORSO.ordinal()],
                    taken[MechSection.LEFT_SHOULDER.ordinal()], taken[MechSection.ARMS.ordinal()],
                    taken[MechSection.HEAD.ordinal()]);
            return String.format(Locale.ROOT, "mech hull-down  t=%.1fs  cover %d  |  taken: %s",
                    tick / 30f, coverLevel, state);
        }
    }

    /** Sums over an arm's seeds. */
    private record Totals(int plays, int ticks, int coverLevel, float startX, float startY,
                          float[] taken, int[] hitTicks, int shots, int deaths,
                          int coveredTicks, int aliveTicks, double maxDrift) {
        static Totals of(List<Trace> traces) {
            float[] taken = new float[MechSection.values().length];
            int[] hits = new int[MechSection.values().length];
            int ticks = 0;
            int shots = 0;
            int deaths = 0;
            for (Trace t : traces) {
                ticks += t.ticks;
                shots += t.shots;
                if (t.deathTick >= 0) deaths++;
                for (int i = 0; i < taken.length; i++) {
                    taken[i] += t.taken[i];
                    hits[i] += t.hitTicks[i];
                }
            }
            int cover = Integer.MAX_VALUE;
            int covered = 0;
            int alive = 0;
            double drift = 0;
            for (Trace t : traces) {
                cover = Math.min(cover, t.coverLevel);
                covered += t.coveredTicks;
                alive += t.aliveTicks;
                drift = Math.max(drift, t.maxDrift);
            }
            return new Totals(traces.size(), ticks, cover, traces.get(0).startX, traces.get(0).startY,
                    taken, hits, shots, deaths, covered, alive, drift);
        }

        float taken(MechSection s) { return taken[s.ordinal()]; }

        int hits(MechSection s) { return hitTicks[s.ordinal()]; }

        float total() {
            float sum = 0f;
            for (float v : taken) sum += v;
            return sum;
        }

        /** Damage taken per second of exposure, so arms that ran to different lengths compare. */
        float perSecond(MechSection s) {
            return ticks == 0 ? 0f : taken(s) / (ticks / 30f);
        }

        /** Damage on the pod and the nose gun, the weapons hull-down keeps in the fight. */
        float mountsPerSecond() {
            return perSecond(MechSection.LEFT_SHOULDER) + perSecond(MechSection.ARMS);
        }

        Map<String, Number> metrics() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("plays", plays);
            m.put("ticks", ticks);
            m.put("coverLevelTowardShooters", coverLevel);
            m.put("houndStartX", startX);
            m.put("houndStartY", startY);
            m.put("coveredShareOfAliveTicks", aliveTicks > 0 ? (float) coveredTicks / aliveTicks : -1f);
            m.put("maxDriftCells", maxDrift);
            m.put("deaths", deaths);
            m.put("houndShots", shots);
            m.put("takenTotal", total());
            for (MechSection s : MechSection.values()) {
                m.put("taken." + s.name(), taken(s));
                m.put("hitTicks." + s.name(), hits(s));
                m.put("perSecond." + s.name(), perSecond(s));
            }
            m.put("legsShareOfDamage", total() > 0f ? taken(MechSection.LEGS) / total() : -1f);
            return m;
        }
    }

    // ---- verdicts ----

    private SceneReport reportOpen(Totals o) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("open-control-no-cover", o.coverLevel == 0,
                "cover level toward the shooters " + o.coverLevel + " (wanted 0)"));
        verdicts.add(Verdict.of("legs-hit-in-the-open", o.hits(MechSection.LEGS) > 0,
                o.hits(MechSection.LEGS) + " ticks of leg hits over " + o.plays + " plays"));
        return new SceneReport(id(), OPEN_LOOP, o.ticks, verdicts, o.metrics());
    }

    private SceneReport reportHullDown(Totals h, Totals o) {
        float legsHull = h.perSecond(MechSection.LEGS);
        float legsOpen = o.perSecond(MechSection.LEGS);
        float shareHull = h.total() > 0f ? h.taken(MechSection.LEGS) / h.total() : 1f;
        float shareOpen = o.total() > 0f ? o.taken(MechSection.LEGS) / o.total() : 0f;
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("berm-gives-cover", h.coverLevel > 0,
                "cover level toward the shooters " + h.coverLevel + " (wanted above 0)"));
        verdicts.add(Verdict.of("fewer-leg-hits", h.hits(MechSection.LEGS) < o.hits(MechSection.LEGS)
                        && legsHull < legsOpen,
                String.format(Locale.ROOT, "legs hit on %d ticks behind the berm against %d in the open;"
                                + " %.2f against %.2f damage a second",
                        h.hits(MechSection.LEGS), o.hits(MechSection.LEGS), legsHull, legsOpen)));
        verdicts.add(Verdict.of("legs-a-smaller-share", shareHull < shareOpen,
                String.format(Locale.ROOT, "legs took %.0f%% of the damage behind the berm against %.0f%% in the open",
                        shareHull * 100f, shareOpen * 100f)));
        // Hull-down is not hidden: the weapons stay in the fight, and take their hits.
        verdicts.add(Verdict.of("weapons-still-exposed", h.mountsPerSecond() > 0.5f * o.mountsPerSecond(),
                String.format(Locale.ROOT, "pod and gun took %.2f damage a second behind the berm against %.2f"
                        + " in the open (wanted more than half)", h.mountsPerSecond(), o.mountsPerSecond())));
        verdicts.add(Verdict.of("fires-from-cover", h.shots > 0, h.shots + " rounds fired from behind the berm"));
        Map<String, Number> m = h.metrics();
        m.put("open.perSecond.LEGS", legsOpen);
        m.put("open.legsShareOfDamage", shareOpen);
        return new SceneReport(id(), HULL_DOWN_LOOP, h.ticks, verdicts, m);
    }
}
