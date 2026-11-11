package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidOwnedMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingStanding;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
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
 * pictures are marked-up mock-ups of behaviour not built yet. The defence pictures ({@code raid-works-defences}) set
 * the game's own props on the battle round the works' wall and halls, as mock-ups of defences not generated yet;
 * {@code -Dsnapshot.raidDemolition.only=<prefix>} renders only the pictures whose names start with it. Nothing is
 * played. Opt-in.
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
        // More defences for the works (mock-ups with the game's own props, set on the battle, not generated).
        out.add(shot(context, "defences-0-now.png", (w, m) -> {
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "THE WORKS TODAY: wall, gates, emplacements, its own guard", ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "defences-a1-layered-guarded.png", (w, m) -> {
            Perimeter p = perimeter(w);
            belt(w, p);
            checkpoints(w, p);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (a) GUARDED: hedgehog belt outside the wall, sandbagged checkpoint at each gate",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "defences-a2-layered-fortified.png", (w, m) -> {
            Perimeter p = perimeter(w);
            belt(w, p);
            checkpoints(w, p);
            towers(w, p);
            hallRings(w);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (a) FORTIFIED: adds watchtowers on the wall and sandbag lines at every hall",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "defences-b-wall-line.png", (w, m) -> {
            Perimeter p = perimeter(w);
            belt(w, p);
            checkpoints(w, p);
            towers(w, p);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (b) THE WALL LINE: belt, checkpoints and towers at the wall, nothing inside",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "defences-c-hall-rings.png", (w, m) -> {
            hallRings(w);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (c) THE HALLS: sandbag lines and barriers round every hall, the wall as it is",
                    ReviewStyle.NOTE);
            return null;
        }));
        // The defences as built, on the owned map raided from a guarded and a fortified market (open adds nothing).
        for (String security : List.of("guarded", "fortified")) {
            out.add(shot(context, "built-" + security + ".png", (w, m) -> {
                m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "AS BUILT: the works raided from a "
                        + security + " market that fields mechs", ReviewStyle.NOTE);
                machinePostLabels(w, m);
                return null;
            }));
        }
        // The works' machines (owner's note: "Also consider things like vehicles and mechs"): mock-ups with the game's
        // parked-hull props standing in for live machines.
        out.add(shot(context, "machines-a1-layered-guarded.png", (w, m) -> {
            gatePickets(w, m, perimeter(w));
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (a) GUARDED: a light vehicle holds each gate, hull-down behind the checkpoint",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "machines-a2-layered-fortified.png", (w, m) -> {
            Perimeter p = perimeter(w);
            gatePickets(w, m, p);
            yardReserve(w, m);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (a) FORTIFIED: also an armour reserve in the works' yard that drives to the hall under attack",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "machines-b-patrol.png", (w, m) -> {
            Perimeter p = perimeter(w);
            int in = 3;
            List<ReviewAnnotation.Point> loop = List.of(new ReviewAnnotation.Point(p.x0() + in, p.y0() + in),
                    new ReviewAnnotation.Point(p.x1() - in, p.y0() + in), new ReviewAnnotation.Point(p.x1() - in, p.y1() - in),
                    new ReviewAnnotation.Point(p.x0() + in, p.y1() - in), new ReviewAnnotation.Point(p.x0() + in, p.y0() + in));
            m.polyline(loop, "PATROL: circles inside the wall", ReviewStyle.ROUTE);
            standNear(w, p.x0() + in, (p.y0() + p.y1()) / 2, "doodad.parked-apc-army", 6);
            standNear(w, p.x1() - in - 2, (p.y0() + p.y1()) / 2, "doodad.parked-tank", 6);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (b) PATROL: the works' machines drive a loop inside the wall, meeting the raid where it comes in",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.add(shot(context, "machines-c-garrison-reserve.png", (w, m) -> {
            float[] g = garrison(w);
            float dx = g[0] - w.cx(), dy = g[1] - w.cy(), d = (float) Math.max(1, Math.hypot(dx, dy));
            float reach = w.across() * 0.42f;
            float fx = w.cx() + dx / d * reach, fy = w.cy() + dy / d * Math.min(reach, w.across() * 9f / 32f - 4);
            Building hall = w.halls().get(0);
            m.arrow(fx, fy, (hall.minX + hall.maxX) / 2f, (hall.minY + hall.maxY) / 2f,
                    "on the alarm, from the garrison", ReviewStyle.APPROACH);
            m.label(fx, fy, "GARRISON ARMOUR RESERVE (off this frame)", ReviewStyle.KEEP);
            m.label(w.cx(), w.cy() - w.across() * 9f / 32f + 3, "MOCK-UP (c) GARRISON RESERVE: no machines in the works; the garrison's armour drives over on the alarm",
                    ReviewStyle.NOTE);
            return null;
        }));
        out.removeIf(a -> a == null);
        return out;
    }

    /** Each of the works' machine posts with the machines posted at it. */
    private static void machinePostLabels(Works w, ReviewAnnotations.Builder m) {
        for (TacticalNode node : w.sim().getTacticalMap().all()) {
            if (!RaidSiteTemplate.WORKS_MACHINES.equals(node.place())) continue;
            int posted = 0;
            for (Squad squad : w.sim().getSquads()) if (squad.assignedNode == node) posted += squad.originalSize;
            m.label(node.anchorX, node.anchorY + 2, "MACHINE POST: " + posted + " mech" + (posted == 1 ? "" : "s"),
                    posted > 0 ? ReviewStyle.ALLY : ReviewStyle.KEEP);
            System.out.printf(Locale.ROOT, "[raid-demolition-look] machine post %d,%d posted %d%n", node.anchorX,
                    node.anchorY, posted);
        }
    }

    /** A light vehicle just inside each gate of three cells or wider, beside its mouth. */
    private static void gatePickets(Works w, ReviewAnnotations.Builder m, Perimeter p) {
        for (int[] g : p.gates()) {
            if (g[4] < 3) continue;
            int ox = g[2], oy = g[3], aside = g[4] / 2 + 2;
            int x = g[0] - ox * 4 + (ox == 0 ? aside : 0), y = g[1] - oy * 4 + (oy == 0 ? aside : 0);
            if (standNear(w, x, y, "doodad.parked-apc-army", 4) != null) {
                m.label(x, y + 3, "GATE PICKET", ReviewStyle.ALLY);
            }
        }
    }

    /** Two tanks on the open yard nearest the works' middle, with the alarm's run to hall 1. */
    private static void yardReserve(Works w, ReviewAnnotations.Builder m) {
        int[] at = standNear(w, Math.round(w.cx()), Math.round(w.cy()), "doodad.parked-tank", 14);
        if (at == null) return;
        standNear(w, at[0] + 4, at[1], "doodad.parked-tank", 6);
        m.label(at[0] + 2, at[1] + 4, "ARMOUR RESERVE", ReviewStyle.ALLY);
        Building hall = w.halls().get(0);
        m.arrow(at[0] + 2, at[1], (hall.minX + hall.maxX) / 2f, (hall.minY + hall.maxY) / 2f, "on the alarm",
                ReviewStyle.APPROACH);
    }

    /** A prop on the first open spot outward from {@code (x, y)} within {@code radius}; where it stood, or null. */
    private static int[] standNear(Works w, int x, int y, String id, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (prop(w, x + dx, y + dy, id, 0)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /** The middle of the garrison's posts. */
    private static float[] garrison(Works w) {
        float sx = 0, sy = 0;
        int n = 0;
        for (TacticalNode node : w.sim().getTacticalMap().all()) {
            if (!RaidSiteTemplate.GARRISON.equals(node.place())) continue;
            sx += node.anchorX;
            sy += node.anchorY;
            n++;
        }
        return n == 0 ? new float[]{w.cx(), w.cy() - 100} : new float[]{sx / n, sy / n};
    }

    /** {@code w} framed on its whole wall and the ground just outside it, for the defence pictures. */
    private static Works framedOnWall(Works w) {
        Perimeter p = perimeter(w);
        int margin = 9;
        float across = Math.max(p.x1() - p.x0() + 2f * margin, (p.y1() - p.y0() + 2f * margin) * 16f / 9f);
        return new Works(w.sim(), w.halls(), w.buildings(), (p.x0() + p.x1() + 1) / 2f, (p.y0() + p.y1() + 1) / 2f,
                across, w.landing());
    }

    /** The works' wall: the bounds of its line and each gap in it (middle x, y, outward dx, dy, width). */
    private record Perimeter(int x0, int y0, int x1, int y1, List<int[]> gates) { }

    private static Perimeter perimeter(Works w) {
        CellTopology t = w.sim().getTopology();
        int cx = Math.round(w.cx()), cy = Math.round(w.cy());
        int x0 = scan(w, cx, cy, -1, 0), x1 = scan(w, cx, cy, 1, 0);
        int y0 = scan(w, cx, cy, 0, -1), y1 = scan(w, cx, cy, 0, 1);
        List<int[]> gates = new ArrayList<>();
        gaps(w, x0, y0, x0, y1, -1, 0, gates);
        gaps(w, x1, y0, x1, y1, 1, 0, gates);
        gaps(w, x0, y0, x1, y0, 0, -1, gates);
        gaps(w, x0, y1, x1, y1, 0, 1, gates);
        System.out.printf(Locale.ROOT, "[raid-demolition-look] works wall x %d..%d y %d..%d, %d gaps%n", x0, x1, y0, y1,
                gates.size());
        return new Perimeter(x0, y0, x1, y1, gates);
    }

    /**
     * The works' own wall out from {@code (x, y)} along {@code (dx, dy)}: the first wall cell on a long straight line
     * ({@link #wallLine}), the median over lines a few cells apart across the way, so a gate on one line does not
     * lose the wall. A building's wall is short; the works' wall runs the length of the works.
     */
    private static int scan(Works w, int x, int y, int dx, int dy) {
        CellTopology t = w.sim().getTopology();
        List<Integer> found = new ArrayList<>();
        for (int k = -12; k <= 12; k += 3) {
            int sx = x + (dx == 0 ? k : 0), sy = y + (dy == 0 ? k : 0);
            for (int i = 1; i < 200; i++) {
                int cx = sx + dx * i, cy = sy + dy * i;
                if (wallLine(t, cx, cy, dy, dx)) {
                    found.add(dx != 0 ? cx : cy);
                    break;
                }
            }
        }
        if (found.isEmpty()) return dx != 0 ? x + dx * 60 : y + dy * 60;
        found.sort(Integer::compare);
        return found.get(found.size() / 2);
    }

    /** Whether {@code (x, y)} is a wall cell with 18 or more wall cells within 15 along {@code (ax, ay)}. */
    private static boolean wallLine(CellTopology t, int x, int y, int ax, int ay) {
        if (!t.isWall(x, y)) return false;
        int walls = 0;
        for (int i = -15; i <= 15; i++) if (t.isWall(x + ax * i, y + ay * i)) walls++;
        return walls >= 18;
    }

    /** Each run of walkable cells along a wall side, with the side's outward step. */
    private static void gaps(Works w, int ax, int ay, int bx, int by, int ox, int oy, List<int[]> gates) {
        var grid = w.sim().getGrid();
        boolean alongX = ay == by;
        int from = alongX ? ax : ay, to = alongX ? bx : by, run = -1;
        for (int i = from; i <= to + 1; i++) {
            int x = alongX ? i : ax, y = alongX ? ay : i;
            boolean open = i <= to && grid.isWalkable(x, y);
            if (open && run < 0) run = i;
            if (!open && run >= 0) {
                int mid = (run + i - 1) / 2;
                gates.add(new int[]{alongX ? mid : ax, alongX ? ay : mid, ox, oy, i - run});
                run = -1;
            }
        }
    }

    /** A prop with its corner at {@code (x, y)} if every cell under it is open ground; whether it stood. */
    private static boolean prop(Works w, int x, int y, String id, int turns) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef def = registry == null ? null : registry.doodad(id);
        if (def == null) throw new IllegalStateException("no prop " + id);
        Doodad d = new Doodad(x, y, def, turns);
        var grid = w.sim().getGrid();
        CellTopology t = w.sim().getTopology();
        for (int dy = 0; dy < d.footprintCellsY; dy++) {
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                int cx = x + dx, cy = y + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || t.getBuildingId(cx, cy) != 0
                        || taken(w, cx, cy)) return false;
            }
        }
        w.sim().getDoodadService().addDoodad(d);
        return true;
    }

    private static boolean taken(Works w, int x, int y) {
        for (Doodad d : w.sim().getDoodads()) {
            if (x >= d.cellX && x < d.cellX + d.footprintCellsX && y >= d.cellY && y < d.cellY + d.footprintCellsY) return true;
        }
        return false;
    }

    /** Hedgehogs every other cell on a ring four cells outside the wall, the gates' mouths left open. */
    private static void belt(Works w, Perimeter p) {
        int out = 4;
        int x0 = p.x0() - out, x1 = p.x1() + out, y0 = p.y0() - out, y1 = p.y1() + out;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (x != x0 && x != x1 && y != y0 && y != y1) continue;
                if ((x + y) % 2 != 0 || nearGate(p, x, y, 4)) continue;
                prop(w, x, y, "doodad.fort-hedgehog", 0);
            }
        }
    }

    private static boolean nearGate(Perimeter p, int x, int y, int r) {
        for (int[] g : p.gates()) if (Math.abs(g[0] - x) <= r + g[4] / 2 && Math.abs(g[1] - y) <= r + g[4] / 2) return true;
        return false;
    }

    /** A sandbag run either side of each gate's mouth, three cells out, facing away from the works. */
    private static void checkpoints(Works w, Perimeter p) {
        for (int[] g : p.gates()) {
            int ox = g[2], oy = g[3], half = g[4] / 2 + 1;
            String facing = "doodad.fort-sandbag-" + (ox < 0 ? "w" : ox > 0 ? "e" : oy < 0 ? "s" : "n");
            int lx = g[0] + ox * 3, ly = g[1] + oy * 3;
            for (int k = 0; k < 5; k++) {
                if (ox != 0) {
                    prop(w, lx, g[1] - half - k, facing, 0);
                    prop(w, lx, g[1] + half + k, facing, 0);
                } else {
                    prop(w, g[0] - half - k, ly, facing, 0);
                    prop(w, g[0] + half + k, ly, facing, 0);
                }
            }
        }
    }

    /** A watchtower inside each corner of the wall, moved in until it finds open ground. */
    private static void towers(Works w, Perimeter p) {
        int[][] corners = {{p.x0() + 1, p.y0() + 1, 1, 1}, {p.x1() - 3, p.y0() + 1, -1, 1},
                {p.x0() + 1, p.y1() - 3, 1, -1}, {p.x1() - 3, p.y1() - 3, -1, -1}};
        for (int[] c : corners) {
            done:
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) if (prop(w, c[0] + c[2] * i, c[1] + c[3] * j, "doodad.watchtower", 0)) break done;
            }
        }
    }

    /** A sandbag parapet three cells off every side of each hall, a way through every sixth cell, with a jersey barrier at each corner. */
    private static void hallRings(Works w) {
        for (Building hall : w.halls()) {
            int x0 = hall.minX - 3, x1 = hall.maxX + 3, y0 = hall.minY - 3, y1 = hall.maxY + 3;
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                if ((x - x0) % 6 == 0) continue; // a way through every few cells
                prop(w, x, y0, "doodad.fort-sandbag-s", 0);
                prop(w, x, y1, "doodad.fort-sandbag-n", 0);
            }
            for (int y = y0 + 1; y <= y1 - 1; y++) {
                if ((y - y0) % 6 == 0) continue;
                prop(w, x0, y, "doodad.fort-sandbag-w", 0);
                prop(w, x1, y, "doodad.fort-sandbag-e", 0);
            }
            prop(w, x0, y0, "doodad.jersey-barrier", 0);
            prop(w, x1, y0, "doodad.jersey-barrier", 0);
            prop(w, x0, y1, "doodad.jersey-barrier", 0);
            prop(w, x1, y1, "doodad.jersey-barrier", 0);
        }
    }

    private static SnapshotArtifact shot(SnapshotContext context, String name,
                                         BiFunction<Works, ReviewAnnotations.Builder, Void> stage) throws Exception {
        String only = System.getProperty("snapshot.raidDemolition.only", "");
        if (!name.startsWith(only)) return null;
        RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(MAP, StoryMapPools.pool(MAP).home());
        long seed = StoryMapPools.pool(MAP).owned().seed();
        // The built pictures raid the owned map from a market at that security; the rest from the reference market.
        TargetProfile raided = name.startsWith("built-fortified") ? ground.reference().withDefences(true, true, false)
                : ground.reference();
        // The built pictures land a company of two loads, against which the market can afford its mechs.
        List<ShuttleAssignment> company = name.startsWith("built")
                ? List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 2), new ShuttleAssignment(ShuttleType.VALKYRIE, 2), new ShuttleAssignment(ShuttleType.VALKYRIE, 2)) : List.of();
        try (BattleSimulation sim = BattleSetup.createPooledRaid(ground, seed, true, company, raided,
                name.startsWith("built"))) {
            Works works = works(sim);
            if (name.startsWith("defences") || name.startsWith("built") || name.startsWith("machines")) {
                works = framedOnWall(works);
            }
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
