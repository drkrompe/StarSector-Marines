package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Impact marks ({@link ImpactDecals}, {@link DecalKind}) on real ground, through the look preset: every
 * {@link ImpactMark} laid by its own recipe in a column west to east, three rows on the ground (rounds flying east,
 * north-east and south-east) and a fourth row of its wall recipe. The wall row is laid on open ground too: a
 * building's walls lie under its roof, which draws over decals. Frames at play zoom and close on each half of the
 * columns, on green country and on arid ground. Owner-facing look frames only; there is no verdict.
 */
public final class ImpactDecalsSnapshotSuite implements SnapshotSuite {

    /** Every mark that leaves one, in the row's order west to east. */
    static final List<ImpactMark> MARKS = List.of(ImpactMark.values()).stream()
            .filter(mark -> mark != ImpactMark.NONE).toList();
    /** A typical splash for each mark's column: what explosive marks are sized to. */
    private static final float SPLASH = 1.2f;
    private static final float SPACING = 3.4f;
    private static final float[] HEADINGS = {0f, -35f, 35f};

    @Override public String id() { return "impact-decals"; }

    @Override public String label() { return "Impact decals (marks by calibre and weapon type)"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>(
                LookReview.record(context.modRoot(), shot("impact-decals", LookGround.verdant())));
        artifacts.addAll(LookReview.record(context.modRoot(),
                shot("impact-decals-arid", LookGround.of(SurfacePalette.ARID, 7L))));
        return artifacts;
    }

    static LookReview.Shot shot(String name, LookGround ground) {
        int width = Math.round(MARKS.size() * SPACING) + 4;
        int height = 18;
        return new LookReview.Shot(name, ground, width, height, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            Random rng = new Random(11);
            for (int i = 0; i < MARKS.size(); i++) {
                ImpactMark mark = MARKS.get(i);
                for (int row = 0; row < HEADINGS.length; row++) {
                    float[] at = placement.openNear(2.5f + i * SPACING, 3f + row * 4f);
                    lay(sim, rng, mark, at[0], at[1], false, HEADINGS[row]);
                }
            }
            for (int i = 0; i < MARKS.size(); i++) {
                float[] at = placement.openNear(2.5f + i * SPACING, 3f + HEADINGS.length * 4f);
                lay(sim, rng, MARKS.get(i), at[0], at[1], true, 0f);
            }
            return sim;
        }, placement -> {
            float cx = placement.site().centreX();
            float cy = placement.site().centreY();
            float halfSpan = MARKS.size() * SPACING / 4f;
            return List.of(
                    LookReview.View.play(cx, cy),
                    LookReview.View.close("close-west", cx - halfSpan, cy, 24f),
                    LookReview.View.close("close-east", cx + halfSpan, cy, 24f));
        }, 36, 6, 1).withStills(5);
    }

    /**
     * Lays one instance of {@code mark}, rolling again until it leaves something, so a mark that lays only now and
     * then (a beam, a flame, a small-arms round in the ground) still shows in its column.
     */
    private static void lay(BattleSimulation sim, Random rng, ImpactMark mark, float x, float y, boolean isWall,
                            float headingDeg) {
        List<Decal> laid = new ArrayList<>();
        for (int attempt = 0; attempt < 64 && laid.isEmpty(); attempt++) {
            ImpactDecals.spawnMark(laid::add, rng, mark, x, y, isWall, headingDeg, SPLASH);
        }
        laid.forEach(sim::addDecal);
    }
}
