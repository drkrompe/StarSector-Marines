package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The evac cordon's works for the owner ({@code colony-breach-nouns.md}, Evac cordon): the colony played as shipped
 * and unrecorded to {@code -Dsnapshot.evacCordon.tick} (default 1200, forty seconds, the line landed and in its
 * trench), then four seconds recorded through the look preset on the pickup, the whole cordon ({@code cordon}) and
 * the field round it ({@code wide}) and the colony round that ({@code region}). Each seed plays twice, with the works ({@code works}) and on the control
 * {@code -Dbattle.colonyBreach.cordonWorks=false} ({@code open}). {@code -Dsnapshot.evacCordon.seeds} (default
 * {@code 38/arid}, the campaign map) picks the seeds, each {@code seed} or {@code seed/biome}. Opt-in.
 */
public final class EvacCordonLookSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final String SWITCH = "battle.colonyBreach.cordonWorks";
    /** The cordon across the frame: the wall ring is 39 cells across. */
    private static final float CORDON_CELLS = 52f;
    private static final float WIDE_CELLS = 120f;
    /** The cordon against the colony: the landing field, the shelter's side of town and the walk between. */
    private static final float REGION_CELLS = 240f;

    @Override public String id() { return "evac-cordon-look"; }

    @Override public String label() { return "Colony Breach's evac cordon: the pickup's works and its line in the trench"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        int tick = Integer.getInteger("snapshot.evacCordon.tick", 1200);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String token : System.getProperty("snapshot.evacCordon.seeds", "38/arid").split(",")) {
                String[] seedAndBiome = token.trim().split("/");
                long seed = Long.parseLong(seedAndBiome[0]);
                String biome = seedAndBiome.length > 1 ? seedAndBiome[1].toUpperCase(Locale.ROOT) : null;
                out.add(placement(context, seed, biome));
                if (Boolean.getBoolean("snapshot.evacCordon.placementOnly")) continue;
                out.addAll(marked(context, seed, biome));
                for (boolean works : new boolean[]{true, false}) {
                    String before = System.getProperty(SWITCH);
                    System.setProperty(SWITCH, Boolean.toString(works));
                    try {
                        out.addAll(LookReview.record(context.modRoot(), shot(seed, biome, tick, works)));
                    } finally {
                        if (before == null) System.clearProperty(SWITCH);
                        else System.setProperty(SWITCH, before);
                    }
                }
            }
            return out;
        }
    }

    private static LookReview.Shot shot(long seed, String biome, int fromTick, boolean works) {
        float[] lift = new float[2];
        String name = String.format(Locale.ROOT, "cordon-s%d-%s-t%d", seed, works ? "works" : "open", fromTick);
        return new LookReview.Shot(name, LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim;
            try {
                sim = HeadlessBattles.fixture(colony(seed, biome));
            } catch (Exception e) {
                throw new IllegalStateException("colony fixture", e);
            }
            float[] at = lift(sim);
            lift[0] = at[0];
            lift[1] = at[1];
            while (sim.getSimTickIndex() < fromTick && !sim.isComplete()) sim.advance(BattleSimulation.TICK_DT);
            CivilianEvacuationTracker tracker = sim.getCivilianEvacuationTracker();
            System.out.printf("    [look/%s] lift at %.0f,%.0f, %d guards alive, %d of %d lifted, %d lost, tick %d%n",
                    name, lift[0], lift[1], sim.liveRescuePickupGuards(), tracker.evacuatedCount(),
                    tracker.registeredCount(), tracker.lostCount(), sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(
                new LookReview.View("cordon", sim -> new float[]{lift[0], lift[1], CORDON_CELLS}),
                new LookReview.View("wide", sim -> new float[]{lift[0], lift[1], WIDE_CELLS}),
                new LookReview.View("region", sim -> new float[]{lift[0], lift[1], REGION_CELLS})), 120, 6, 1);
    }

    /**
     * Where the cordon's defenders are and what they are doing, for the owner: the colony with the works played to
     * each of {@code -Dsnapshot.evacCordon.markTicks} (default {@code 1200,6000}), drawn through the look preset with
     * every guard team's men outlined and named with how many stand in the trench, an arrow from a team to the post it
     * is ordered to when it is off it, the mech, the lift, the shelter and holdouts and the company's pads; at the
     * cordon ({@code cordon}), against the colony ({@code region}) and, at the first tick, the whole map
     * ({@code map}). Each team's reading is printed too.
     */
    private static List<SnapshotArtifact> marked(SnapshotContext context, long seed, String biome) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = HeadlessBattles.fixture(colony(seed, biome))) {
            float[] lift = lift(sim);
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            boolean first = true;
            for (String token : System.getProperty("snapshot.evacCordon.markTicks", "1200,6000").split(",")) {
                int tick = Integer.parseInt(token.trim());
                while (sim.getSimTickIndex() < tick && !sim.isComplete()) sim.advance(BattleSimulation.TICK_DT);
                ReviewAnnotations marks = marks(sim, lift, String.format(Locale.ROOT, "s%d t%d", seed, tick));
                List<float[]> views = new ArrayList<>(List.of(
                        new float[]{lift[0], lift[1], CORDON_CELLS}, new float[]{lift[0], lift[1], REGION_CELLS}));
                List<String> names = new ArrayList<>(List.of("cordon", "region"));
                if (first) {
                    views.add(new float[]{w / 2f, h / 2f, Math.max(w, h * LookReview.WIDTH / (float) LookReview.HEIGHT)});
                    names.add("map");
                }
                for (int i = 0; i < views.size(); i++) {
                    float[] v = views.get(i);
                    BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                            LookReview.HEIGHT, v[0], v[1], v[2]);
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "marked/cordon-s%d-t%d-%s.png",
                            seed, sim.getSimTickIndex(), names.get(i)), look.renderMarked(sim, marks)));
                }
                first = false;
            }
        }
        return out;
    }

    /**
     * The colony as the owner places things on it (owner, 2026-10-28: click where the cordon, the bores and the
     * objectives should go): the whole map through the look preset, unmarked, at setup with the works off, and beside
     * it {@code placement/colony-s<seed>.json} holding the frame's cell scale, the map's open ground (walkable and
     * outside every building, a row-major bit set, base64) and where the lift, shelter, holdouts, company pads and
     * eruption sites stand today. A placement page draws its marks over the picture from that file.
     */
    private static SnapshotArtifact placement(SnapshotContext context, long seed, String biome) throws Exception {
        String before = System.getProperty(SWITCH);
        System.setProperty(SWITCH, "false");
        try (BattleSimulation sim = HeadlessBattles.fixture(colony(seed, biome))) {
            NavigationGrid grid = sim.getGrid();
            CellTopology topology = sim.getTopology();
            int w = grid.getWidth(), h = grid.getHeight();
            float across = Math.max(w, h * LookReview.WIDTH / (float) LookReview.HEIGHT);
            byte[] open = new byte[(w * h + 7) / 8];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!grid.isWalkable(x, y) || topology.getBuildingId(x, y) != 0) continue;
                    int bit = y * w + x;
                    open[bit >> 3] |= (byte) (1 << (bit & 7));
                }
            }
            float[] lift = lift(sim);
            JSONObject json = new JSONObject()
                    .put("seed", seed).put("width", w).put("height", h)
                    .put("frameWidth", LookReview.WIDTH).put("frameHeight", LookReview.HEIGHT)
                    .put("cellPx", (int) Math.floor(LookReview.WIDTH / across))
                    .put("open", Base64.getEncoder().encodeToString(open))
                    .put("lift", new JSONArray().put(Math.round(lift[0])).put(Math.round(lift[1])))
                    .put("cordonTrench", PickupCordonWorks.TRENCH).put("cordonWall", PickupCordonWorks.WALL);
            JSONArray redoubts = new JSONArray();
            for (CivilianRedoubt r : sim.getCivilianRedoubts()) {
                redoubts.put(new JSONObject().put("label", r.isMain() ? "Shelter" : "Holdout " + r.number())
                        .put("box", new JSONArray().put(r.left()).put(r.bottom()).put(r.right()).put(r.top())));
            }
            json.put("redoubts", redoubts);
            JSONArray pads = new JSONArray();
            for (LandingPad pad : sim.getMarineLandingPads()) pads.put(new JSONArray().put(pad.centerX).put(pad.centerY));
            json.put("pads", pads);
            JSONArray bores = new JSONArray();
            if (sim.getColonyBreach() != null) {
                for (ColonyEruptionPlan.Site site : sim.getColonyBreach().plan().sites()) {
                    bores.put(new JSONObject().put("label", site.mother() ? "Mother bore" : "Bore " + site.number())
                            .put("x", site.x()).put("y", site.y()));
                }
            }
            json.put("bores", bores);
            Path file = context.projectRoot().resolve(String.format(Locale.ROOT,
                    "build/snapshots/evac-cordon-look/placement/colony-s%d.json", seed));
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
            System.out.printf("    [placement/s%d] %dx%d cells, %d px a cell, wrote %s%n",
                    seed, w, h, json.getInt("cellPx"), file);
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                    LookReview.HEIGHT, w / 2f, h / 2f, across);
            return new SnapshotArtifact(String.format(Locale.ROOT, "placement/colony-s%d.png", seed),
                    look.renderMarked(sim, ReviewAnnotations.builder().build()));
        } finally {
            if (before == null) System.clearProperty(SWITCH);
            else System.setProperty(SWITCH, before);
        }
    }

    /** The marks {@link #marked} draws, printing each guard team's reading as it goes. */
    private static ReviewAnnotations marks(BattleSimulation sim, float[] lift, String tag) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        marks.label(lift[0], lift[1], "LIFT", ReviewStyle.LANDING);
        for (CivilianRedoubt r : sim.getCivilianRedoubts()) {
            marks.box(r.left(), r.bottom(), r.right(), r.top(), r.isMain() ? "SHELTER" : "Holdout " + r.number(),
                    r.isMain() ? ReviewStyle.LANDING : ReviewStyle.ALLY);
        }
        for (LandingPad pad : sim.getMarineLandingPads()) {
            marks.label(pad.centerX + .5f, pad.centerY + .5f, "Company pad", ReviewStyle.LANDING);
        }
        int swarmNear = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.identity().type(unit).isSwarm()) continue;
            float dx = sim.world().x(unit) - lift[0], dy = sim.world().y(unit) - lift[1];
            if (dx * dx + dy * dy <= 30f * 30f) swarmNear++;
        }
        System.out.printf("    [cordon/%s] %d guards alive, %d swarm within 30 cells of the lift%n",
                tag, sim.liveRescuePickupGuards(), swarmNear);
        List<Squad> teams = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.rescuePickupGuard && squad.aliveMembers > 0) teams.add(squad);
        }
        teams.sort(Comparator.comparingInt(s -> s.id));
        int number = 0;
        for (Squad squad : teams) {
            List<ReviewAnnotation.Cell> cells = new ArrayList<>();
            float sx = 0f, sy = 0f;
            int inTrench = 0, n = sim.squadMemberCount(squad.id);
            for (int j = 0; j < n; j++) {
                long id = sim.squadMemberAt(squad.id, j);
                int cx = sim.world().cellX(id), cy = sim.world().cellY(id);
                cells.add(new ReviewAnnotation.Cell(cx, cy));
                sx += sim.world().x(id);
                sy += sim.world().y(id);
                if (sim.fightingPositions().runIdAt(cx, cy) >= 0) inTrench++;
            }
            if (n == 0) continue;
            sx /= n;
            sy /= n;
            ObjectiveAssignment order = squad.assignmentForExecution();
            String kind = order == null ? "no order" : order.kind().name();
            if (squad.rescuePickupMech) {
                marks.cells(cells, "Cordon mech", ReviewStyle.ALLY);
                System.out.printf("    [cordon/%s] mech at %.0f,%.0f, %s%n", tag, sx, sy, kind);
                continue;
            }
            number++;
            String name = String.format(Locale.ROOT, "Team %d: %d/%d in trench", number, inTrench, n);
            marks.cells(cells, name, ReviewStyle.ALLY);
            float off = order == null ? 0f
                    : (float) Math.hypot(order.targetCellX() + .5f - sx, order.targetCellY() + .5f - sy);
            if (order != null && off > 3f) {
                marks.arrow(sx, sy, order.targetCellX() + .5f, order.targetCellY() + .5f, "its post", ReviewStyle.ROUTE);
            }
            System.out.printf("    [cordon/%s] team %d (squad %d): %d of %d alive, %d in the trench, %.1f cells off its "
                    + "post, %s%n", tag, number, squad.id, n, squad.originalSize, inTrench, off, kind);
        }
        return marks.build();
    }

    /** Where the survivors' pickup comes down: the civilian craft's landing cell. */
    private static float[] lift(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (sim.world().airFaction(id) == Faction.CIVILIAN && mission != null && mission.awaitingEvacuees) {
                return new float[]{mission.lzX, mission.lzY};
            }
        }
        throw new IllegalStateException("no pickup craft");
    }

    /** The checked-in colony fixture on {@code seed}, on the world {@code biome} names (null keeps the fixture's). */
    static JSONObject colony(long seed, String biome) throws Exception {
        try (InputStream stream = EvacCordonLookSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            JSONObject fixture = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).put("seed", seed);
            if (biome != null) fixture.getJSONObject("targetProfile").put("surface", biome);
            return fixture;
        }
    }
}
