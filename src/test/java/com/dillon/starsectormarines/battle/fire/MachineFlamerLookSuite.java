package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.ReviewFactions;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The machine flamers burning (`mechs-nouns.md`, Machine flamers), in the look preset: the pirate Torch with its FL-1
 * burner in the north lane and the Path Censer with its FL-2 lance in the south, each on a bare pad facing east at a
 * militia picket standing in brush with a light wall behind it. Nothing is scripted: each rig fires when its own fire
 * control lets it ({@link FireReach}), and the fire goes as far as the brush and the wall carry it. The picket wears
 * plate nothing here gets through, so the picture is the fire's. A look shot only: no outcome is judged.
 */
public final class MachineFlamerLookSuite implements SnapshotSuite {

    private static final int WINDOW_W = 34, WINDOW_H = 24;
    private static final int SECONDS = 30;
    /** Window columns: the rigs' file, where the brush starts (four bare cells ahead of them), and the brush's end. */
    private static final int RIG_X = 3, BRUSH_X = 8, BRUSH_END = 26;

    /** One lane: its rig, its row, and how far ahead of the rig its picket and wall stand. */
    private record Lane(String key, MechVariant rig, int row, int picketAhead, int wallAhead) { }

    private static final List<Lane> LANES = List.of(
            new Lane("torch", MechVariant.TORCH, 17, 6, 9),
            new Lane("censer", MechVariant.CENSER, 6, 9, 12));

    @Override public String id() { return "machine-flamer-look"; }

    @Override public String label() { return "Look: the Torch's burner and the Censer's lance burning brush and a light wall"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        try (ReviewFactions ignored = ReviewFactions.bind("pirates", "luddic_path")) {
            return LookReview.record(context.modRoot(), shot());
        }
    }

    private static LookReview.Shot shot() {
        return new LookReview.Shot("flamers-burn", LookGround.verdant(), WINDOW_W, WINDOW_H, placement -> {
            LookGround.Site site = placement.site();
            MapResult map = placement.map();
            for (Lane lane : LANES) {
                FlamerForestLookSuite.plantWood(map, site, BRUSH_X, lane.row() - 4, BRUSH_END, lane.row() + 5,
                        BRUSH_END, Integer.MIN_VALUE, Integer.MIN_VALUE);
                bare(map, site, 0, lane.row() - 4, BRUSH_X, lane.row() + 5);
                lightWall(map, site, RIG_X + lane.wallAhead(), lane.row() - 2, lane.row() + 2);
            }
            SceneBuilder builder = placement.holdouts(placement.builder());
            for (Lane lane : LANES) {
                int x = RIG_X, y = lane.row();
                builder.squad(lane.key()).faction(Faction.MARINE).size(1).at(x, y)
                        .mech(lane.rig(), MechRole.ASSAULT)
                        .assigned(id -> ObjectiveAssignment.defendArea(id, site.x(x), site.y(y)))
                        .done();
                int px = RIG_X + lane.picketAhead();
                builder.squad(lane.key() + "-picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                        .at(px, y).inFile(px, y - 1, 0, 1).armor(400f, 60f).unarmed().stationary().done();
            }
            SceneWorld world = builder.build();
            for (Lane lane : LANES) {
                long rig = placement.subject(lane.key(), world.members(lane.key())[0]);
                world.sim().world().setPos(rig, site.x(RIG_X) + .5f, site.y(lane.row()) + .5f);
                FacingSystem.faceStanding(world.sim().getEntityWorld(), world.sim().getBattleComponents(), rig, -90f);
            }
            return world.sim();
        }, placement -> {
            LookGround.Site site = placement.site();
            List<LookReview.View> views = new ArrayList<>();
            views.add(LookReview.View.play(site.x(WINDOW_W / 2f), site.y(WINDOW_H / 2f)));
            for (Lane lane : LANES) {
                views.add(LookReview.View.close(lane.key(), site.x(RIG_X + lane.wallAhead() / 2f + 1f),
                        site.y(lane.row() + .5f), 16f));
            }
            return views;
        }, 30 * SECONDS, 2, 4);
    }

    /** Clears window cells {@code [x0, x1) x [y0, y1)} to bare dirt with nothing on it: the rigs' pads. */
    private static void bare(MapResult map, LookGround.Site site, int x0, int y0, int x1, int y1) {
        for (int ly = y0; ly < y1; ly++) {
            for (int lx = x0; lx < x1; lx++) {
                int x = site.x(lx), y = site.y(ly);
                if (!map.grid.inBounds(x, y)) continue;
                map.grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, false);
                map.grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, false);
                map.grid.setWalkable(x, y, true);
                map.topology.setFixture(x, y, false);
                map.topology.setNatureOverlayIndex(x, y, -1);
                map.topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
            }
        }
        int ax = site.x(x0), bx = site.x(x1 - 1), ay = site.y(y0), by = site.y(y1 - 1);
        map.doodads.removeIf(d -> d.cellX >= Math.min(ax, bx) && d.cellX <= Math.max(ax, bx)
                && d.cellY >= Math.min(ay, by) && d.cellY <= Math.max(ay, by));
    }

    /**
     * A light wall across the lane: a short north-south run of ordinary wall at a light wall's structure
     * ({@link BattleFire#LIGHT_WALL_HP}), which a fire held against it catches and burns through to rubble.
     */
    private static void lightWall(MapResult map, LookGround.Site site, int lx, int ly0, int ly1) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        int surface = topology.surfaceIndex(InstallationWalls.PERIMETER);
        int x = site.x(lx);
        for (int ly = ly0; ly <= ly1; ly++) {
            int y = site.y(ly);
            topology.setNatureOverlayIndex(x, y, -1);
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setWallHp(x, y, BattleFire.LIGHT_WALL_HP);
            topology.setWall(x, y, true);
            topology.setSurface(x, y, surface);
            topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
        }
        for (int ly = ly0 - 1; ly <= ly1 + 1; ly++) {
            int y = site.y(ly);
            for (int cx = x - 1; cx <= x + 1; cx++) {
                if (topology.isWall(cx, y)) {
                    int mask = 0;
                    if (!topology.isWall(cx, y + 1)) mask |= CellTopology.WALL_DIR_N;
                    if (!topology.isWall(cx, y - 1)) mask |= CellTopology.WALL_DIR_S;
                    if (!topology.isWall(cx + 1, y)) mask |= CellTopology.WALL_DIR_E;
                    if (!topology.isWall(cx - 1, y)) mask |= CellTopology.WALL_DIR_W;
                    topology.setWallDirMask(cx, y, mask);
                }
                grid.recomputeCoverAt(cx, y);
            }
        }
    }
}
