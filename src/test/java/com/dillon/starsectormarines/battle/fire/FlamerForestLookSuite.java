package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A flamer squad burning brush in a wood (`fire-nouns.md`, Spread), in the look preset: four marines with flamers at
 * the west of the window, a militia picket standing in brush at the flamer's range east of them, and trees behind it.
 * Nothing is scripted: the squad throws when its own rules let it ({@link FireReach}), and the fire goes as far as the
 * fuel carries it. Play zoom on the squad and the fire together, and close on the ground between them.
 *
 * <p>{@code fire-flamer-forest} stands the squad on open grass at the wood's edge. {@code fire-flamer-woods} stands it
 * among the trees. A variant may lengthen the flamer, for a question about its reach.
 */
public class FlamerForestLookSuite implements SnapshotSuite {

    private static final int WINDOW_W = 48, WINDOW_H = 30;
    private static final int SECONDS = 45;
    private static final String FLAMER = "weapon.flamer";

    /**
     * One way of playing the shot: where the squad stands (window cells from the west edge, and whether among trees)
     * and how far the flamer reaches.
     */
    record Variant(String name, int squadX, boolean amongTrees, float flamerRange) { }

    @Override public String id() { return "fire-flamer-forest"; }

    @Override public String label() { return "Look: a flamer squad burns brush at a forest edge without the wood running away"; }

    List<Variant> variants() {
        return List.of(new Variant("flamer-forest", 8, false, 8f));
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (Variant variant : variants()) out.addAll(record(context, variant));
        return out;
    }

    private static List<SnapshotArtifact> record(SnapshotContext context, Variant variant) throws Exception {
        WeaponRegistry weapons = WeaponRegistry.installed();
        float stockRange = WeaponRegistry.require(FLAMER).range;
        try {
            if (variant.flamerRange() != stockRange) {
                WeaponRegistry.install(withFlamerRange(context.projectRoot(), variant.flamerRange()));
            }
            return LookReview.record(context.modRoot(), shot(variant));
        } finally {
            WeaponRegistry.install(weapons);
        }
    }

    private static LookReview.Shot shot(Variant variant) {
        LookGround ground = LookGround.verdant();
        int range = (int) Math.floor(variant.flamerRange());
        return new LookReview.Shot(variant.name(), ground, WINDOW_W, WINDOW_H, placement -> {
            LookGround.Site site = placement.site();
            int cy = WINDOW_H / 2;
            int[] squad = walkableNear(placement.map(), site, variant.squadX(), cy);
            int[] picket = walkableNear(placement.map(), site, squad[0] + range, squad[1]);
            plantWood(placement.map(), site, variant.amongTrees(), squad[0]);
            SceneBuilder builder = placement.builder();
            builder.squad("flamers").faction(Faction.MARINE).size(4).at(squad[0], squad[1])
                    .inFile(squad[0], squad[1] - 3, 0, 2).primary("weapon.smg").special("special.flamer", 4)
                    .health(400f).stationary().done();
            // Each picket man stands in a flamer's own row, at the flamer's range exactly: one row off is out of reach.
            // The picket still fights back with what militia carry; the flamers are made hard enough to outlast it,
            // so the picture is the fire's and not a firefight's. The picket is hard enough to outlast the squad's
            // rifles, so the flamers have a target in the brush to throw at.
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(4)
                    .at(picket[0], picket[1]).inFile(picket[0], squad[1] - 3, 0, 2).health(20000f).unarmed()
                    .stationary().done();
            SceneWorld world = builder.build();
            long[] flamers = world.members("flamers");
            int[] spent = {0, 0};
            placement.beforeTick(sim -> countSpent(sim, spent));
            placement.beforeTick(sim -> log(variant, sim, flamers, spent));
            return world.sim();
        }, placement -> {
            LookGround.Site site = placement.site();
            float midY = site.y(WINDOW_H / 2f);
            return List.of(LookReview.View.play(site.x(variant.squadX() + 12f), midY),
                    LookReview.View.close("close", site.x(variant.squadX() + range / 2f), midY, 22f));
        }, 30 * SECONDS, 6, 3);
    }

    /**
     * Counts the flamer charges thrown last tick ({@code spent[0]}) and those whose landing cell had nothing left to
     * catch, neither standing fuel nor fire ({@code spent[1]}): a charge spent on burnt or bare ground.
     */
    private static void countSpent(BattleSimulation sim, int[] spent) {
        FireField fire = sim.fireField();
        for (ShotEvent shot : sim.getShotsThisFrame()) {
            if (shot.specialEquipmentDef == null || !FLAMER.equals(shot.specialEquipmentDef.weaponId())) continue;
            spent[0]++;
            int x = (int) Math.floor(shot.toX), y = (int) Math.floor(shot.toY);
            if (!fire.canBurn(x, y) && !fire.isBurning(x, y)) spent[1]++;
        }
    }

    /** Every five seconds: charges left and thrown on spent ground, the fire's size, and the squad's health and distance from it. */
    private static void log(Variant variant, BattleSimulation sim, long[] flamers, int[] spent) {
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
        System.out.printf("    [%s] t=%ds charges left %d (%d of %d thrown landed on spent ground), burning %d, burnt %d, flamers alive %d (health %.0f),"
                        + " nearest fire %s%n", variant.name(), sim.getSimTickIndex() / 30, ammo, spent[1], spent[0], fire.burningCount(),
                fire.burntCount(), alive, health, nearest == Integer.MAX_VALUE ? "none" : nearest + " cells");
    }

    /** The bundled weapon catalogs with the flamer's reach set to {@code range}. */
    private static WeaponRegistry withFlamerRange(Path projectRoot, float range) throws Exception {
        WeaponRegistry registry = new WeaponRegistry();
        for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
            JSONObject root = new JSONObject(Files.readString(projectRoot.resolve("mod").resolve(path)));
            JSONArray list = root.optJSONArray("weapons");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject weapon = list.getJSONObject(i);
                    if (FLAMER.equals(weapon.optString("id"))) weapon.getJSONObject("sim").put("range", range);
                }
            }
            registry.ingest(root);
        }
        return registry;
    }

    /**
     * A wood on the window: brush from four cells east of the squad to the window's east edge, and trees from a dozen
     * cells east of the squad; or, among the trees, across the whole window but the squad's own file.
     */
    private static void plantWood(MapResult map, LookGround.Site site, boolean amongTrees, int squadX) {
        TileRegistry registry = TileRegistry.installed();
        int shrub = registry != null && registry.has("nature.shrub-1") ? registry.indexOf("nature.shrub-1") : -1;
        for (int ly = 0; ly < WINDOW_H; ly++) {
            for (int lx = squadX + 4; lx < WINDOW_W; lx++) {
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
        int from = amongTrees ? 0 : squadX + 12;
        for (int ly = 1; ly + 6 < WINDOW_H; ly += 6) {
            for (int lx = from + (ly / 6 % 2) * 3; lx + 6 < WINDOW_W; lx += 7) {
                // Among the trees, the squad's own file and the cells beside it stay open to stand on.
                if (amongTrees && lx + 6 > squadX - 1 && lx < squadX + 2) continue;
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

    /** The owner's fire-flamer-woods pick: the squad among the trees, once per answer. */
    public static final class Woods extends FlamerForestLookSuite {
        @Override public String id() { return "fire-flamer-woods"; }

        @Override public String label() { return "Look: a flamer squad among trees burning brush at its full reach"; }

        @Override List<Variant> variants() {
            return List.of(new Variant("woods", 10, true, 8f));
        }
    }
}
