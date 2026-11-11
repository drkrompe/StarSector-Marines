package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.derelict.ClaimLattice;
import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.HaulerWork;
import com.dillon.starsectormarines.battle.derelict.Salvage;
import com.dillon.starsectormarines.battle.derelict.StripperWork;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.LongSurveyGround;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Long Survey's picks shown working in their real places, for the owner's picks page: every frame from the look
 * preset at 1920x1080, launched as the debug mission player launches each chapter and played a short while, with short
 * labels drawn on so the faction's parts read. A frame's subject stands on clear ground it could stand on (a printed
 * walker at the bay mouth, a stripper beside the salvage it cuts, a hauler on a haul road); every other unit in the frame
 * is moved off it for the frame and put back after, so nothing overlaps a wall, frame or building. The stake's tripod,
 * which is not built, is its candidate art laid over the spire's own places, marked as a stand-in. Nothing is judged.
 * Opt-in.
 */
public final class LongSurveyPicksLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    /** The candidate art `export.py` writes, for the tripod stand-in. */
    private static final String ART = "build/fallen-hull-art/graphics/battle/fallen-hull";
    private static final float STAKE_PX = 128f;

    @Override public String id() { return "long-survey-picks-look"; }

    @Override public String label() { return "The Long Survey's picks working in their real places, labelled"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = new HeadlessStrings(context.modRoot())) {
            chapter(context, DebugMissionCatalog.Scenario.LONG_SURVEY_FALL, out, this::fall);
            claim(context, out);
        }
        return out;
    }

    private interface Frames {
        void shoot(SnapshotContext context, BattleSimulation sim, FallenHull hull, DerelictSecurityDirector director,
                   List<SnapshotArtifact> out) throws Exception;
    }

    /** Seconds The Fall plays before it is shot, and the longest the claim plays for its stripper and hauler. */
    private static final float WALKERS_SECONDS = 150f, WORK_SECONDS = 900f;
    /** Salvage piles laid on the claim for the stripper to cut, and the metal in each (UNTUNED stand-in). */
    private static final int PILES = 3;
    private static final float PILE_METAL = 400f;

    /**
     * The claim: no chapter with stakes is built as a battle yet, so the Stakes chapter's ground is stood up and the
     * hull's rules installed on it as The Fall installs them, with its starting stakes, a stripper, a hauler and a line
     * frame at the bay mouth, and a few salvage piles beside the town's buildings near a haul road's far end (no ground
     * lays salvage yet). It plays until a stripper is cutting at a pile and a loaded hauler is on a haul road, and is
     * shot where they really are.
     */
    private void claim(SnapshotContext context, List<SnapshotArtifact> out) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        StoryMap chapter = StoryMap.LONG_SURVEY_STAKES;
        SurfacePalette home = StoryMapPools.pool(chapter).home();
        LongSurveyGround.Ground ground = LongSurveyGround.ground(chapter, home);
        try (BattleSimulation sim = LongSurveyGround.stand(ground, home)) {
            FallenHullSite probe = LongSurveyGround.rulesSite(ground.hull(), List.of(), List.of(), 0f);
            List<FallenHullSite.Pile> piles = townPiles(sim, probe);
            List<FallenHullSite.Posting> walkers = new ArrayList<>();
            for (DerelictWalkerFrame f : List.of(DerelictWalkerFrame.STRIPPER, DerelictWalkerFrame.HAULER,
                    DerelictWalkerFrame.LINE)) {
                walkers.add(new FallenHullSite.Posting(f, probe.mouthX(), probe.mouthY()));
            }
            FallenHull hull = FallenHull.install(sim, LongSurveyGround.rulesSite(ground.hull(), piles, walkers, 200f));
            DerelictSecurityDirector director = sim.derelictSecurity();
            if (hull == null || director == null) {
                log("the hull would not stand on %s", chapter);
                return;
            }
            play(sim, 3f);
            log("claim: piles %d, stakes %s", piles.size(), stagesOf(hull));
            stakes(context, sim, hull, director, out);
            work(context, sim, hull, out);
        }
    }

    /**
     * Up to {@link #PILES} open cells beside a town building, each with room for a stripper beside it, nearest the far
     * ends of the haul roads (so the hauler walks a road home), none within 6 cells of another.
     */
    private static List<FallenHullSite.Pile> townPiles(BattleSimulation sim, FallenHullSite site) {
        List<int[]> ends = new ArrayList<>();
        for (int[] road : site.haulRoads()) {
            int n = road.length / 2;
            int i = site.hull().chebyshev(road[0], road[1]) > site.hull().chebyshev(road[2 * n - 2], road[2 * n - 1])
                    ? 0 : n - 1;
            ends.add(new int[]{road[2 * i], road[2 * i + 1]});
        }
        List<FallenHullSite.Pile> out = new ArrayList<>();
        for (int[] end : ends) {
            int[] best = null;
            int bestD = Integer.MAX_VALUE;
            for (int y = end[1] - 14; y <= end[1] + 14; y++) {
                for (int x = end[0] - 14; x <= end[0] + 14; x++) {
                    if (!clear(sim, x, y, 1) || !besideBuilding(sim, x, y)) continue;
                    int d = Math.max(Math.abs(x - end[0]), Math.abs(y - end[1]));
                    boolean apart = true;
                    for (FallenHullSite.Pile p : out) apart &= Math.max(Math.abs(p.x() - x), Math.abs(p.y() - y)) > 6;
                    if (apart && d < bestD) {
                        bestD = d;
                        best = new int[]{x, y};
                    }
                }
            }
            if (best != null) out.add(new FallenHullSite.Pile(best[0], best[1], PILE_METAL));
            if (out.size() >= PILES) break;
        }
        return out;
    }

    /** A cell of the building beside (x, y), or (x, y) itself when none is within 3 cells. */
    private static int[] buildingBeside(BattleSimulation sim, int x, int y) {
        for (int d = 1; d <= 3; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (sim.getGrid().inBounds(x + dx, y + dy) && sim.getTopology().getBuildingId(x + dx, y + dy) != 0)
                        return new int[]{x + dx, y + dy};
                }
            }
        }
        return new int[]{x, y};
    }

    private static boolean besideBuilding(BattleSimulation sim, int x, int y) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 2 : d == 1 ? -2 : 0), ny = y + (d == 2 ? 2 : d == 3 ? -2 : 0);
            if (sim.getGrid().inBounds(nx, ny) && sim.getTopology().getBuildingId(nx, ny) != 0) return true;
        }
        return false;
    }

    /** Plays on until a stripper cuts at a pile and a loaded hauler walks a road, shooting each where it is. */
    private void work(SnapshotContext context, BattleSimulation sim, FallenHull hull, List<SnapshotArtifact> out)
            throws Exception {
        World world = sim.world();
        boolean cutting = false, hauling = false;
        float t = 0f;
        for (; t < WORK_SECONDS && !(cutting && hauling); t += 2f) {
            if (!play(sim, 2f)) break;
            if (!cutting) {
                for (Map.Entry<Integer, StripperWork> e : hull.strippers().entrySet()) {
                    if (e.getValue().load() <= 0f || sim.squadMemberCount(e.getKey()) <= 0) continue;
                    long id = sim.squadMemberAt(e.getKey(), 0);
                    Salvage pile = nearestPile(hull, world.x(id), world.y(id));
                    if (pile == null || Math.hypot(pile.x - world.x(id), pile.y - world.y(id))
                            > StripperWork.REACH_CELLS + 1.5f) continue;
                    log("stripper %d cutting at (%.0f, %.0f) after %.0f s, load %.0f", id, pile.x, pile.y, t,
                            e.getValue().load());
                    stripperShot(context, sim, id, pile, out);
                    cutting = true;
                    break;
                }
            }
            if (!hauling) {
                for (Map.Entry<Integer, HaulerWork> e : hull.haulers().entrySet()) {
                    if (e.getValue().load() <= 0f || sim.squadMemberCount(e.getKey()) <= 0) continue;
                    long id = sim.squadMemberAt(e.getKey(), 0);
                    int[] road = roadUnder(hull.site(), world.x(id), world.y(id));
                    if (road == null) continue;
                    log("hauler %d on a road after %.0f s, load %.0f", id, t, e.getValue().load());
                    haulerShot(context, sim, hull, id, road, out);
                    hauling = true;
                    break;
                }
            }
        }
        if (!cutting || !hauling) log("after %.0f s: stripper cutting %s, hauler on a road %s", t, cutting, hauling);
        // The ground stood up for looking does not play: each is set where its work puts it instead.
        if (!cutting) placedStripper(context, sim, hull, out);
        if (!hauling) placedHauler(context, sim, hull, out);
    }

    /** The stripper set on clear ground within its reach of the nearest pile to the hull, as if cutting it. */
    private void placedStripper(SnapshotContext context, BattleSimulation sim, FallenHull hull,
                                List<SnapshotArtifact> out) throws Exception {
        long stripper = firstOf(sim, hull.strippers().keySet());
        Salvage pile = null;
        int[] at = null;
        int best = Integer.MAX_VALUE;
        for (Salvage s : hull.salvage()) {
            if (s.kind != Salvage.Kind.STRUCTURE) continue;
            int[] c = clearRing(sim, (int) s.x, (int) s.y, 1, 1, (int) StripperWork.REACH_CELLS + 1);
            int d = hull.site().hull().chebyshev((int) s.x, (int) s.y);
            if (c != null && d < best) {
                best = d;
                pile = s;
                at = c;
            }
        }
        if (stripper == 0L || pile == null) {
            log("no stripper (%d) or no pile with room beside it", stripper);
            return;
        }
        move(sim, stripper, at[0] + .5f, at[1] + .5f);
        stripperShot(context, sim, stripper, pile, out);
    }

    /** The hauler set on clear ground on the longest haul road, two fifths of the way out, carrying a load. */
    private void placedHauler(SnapshotContext context, BattleSimulation sim, FallenHull hull,
                              List<SnapshotArtifact> out) throws Exception {
        Map.Entry<Integer, HaulerWork> work = null;
        for (Map.Entry<Integer, HaulerWork> e : hull.haulers().entrySet()) {
            if (sim.squadMemberCount(e.getKey()) > 0) work = e;
        }
        int[] road = null, at = null;
        for (int[] r : hull.site().haulRoads()) {
            int n = r.length / 2, i = n * 2 / 5;
            int[] c = n < 8 ? null : clearNear(sim, r[2 * i], r[2 * i + 1], 2, 3);
            if (c != null && (road == null || r.length > road.length)) {
                road = r;
                at = c;
            }
        }
        if (work == null || road == null) {
            log("no hauler, or no clear ground on a haul road");
            return;
        }
        long hauler = sim.squadMemberAt(work.getKey(), 0);
        work.getValue().takeOn(work.getValue().room());
        move(sim, hauler, at[0] + .5f, at[1] + .5f);
        haulerShot(context, sim, hull, hauler, road, out);
    }

    private static Salvage nearestPile(FallenHull hull, float x, float y) {
        Salvage best = null;
        double bestD = Double.MAX_VALUE;
        for (Salvage s : hull.salvage()) {
            if (s.kind != Salvage.Kind.STRUCTURE) continue;
            double d = Math.hypot(s.x - x, s.y - y);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** The road whose line runs within 2 cells of (x, y), away from both its ends, or null. */
    private static int[] roadUnder(FallenHullSite site, float x, float y) {
        for (int[] road : site.haulRoads()) {
            int n = road.length / 2;
            for (int i = 3; i < n - 3; i++) {
                if (Math.hypot(road[2 * i] + .5f - x, road[2 * i + 1] + .5f - y) <= 2f) return road;
            }
        }
        return null;
    }

    /** A stripper cutting at its pile, where it stands. */
    private void stripperShot(SnapshotContext context, BattleSimulation sim, long stripper, Salvage pile,
                              List<SnapshotArtifact> out) throws Exception {
        World world = sim.world();
        float x = world.renderX(stripper), y = world.renderY(stripper);
        float cx = (pile.x + x) / 2f, cy = (pile.y + y) / 2f, cells = 24f;
        Map<Long, float[]> moved = clearFrame(sim, cx, cy, cells, Set.of(stripper));
        Shot s = shot(context, sim, cx, cy, cells, true);
        int[] wall = buildingBeside(sim, (int) pile.x, (int) pile.y);
        s.label("salvage: this town building's metal (a stand-in pile)", wall[0] + .5f, wall[1] + .5f, -360, -190);
        s.label("stripper: cutting it, a load at a time", x, y + 1.5f, 300, 190);
        out.add(s.done("stripper.png"));
        restore(sim, moved);
    }

    /** A loaded hauler on its haul road, where it walks, the road drawn and the way home marked. */
    private void haulerShot(SnapshotContext context, BattleSimulation sim, FallenHull hull, long hauler, int[] road,
                            List<SnapshotArtifact> out) throws Exception {
        World world = sim.world();
        float x = world.renderX(hauler), y = world.renderY(hauler), cells = 30f;
        Map<Long, float[]> moved = clearFrame(sim, x, y, cells, Set.of(hauler));
        Shot s = shot(context, sim, x, y, cells, true);
        s.road(road);
        FallenHullSite.Rect box = hull.site().hull();
        int n = road.length / 2, i = 0;
        double best = Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            double d = Math.hypot(road[2 * k] + .5f - x, road[2 * k + 1] + .5f - y);
            if (d < best) {
                best = d;
                i = k;
            }
        }
        boolean hullAtEnd = box.chebyshev(road[2 * n - 2], road[2 * n - 1]) < box.chebyshev(road[0], road[1]);
        int j = hullAtEnd ? Math.min(n - 1, i + 6) : Math.max(0, i - 6);
        s.arrow(road[2 * i] + .5f, road[2 * i + 1] + .5f, road[2 * j] + .5f, road[2 * j + 1] + .5f);
        s.label("hauler, loaded: carries the metal home; killed loaded, the load is lost", x, y + 1f, 0, -190);
        int k = hullAtEnd ? Math.max(0, i - 8) : Math.min(n - 1, i + 8);
        s.label("haul road: the arrow points back to the hull", road[2 * k] + .5f, road[2 * k + 1] + .5f, 0, 190);
        out.add(s.done("hauler.png"));
        restore(sim, moved);
    }

    private static void chapter(SnapshotContext context, DebugMissionCatalog.Scenario scenario,
                                List<SnapshotArtifact> out, Frames frames) throws Exception {
        var mission = scenario.mission();
        var ops = new MarineOpsContext(null);
        ops.resetDebugPreset(mission);
        var preset = DebugMissionCatalog.preset(mission);
        var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
        var plan = MissionLaunch.captureScenario(ops, mission, lift, FlybyRoster.EMPTY, List.of(), List.of(), null,
                mission.battleSeed);
        try (var prepared = MissionLaunch.finishSimulation(ops, mission, FlybyRoster.EMPTY, plan, plan.builder().get())) {
            BattleSimulation sim = prepared.simulation();
            play(sim, WALKERS_SECONDS);
            DerelictSecurityDirector director = sim.derelictSecurity();
            FallenHull hull = director == null ? null : director.hull();
            if (hull == null) {
                log("%s: no fallen hull", scenario);
                return;
            }
            frames.shoot(context, sim, hull, director, out);
        }
    }

    /** Plays {@code seconds}; false once the battle stops advancing. */
    private static boolean play(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) {
            int before = sim.getSimTickIndex();
            sim.advance(BattleSimulation.TICK_DT);
            if (sim.getSimTickIndex() == before) return false;
        }
        return true;
    }

    private static int countAt(FallenHull hull, SurveyStake.Stage stage) {
        int n = 0;
        for (SurveyStake s : hull.stakes().values()) if (s.stage() == stage) n++;
        return n;
    }

    private static String stagesOf(FallenHull hull) {
        StringBuilder sb = new StringBuilder();
        for (SurveyStake.Stage st : SurveyStake.Stage.values()) sb.append(st).append('=').append(countAt(hull, st)).append(' ');
        return sb.toString();
    }

    // ---- The Fall: the hull, its forge, a stripper and a hauler

    private void fall(SnapshotContext context, BattleSimulation sim, FallenHull hull, DerelictSecurityDirector director,
                      List<SnapshotArtifact> out) throws Exception {
        World world = sim.world();
        FallenHullSite site = hull.site();
        FallenHullSite.Rect box = site.hull();
        float hx = box.centreX() + .5f, hy = box.centreY() + .5f;
        float span = Math.max(box.right() - box.left(), box.top() - box.bottom()) + 1;

        // The whole hull at the end of its trench, roofed.
        Shot whole = shot(context, sim, hx, hy, span * 1.45f, true);
        whole.label("Fallen hull: an Explorarium survey mothership, broken in three", hx, box.top() + 1f, 0, -150);
        List<FallenHullSite.Rect> sections = site.sections();
        String[] names = {"stern", "midsection", "bow"};
        for (int i = 0; i < sections.size() && sections.size() > 1; i++) {
            FallenHullSite.Rect s = sections.get(i);
            String name = sections.size() == names.length ? names[i] : "section " + (i + 1);
            whole.label(name, s.centreX() + .5f, s.bottom(), 0, 110);
        }
        whole.label("autoforge, in its forge hall", site.forgeX() + .5f, site.forgeY() + .5f, 140, -40);
        out.add(whole.done("hull-whole.png"));

        // The hull closer, its roof the ship's own painting, torn at the breaks.
        FallenHullSite.Rect mid = sections.get(sections.size() / 2);
        Shot close = shot(context, sim, mid.centreX() + .5f, mid.centreY() + .5f, 56f, true);
        close.label("hull roof: the ship's own plating, burnt", mid.centreX() + .5f, mid.centreY() + 3.5f, -260, -170);
        if (sections.size() > 1) {
            FallenHullSite.Rect a = sections.get(sections.size() / 2 - 1);
            float bx = (a.right() + mid.left() + 1) / 2f, by = (mid.bottom() + mid.top() + 1) / 2f;
            if (Math.abs(a.centreY() - mid.centreY()) > Math.abs(a.centreX() - mid.centreX())) {
                bx = (mid.left() + mid.right() + 1) / 2f;
                by = (Math.min(a.top(), mid.top()) + Math.max(a.bottom(), mid.bottom()) + 1) / 2f;
            }
            close.label("where the hull broke apart: its sections lie apart, torn ends open", bx, by, 0, 190);
        }
        out.add(close.done("hull-close.png"));

        forge(context, sim, hull, director, out);
    }

    /** The forge in its hall, roofs off, and the newest printed walker leaving by the bay mouth. */
    private void forge(SnapshotContext context, BattleSimulation sim, FallenHull hull, DerelictSecurityDirector director,
                       List<SnapshotArtifact> out) throws Exception {
        World world = sim.world();
        FallenHullSite site = hull.site();
        long forge = hull.forgeId();
        if (sim.resolveUnit(forge) == 0L) {
            log("the forge is gone");
            return;
        }
        float fx = world.renderX(forge), fy = world.renderY(forge);
        long walker = 0L;
        float best = Float.MAX_VALUE;
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.frame() == DerelictWalkerFrame.STRIPPER || w.frame() == DerelictWalkerFrame.HAULER) continue;
            if (sim.squadMemberCount(w.squadId()) <= 0) continue;
            long id = sim.squadMemberAt(w.squadId(), 0);
            float d = (float) Math.hypot(world.x(id) - site.mouthX(), world.y(id) - site.mouthY());
            if (d < best) {
                best = d;
                walker = id;
            }
        }
        // Just out of the forge, on the hall's deck, a step toward the bay mouth.
        float mx = site.mouthX() - fx, my = site.mouthY() - fy, ml = (float) Math.max(1e-3, Math.hypot(mx, my));
        int[] at = deckNear(sim, Math.round(fx + mx / ml * 7f), Math.round(fy + my / ml * 7f), 2, 5);
        float cx = fx, cy = fy, cells = 34f;
        if (walker != 0L && at != null) {
            cx = (fx + at[0] + .5f) / 2f;
            cy = (fy + at[1] + .5f) / 2f;
        }
        Set<Long> keep = new HashSet<>(List.of(forge));
        if (walker != 0L) keep.add(walker);
        Map<Long, float[]> moved = clearFrame(sim, cx, cy, cells, keep);
        float[] home = walker != 0L ? new float[]{world.x(walker), world.y(walker)} : null;
        if (walker != 0L && at != null) move(sim, walker, at[0] + .5f, at[1] + .5f);
        Shot s = shot(context, sim, cx, cy, cells, false);
        s.label("autoforge (ring forge): prints walkers and stakes from metal", fx, fy + 2.5f, 0, -170);
        if (walker != 0L && at != null) {
            s.label("printed walker, just out of the forge, walking to the bay mouth", at[0] + .5f, at[1] + .5f,
                    0, 170);
        }
        out.add(s.done("forge.png"));
        if (home != null) move(sim, walker, home[0], home[1]);
        restore(sim, moved);
    }

    // ---- Stakes: the claim, and a stake close

    private void stakes(SnapshotContext context, BattleSimulation sim, FallenHull hull,
                        DerelictSecurityDirector director, List<SnapshotArtifact> out) throws Exception {
        World world = sim.world();
        FallenHullSite site = hull.site();
        Map<Integer, ClaimLattice.Point> points = new LinkedHashMap<>();
        for (ClaimLattice.Point p : site.lattice()) points.put(p.id(), p);
        FallenHullSite.Rect box = site.hull();
        int minX = box.left(), minY = box.bottom(), maxX = box.right(), maxY = box.top();
        for (int id : hull.stakes().keySet()) {
            ClaimLattice.Point p = points.get(id);
            if (p == null) continue;
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        float cx = (minX + maxX + 1) / 2f, cy = (minY + maxY + 1) / 2f;
        float cells = Math.max(80f, Math.max(maxX - minX, (maxY - minY) * 16f / 9f) * 1.25f);

        // The claim: the hull, its stakes and their links, the haul roads.
        Shot claim = shot(context, sim, cx, cy, cells, true);
        for (int[] road : site.haulRoads()) claim.road(road);
        for (Map.Entry<Integer, SurveyStake> e : hull.stakes().entrySet()) {
            ClaimLattice.Point p = points.get(e.getKey());
            if (p == null) continue;
            int parent = e.getValue().parent;
            ClaimLattice.Point q = points.get(parent);
            float[] to = q != null ? new float[]{q.x() + .5f, q.y() + .5f}
                    : box.nearest(p.x(), p.y()) == null ? null
                    : new float[]{box.nearest(p.x(), p.y())[0] + .5f, box.nearest(p.x(), p.y())[1] + .5f};
            if (to != null) claim.link(p.x() + .5f, p.y() + .5f, to[0], to[1]);
        }
        claim.label("fallen hull: its forge prints stakes and walkers from hauled metal", box.centreX() + .5f,
                box.top() + 1f, 0, -140);
        Set<SurveyStake.Stage> named = new HashSet<>();
        int side = 1;
        for (Map.Entry<Integer, SurveyStake> e : hull.stakes().entrySet()) {
            SurveyStake.Stage stage = e.getValue().stage();
            ClaimLattice.Point p = points.get(e.getKey());
            if (p == null || !named.add(stage)) continue;
            claim.label("survey stake, " + stage.name().toLowerCase(), p.x() + .5f, p.y() + .5f, 200 * side, 120);
            side = -side;
        }
        claim.legend(new String[]{"dashed amber: haul roads", "thin cyan: each stake's relay link toward the hull"});
        out.add(claim.done("claim.png"));

        // A stake close: each stage it has here, spire (built) and tripod (stand-in).
        List<long[]> units = new ArrayList<>();   // {pointId, unit, stage ordinal}
        for (Map.Entry<Integer, SurveyStake> e : hull.stakes().entrySet()) {
            long unit = stakeUnit(hull, director, e.getKey());
            if (unit != 0L && sim.resolveUnit(unit) != 0L) {
                units.add(new long[]{e.getKey(), unit, e.getValue().stage().ordinal()});
            }
        }
        if (units.isEmpty()) {
            log("no stake structures stand");
            return;
        }
        // One stake of each stage that stands here, each in its own frame: spire (built), then tripod (stand-in).
        Path art = context.projectRoot().resolve(ART);
        boolean tripodArt = Files.exists(art.resolve("stake-tripod-planted.png"));
        if (!tripodArt) log("no tripod art at %s; run art-source/fallen-hull/export.py", art);
        Set<Long> seen = new HashSet<>();
        for (long[] u : units) {
            if (!seen.add(u[2])) continue;
            SurveyStake.Stage stage = SurveyStake.Stage.values()[(int) u[2]];
            String name = stage.name().toLowerCase();
            float x = world.renderX(u[1]), y = world.renderY(u[1]), near = 24f;
            Map<Long, float[]> moved = clearFrame(sim, x, y, near, Set.of(u[1]));
            Shot spire = shot(context, sim, x, y, near, true);
            spire.label("spire stake, " + name, x, y, 300, -200);
            out.add(spire.done("stake-spire-" + name + ".png"));
            if (tripodArt) {
                Map<Long, float[]> parked = clearFrame(sim, x, y, near, Set.of());
                Shot tripod = shot(context, sim, x, y, near, true);
                String file = switch (stage) {
                    case PLANTED -> "stake-tripod-planted.png";
                    case RAISED -> "stake-tripod-raised.png";
                    default -> "stake-tripod-store.png";
                };
                tripod.piece(ImageIO.read(art.resolve(file).toFile()), x, y, STAKE_PX);
                tripod.label("tripod stake, " + name + " (art stand-in, not built)", x, y, 300, -200);
                out.add(tripod.done("stake-tripod-" + name + ".png"));
                restore(sim, parked);
            }
            restore(sim, moved);
        }
    }

    /** The structure a stake stands as: its folded pylon, the raised node, or the established store. */
    private static long stakeUnit(FallenHull hull, DerelictSecurityDirector director, int pointId) {
        Long pylon = hull.pylons().get(pointId);
        if (pylon != null) return pylon;
        int zone = FallenHull.zoneOf(pointId);
        Long bay = director.serviceBays().get(zone);
        if (bay != null) return bay;
        Long node = director.nodes().get(zone);
        return node == null ? 0L : node;
    }

    // ---- placing

    private static long firstOf(BattleSimulation sim, Set<Integer> squads) {
        for (int squad : squads) {
            if (sim.squadMemberCount(squad) > 0) return sim.squadMemberAt(squad, 0);
        }
        return 0L;
    }

    /** Every unit but {@code keep} within the frame moved off the map's edge; their places, to put them back. */
    private static Map<Long, float[]> clearFrame(BattleSimulation sim, float cx, float cy, float cells, Set<Long> keep) {
        World world = sim.world();
        float halfW = cells / 2f + 4f, halfH = cells * H / W / 2f + 4f;
        Map<Long, float[]> moved = new LinkedHashMap<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (keep.contains(id) || !world.hasPosition(id)) continue;
            float x = world.renderX(id), y = world.renderY(id);
            if (Math.abs(x - cx) > halfW || Math.abs(y - cy) > halfH) continue;
            moved.put(id, new float[]{x, y});
        }
        // Parked in the map's corner farthest from the frame.
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        float px = cx < w / 2f ? w - 2.5f : 1.5f, py = cy < h / 2f ? h - 2.5f : 1.5f;
        for (long id : moved.keySet()) move(sim, id, px, py);
        return moved;
    }

    /** Moves a unit, its ground body too when it has one: a body is what infantry are drawn at. */
    private static void move(BattleSimulation sim, long id, float x, float y) {
        sim.world().setPos(id, x, y);
        EntityWorld ew = sim.getEntityWorld();
        BattleComponents c = sim.getBattleComponents();
        if (ew.has(id, c.GROUND_KINEMATICS)
                && ew.getObject(id, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY) instanceof GroundBody b) {
            b.teleport(x, y, b.facingDegrees);
        }
    }

    private static void restore(BattleSimulation sim, Map<Long, float[]> moved) {
        for (Map.Entry<Long, float[]> e : moved.entrySet()) move(sim, e.getKey(), e.getValue()[0], e.getValue()[1]);
    }

    /** The nearest cell to (x, y), within {@code search}, with every cell within {@code r} walkable and no wall. */
    private static int[] deckNear(BattleSimulation sim, int x, int y, int r, int search) {
        for (int d = 0; d <= search; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != d) continue;
                    boolean ok = true;
                    for (int yy = y + dy - r; yy <= y + dy + r && ok; yy++) {
                        for (int xx = x + dx - r; xx <= x + dx + r && ok; xx++) {
                            ok = sim.getGrid().inBounds(xx, yy) && sim.getGrid().isWalkable(xx, yy)
                                    && !sim.getTopology().isWall(xx, yy);
                        }
                    }
                    if (ok) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /** The nearest cell to (x, y), within {@code search}, with every cell within {@code r} clear ground. */
    private static int[] clearNear(BattleSimulation sim, int x, int y, int r, int search) {
        return clearRing(sim, x, y, r, 0, search);
    }

    /** As {@link #clearNear}, at least {@code from} cells off (x, y). */
    private static int[] clearRing(BattleSimulation sim, int x, int y, int r, int from, int search) {
        for (int d = from; d <= search; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != d) continue;
                    if (clear(sim, x + dx, y + dy, r)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    private static boolean clear(BattleSimulation sim, int cx, int cy, int r) {
        for (int yy = cy - r; yy <= cy + r; yy++) {
            for (int xx = cx - r; xx <= cx + r; xx++) {
                if (!sim.getGrid().inBounds(xx, yy) || !sim.getGrid().isWalkable(xx, yy)) return false;
                if (sim.getTopology().getBuildingId(xx, yy) != 0 || sim.getTopology().isWall(xx, yy)) return false;
            }
        }
        return true;
    }

    // ---- drawing

    private static Shot shot(SnapshotContext context, BattleSimulation sim, float x, float y, float cells,
                             boolean roofs) throws Exception {
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, x, y, cells);
        if (!roofs) renderer = renderer.withoutRoofs();
        BufferedImage frame = renderer.render(sim, "");
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return new Shot(img, g, renderer.cameraOf(sim));
    }

    /** A look frame with labels and overlays drawn on in screen space; labels go last so they sit over everything. */
    private static final class Shot {
        private static final Color AMBER = new Color(255, 200, 70);
        private static final Color CYAN = new Color(110, 220, 255);
        private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 26);
        final BufferedImage image;
        final Graphics2D g;
        final BattleCamera cam;
        private final List<Runnable> labels = new ArrayList<>();

        Shot(BufferedImage image, Graphics2D g, BattleCamera cam) {
            this.image = image;
            this.g = g;
            this.cam = cam;
        }

        float sx(float cellX) { return cam.cellToScreenX(cellX); }

        float sy(float cellY) { return H - cam.cellToScreenY(cellY); }

        /** A haul road: a dashed amber line along its polyline. */
        void road(int[] poly) {
            Path2D path = new Path2D.Float();
            for (int i = 0; i + 1 < poly.length; i += 2) {
                float x = sx(poly[i] + .5f), y = sy(poly[i + 1] + .5f);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .85f));
            g.setColor(AMBER);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{18f, 14f},
                    0f));
            g.draw(path);
            g.setComposite(AlphaComposite.SrcOver);
        }

        /** A stake's relay link: a thin cyan line. */
        void link(float x0, float y0, float x1, float y1) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .8f));
            g.setColor(CYAN);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(Math.round(sx(x0)), Math.round(sy(y0)), Math.round(sx(x1)), Math.round(sy(y1)));
            g.setComposite(AlphaComposite.SrcOver);
        }

        /** An arrow along the road, the way the load goes. */
        void arrow(float x0, float y0, float x1, float y1) {
            float ax = sx(x0), ay = sy(y0), bx = sx(x1), by = sy(y1);
            double ang = Math.atan2(by - ay, bx - ax);
            g.setColor(AMBER);
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(Math.round(ax), Math.round(ay), Math.round(bx), Math.round(by));
            Path2D head = new Path2D.Float();
            head.moveTo(bx + Math.cos(ang) * 22, by + Math.sin(ang) * 22);
            head.lineTo(bx + Math.cos(ang + 2.4) * 20, by + Math.sin(ang + 2.4) * 20);
            head.lineTo(bx + Math.cos(ang - 2.4) * 20, by + Math.sin(ang - 2.4) * 20);
            head.closePath();
            g.fill(head);
        }

        /** A sprite authored at {@code pxPerCell}, centred on cell point (x, y). */
        void piece(BufferedImage sprite, float x, float y, float pxPerCell) {
            float k = cam.cellPxSize() / pxPerCell;
            float w = sprite.getWidth() * k, h = sprite.getHeight() * k;
            AffineTransform t = new AffineTransform();
            t.translate(sx(x) - w / 2f, sy(y) - h / 2f);
            t.scale(k, k);
            g.drawImage(sprite, t, null);
        }

        /** A short label in a box {@code (dx, dy)} pixels off cell point (x, y), a leader line to a ring there. */
        void label(String text, float x, float y, int dx, int dy) {
            labels.add(() -> {
                float px = sx(x), py = sy(y);
                g.setFont(FONT);
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(text), pad = 12, bw = tw + 2 * pad, bh = fm.getHeight() + pad;
                int bx = Math.round(px + dx - bw / 2f), by = Math.round(py + dy - bh / 2f);
                bx = Math.max(16, Math.min(W - 16 - bw, bx));
                by = Math.max(16, Math.min(H - 16 - bh, by));
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(AMBER);
                float lx = Math.max(bx, Math.min(bx + bw, px)), ly = Math.max(by, Math.min(by + bh, py));
                g.drawLine(Math.round(px), Math.round(py), Math.round(lx), Math.round(ly));
                g.drawOval(Math.round(px) - 9, Math.round(py) - 9, 18, 18);
                g.setColor(new Color(14, 12, 20, 215));
                g.fillRoundRect(bx, by, bw, bh, 12, 12);
                g.setColor(AMBER);
                g.drawRoundRect(bx, by, bw, bh, 12, 12);
                g.setColor(Color.WHITE);
                g.drawString(text, bx + pad, by + pad / 2 + fm.getAscent());
            });
        }

        /** Lines of key in the frame's bottom-left corner. */
        void legend(String[] lines) {
            labels.add(() -> {
                g.setFont(FONT.deriveFont(Font.PLAIN, 22f));
                FontMetrics fm = g.getFontMetrics();
                int w = 0;
                for (String l : lines) w = Math.max(w, fm.stringWidth(l));
                int lh = fm.getHeight(), bh = lh * lines.length + 16, bw = w + 24;
                g.setColor(new Color(14, 12, 20, 215));
                g.fillRoundRect(16, H - 16 - bh, bw, bh, 12, 12);
                g.setColor(Color.WHITE);
                for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 28, H - 16 - bh + 8 + fm.getAscent() + i * lh);
            });
        }

        SnapshotArtifact done(String name) {
            for (Runnable r : labels) r.run();
            g.dispose();
            return new SnapshotArtifact(name, image);
        }
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/long-survey-picks] " + format + "%n", args);
    }
}
