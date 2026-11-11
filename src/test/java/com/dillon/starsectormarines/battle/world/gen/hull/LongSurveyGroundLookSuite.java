package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.setup.LongSurveyGround;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.HaulRoad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.Stake;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Long Survey's crash-site ground ({@code long-survey-arc.md}, Seeds; {@code fallen-hull-nouns.md}): each chapter's
 * owned map built as its setup builds it, on its home, at 1920x1080. Through the look preset: the whole map
 * ({@code <chapter>/1-whole.png}), the hull with its trench and debris ({@code 2-hull.png}) and the hull with its roof
 * lifted ({@code 3-interior.png}). A diagram of the claim, marked on the review frame and not a look picture
 * ({@code 4-claim.png}): the lattice points by ring, the haul roads, the starting stakes by stage, the forge and the
 * hull's aprons. Nothing is played. Opt-in; {@code -Dsnapshot.longSurvey.chapters=fall,line} names chapters.
 */
public final class LongSurveyGroundLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;

    @Override public String id() { return "long-survey-ground-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "The Long Survey's crash-site ground, each chapter on its owned seed"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        String asked = System.getProperty("snapshot.longSurvey.chapters", "").trim();
        String worlds = System.getProperty("snapshot.longSurvey.worlds", "").trim();
        List<SnapshotArtifact> out = new ArrayList<>();
        for (StoryMap chapter : StoryMap.values()) {
            if (!chapter.longSurvey()) continue;
            String tag = chapter.id.substring("long-survey-".length());
            if (!asked.isEmpty() && !List.of(asked.split(",")).contains(tag)) continue;
            SurfacePalette home = StoryMapPools.pool(chapter).home();
            if (!worlds.isEmpty()) {
                out.addAll(worldShots(context, chapter, worlds));
                continue;
            }
            if (Boolean.getBoolean("snapshot.longSurvey.boarding")) {
                out.addAll(boardingShots(context, chapter, home));
                continue;
            }
            LongSurveyGround.Ground ground = LongSurveyGround.ground(chapter, home);
            HullSite site = ground.hull();
            try (BattleSimulation sim = LongSurveyGround.stand(ground, home)) {
                int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
                BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                        w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f);
                out.add(new SnapshotArtifact(chapter.id + "/1-whole.png", look.render(sim, "")));
                Area hull = site.footprint(), trench = site.trench();
                int x0 = Math.min(hull.left(), trench.left()), x1 = Math.max(hull.right(), trench.right());
                int y0 = Math.min(hull.bottom(), trench.bottom()), y1 = Math.max(hull.top(), trench.top());
                look.refocus((x0 + x1) / 2f, (y0 + y1) / 2f, Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 30f);
                out.add(new SnapshotArtifact(chapter.id + "/2-hull.png", look.render(sim, "")));
                BattleReviewFrameRenderer open = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                        hull.centreX(), hull.centreY(), Math.max(hull.width(), hull.height() * 16f / 9f) + 16f)
                        .withoutRoofs();
                out.add(new SnapshotArtifact(chapter.id + "/3-interior.png", open.render(sim, "")));
                BattleReviewFrameRenderer diagram = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                        w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f);
                out.add(new SnapshotArtifact(chapter.id + "/4-claim.png", diagram.render(sim,
                        chapter.id + " seed " + ground.battleSeed() + " | lattice, haul roads and starting stakes",
                        claim(site))));
            }
            System.out.printf(Locale.ROOT, "[%s] %s: hull %s, %d lattice points, %d roads, %d stakes, problems %s%n",
                    id(), chapter.id, site.footprint(), site.lattice().size(), site.roads().size(),
                    site.stakes().size(), site.problems());
        }
        return out;
    }

    /**
     * One hull shot per world the ground is skinned for, framed alike ({@code <chapter>/world-<label>.png}):
     * {@code -Dsnapshot.longSurvey.worlds=chicomoztoc:ARID,kazeron:ROCK} names each world by a label and its palette.
     */
    private static List<SnapshotArtifact> worldShots(SnapshotContext context, StoryMap chapter, String worlds)
            throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String named : worlds.split(",")) {
            String[] parts = named.trim().split(":");
            SurfacePalette world = SurfacePalette.valueOf(parts[parts.length - 1].trim().toUpperCase(Locale.ROOT));
            String label = parts[0].trim().toLowerCase(Locale.ROOT);
            LongSurveyGround.Ground ground = LongSurveyGround.ground(chapter, world);
            Area hull = ground.hull().footprint(), trench = ground.hull().trench();
            int x0 = Math.min(hull.left(), trench.left()), x1 = Math.max(hull.right(), trench.right());
            int y0 = Math.min(hull.bottom(), trench.bottom()), y1 = Math.max(hull.top(), trench.top());
            try (BattleSimulation sim = LongSurveyGround.stand(ground, world)) {
                BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                        (x0 + x1) / 2f, (y0 + y1) / 2f, Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 30f);
                out.add(new SnapshotArtifact(chapter.id + "/world-" + label + ".png", look.render(sim, "")));
            }
        }
        return out;
    }

    /**
     * Boarding the ship ({@code <chapter>/boarding-<n>.png}), roofs on, as a played battle draws them: the hull from
     * outside; a fire team in the aft breach with the roof lifted over the hold they stand in, as standing in it lifts
     * it, and the rest of the ship still roofed; and the forge hall with its roof lifted. Nobody moves.
     * {@code -Dsnapshot.longSurvey.boarding=true}.
     */
    private static List<SnapshotArtifact> boardingShots(SnapshotContext context, StoryMap chapter, SurfacePalette home)
            throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        LongSurveyGround.Ground ground = LongSurveyGround.ground(chapter, home);
        HullSite site = ground.hull();
        try (BattleSimulation sim = LongSurveyGround.stand(ground, home)) {
            Area hull = site.footprint();
            int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                    w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f);
            out.add(new SnapshotArtifact(chapter.id + "/breakup-0-whole.png", look.render(sim, "")));
            for (int k = 0; k < site.sections().size(); k++) {
                Area s = site.sections().get(k);
                look.refocus(s.centreX(), s.centreY(), Math.max(s.width(), s.height() * 16f / 9f) + 28f);
                out.add(new SnapshotArtifact(chapter.id + "/breakup-" + (k + 1) + "-"
                        + HullPlan.SECTIONS.get(k).name() + ".png", look.render(sim, "")));
            }
            look.refocus(hull.centreX(), hull.centreY(), Math.max(hull.width(), hull.height() * 16f / 9f) + 24f);
            out.add(new SnapshotArtifact(chapter.id + "/boarding-1-outside.png", look.render(sim, "")));

            // The aft rent into the first hold.
            List<Area> ways = site.interior().entrances();
            int aft = 0;
            List<HullPlan.Entrance> planned = HullPlan.plan().entrances();
            for (int k = 0; k < planned.size(); k++) if (planned.get(k).name().equals(HullPlan.BREACH + ".aft")) aft = k;
            Area breach = ways.get(aft);
            Area hold = site.interior().chambers().get(0);
            int placed = 0;
            for (int y = breach.bottom() - 1; y <= breach.top() + 1 && placed < 5; y++) {
                for (int x = breach.left(); x <= breach.right() && placed < 5; x++) {
                    if (!sim.getGrid().isWalkable(x, y) || (x + y) % 2 != 0) continue;
                    sim.spawn(new EntitySpec("Boarder " + (++placed), Faction.MARINE, UnitType.MARINE, x, y));
                }
            }
            Building inside = buildingAt(sim, hold.centreX(), hold.centreY());
            if (inside != null) inside.currentAlpha = inside.targetAlpha = 0f;
            float cx = (2 * breach.centreX() + hold.centreX()) / 3f, cy = (2 * breach.centreY() + hold.centreY()) / 3f;
            look.refocus(cx, cy, 44f);
            out.add(new SnapshotArtifact(chapter.id + "/boarding-2-breach.png", look.render(sim, "")));
            if (inside != null) inside.currentAlpha = inside.targetAlpha = 1f;

            Area forge = site.forgeHall();
            Building vault = buildingAt(sim, forge.centreX(), forge.centreY());
            if (vault != null) vault.currentAlpha = vault.targetAlpha = 0f;
            look.refocus(forge.centreX(), forge.centreY(), 56f);
            out.add(new SnapshotArtifact(chapter.id + "/boarding-3-forge.png", look.render(sim, "")));
            System.out.printf(Locale.ROOT, "[long-survey-ground-look] %s boarding: %d boarders, hold %s, forge %s%n",
                    chapter.id, placed, inside == null ? "-" : inside.id, vault == null ? "-" : vault.id);
        }
        return out;
    }

    private static Building buildingAt(BattleSimulation sim, int x, int y) {
        int id = sim.getTopology().getBuildingId(x, y);
        return id == 0 ? null : sim.getBuildings().get(id);
    }

    /** The claim drawn over the map: haul roads, lattice points by ring, stakes by stage, the forge and the aprons. */
    static ReviewAnnotations claim(HullSite site) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (HaulRoad road : site.roads()) {
            List<ReviewAnnotation.Point> points = new ArrayList<>();
            for (Cell c : road.cells()) points.add(new ReviewAnnotation.Point(c.x() + 0.5f, c.y() + 0.5f));
            marks.polyline(points, null, ReviewStyle.ROUTE);
        }
        for (LatticePoint p : site.lattice()) {
            Cell c = p.cell();
            marks.box(c.x() - 2, c.y() - 2, c.x() + 2, c.y() + 2, "r" + p.ring(), ReviewStyle.POSITION);
        }
        for (Stake stake : site.stakes()) {
            Cell c = site.point(stake.point()).cell();
            marks.box(c.x() - 3, c.y() - 3, c.x() + 3, c.y() + 3, stake.stage().name().toLowerCase(Locale.ROOT),
                    ReviewStyle.OBJECTIVE);
        }
        Area f = site.forgeHall();
        marks.box(f.left(), f.bottom(), f.right(), f.top(), "forge", ReviewStyle.KEEP);
        Area b = site.bay();
        marks.box(b.left(), b.bottom(), b.right(), b.top(), "bay", ReviewStyle.NOTE);
        for (var apron : site.interior().aprons()) {
            Area a = apron.ground();
            marks.box(a.left(), a.bottom(), a.right(), a.top(), null, ReviewStyle.LANDING);
        }
        return marks.build();
    }
}
