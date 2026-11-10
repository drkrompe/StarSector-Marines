package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.ReviewFactions;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The low-tech row's first faction variants (`mech-lines.md`): the pirate Hauler, and the Luddic Path's Foundry
 * Breaker and Pilgrim, each its own body over its frame's legs and mounts with its stock fit drawn as layers. Three
 * shots, owner-facing look frames only, no outcome judged:
 * <ul>
 *   <li>{@code variants-line}: the three stood beside their frames (a pirate Bulwark and Hound), facing north;</li>
 *   <li>{@code variants-walk}: the three walking east on real move orders, for the gait under each body;</li>
 *   <li>{@code pilgrim-crush}: a Pilgrim ordered across an ordinary wall row, which it pushes down by its weight.</li>
 * </ul>
 */
public final class LowtechVariantsLookSuite implements SnapshotSuite {

    @Override public String id() { return "lowtech-variants-look"; }

    @Override public String label() { return "Pirate Hauler, Path Foundry Breaker and Pilgrim: line, walk, wall crush"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        try (ReviewFactions ignored = ReviewFactions.bind("pirates", "luddic_path")) {
            out.addAll(LookReview.record(context.modRoot(), line()));
            out.addAll(LookReview.record(context.modRoot(), walk()));
            out.addAll(LookReview.record(context.modRoot(), crush()));
        }
        return out;
    }

    /** Variants beside their frames, west to east: Hound, Breaker, Bulwark, Hauler, Pilgrim. */
    private static final MechVariant[] LINE = {MechVariant.HOUND, MechVariant.FOUNDRY_BREAKER, MechVariant.BULWARK,
            MechVariant.HAULER, MechVariant.PILGRIM};

