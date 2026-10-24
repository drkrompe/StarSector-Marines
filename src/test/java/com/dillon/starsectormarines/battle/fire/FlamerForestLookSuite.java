package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * A flamer squad burning brush at a forest's edge (`fire-nouns.md`, Spread), in the look preset: four marines with
 * flamers on open grass at the west of the window, a militia picket standing in the brush seven cells east, and the
 * wood behind it. Nothing is scripted: the squad throws when its own rules let it ({@link FireReach}), and the fire
 * goes as far as the fuel carries it. Play zoom on the squad and the fire together, and close on the picket's ground.
 */
public final class FlamerForestLookSuite implements SnapshotSuite {

    private static final int WINDOW_W = 48, WINDOW_H = 30;
    /** Where the squad stands, in window cells, and how far east of it the picket stands: the flamer's range. */
    private static final int SQUAD_X = 8, PICKET_RANGE = 7;
    private static final int SECONDS = 45;

    @Override public String id() { return "fire-flamer-forest"; }

    @Override public String label() { return "Look: a flamer squad burns brush at a forest edge without the wood running away"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        LookGround ground = LookGround.verdant();
        LookReview.Shot shot = new LookReview.Shot("flamer-forest", ground, WINDOW_W, WINDOW_H, placement -> {
            LookGround.Site site = placement.site();
            plantWood(placement.map(), site);
            SceneBuilder builder = placement.builder();
            int cy = WINDOW_H / 2;
            int[] squad = walkableNear(placement.map(), site, SQUAD_X, cy);
            int[] picket = walkableNear(placement.map(), site, squad[0] + PICKET_RANGE, squad[1]);
            builder.squad("flamers").faction(Faction.MARINE).size(4).at(squad[0], squad[1])
                    .inFile(squad[0], squad[1] - 3, 0, 2).primary("weapon.smg").special("special.flamer", 4)
                    .health(400f).stationary().done();
            // The picket still fights back with what militia carry; the flamers are made hard enough to outlast it,
            // so the picture is the fire's and not a firefight's. Hard enough to outlast the squad's rifles, so the flamers have a target in the brush to throw at.
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                    .at(picket[0], picket[1]).inFile(picket[0], picket[1] - 2, 0, 2).health(20000f).unarmed()
                    .stationary().done();
            SceneWorld world = builder.build();
            long[] flamers = world.members("flamers");
            placement.beforeTick(sim -> {
                if (sim.getSimTickIndex() % 150 != 0) return;
                int ammo = 0, alive = 0;
                float health = 0f;
                int nearest = Integer.MAX_VALUE;
                FireField fire = sim.fireField();
                for (long id : flamers) {
                    if (!sim.world().isAlive(id)) continue;
                    alive++;
                    health += sim.world().hp(id);
                    if (sim.world().hasSecondaryWeapon(id)) ammo += sim.world().secondaryAmmo(id);
                    for (int i = 0; i < fire.burningCount(); i++) {
                        int idx = fire.burningCell(i);
                        nearest = Math.min(nearest, Math.max(Math.abs(idx % fire.width() - sim.world().cellX(id)),
                                Math.abs(idx / fire.width() - sim.world().cellY(id))));
                    }
                }
                System.out.printf("    [flamer-forest] t=%ds charges left %d, burning %d, burnt %d, flamers alive %d"
                                + " (health %.0f), nearest fire %d cells%n", sim.getSimTickIndex() / 30, ammo,
                        fire.burningCount(), fire.burntCount(), alive, health, nearest);
            });
            return world.sim();
        }, placement -> {
            LookGround.Site site = placement.site();
            float midY = site.y(WINDOW_H / 2f);
            return List.of(LookReview.View.play(site.x(SQUAD_X + 14f), midY),
                    LookReview.View.close("close", site.x(SQUAD_X + PICKET_RANGE - 1f), midY, 22f));
        }, 30 * SECONDS, 2, 3);
        return LookReview.record(context.modRoot(), shot);
    }

    /**
     * A wood east of the squad on the open window: brush from four cells past the squad to the window's east edge,
     * and trees from a dozen cells past it, so the fire has brush to run in and crowns to climb into.
     */
    private static void plantWood(MapResult map, LookGround.Site site) {
        TileRegistry registry = TileRegistry.installed();
        int shrub = registry != null && registry.has("nature.shrub-1") ? registry.indexOf("nature.shrub-1") : -1;
        for (int ly = 0; ly < WINDOW_H; ly++) {
            for (int lx = SQUAD_X + 4; lx < WINDOW_W; lx++) {
                int x = site.x(lx), y = site.y(ly);
                if (!map.grid.isWalkable(x, y) || map.topology.getBuildingId(x, y) != 0) continue;
                map.topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
                int hash = (x * 73856093) ^ (y * 19349663);
                if (shrub >= 0 && Math.floorMod(hash, 10) < 6) map.topology.setNatureOverlayIndex(x, y, shrub);
            }
        }
        List<ForestField.Tree> trees = new ArrayList<>();
        TreeCanopy[] kinds = {TreeCanopy.OAK, TreeCanopy.PINE_LARGE, TreeCanopy.BIRCH, TreeCanopy.PINE, TreeCanopy.OAK_SMALL};
        int k = 0;
        for (int ly = 1; ly + 6 < WINDOW_H; ly += 6) {
            for (int lx = SQUAD_X + 12 + (ly / 6 % 2) * 3; lx + 6 < WINDOW_W; lx += 7) {
                trees.add(new ForestField.Tree(site.x(lx), site.y(ly), kinds[k % kinds.length], k % 4));
                k++;
            }
        }
        for (ForestField.Tree tree : trees) {
            try {
                ForestBrush.stampTrees(List.of(tree), map.grid, map.topology, map.doodads, null, false);
            } catch (IllegalArgumentException standing) {
                // The generator has something there already (a rock, a prop); the wood goes round it.
            }
        }
    }

    /** The walkable cell nearest a window-local point, in window cells. */
    private static int[] walkableNear(MapResult map, LookGround.Site site, int lx, int ly) {
        for (int r = 0; r < 12; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = site.x(lx + dx), y = site.y(ly + dy);
                    if (map.grid.inBounds(x, y) && map.grid.isWalkable(x, y)) return new int[]{lx + dx, ly + dy};
                }
            }
        }
        return new int[]{lx, ly};
    }
}
