package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The picket nests that close the line's gaps ({@link CordonGapNests}) on real pooled Cordon maps, through the look
 * preset at play zoom: for each shot asked for, the added nests of one picket course with a swarm pack coming at the
 * line through them, the still taken where the guns' fire is thickest. Each shot is also written with the gap law's
 * rings ({@link CordonGapLookSuite#rings}) over the same frame, so which guns meet is visible.
 *
 * <p>{@code -Dsnapshot.cordonNests.shots} lists the shots as {@code variant:density:biome:seed:course} (the course
 * as its western redoubt's number), comma-separated. Prints each added nest, whether it stands at a street mouth, and
 * the rounds the added nests fired. Opt-in: owner stills, not part of {@code all}.
 */
public final class CordonGapNestsLookSuite implements SnapshotSuite {

    static final int W = LookReview.WIDTH, H = LookReview.HEIGHT;
    static final int TICKS = 480, EVERY = 6, FIRST_STILL = 60;
    private static final String DEFAULT_SHOTS =
            "line:urban:verdant:1:3,line:town:rock:8:2,line:rural:verdant:15:3";

    @Override public String id() { return "cordon-gap-nests"; }

    @Override public String label() { return "Cordon: picket nests closing the line's gaps, on pooled maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        DiskRegistries.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String spec : System.getProperty("snapshot.cordonNests.shots", DEFAULT_SHOTS).split(",")) {
            String[] p = spec.trim().split(":");
            CordonVariant variant = CordonVariant.valueOf(p[0].toUpperCase(Locale.ROOT));
            CordonPlan.Density density = CordonPlan.Density.valueOf(p[1].toUpperCase(Locale.ROOT));
            SurfacePalette biome = SurfacePalette.valueOf(p[2].toUpperCase(Locale.ROOT));
            long seed = Long.parseLong(p[3]);
            int course = Integer.parseInt(p[4]);
            out.addAll(shot(context, variant, density, biome, seed, course, String.join("-", p)));
        }
        return out;
    }

    private List<SnapshotArtifact> shot(SnapshotContext context, CordonVariant variant, CordonPlan.Density density,
                                        SurfacePalette biome, long seed, int course, String name) throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.map(variant, density, biome, seed);
        Laid picket = cordon.map().layout.find(CordonPlan.PICKET_LINE + course + "-" + (course + 1));
        int added = CordonGapNests.added(picket);
        if (added == 0) {
            System.out.printf(Locale.ROOT, "    [cordon-gap-nests] %s: no nests were added to course %d%n", name, course);
            return List.of();
        }
        List<int[]> nests = picket.nests().subList(picket.nests().size() - added, picket.nests().size());
        int front = CordonFront.frontward(cordon.plan());
        CordonGapNests.Ground ground = CordonGapNests.mapGround(cordon.map().grid, cordon.map().topology, Set.of());
        int[] focus = nests.get(0);
        for (int[] n : nests) {
            boolean mouth = CordonGapNests.mouth(ground, n[0], n[1], front);
            System.out.printf(Locale.ROOT, "    [cordon-gap-nests] %s: added nest at %d,%d%s%n", name, n[0], n[1],
                    mouth ? " (street mouth)" : "");
            if (mouth) focus = n;
        }
        for (CordonGapLaw.Gap gap : CordonSetup.gaps(cordon.map(), cordon.plan())) {
            System.out.printf(Locale.ROOT, "    [cordon-gap-nests] %s: gap %d-%d widest step %.1f (law %.1f)%n", name,
                    gap.west(), gap.east(), gap.widestStep(), CordonGapLaw.LIMIT);
        }
        List<int[]> guns = CordonGapLookSuite.lineGuns(cordon);
        float fx = focus[0] + 0.5f, fy = focus[1] + 0.5f - front * 6, cells = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
        try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD)) {
            List<Long> pack = CordonGapLookSuite.pack(sim, focus[0], focus[1] + front * CordonGapLookSuite.PACK_OUT,
                    focus[0], focus[1] - front * CordonGapLookSuite.PACK_THROUGH);
            ReviewImpactFx effects = new ReviewImpactFx(context.modRoot());
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, fx, fy, cells);
            BufferedImage best = null;
            int bestPainted = -1, bestTick = -1, fromAdded = 0, fired = 0;
            Set<Long> shooters = new HashSet<>();
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                effects.feed(sim);
                for (ShotEvent shot : sim.getShotsThisFrame()) {
                    if (shot.shooterFaction != Faction.ALLY) continue;
                    fired++;
                    for (int[] n : nests) {
                        if (Math.abs(shot.fromX - (n[0] + 0.5f)) < 1f && Math.abs(shot.fromY - (n[1] + 0.5f)) < 1f) {
                            fromAdded++;
                            shooters.add(shot.shooterId);
                        }
                    }
                }
                if (tick < FIRST_STILL || tick % EVERY != 0) continue;
                BufferedImage frame = renderer.render(sim, "", ReviewAnnotations.NONE, effects);
                if (effects.lastPainted() > bestPainted) {
                    bestPainted = effects.lastPainted();
                    bestTick = tick;
                    best = frame;
                }
            }
            int alive = 0;
            for (long id : pack) if (sim.world().isAlive(id)) alive++;
            int standing = 0;
            for (long id : shooters) if (sim.world().isAlive(id)) standing++;
            System.out.printf(Locale.ROOT, "    [cordon-gap-nests] %s: %d allied rounds, %d from the %d added nests"
                            + " (%d of them fired, %d still standing); pack %d of %d alive; still at tick %d%n", name,
                    fired, fromAdded, nests.size(), shooters.size(), standing, alive, pack.size(), bestTick);
            BufferedImage rings = new BufferedImage(best.getWidth(), best.getHeight(), BufferedImage.TYPE_INT_RGB);
            rings.getGraphics().drawImage(best, 0, 0, null);
            CordonGapLookSuite.rings(rings, sim.getGrid().getWidth(), sim.getGrid().getHeight(), fx, fy, cells, guns,
                    new CordonGapLookSuite.Gun(CordonGapLaw.REACH, CordonGapLaw.HIT_SPREAD, CordonGapLaw.SPLASH));
            return List.of(new SnapshotArtifact(name + ".png", best), new SnapshotArtifact(name + "-rings.png", rings));
        }
    }
}
