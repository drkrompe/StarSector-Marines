package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The industrial and checkpoint street kit ({@link StreetDressing}) staged on a colony's main street, ahead of the
 * industrial zone and the cordoned facility that will lay it: a pipe gantry across the street a third of the way
 * from the plaza, and a boom gate with bollards across it near the hall. A preview of the hooks on a real street,
 * not a map the game lays. Before staging, the street as laid is framed twice for the owner's style choice: as
 * shipped (cast-iron lamps, canvas awnings) and restyled industrial (cobra-head lamps, steel canopies). Look-preset
 * stills. Opt-in.
 */
public final class StreetKitLookSuite implements SnapshotSuite {

    static final long SEED = 1;
    static final float CLOSE_CELLS = 30f;

    @Override public String id() { return "street-kit-look"; }

    @Override public String label() { return "Pipe gantry and checkpoint kit staged on a main street"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        MapScale scale = MapScale.COLONY;
        MapResult map = new BspCityGenerator().generate(scale.width, scale.height, SEED, null, null,
                BattleSetup.colonyPlanFor(scale, SEED));
        if (map.mainStreets.isEmpty()) throw new IllegalStateException("seed " + SEED + " has no main street");
        MainStreet street = map.mainStreets.get(0);
        try (BattleSimulation sim = BattleSetup.createCivilianRescue(SEED, Collections.emptyList(), false,
                RiskLevel.HIGH)) {
            if (!MainStreetLookSuite.sameStreet(sim.getTopology(), street))
                throw new IllegalStateException("the battle laid another map");
            // The style choice first, on the street as the game lays it: colonial (shipped) against industrial.
            float nearX = street.startX() + (street.endX() - street.startX()) / 3f;
            float nearY = street.startY() + (street.endY() - street.startY()) / 3f;
            out.add(new SnapshotArtifact("option-a-colonial.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, nearX, nearY, CLOSE_CELLS).render(sim, "colonial")));
            restyle(sim.getDoodads());
            out.add(new SnapshotArtifact("option-b-industrial.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, nearX, nearY, CLOSE_CELLS).render(sim, "industrial")));

            int[] gantry = stage(sim, street, 1f / 3f, true);
            int[] gate = stage(sim, street, 0.8f, false);
            System.out.printf(Locale.ROOT, "[%s] gantry at %d,%d; gate at %d,%d%n", id(),
                    gantry[0], gantry[1], gate[0], gate[1]);
            out.add(new SnapshotArtifact("gantry.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, gantry[0] + 0.5f, gantry[1] + 0.5f, CLOSE_CELLS).render(sim, "gantry")));
            out.add(new SnapshotArtifact("gantry-play.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, gantry[0] + 0.5f, gantry[1] + 0.5f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                    .render(sim, "gantry")));
            out.add(new SnapshotArtifact("checkpoint.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, gate[0] + 0.5f, gate[1] + 0.5f, CLOSE_CELLS).render(sim, "checkpoint")));
        }
        return out;
    }

    /** Option B: every lamp a cobra-head and every awning a steel canopy, each in its own place and turn. */
    private static void restyle(List<Doodad> doodads) {
        TileRegistry registry = TileRegistry.installed();
        for (int i = 0; i < doodads.size(); i++) {
            Doodad d = doodads.get(i);
            String id = StreetDressing.LAMP.equals(d.definitionId()) ? StreetDressing.LAMP_INDUSTRIAL
                    : StreetDressing.AWNINGS.contains(d.definitionId()) ? StreetDressing.AWNINGS.get(2) : null;
            if (id != null) doodads.set(i, new Doodad(d.cellX, d.cellY, registry.doodad(id), d.quarterTurns));
        }
    }

    /**
     * Stages a gantry or a gate across the street at the first spot from {@code share} of the way down where both
     * kerbs are open pavement. Returns the street's centre cell there.
     */
    private static int[] stage(BattleSimulation sim, MainStreet street, float share, boolean gantry) {
        boolean horizontal = street.startY() == street.endY();
        int from = Math.round(horizontal ? street.startX() : street.startY());
        int to = Math.round(horizontal ? street.endX() : street.endY());
        int mid = Math.round(horizontal ? street.startY() : street.startX());
        int dir = Integer.signum(to - from);
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (int a = from + Math.round((to - from) * share); a != to; a += dir) {
            int lo = kerb(grid, topology, horizontal, a, mid, -1), hi = kerb(grid, topology, horizontal, a, mid, 1);
            // Both kerbs of the street itself, not the far side of a cross street.
            if (lo < 0 || hi < 0 || hi - lo > street.width() + 2) continue;
            if (gantry) {
                StreetDressing.pipeRun(grid, topology, sim.getDoodads(), x(horizontal, a, lo), y(horizontal, a, lo),
                        x(horizontal, a, hi), y(horizontal, a, hi), 0);
            } else {
                int px = x(horizontal, a, lo), py = y(horizontal, a, lo);
                StreetDressing.boomGate(grid, topology, sim.getDoodads(), px, py,
                        horizontal ? 0 : 1, horizontal ? 1 : 0);
                for (int c = lo + 1 + StreetDressing.BOOM_REACH; c < hi; c++)
                    StreetDressing.bollard(grid, topology, sim.getDoodads(), x(horizontal, a, c), y(horizontal, a, c));
                int back = a - dir * 2;
                StreetDressing.jerseyBarrier(grid, topology, sim.getDoodads(),
                        Math.min(x(horizontal, back, hi - 2), x(horizontal, back, hi - 1)),
                        Math.min(y(horizontal, back, hi - 2), y(horizontal, back, hi - 1)), !horizontal);
            }
            return new int[]{x(horizontal, a, mid), y(horizontal, a, mid)};
        }
        throw new IllegalStateException("no open kerbs on the street");
    }

    /** The first pavement cell out from the centre line toward {@code side}, if it is open ground. */
    private static int kerb(NavigationGrid grid, CellTopology topology, boolean horizontal, int a, int mid, int side) {
        for (int c = mid; c >= 0 && c < (horizontal ? grid.getHeight() : grid.getWidth()); c += side) {
            int x = x(horizontal, a, c), y = y(horizontal, a, c);
            if (topology.getGroundKind(x, y) == GroundKind.STREET) continue;
            return grid.isWalkable(x, y) && !topology.isFixture(x, y) ? c : -1;
        }
        return -1;
    }

    private static int x(boolean horizontal, int along, int across) { return horizontal ? along : across; }

    private static int y(boolean horizontal, int along, int across) { return horizontal ? across : along; }
}
