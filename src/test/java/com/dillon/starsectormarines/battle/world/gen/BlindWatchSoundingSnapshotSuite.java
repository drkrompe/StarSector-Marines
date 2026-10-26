package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
import java.util.Set;

/**
 * The Sounding, Blind Watch's authored map ({@code blind-watch-sounding.md}), through the look preset: the whole post,
 * the landing ground behind its ridge, each installation at play zoom (the mast and the rim guns, the dish field and
 * its control hut, the uplink on its knoll), the hab cluster, the boulder field, the rille's crossings and the cold
 * drop by the lander pad. Real
 * ground, nothing drawn over it, except the last frame: the whole map marked with the three sites, the approaches,
 * the defences and the vehicles' lanes, for reading the mission at a glance. Every camera is placed from the recipe's
 * own sites, so a run before a change and one after frame the same ground. Opt-in. 1920x1080.
 */
public final class BlindWatchSoundingSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final long SEED = 7150;
    private static final float SITE_CELLS = 100f, CLOSE_CELLS = 36f;

    @Override public String id() { return "blind-watch-sounding"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "The Sounding: Blind Watch's authored map, whole and by installation"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        TargetProfile warion = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.ROCK, SettlementLink.NONE);
        BlindWatchSounding.Layout layout = BlindWatchSounding.generate(SEED, warion);
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.buildMap(layout.map(), List.of(), layout.map().defensePosts, SEED).sim()) {
            int w = BlindWatchSounding.WIDTH, h = BlindWatchSounding.HEIGHT;
            float play = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
            frame(out, look, sim, "00-whole-post.png", w / 2f, h / 2f, Math.max(w, h * 16f / 9f));
            BlindWatchSounding.Area landing = layout.landing();
            frame(out, look, sim, "01-landing-ground-play.png", landing.centre().x() + 12, landing.centre().y(), play);
            BlindWatchSounding.Cell mast = layout.sites().get(BlindWatchSounding.Installation.MAST).plant();
            frame(out, look, sim, "02-rim-site.png", mast.x() - 2, mast.y() - 6, SITE_CELLS);
            frame(out, look, sim, "03-mast-play.png", mast.x() - 4, mast.y() - 2, play);
            BlindWatchSounding.Cell spurGun = layout.nests().get(0).cell();
            frame(out, look, sim, "04-spur-gun-close.png", spurGun.x(), spurGun.y(), CLOSE_CELLS);
            BlindWatchSounding.Cell dishes = layout.sites().get(BlindWatchSounding.Installation.DISH_FIELD).plant();
            frame(out, look, sim, "05-dish-field-play.png", dishes.x() - 4, dishes.y(), play);
            var hut = layout.controlHut();
            look.withoutRoofs();
            frame(out, look, sim, "06-control-hut-close.png", (hut.left + hut.right) / 2f, (hut.top + hut.bottom) / 2f,
                    CLOSE_CELLS);
            look.withRoofs();
            BlindWatchSounding.Cell uplink = layout.sites().get(BlindWatchSounding.Installation.UPLINK).plant();
            frame(out, look, sim, "07-uplink-knoll-play.png", uplink.x() - 8, uplink.y() + 3, play);
            BlindWatchSounding.Area hab = layout.habCluster();
            frame(out, look, sim, "08-hab-cluster-play.png", hab.centre().x(), hab.centre().y(), play);
            look.withoutRoofs();
            frame(out, look, sim, "09-hab-cluster-close.png", hab.centre().x() - 6, hab.centre().y(), CLOSE_CELLS * 1.4f);
            look.withRoofs();
            frame(out, look, sim, "10-boulder-field-play.png", 84, 94, play);
            BlindWatchSounding.Cell middle = layout.rilleCrossings().get(1);
            frame(out, look, sim, "11-rille-middle-crossing-play.png", middle.x(), middle.y() - 4, play);
            BlindWatchSounding.Cell west = layout.rilleCrossings().get(0);
            frame(out, look, sim, "12-rille-west-crossing-play.png", west.x(), west.y() + 4, play);
            frame(out, look, sim, "13-crater-floor-site.png", 168, 52, SITE_CELLS);
            BlindWatchSounding.Area pods = layout.podRacks();
            frame(out, look, sim, "14-cold-drop-play.png", pods.right() + 4, pods.top() + 3, play);
            look.refocus(w / 2f + .5f, h / 2f + .5f, Math.max(w, h * 16f / 9f));
            out.add(new SnapshotArtifact("15-objectives-overview.png", look.renderMarked(sim, objectiveMarks(layout))));
        }
        return out;
    }

    /** The mission read off the recipe's own sites: the three charges, the approaches, the defences, the lanes. */
    private static ReviewAnnotations objectiveMarks(BlindWatchSounding.Layout layout) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (var approach : layout.approaches().entrySet())
            marks.polyline(points(approach.getValue()), "Approach: " + approach.getKey().name().toLowerCase().replace('_', ' '),
                    ReviewStyle.ROUTE);
        BlindWatchSounding.Area landing = layout.landing();
        marks.box(landing.left(), landing.bottom(), landing.right(), landing.top(), "Landing ground", ReviewStyle.LANDING);
        String[] names = {"Charge: ranging mast (eyes)", "Charge: uplink (voice)", "Charge: dish field (ears)"};
        for (BlindWatchSounding.Installation installation : BlindWatchSounding.Installation.values()) {
            BlindWatchSounding.Area area = layout.sites().get(installation).area();
            marks.box(area.left(), area.bottom(), area.right(), area.top(), names[installation.ordinal()], ReviewStyle.KEEP);
        }
        var riggers = layout.riggersModule();
        marks.box(riggers.left, riggers.bottom, riggers.right, riggers.top, "Side: the riggers", ReviewStyle.ALLY);
        BlindWatchSounding.Area pods = layout.podRacks();
        marks.box(pods.left(), pods.bottom(), pods.right(), pods.top(), "Hidden: the cold store", ReviewStyle.OBJECTIVE);
        BlindWatchSounding.Cell console = layout.console();
        marks.label(console.x() + .5f, console.y() + .5f, "Hidden: the console", ReviewStyle.OBJECTIVE);
        marks.polyline(points(layout.mortarLane()), "Mortar carrier's lane", ReviewStyle.DUG_IN);
        for (BlindWatchSounding.Nest nest : layout.nests())
            marks.label(nest.cell().x() + .5f, nest.cell().y() + .5f, "Rim gun", ReviewStyle.FRONT);
        for (BlindWatchSounding.Pillbox pillbox : layout.pillboxes()) {
            var b = pillbox.building();
            marks.box(b.left, b.bottom, b.right, b.top, "Pillbox", ReviewStyle.FRONT);
        }
        BlindWatchSounding.Cell truck = layout.technicalStands().get(0);
        marks.label(truck.x() + .5f, truck.y() + .5f, "Rocket truck", ReviewStyle.FRONT);
        return marks.build();
    }

    private static List<ReviewAnnotation.Point> points(List<BlindWatchSounding.Cell> cells) {
        List<ReviewAnnotation.Point> points = new ArrayList<>();
        for (BlindWatchSounding.Cell c : cells) points.add(new ReviewAnnotation.Point(c.x() + .5f, c.y() + .5f));
        return points;
    }

    private static void frame(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              String name, float x, float y, float cells) throws Exception {
        look.refocus(x + 0.5f, y + 0.5f, cells);
        out.add(new SnapshotArtifact(name, look.render(sim, "", ReviewAnnotations.NONE)));
    }
}
