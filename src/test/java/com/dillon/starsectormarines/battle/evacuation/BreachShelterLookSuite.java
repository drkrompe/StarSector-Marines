package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where Colony Breach's survivors wait, for the owner placing static defences there: the colony played as shipped to
 * {@code -Dsnapshot.breachShelter.tick} (default 300, ten seconds, the militia at its posts) and drawn through the
 * look preset on the shelter at play zoom ({@code play}, 60 cells, or wider to hold its claimed ground) and with its streets round it ({@code wide}, 120),
 * each plain and marked with the shelter's and holdouts' claimed ground, then each holdout at play zoom.
 * {@code -Dsnapshot.breachShelter.seeds} (default {@code 38/arid}, the Khettara's map) picks the seeds, each
 * {@code seed} or {@code seed/biome}. Opt-in.
 */
public final class BreachShelterLookSuite implements SnapshotSuite {

    private static final float PLAY_CELLS = 60f;
    private static final float WIDE_CELLS = 120f;

    @Override public String id() { return "breach-shelter-look"; }

    @Override public String label() { return "Colony Breach's shelter and holdouts, where the survivors wait"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        int tick = Integer.getInteger("snapshot.breachShelter.tick", 300);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String token : System.getProperty("snapshot.breachShelter.seeds", "38/arid").split(",")) {
                String[] seedAndBiome = token.trim().split("/");
                long seed = Long.parseLong(seedAndBiome[0]);
                String biome = seedAndBiome.length > 1 ? seedAndBiome[1].toUpperCase(Locale.ROOT) : null;
                try (BattleSimulation sim = HeadlessBattles.fixture(EvacCordonLookSuite.colony(seed, biome))) {
                    int opened = -1;
                    while (sim.getSimTickIndex() < tick && !sim.isComplete()) {
                        sim.advance(BattleSimulation.TICK_DT);
                        if (opened < 0 && sim.getColonyBreach() != null && sim.getColonyBreach().heldBattery() == 0L)
                            opened = sim.getSimTickIndex();
                    }
                    if (opened >= 0) System.out.printf("    [breach-shelter/s%d] the Locust opened at tick %d%n", seed, opened);
                    ReviewAnnotations marks = marks(sim);
                    ReviewAnnotations none = ReviewAnnotations.builder().build();
                    String tag = String.format(Locale.ROOT, "s%d-t%d", seed, sim.getSimTickIndex());
                    for (CivilianRedoubt r : sim.getCivilianRedoubts()) {
                        float x = (r.left() + r.right() + 1) / 2f, y = (r.bottom() + r.top() + 1) / 2f;
                        String place = r.isMain() ? "shelter" : "holdout-" + r.number();
                        // Play zoom, or wider when the claimed ground and a margin round it would not fit.
                        float close = Math.max(PLAY_CELLS, Math.max(r.right() - r.left() + 12f,
                                (Math.abs(r.top() - r.bottom()) + 12f) * LookReview.WIDTH / LookReview.HEIGHT));
                        out.add(still(context, sim, x, y, close, none, tag + "-" + place + "-play.png"));
                        out.add(still(context, sim, x, y, close, marks, tag + "-" + place + "-play-marked.png"));
                        if (!r.isMain()) continue;
                        out.add(still(context, sim, x, y, WIDE_CELLS, none, tag + "-shelter-wide.png"));
                        out.add(still(context, sim, x, y, WIDE_CELLS, marks, tag + "-shelter-wide-marked.png"));
                        System.out.printf("    [breach-shelter/%s] shelter %d..%d x %d..%d, %d survivors registered, "
                                        + "Locust %s%n", tag, r.left(), r.right(), r.bottom(), r.top(),
                                sim.getCivilianEvacuationTracker().registeredCount(),
                                sim.getColonyBreach() != null && sim.getColonyBreach().heldBattery() != 0L
                                        ? "holding fire" : "firing or absent");
                    }
                }
            }
            return out;
        }
    }

    private static SnapshotArtifact still(SnapshotContext context, BattleSimulation sim, float x, float y, float cells,
                                          ReviewAnnotations marks, String name) throws Exception {
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                LookReview.HEIGHT, x, y, cells);
        return new SnapshotArtifact(name, look.renderMarked(sim, marks));
    }

    private static ReviewAnnotations marks(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (CivilianRedoubt r : sim.getCivilianRedoubts()) {
            marks.box(r.left(), r.bottom(), r.right(), r.top(), r.isMain() ? "SHELTER" : "Holdout " + r.number(),
                    r.isMain() ? ReviewStyle.LANDING : ReviewStyle.ALLY);
        }
        return marks.build();
    }
}