    static LookReview.Shot line() {
        return LookReview.Shot.of("variants-line", 26, 12, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            float[][] at = new float[LINE.length][];
            for (int i = 0; i < LINE.length; i++) {
                at[i] = placement.openNear(4f + i * 4.5f, 6f);
                int x = (int) at[i][0], y = (int) at[i][1];
                builder.squad(key(i)).faction(Faction.MARINE).size(1)
                        .at(x - placement.site().x0(), y - placement.site().y0())
                        .mech(LINE[i], MechRole.BALANCED)
                        .assigned(id -> ObjectiveAssignment.defendArea(id, x, y))
                        .done();
            }
            SceneWorld world = builder.build();
            for (int i = 0; i < LINE.length; i++) {
                long id = placement.subject(key(i), world.members(key(i))[0]);
                world.sim().world().setPos(id, (int) at[i][0] + .5f, (int) at[i][1] + .5f);
                FacingSystem.faceStanding(world.sim().getEntityWorld(), world.sim().getBattleComponents(), id, 0f);
            }
            return world.sim();
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, key(0), key(4)),
                LookReview.View.follow("close", placement, 22f, key(0), key(4))), 30).withStills(2);
    }

    private static final MechVariant[] WALKERS = {MechVariant.FOUNDRY_BREAKER, MechVariant.HAULER, MechVariant.PILGRIM};

    static LookReview.Shot walk() {
        return LookReview.Shot.of("variants-walk", 34, 14, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            long[] ids = new long[WALKERS.length];
            int[][] goal = new int[WALKERS.length][];
            for (int i = 0; i < WALKERS.length; i++) {
                float[] at = placement.openNear(3f, 2.5f + i * 4.5f);
                float[] to = placement.openNear(30f, 2.5f + i * 4.5f);
                goal[i] = new int[] {(int) to[0], (int) to[1]};
                int x = (int) at[0], y = (int) at[1];
                builder.squad("walker" + i).faction(Faction.MARINE).size(1)
                        .at(x - placement.site().x0(), y - placement.site().y0())
                        .mech(WALKERS[i], MechRole.BALANCED)
                        .assigned(id -> ObjectiveAssignment.defendArea(id, x, y))
                        .done();
            }
            SceneWorld world = builder.build();
            for (int i = 0; i < WALKERS.length; i++) {
                ids[i] = placement.subject("walker" + i, world.members("walker" + i)[0]);
                FacingSystem.faceStanding(world.sim().getEntityWorld(), world.sim().getBattleComponents(), ids[i],
                        -90f);
            }
            boolean[] ordered = {false};
            placement.beforeTick(sim -> {
                if (ordered[0]) return;
                ordered[0] = true;
                for (int i = 0; i < ids.length; i++) {
                    sim.getMechMoveOrderService().requestMove(ids[i], goal[i][0], goal[i][1]);
                }
            });
            return world.sim();
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "walker0", "walker1", "walker2"),
                LookReview.View.follow("close", placement, 18f, "walker1")), 600).withStills(20, 50, 80);
    }

    /** The wall row's place in the crush shot's window: an ordinary city wall, 100 structure. */
    private static final int WALL_Y = 9;
    private static final int WALL_X0 = 2, WALL_X1 = 25;

    static LookReview.Shot crush() {
        return LookReview.Shot.of("pilgrim-crush", 28, 20, placement -> {
            layWall(placement);
            SceneBuilder builder = placement.holdouts(placement.builder());
            float[] at = placement.openNear(14f, 16f);
            int x = (int) at[0], y = (int) at[1];
            builder.squad("pilgrim").faction(Faction.MARINE).size(1)
                    .at(x - placement.site().x0(), y - placement.site().y0())
                    .mech(MechVariant.PILGRIM, MechRole.BALANCED)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, x, y))
                    .done();
            SceneWorld world = builder.build();
            long pilgrim = placement.subject("pilgrim", world.members("pilgrim")[0]);
            FacingSystem.faceStanding(world.sim().getEntityWorld(), world.sim().getBattleComponents(), pilgrim, 180f);
            float[] goal = placement.openNear(14f, 2f);
            boolean[] ordered = {false};
            placement.beforeTick(sim -> {
                if (ordered[0]) return;
                ordered[0] = true;
                sim.getMechMoveOrderService().requestMove(pilgrim, (int) goal[0], (int) goal[1]);
            });
            return world.sim();
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "pilgrim"),
                LookReview.View.follow("close", placement, 14f, "pilgrim")), 720).withStills(0, 40, 60, 80, 110);
    }

    private static String key(int i) {
        return "line" + i;
    }

    /** Clears a strip of the window to bare ground and stands an ordinary perimeter-faced wall row on it. */
    private static void layWall(LookReview.Placement placement) {
        NavigationGrid grid = placement.map().grid;
        CellTopology topology = placement.map().topology;
        LookGround.Site site = placement.site();
        int surface = topology.surfaceIndex(InstallationWalls.PERIMETER);
        int x0 = site.x0() + WALL_X0 - 1, x1 = site.x0() + WALL_X1 + 1;
        int y0 = site.y0() + WALL_Y - 2, y1 = site.y0() + WALL_Y + 2;
        placement.map().doodads.removeIf(d -> d.cellX >= x0 && d.cellX <= x1 && d.cellY >= y0 && d.cellY <= y1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, false);
                grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, false);
                grid.setWalkable(x, y, true);
                topology.setFixture(x, y, false);
                topology.setNatureOverlayIndex(x, y, -1);
            }
        }
        int y = site.y0() + WALL_Y;
        for (int x = site.x0() + WALL_X0; x <= site.x0() + WALL_X1; x++) {
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setWallHp(x, y, WallCrush.HEAVY_CEILING);
            topology.setWall(x, y, true);
            topology.setSurface(x, y, surface);
            topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
        }
        for (int cy = y - 1; cy <= y + 1; cy++) {
            for (int x = x0; x <= x1; x++) {
                if (topology.isWall(x, cy)) {
                    int mask = 0;
                    if (!topology.isWall(x, cy + 1)) mask |= CellTopology.WALL_DIR_N;
                    if (!topology.isWall(x, cy - 1)) mask |= CellTopology.WALL_DIR_S;
                    if (!topology.isWall(x + 1, cy)) mask |= CellTopology.WALL_DIR_E;
                    if (!topology.isWall(x - 1, cy)) mask |= CellTopology.WALL_DIR_W;
                    topology.setWallDirMask(x, cy, mask);
                }
                grid.recomputeCoverAt(x, cy);
            }
        }
    }
}
