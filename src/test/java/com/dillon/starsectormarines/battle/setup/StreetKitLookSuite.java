package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * The industrial and checkpoint street kit ({@link StreetDressing}) staged on a colony's main street, ahead of the
 * industrial zone that will lay its gantries (the cordoned facility lays the boom gate at its gatehouses): a pipe gantry across the street a third of the way
 * from the plaza, and a boom gate with bollards across it near the hall. A preview of the hooks on a real street,
 * not a map the game lays. Before staging, the street as laid is framed for the owner's lamp choice, wide and close
 * on its first lamp: as shipped (A, lantern heads on the kerb) and with each kerb lamp moved onto the shopfront behind
 * it on a wall bracket (B). Look-preset stills. Opt-in.
 */
public final class StreetKitLookSuite implements SnapshotSuite {

    static final long SEED = 1;
    static final float CLOSE_CELLS = 30f;
    static final float AWNING_CELLS = 12f;
    /** Cells of sight round the awning that put the figure under it in the player's view. */
    static final int SEEN_RANGE = 4;
    static final int LOBSTER_SEEDS = 16;

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
            // The lamp choice first, on the street as the game lays it: kerb heads (shipped) against wall brackets.
            float nearX = street.startX() + (street.endX() - street.startX()) / 3f;
            float nearY = street.startY() + (street.endY() - street.startY()) / 3f;
            Doodad lamp = null;
            for (Doodad d : sim.getDoodads()) if (StreetDressing.LAMP.equals(d.definitionId())) { lamp = d; break; }
            if (lamp == null) throw new IllegalStateException("no kerb lamp on the street");
            lampOption(context, sim, "lamps-a-kerb", nearX, nearY, lamp, out);
            awningFade(context, sim, out);
            lampOption(context, sim, "lamps-b-wall", nearX, nearY, onWalls(sim, lamp), out);

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
        lobster(context, out);
        return out;
    }

    /**
     * The lobster trade's dressing where colony maps lay it, each found on the first seed that carries it and framed
     * close with roofs off: a main street's licensed kitchen and its burst tank, a market fish stall's tank, and a
     * freight yard's crates.
     */
    private static void lobster(SnapshotContext context, List<SnapshotArtifact> out) throws Exception {
        Map<String, String> wanted = new LinkedHashMap<>();
        wanted.put("lobster-kitchen", "doodad.lobster-cooker");
        wanted.put("lobster-burst", "doodad.lobster-tank-burst");
        wanted.put("lobster-stall", "doodad.lobster-tank");
        wanted.put("lobster-freight", "doodad.lobster-crate");
        for (long seed = 1; seed <= LOBSTER_SEEDS && !wanted.isEmpty(); seed++) {
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                for (Iterator<Map.Entry<String, String>> it = wanted.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, String> want = it.next();
                    Doodad at = null;
                    for (Doodad d : sim.getDoodads()) {
                        // A stall's tank is one with no range beside it.
                        if (want.getValue().equals(d.definitionId())
                                && (!want.getKey().equals("lobster-stall") || !nearCooker(sim, d))) { at = d; break; }
                    }
                    if (at == null) continue;
                    System.out.printf(Locale.ROOT, "[street-kit-look] %s on seed %d at %d,%d%n", want.getKey(), seed,
                            at.cellX, at.cellY);
                    out.add(new SnapshotArtifact(want.getKey() + ".png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, at.cellX + 0.5f, at.cellY + 0.5f, AWNING_CELLS)
                            .withoutRoofs().render(sim, want.getKey())));
                    it.remove();
                }
            }
        }
    }

    private static boolean nearCooker(BattleSimulation sim, Doodad tank) {
        for (Doodad d : sim.getDoodads()) {
            if ("doodad.lobster-cooker".equals(d.definitionId())
                    && Math.abs(d.cellX - tank.cellX) <= 2 && Math.abs(d.cellY - tank.cellY) <= 2) return true;
        }
        return false;
    }

    /**
     * The first awning on the street with nobody under it, then with a defender under it the player cannot see (the
     * awning stays whole over them), then with that defender in the player's sight (a scout's vision projected onto
     * the awning for one fog update), where the awning fades over them and the pavement shows through.
     */
    private static void awningFade(SnapshotContext context, BattleSimulation sim, List<SnapshotArtifact> out)
            throws Exception {
        Doodad awning = null;
        for (Doodad d : sim.getDoodads()) {
            if (StreetDressing.AWNINGS.contains(d.definitionId())) { awning = d; break; }
        }
        long figure = 0L;
        for (int i = 0; i < sim.getRoster().liveCount() && figure == 0L; i++) {
            long u = sim.getRoster().get(i);
            UnitType type = sim.identity().type(u);
            if (sim.identity().faction(u) == Faction.DEFENDER && type != null && !type.isStatic()) figure = u;
        }
        if (awning == null || figure == 0L) throw new IllegalStateException("no awning or no defender to stage");
        float cx = awning.cellX + awning.footprintCellsX * 0.5f, cy = awning.cellY + awning.footprintCellsY * 0.5f;
        out.add(new SnapshotArtifact("awning-clear.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, cx, cy, AWNING_CELLS).render(sim, "awning")));
        float x = sim.world().x(figure), y = sim.world().y(figure);
        sim.world().setPos(figure, cx, cy);
        out.add(new SnapshotArtifact("awning-fade.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, cx, cy, AWNING_CELLS).render(sim, "awning")));
        FogOfWarService fog = sim.getFogOfWar();
        fog.addEphemeralSource((int) cx, (int) cy, SEEN_RANGE, 0f);
        fog.tick(0, sim.getRoster());
        out.add(new SnapshotArtifact("awning-fade-seen.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, cx, cy, AWNING_CELLS).render(sim, "awning")));
        fog.clearEphemeralSources();
        fog.tick(0, sim.getRoster());
        sim.world().setPos(figure, x, y);
    }

    private static void lampOption(SnapshotContext context, BattleSimulation sim, String name, float x, float y,
                                   Doodad lamp, List<SnapshotArtifact> out) throws IOException {
        out.add(new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, x, y, CLOSE_CELLS).render(sim, name)));
        out.add(new SnapshotArtifact(name + "-close.png", BattleReviewFrameRenderer.look(context.modRoot(),
                1920, 1080, lamp.cellX + 0.5f, lamp.cellY + 0.5f, AWNING_CELLS).render(sim, name)));
    }

    /**
     * Option B: each kerb lamp taken off the kerb and hung on a bracket off the wall behind it, on the pavement row
     * in front of the wall, slid along it clear of any awning. Returns where {@code follow} went.
     */
    private static Doodad onWalls(BattleSimulation sim, Doodad follow) {
        TileRegistry registry = TileRegistry.installed();
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        List<Doodad> doodads = sim.getDoodads();
        boolean[] awned = new boolean[grid.getWidth() * grid.getHeight()];
        for (Doodad d : doodads) {
            if (!StreetDressing.AWNINGS.contains(d.definitionId())) continue;
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++)
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) awned[y * grid.getWidth() + x] = true;
        }
        Doodad moved = follow;
        for (int i = 0; i < doodads.size(); i++) {
            Doodad d = doodads.get(i);
            if (!StreetDressing.LAMP.equals(d.definitionId())) continue;
            // The lamp's front faces the carriageway; the wall lies the other way across the pavement.
            int[] front = {0, 1, -1, 0, 0, -1, 1, 0};
            int wx = -front[d.quarterTurns * 2], wy = -front[d.quarterTurns * 2 + 1];
            int x = d.cellX, y = d.cellY;
            for (int k = 0; k < 4 && (grid.isWalkable(x + wx, y + wy) || topology.isFixture(x + wx, y + wy)); k++) {
                x += wx;
                y += wy;
            }
            if (grid.isWalkable(x + wx, y + wy) || topology.isFixture(x + wx, y + wy)) continue;
            Doodad wall = null;
            for (int slide : new int[]{0, 1, -1, 2, -2}) {
                int cx = x + wy * slide, cy = y + wx * slide;
                if (awned[cy * grid.getWidth() + cx] || !grid.isWalkable(cx, cy) || grid.isDoorway(cx, cy)) continue;
                if (grid.isWalkable(cx + wx, cy + wy) || topology.isFixture(cx + wx, cy + wy)) continue;
                List<Doodad> one = new ArrayList<>();
                StreetDressing.wallLamp(one, StreetDressing.WALL_LAMP, cx, cy, wx, wy);
                wall = one.isEmpty() ? null : one.get(0);
                break;
            }
            if (wall == null) continue;
            doodads.set(i, wall);
            grid.setWalkable(d.cellX, d.cellY, true);
            topology.setFixture(d.cellX, d.cellY, false);
            if (d == follow) moved = wall;
        }
        if (registry.doodad(StreetDressing.WALL_LAMP) == null) throw new IllegalStateException("no wall lamp art");
        return moved;
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
