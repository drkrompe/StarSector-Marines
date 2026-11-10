package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidOwnedMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingStanding;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The owner's questions on the Raid's demolition objective ({@code raid-demolish-the-works.md}), drawn on the owned
 * heavy-industry works through the look preset at 1920x1080: what counts as a target, how much of a building must
 * fall, the marker's look and how much effort soldiers give a structure. The fall pictures knock real wall cells down
 * through the map-structure channel ({@link BattleSimulation#damageCell}) and cave real roofs in; the marker and effort
 * pictures are marked-up mock-ups of behaviour not built yet. Nothing is played. Opt-in.
 */
public final class RaidDemolitionLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final StoryMap MAP = StoryMap.RAID_HEAVY_INDUSTRY;
    /** Cells of ground shown round the works in a close frame. */
    private static final int MARGIN = 14;
    /** Enough wall damage to knock any cell down in one go. */
    private static final int RAZE = 100_000;

    @Override public String id() { return "raid-demolition-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "The Raid demolition objective's questions on the owned heavy-industry works"; }

    /** The works as a raid stands it up: its halls (one per machine), its other buildings and its frame. */
    private record Works(BattleSimulation sim, List<Building> halls, List<Building> buildings, float cx, float cy,
                         float across, int[] landing) { }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        // What counts as a target.
        out.add(shot(context, "targets-a-machine-halls.png", (w, m) -> {
            int n = 0;
            for (Building hall : w.halls()) box(m, hall, "TARGET: MACHINE HALL " + (++n), ReviewStyle.KEEP);
            return null;
        }));
        out.add(shot(context, "targets-b-every-works-building.png", (w, m) -> {
            for (Building b : w.buildings()) {
                box(m, b, w.halls().contains(b) ? "TARGET: MACHINE HALL" : "TARGET", ReviewStyle.KEEP);
            }
            return null;
        }));
        out.add(shot(context, "targets-c-halls-and-generator.png", (w, m) -> {
            int n = 0;
            for (Building hall : w.halls()) box(m, hall, "TARGET: MACHINE HALL " + (++n), ReviewStyle.KEEP);
            Building generator = largestNonHall(w);
            if (generator != null) box(m, generator, "TARGET: POWER HOUSE (stand-in building; stops every machine)", ReviewStyle.OBJECTIVE);
            return null;
        }));
        // How much of a building must fall: hall 1 brought to each line, other halls whole.
        out.add(shot(context, "fall-a-half-the-walls.png", (w, m) -> fall(w, m, 0.5f, false,
                "HALL 1 BROUGHT DOWN: half its walls down")));
        out.add(shot(context, "fall-b-three-quarters.png", (w, m) -> fall(w, m, 0.75f, false,
                "HALL 1 BROUGHT DOWN: three quarters of its walls down")));
        out.add(shot(context, "fall-c-roof-in-and-a-breach.png", (w, m) -> fall(w, m, 0.12f, true,
                "HALL 1 BROUGHT DOWN: roof caved in and one breach")));
        // The marker, over hall 1 a third of the way down and the other halls whole (mock-ups).
        out.add(shot(context, "marker-a-outline-and-ring.png", (w, m) -> marker(w, m, "a")));
        out.add(shot(context, "marker-b-bar.png", (w, m) -> marker(w, m, "b")));
        out.add(shot(context, "marker-c-wall-tint.png", (w, m) -> marker(w, m, "c")));
        // How much effort soldiers give a structure (mock-ups).
        out.add(shot(context, "effort-a-when-no-enemy-in-sight.png", (w, m) -> effort(w, m,
                "the squad fights first; it turns on the hall only when no enemy is in sight",
                "no enemy in sight: launcher and charges on the hall", null)));
        out.add(shot(context, "effort-b-heavy-weapons-keep-working.png", (w, m) -> effort(w, m,
                "riflemen fight; the squad's heavy weapon and demo man keep working the hall",
                "launcher and demo man stay on the hall", "riflemen on the enemy")));
        out.add(shot(context, "effort-c-only-on-order.png", (w, m) -> effort(w, m,
                "squads never fire on a building unless the player orders it",
                "player's order: bring the hall down", null)));
        return out;
    }

    private static SnapshotArtifact shot(SnapshotContext context, String name,
                                         BiFunction<Works, ReviewAnnotations.Builder, Void> stage) throws Exception {
        RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(MAP, StoryMapPools.pool(MAP).home());
        long seed = StoryMapPools.pool(MAP).owned().seed();
        try (BattleSimulation sim = BattleSetup.createPooledRaid(ground, ground.home(), seed, true, List.of())) {
            Works works = works(sim);
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            stage.apply(works, marks);
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                    works.cx(), works.cy(), works.across());
            int objectiveHalls = 0;
            for (var objective : sim.getObjectives()) {
                if (!(objective instanceof RaidObjective raid)) continue;
                for (int i = 0; i < raid.sites().size(); i++) if (raid.hasHall(i)) objectiveHalls++;
            }
            System.out.printf(Locale.ROOT, "[raid-demolition-look] %s halls %d works buildings %d machines in a hall %d%n",
                    name, works.halls().size(), works.buildings().size(), objectiveHalls);
            return new SnapshotArtifact(name, look.renderMarked(sim, marks.build()));
        }
    }

    private static Works works(BattleSimulation sim) {
        CellTopology topology = sim.getTopology();
        RaidObjective raid = null;
        for (var objective : sim.getObjectives()) if (objective instanceof RaidObjective r) raid = r;
        if (raid == null) throw new IllegalStateException("no Raid objective");
        Map<Integer, Building> halls = new LinkedHashMap<>();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (RaidObjective.Site site : raid.sites()) {
            int id = nearestBuilding(topology, site.cellX(), site.cellY());
            if (id > 0) halls.putIfAbsent(id, sim.getBuildings().get(id));
            x0 = Math.min(x0, site.cellX()); x1 = Math.max(x1, site.cellX());
            y0 = Math.min(y0, site.cellY()); y1 = Math.max(y1, site.cellY());
        }
        for (TacticalNode post : sim.getTacticalMap().ofKind(TacticalNode.Kind.GUARDPOST)) {
            if (!RaidSiteTemplate.WORKS.equals(post.place())) continue;
            x0 = Math.min(x0, post.anchorX); x1 = Math.max(x1, post.anchorX);
            y0 = Math.min(y0, post.anchorY); y1 = Math.max(y1, post.anchorY);
        }
        for (Building hall : halls.values()) {
            x0 = Math.min(x0, hall.minX); x1 = Math.max(x1, hall.maxX);
            y0 = Math.min(y0, hall.minY); y1 = Math.max(y1, hall.maxY);
        }
        List<Building> buildings = new ArrayList<>();
        for (Building b : sim.getBuildings().all()) {
            if (b.minX >= x0 - 2 && b.maxX <= x1 + 2 && b.minY >= y0 - 2 && b.maxY <= y1 + 2) buildings.add(b);
        }
        float across = Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 2f * MARGIN * 16f / 9f;
        LandingPad pad = sim.getMarineLandingPads().get(0);
        return new Works(sim, List.copyOf(halls.values()), buildings, (x0 + x1 + 1) / 2f, (y0 + y1 + 1) / 2f, across,
                new int[]{pad.centerX, pad.centerY});
    }

    private static int nearestBuilding(CellTopology topology, int x, int y) {
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int id = topology.getBuildingId(x + dx, y + dy);
                    if (id > 0) return id;
                }
            }
        }
        return 0;
    }

    private static Building largestNonHall(Works w) {
        return w.buildings().stream().filter(b -> !w.halls().contains(b))
                .max(Comparator.comparingInt(Building::cellCount)).orElse(null);
    }

    private static List<int[]> outline(Works w, Building b) {
        CellTopology t = w.sim().getTopology();
        return BuildingStanding.outline(b.cellsX, b.cellsY, t::isWall, t.getWidth(), t.getHeight());
    }

    /** Knocks {@code share} of {@code b}'s outline down, a run of it round one side. */
    private static void knockDown(Works w, Building b, float share) {
        List<int[]> outline = new ArrayList<>(outline(w, b));
        float cx = (b.minX + b.maxX) / 2f, cy = (b.minY + b.maxY) / 2f;
        outline.sort(Comparator.comparingDouble(c -> Math.atan2(c[1] - cy, c[0] - cx)));
        int fall = Math.round(share * outline.size());
        for (int i = 0; i < fall; i++) w.sim().damageCell(outline.get(i)[0], outline.get(i)[1], RAZE);
    }

    private static Void fall(Works w, ReviewAnnotations.Builder m, float share, boolean roofCriterion, String text) {
        Building hall = w.halls().get(0);
        List<int[]> outline = outline(w, hall);
        knockDown(w, hall, share);
        float standing = BuildingStanding.standingShare(outline, w.sim().getTopology()::isWall);
        // At the line the building counts as brought down and its roof caves in, so the fall reads from above.
        caveIn(w, hall);
        fallen(w, m, outline, "fallen wall");
        m.label((hall.minX + hall.maxX) / 2f, hall.maxY + 3, text + (roofCriterion ? "" : String.format(Locale.ROOT,
                " (%.0f%% of its outline standing; roof caves in at the line)", standing * 100f)), ReviewStyle.KEEP);
        return null;
    }

    private static void caveIn(Works w, Building b) {
        for (int i = 0; i < b.cellsX.length; i++) {
            w.sim().mapEditor().destroyRoof(b.cellsX[i], b.cellsY[i], RoofDamage.COLLAPSED);
        }
    }

    /** Marks the cells of {@code outline} that are down. */
    private static void fallen(Works w, ReviewAnnotations.Builder m, List<int[]> outline, String text) {
        List<ReviewAnnotation.Cell> down = new ArrayList<>();
        for (int[] c : outline) if (!w.sim().getTopology().isWall(c[0], c[1])) down.add(new ReviewAnnotation.Cell(c[0], c[1]));
        if (!down.isEmpty()) m.cells(down, text, ReviewStyle.KEEP);
    }

    private static Void marker(Works w, ReviewAnnotations.Builder m, String look) {
        List<List<int[]>> outlines = new ArrayList<>();
        for (Building hall : w.halls()) outlines.add(outline(w, hall));
        knockDown(w, w.halls().get(0), 1f / 3f);
        int n = 0;
        for (Building hall : w.halls()) {
            List<int[]> outline = outlines.get(n++);
            float standing = BuildingStanding.standingShare(outline, w.sim().getTopology()::isWall);
            int pct = Math.round(BuildingStanding.progress(standing) * 100f);
            float lx = (hall.minX + hall.maxX) / 2f, ly = hall.maxY + 2;
            switch (look) {
                case "a" -> box(m, hall, String.format(Locale.ROOT, "HALL %d  %d%%  (ring fills as it falls)", n, pct),
                        pct > 0 ? ReviewStyle.KEEP : ReviewStyle.OBJECTIVE);
                case "b" -> m.label(lx, ly, String.format(Locale.ROOT, "HALL %d  %s  %d%%", n, bar(pct), pct),
                        ReviewStyle.KEEP);
                default -> {
                    List<ReviewAnnotation.Cell> standingCells = new ArrayList<>();
                    for (int[] c : outline) {
                        if (w.sim().getTopology().isWall(c[0], c[1])) standingCells.add(new ReviewAnnotation.Cell(c[0], c[1]));
                    }
                    m.cells(standingCells, String.format(Locale.ROOT, "HALL %d  %d%%: green walls stand, magenta are down", n, pct),
                            ReviewStyle.POSITION);
                    fallen(w, m, outline, null);
                }
            }
        }
        return null;
    }

    private static String bar(int pct) {
        int filled = Math.round(pct / 10f);
        return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
    }

    /** {@code fight} labels a squad's arrow to an enemy in sight, or null for none in sight. */
    private static Void effort(Works w, ReviewAnnotations.Builder m, String rule, String toHall, String fight) {
        Building hall = w.halls().get(0);
        float hx = (hall.minX + hall.maxX) / 2f, hy = (hall.minY + hall.maxY) / 2f;
        // A squad on the street west of the hall; an enemy up the street to the north.
        float sx = hall.minX - 2f, sy = hy;
        m.label(sx, sy, "SQUAD", ReviewStyle.ALLY);
        m.arrow(sx, sy, hx, hy, toHall, ReviewStyle.APPROACH);
        if (fight != null) {
            float ex = sx, ey = hall.maxY + 12f;
            m.label(ex, ey, "ENEMY IN SIGHT", ReviewStyle.KEEP);
            m.arrow(sx, sy + 1, ex, ey - 1, fight, ReviewStyle.ROUTE);
        }
        box(m, hall, "TARGET", ReviewStyle.KEEP);
        m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP: " + rule, ReviewStyle.NOTE);
        return null;
    }

    private static void box(ReviewAnnotations.Builder m, Building b, String text, ReviewStyle style) {
        m.box(b.minX, b.minY, b.maxX, b.maxY, text, style);
    }
}
