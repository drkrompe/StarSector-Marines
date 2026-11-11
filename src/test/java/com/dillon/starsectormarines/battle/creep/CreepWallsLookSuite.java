package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hive creep reaching the colony's stood-up walls as the battle grows it ({@code voids.md}, buildings in depth;
 * {@code colony-breach-nouns.md}, Creep): the canonical colony fixture, its eruption sites opened on the breach
 * schedule and the field ticked with the roofs caving in under it, as {@link HiveCreepSnapshotSuite} replays it. The
 * look preset at play zoom on the building the creep holds most of the walls of by six minutes, before the tremor
 * ({@code walls-m0.png}) and at two, four and six minutes ({@code walls-mN.png}). Opt-in.
 */
public final class CreepWallsLookSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int TICKS_PER_MINUTE = 1800;
    private static final long ROOF_SEED = 20261021L;

    @Override public String id() { return "creep-walls-look"; }

    @Override public String label() { return "Hive creep on the colony's stood-up walls, as the breach grows it, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        // A renderer first: it installs the tile catalogs the colony's generator places props from.
        new BattleReviewFrameRenderer(context.modRoot(), 64, 64);
        BattleSimulation sim = colony();
        List<ColonyEruptionPlan.Site> sites = sim.getColonyBreach().plan().sites();
        boolean[] opened = new boolean[sites.size()];
        CreepRoofCollapse roofs = new CreepRoofCollapse(ROOF_SEED);
        // Where to look is only known once the creep has grown: grow it on a copy of the schedule first.
        int minutes = Integer.parseInt(System.getProperty("snapshot.creepWalls.minutes", "6").trim());
        BattleSimulation probe = colony();
        grow(probe, sites, new boolean[sites.size()], new CreepRoofCollapse(ROOF_SEED), 0, minutes * TICKS_PER_MINUTE);
        census(probe, minutes + " min");
        Building pick = mostHeld(probe);
        probe.close();
        if (pick == null) throw new IllegalStateException("the creep holds no building's walls");
        float vx = (pick.minX + pick.maxX + 1) / 2f, vy = (pick.minY + pick.maxY + 1) / 2f + 6f;
        System.out.printf(Locale.ROOT, "    [creep-walls] building %d (%s) %d,%d to %d,%d%n", pick.id, pick.kind,
                pick.minX, pick.minY, pick.maxX, pick.maxY);
        List<SnapshotArtifact> out = new ArrayList<>();
        int tick = 0;
        for (int m = 0; m <= minutes; m += 2) {
            tick = grow(sim, sites, opened, roofs, tick, m * TICKS_PER_MINUTE);
            report(sim, pick, m);
            out.add(new SnapshotArtifact(String.format(Locale.ROOT, "walls-m%d.png", m),
                    BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, vx, vy,
                            BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, "")));
        }
        sim.close();
        return out;
    }

    private static int grow(BattleSimulation sim, List<ColonyEruptionPlan.Site> sites, boolean[] opened,
                            CreepRoofCollapse roofs, int tick, int target) {
        CreepField field = sim.creepField();
        for (; tick < target; tick++) {
            for (int i = 0; i < sites.size(); i++) {
                if (opened[i] || tick < ColonyEruptionPlan.eruptionTick(sites.get(i), 0)) continue;
                opened[i] = true;
                ColonyCreep.siteErupted(sim, sites.get(i), tick);
            }
            field.tick(tick);
            if (tick % CreepRoofCollapse.CHECK_TICKS == 0) roofs.look(sim, tick);
        }
        return tick;
    }

    /** The building whose own wall cells the creep covers most, summed. */
    private static Building mostHeld(BattleSimulation sim) {
        CreepField field = sim.creepField();
        Building best = null;
        long bestSum = 0;
        for (Building b : sim.getBuildings().all()) {
            long sum = wallCoverage(sim, field, b)[0];
            if (sum > bestSum) { bestSum = sum; best = b; }
        }
        return best;
    }

    /** The building's wall cells (its own, and those round its floor): summed coverage, cells, and cells showing creep. */
    private static long[] wallCoverage(BattleSimulation sim, CreepField field, Building b) {
        long sum = 0, walls = 0, shown = 0;
        int w = sim.getGrid().getWidth();
        for (int y = b.minY - 1; y <= b.maxY + 1; y++) {
            for (int x = b.minX - 1; x <= b.maxX + 1; x++) {
                if (!sim.getTopology().inBounds(x, y) || !sim.getTopology().isWall(x, y)) continue;
                int cov = field.coverageAt(y * w + x);
                walls++;
                sum += cov;
                if (cov >= 16) shown++;
            }
        }
        return new long[]{sum, walls, shown};
    }

    /** Every wall cell on the map, and how many the creep shows on and holds at all. */
    private static void census(BattleSimulation sim, String when) {
        CreepField field = sim.creepField();
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight(), walls = 0, any = 0, shown = 0, ground = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cov = field.coverageAt(y * w + x);
                if (!sim.getTopology().isWall(x, y)) { if (cov > 0) ground++; continue; }
                walls++;
                if (cov > 0) any++;
                if (cov >= 16) shown++;
            }
        }
        System.out.printf(Locale.ROOT, "    [creep-walls] %s: %d ground cells creeped; %d wall cells, %d held, %d showing%n",
                when, ground, walls, any, shown);
    }

    private static void report(BattleSimulation sim, Building b, int minute) {
        long[] c = wallCoverage(sim, sim.creepField(), b);
        System.out.printf(Locale.ROOT, "    [creep-walls] m%d: %d of %d wall cells show creep, mean coverage %.0f%n",
                minute, c[2], c[1], c[1] == 0 ? 0f : c[0] / (float) c[1]);
    }

    private static BattleSimulation colony() throws Exception {
        String json;
        try (InputStream stream = CreepWallsLookSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        BattleFixture parsed = BattleFixtureJson.fromJson(new JSONObject(json));
        return (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
    }
}
