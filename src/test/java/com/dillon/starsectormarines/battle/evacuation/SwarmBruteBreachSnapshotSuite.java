package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;

/**
 * The Brute breach loops, from the same run that produced their verdicts; and for the owner, a Brute at a fort's wall
 * on real ground: a square of colony compound wall, its gate on the far side, marked works ({@link FortifiedWorks}),
 * three Marines holding inside and a Brute coming at the near wall.
 */
public final class SwarmBruteBreachSnapshotSuite extends BehaviorSceneSnapshotSuite {

    static final int W = 40, H = 24, TICKS = 1500;
    /** The compound's wall ring, window-local and inclusive; its gate is the east side's middle two cells. */
    static final int LEFT = 16, RIGHT = 28, TOP = 6, BOTTOM = 18;
    /**
     * Recorded frames (every 6 ticks) kept as stills: the charge, then every five seconds to the end. A Brute alone
     * opens a house-strength wall in about five seconds.
     */
    static final int[] STILLS = {20, 50, 100, 150, 200, 249};

    public SwarmBruteBreachSnapshotSuite() {
        super(new SwarmBruteBreachScene(), 1920, 1080);
    }

    @Override protected boolean colourAnimation() { return true; }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected int[] stillFrames(String loopId) {
        return loopId.equals("far-door") ? new int[]{6, 10, 13, 16} : new int[0];
    }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(new LookReview.Shot("works-wall", LookGround.verdant(), W, H,
                SwarmBruteBreachSnapshotSuite::stage,
                placement -> List.of(
                        LookReview.View.followAtPlay(placement, "brute"),
                        LookReview.View.follow("brute-close", placement, 12f, "brute")),
                TICKS, 6, STILLS.length, STILLS));
    }

    /** The compound laid on the window's ground, the Marines inside it, the Brute eight cells off its west wall. */
    private static BattleSimulation stage(LookReview.Placement placement) {
        NavigationGrid grid = placement.map().grid;
        CellTopology topology = placement.map().topology;
        LookGround.Site site = placement.site();
        int surface = topology.surfaceIndex(InstallationWalls.PERIMETER);
        int gate = (TOP + BOTTOM) / 2;
        // Cleared to bare ground first: a bush or a boulder left under a wall cell takes the Brute's blows for it.
        int x0 = site.x0() + LEFT - 2, x1 = site.x0() + RIGHT + 2, y0 = site.y0() + TOP - 2, y1 = site.y0() + BOTTOM + 2;
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
        for (int ly = TOP; ly <= BOTTOM; ly++) {
            for (int lx = LEFT; lx <= RIGHT; lx++) {
                boolean ring = lx == LEFT || lx == RIGHT || ly == TOP || ly == BOTTOM;
                boolean gap = lx == RIGHT && (ly == gate || ly == gate + 1);
                if (!ring || gap) continue;
                int x = site.x0() + lx, y = site.y0() + ly;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                grid.setWallHp(x, y, CivilDefenceCompound.WALL_HP);
                topology.setWall(x, y, true);
                topology.setSurface(x, y, surface);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
            }
        }
        for (int ly = TOP - 1; ly <= BOTTOM + 1; ly++) {
            for (int lx = LEFT - 1; lx <= RIGHT + 1; lx++) {
                int x = site.x0() + lx, y = site.y0() + ly;
                if (topology.isWall(x, y)) {
                    int mask = 0;
                    if (!topology.isWall(x, y + 1)) mask |= CellTopology.WALL_DIR_N;
                    if (!topology.isWall(x, y - 1)) mask |= CellTopology.WALL_DIR_S;
                    if (!topology.isWall(x + 1, y)) mask |= CellTopology.WALL_DIR_E;
                    if (!topology.isWall(x - 1, y)) mask |= CellTopology.WALL_DIR_W;
                    topology.setWallDirMask(x, y, mask);
                }
                grid.recomputeCoverAt(x, y);
            }
        }
        FortifiedWorks.mark(grid, site.x0() + LEFT, site.y0() + TOP, site.x0() + RIGHT, site.y0() + BOTTOM);
        SceneBuilder builder = placement.builder();
        // Squadless, they hold their fire: the picture is the Brute and the wall, not who wins.
        for (int i = 0; i < 3; i++) {
            builder.unit("m" + i, Faction.MARINE, UnitType.MARINE, LEFT + 3, TOP + 4 + i * 2,
                    spec -> spec.health(5000f));
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        float[] at = placement.openNear(LEFT - 8f, (TOP + BOTTOM) / 2f);
        placement.subject("brute", sim.spawn(SwarmStrains.spec("Brute", UnitType.SWARM_BRUTE, (int) at[0],
                (int) at[1]).health(5000f)));
        return sim;
    }
}
