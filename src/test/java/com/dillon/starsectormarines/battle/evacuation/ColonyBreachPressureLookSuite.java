package com.dillon.starsectormarines.battle.evacuation;


import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectivePressure;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveResult;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.staffed.LoadingObjective;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Colony Breach's pressure in the middle of the battle, as the owner would judge how fast it ramps
 * (`colony-breach-nouns.md`, Budding, Harvest): the canonical colony played as shipped and unrecorded to
 * {@code -Dsnapshot.breachPressure.tick} (default 4500, two and a half minutes), then ten seconds recorded through the
 * look preset on the densest stretch of the swarm, at play zoom and wide. {@code -Dsnapshot.breachPressure.seeds}
 * (default {@code 7117,4242}) picks the seeds. Opt-in.
 */
public final class ColonyBreachPressureLookSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    /** The window the swarm is counted in when finding where it is thickest: the play-zoom frame, in cells. */
    private static final int WINDOW_W = 60, WINDOW_H = 34;
    private static final float WIDE_CELLS = 160f;

    @Override public String id() { return "colony-breach-pressure-look"; }

    @Override public String label() { return "Colony Breach mid-battle: the swarm where it is thickest, at play zoom and wide"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        int tick = Integer.getInteger("snapshot.breachPressure.tick", 4500);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String token : System.getProperty("snapshot.breachPressure.seeds", "7117,4242").split(",")) {
                long seed = Long.parseLong(token.trim());
                out.add(new SnapshotArtifact(String.format(Locale.ROOT, "breach-s%d-plan.png", seed),
                        planned(context, seed, null)));
                out.add(new SnapshotArtifact(String.format(Locale.ROOT, "breach-s%d-plan-all-objectives.png", seed),
                        planned(context, seed, "all")));
                if (Boolean.parseBoolean(System.getProperty("snapshot.breachPressure.planOnly", "false"))) continue;
                out.addAll(LookReview.record(context.modRoot(), shot(seed, tick)));
            }
            return out;
        }
    }

    /** The colony on {@code seed} played unrecorded to {@code fromTick}, then 300 ticks recorded on its thickest swarm. */
    static LookReview.Shot shot(long seed, int fromTick) {
        float[] centre = new float[2];
        String name = String.format(Locale.ROOT, "breach-s%d-t%d", seed, fromTick);
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(colony(seed));
            } catch (Exception e) {
                throw new IllegalStateException("colony fixture", e);
            }
            while (sim.getSimTickIndex() < fromTick && !sim.isComplete()) sim.advance(BattleSimulation.TICK_DT);
            int swarm = thickest(sim, centre);
            System.out.printf("    [look/%s] %d swarm in the frame at %.0f,%.0f, tick %d%n",
                    name, swarm, centre[0], centre[1], sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(
                new LookReview.View("play", sim -> new float[]{centre[0], centre[1], WINDOW_W}),
                new LookReview.View("wide", sim -> new float[]{centre[0], centre[1], WIDE_CELLS})), 300, 6, 2);
    }

    /**
     * The colony on {@code seed} at tick zero, the whole map through the look preset with what the battle is laid out
     * around marked on it: the shelter and holdouts, the company's pads, the lift zone, the first-wave and mother bore
     * sites, and each side objective with the site its own bore opens at. {@code objectives}
     * forces the side objectives ({@code all} for every one the colony can host), or null for the battle's own roll.
     */
    private static BufferedImage planned(SnapshotContext context, long seed, String objectives) throws Exception {
        String before = System.getProperty(ColonyBreachObjectives.FORCE_PROPERTY);
        if (objectives != null) System.setProperty(ColonyBreachObjectives.FORCE_PROPERTY, objectives);
        try (BattleSimulation sim = HeadlessBattles.fixture(colony(seed))) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            ColonyEruptionPlan plan = sim.getColonyBreach().plan();
            for (ColonyEruptionPlan.Site site : plan.sites()) {
                marks.label(site.x() + .5f, site.y() + .5f, site.mother() ? "MOTHER bore (5 min)"
                        : "Bore " + site.number() + String.format(Locale.ROOT, " (%.0f s)",
                        site.delayTicks() * BattleSimulation.TICK_DT), ReviewStyle.KEEP);
            }
            for (CivilianRedoubt r : sim.getCivilianRedoubts()) {
                marks.box(r.left(), r.bottom(), r.right(), r.top(), r.isMain() ? "SHELTER" : "Holdout " + r.number(),
                        r.isMain() ? ReviewStyle.LANDING : ReviewStyle.ALLY);
            }
            for (LandingPad pad : sim.getMarineLandingPads()) {
                marks.label(pad.centerX + .5f, pad.centerY + .5f, "Company pad", ReviewStyle.LANDING);
            }
            float[] lift = liftZone(sim, plan);
            if (lift != null) marks.label(lift[0], lift[1], "LIFT", ReviewStyle.LANDING);
            ObjectiveBoard board = sim.getOptionalObjectives();
            if (board != null) {
                for (ObjectiveResult result : board.results()) {
                    ObjectiveRuntime runtime = board.runtime(result.key());
                    ObjectivePressure pressure = runtime instanceof LoadingObjective l ? l.pressure()
                            : runtime instanceof PressuredObjective p ? p.pressure() : null;
                    if (!(pressure instanceof BorePressure bore) || bore.centre() == null) continue;
                    int[] c = bore.centre();
                    marks.box(c[0] - 6, c[1] + 6, c[0] + 6, c[1] - 6, "Side: " + result.title(), ReviewStyle.OBJECTIVE);
                    if (bore.site() != null) {
                        marks.arrow(bore.site()[0] + .5f, bore.site()[1] + .5f, c[0] + .5f, c[1] + .5f,
                                "its bore", ReviewStyle.OBJECTIVE);
                    }
                }
            }
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                    LookReview.HEIGHT, w / 2f, h / 2f, Math.max(w, h * LookReview.WIDTH / (float) LookReview.HEIGHT));
            return look.renderMarked(sim, marks.build());
        } finally {
            if (before == null) System.clearProperty(ColonyBreachObjectives.FORCE_PROPERTY);
            else System.setProperty(ColonyBreachObjectives.FORCE_PROPERTY, before);
        }
    }

    /** The middle of the opening protected zone away from the shelter: where the lift comes down. */
    private static float[] liftZone(BattleSimulation sim, ColonyEruptionPlan plan) {
        CivilianRedoubt shelter = null;
        for (CivilianRedoubt r : sim.getCivilianRedoubts()) if (r.isMain()) shelter = r;
        float sx = 0, sy = 0;
        int n = 0;
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            for (int x = 0; x < sim.getGrid().getWidth(); x++) {
                if (!sim.isInsideRescueOpeningProtectedZone(x, y)) continue;
                if (shelter != null && shelter.contains(x, y, 12)) continue;
                sx += x;
                sy += y;
                n++;
            }
        }
        return n == 0 ? null : new float[]{sx / n + .5f, sy / n + .5f};
    }

    /** Puts the centre of the play-zoom window holding the most living swarm into {@code centre}; returns that count. */
    private static int thickest(BattleSimulation sim, float[] centre) {
        List<float[]> bodies = new ArrayList<>();
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit).isSwarm()) bodies.add(new float[]{sim.world().x(unit), sim.world().y(unit)});
        }
        int best = -1;
        for (int y = 0; y + WINDOW_H <= sim.getGrid().getHeight(); y += 4) {
            for (int x = 0; x + WINDOW_W <= sim.getGrid().getWidth(); x += 4) {
                int n = 0;
                for (float[] b : bodies) if (b[0] >= x && b[0] < x + WINDOW_W && b[1] >= y && b[1] < y + WINDOW_H) n++;
                if (n > best) {
                    best = n;
                    centre[0] = x + WINDOW_W / 2f;
                    centre[1] = y + WINDOW_H / 2f;
                }
            }
        }
        return best;
    }

    private static JSONObject colony(long seed) throws Exception {
        try (InputStream stream = ColonyBreachPressureLookSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).put("seed", seed);
        }
    }
}
