package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.fort.GateTowerGarrison;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.DefensePostLayoutRegistry;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.turret.FortWorks;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Opt-in look study for themed fortification lots (`fort-works.md`): a battery fort the production generator sites on
 * real generated ground, built in each {@link EmplacementStyle} through the mission hook
 * ({@link EmplacementStylePolicy#restyle}), so its wall, parapets, barricades and guns are drawn from one family. Owner
 * look frames: real ground, effects and shadows on, no annotations, 1920x1080 at play zoom and close.
 *
 * <p>{@code -Dsnapshot.fortWorks.styles=} names the styles (keys, comma-separated; default every style) and
 * {@code -Dsnapshot.fortWorks.biome=} the ground ({@code arid} by default). Frames go to {@code look/<style>-<view>.png}.
 * {@code -Dsnapshot.fortWorks.structures=true} adds one close frame per structure a fort and a defence lot stand
 * (each fort building, the gate, a bastion nest, the centrepiece, a light and a medium post) as
 * {@code look/<style>-structure-<piece>.png}. {@code -Dsnapshot.fortWorks.noRoofs=true} draws every still without the
 * roof layer (building roofs, a fort's building art and its gate caps), to show what the stamp built under them.
 */
public final class FortWorksLookSnapshotSuite implements SnapshotSuite {

    private static final int MAP_W = 400;
    private static final int MAP_H = 240;
    private static final long SEED = 11L;
    /** Ticks played before the still, so smoke and the guns' first turn have settled. */
    private static final int SETTLE_TICKS = 30;
    /** The free-standing post's layout: a line of guns behind its parapet or barricade. */
    private static final String POST_VARIANT = "line-h";
    /** How long a gate tower's garrison has to walk in and open fire before it is framed. */
    private static final int GARRISON_TICKS = 300;

    @Override public String id() { return "fort-works-look"; }

    @Override public String label() { return "Fortification lots in each emplacement style (look study)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            String selected = System.getProperty("snapshot.fortWorks.styles", "");
            SurfacePalette surface = SurfacePalette.valueOf(
                    System.getProperty("snapshot.fortWorks.biome", "arid").toUpperCase());
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            for (EmplacementStyle style : EmplacementStyle.values()) {
                if (!selected.isBlank() && !List.of(selected.split(",")).contains(style.key)) continue;
                artifacts.addAll(shots(context, style, surface));
            }
            return artifacts;
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private static List<SnapshotArtifact> shots(SnapshotContext context, EmplacementStyle style, SurfacePalette surface)
            throws Exception {
        TargetProfile profile = LookGround.of(surface, SEED).profile().withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 1));
        MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, SEED, null, profile);
        List<DefensePost> posts = new ArrayList<>(map.defensePosts);
        // A post standing alone on the open ground beside the town, built in the style as a generator builds it: a
        // militia style rings it with its barricade, a prepared one pours its parapet.
        float[] fortAt = fortCentre(map.defensePosts);
        int[] field = openField(map, 16, 10, fortAt == null ? MAP_W / 2f : fortAt[0], fortAt == null ? MAP_H / 2f : fortAt[1]);
        DefensePost alone = DefensePostStamper.stampPost(map.grid, map.topology, map.doodads,
                DefensePostLayoutRegistry.requireInstalled().require(DefensePostKind.LARGE, POST_VARIANT),
                field[0] + 8, field[1] + 5, style);
        posts.add(alone);
        boolean structures = Boolean.getBoolean("snapshot.fortWorks.structures");
        List<DefensePost> small = new ArrayList<>();
        if (structures) {
            for (DefensePostKind tier : List.of(DefensePostKind.LIGHT, DefensePostKind.MEDIUM)) {
                int[] at = openField(map, 10, 10, field[0] + 8, field[1] + 5);
                small.add(DefensePostStamper.stampPost(map.grid, map.topology, map.doodads,
                        DefensePostLayoutRegistry.requireInstalled().require(tier, "default"), at[0] + 5, at[1] + 5,
                        style));
            }
            posts.addAll(small);
        }
        boolean kit = Boolean.getBoolean("snapshot.fortWorks.kit");
        int[] yard = null;
        if (kit) {
            yard = openField(map, 14, 9, field[0] + 8, field[1] + 5);
            posts.add(kitYard(map, yard, style));
        }
        EmplacementStylePolicy.restyle(posts, style);
        BattleSimulation sim = BattleSetup.buildMap(map, Collections.emptyList(), posts, SEED).sim();
        // With no marines on the field the battle is won on its first tick and stops playing; a garrison shot plays on.
        if (Boolean.getBoolean("snapshot.fortWorks.garrison")) sim.setMissionCompletionEnabled(false);
        try {
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            for (int tick = 0; tick < SETTLE_TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
            }
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            float[] fort = fortCentre(posts);
            if (fort != null) {
                artifacts.add(still(context, sim, fx, style.key + "-fort-play", fort[0], fort[1],
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS));
                artifacts.add(still(context, sim, fx, style.key + "-fort-close", fort[2], fort[3], 26f));
            }
            float gunsX = 0, gunsY = 0;
            for (DefensePost.TurretSpec gun : alone.turrets) {
                gunsX += gun.cellX + 0.5f;
                gunsY += gun.cellY + 0.5f;
            }
            float postX = gunsX / alone.turrets.size(), postY = gunsY / alone.turrets.size();
            artifacts.add(still(context, sim, fx, style.key + "-post-close", postX, postY, 22f));
            float[] gate = gateCentre(sim);
            if (gate != null) artifacts.add(still(context, sim, fx, style.key + "-gate-close", gate[0], gate[1], 18f));
            if (structures) artifacts.addAll(structureStills(context, sim, fx, style, posts, small));
            if (Boolean.getBoolean("snapshot.fortWorks.garrison")) artifacts.addAll(garrisonStills(context, sim, fx, style));
            if (kit) artifacts.addAll(kitStills(context, sim, fx, style, yard));
            wear(sim, postX, postY, 9f);
            artifacts.add(still(context, sim, fx, style.key + "-post-worn", postX, postY, 22f));
            System.out.printf("    [fort-works-look/%s] %d posts, fort %s%n", style.key, posts.size(),
                    fort == null ? "none" : String.format("at %.0f,%.0f", fort[0], fort[1]));
            return artifacts;
        } finally {
            sim.close();
        }
    }

    private static SnapshotArtifact still(SnapshotContext context, BattleSimulation sim, ReviewImpactFx fx, String name,
                                          float x, float y, float cellsAcross) throws Exception {
        return still(context, sim, fx, name, x, y, cellsAcross, Boolean.getBoolean("snapshot.fortWorks.noRoofs"));
    }

    private static SnapshotArtifact still(SnapshotContext context, BattleSimulation sim, ReviewImpactFx fx, String name,
                                          float x, float y, float cellsAcross, boolean noRoofs) throws Exception {
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y,
                cellsAcross);
        if (noRoofs) renderer.withoutRoofs();
        return new SnapshotArtifact("look/" + name + ".png", renderer.render(sim, "", ReviewAnnotations.NONE, fx));
    }

    /**
     * A squad holding the main gate's first tower and firing out of its slits at a file of targets on the approach
     * ({@link GateTowerGarrison}), framed roofed and with the roofs lifted, and close on the slit down the approach.
     * Played last: the battle moves on from here.
     */
    private static List<SnapshotArtifact> garrisonStills(SnapshotContext context, BattleSimulation sim,
                                                         ReviewImpactFx fx, EmplacementStyle style) throws Exception {
        if (sim.forts().forts().isEmpty()) return List.of();
        GateTowerGarrison.Manned manned = GateTowerGarrison.man(sim, sim.forts().forts().get(0).frame(), 3, 3,
                1_000_000f, "weapon.smg", false);
        for (int tick = 0; tick < GARRISON_TICKS; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            fx.feed(sim);
        }
        int[] floor = manned.floor().get(manned.floor().size() / 2);
        float x = (floor[0] + manned.aim()[0]) * 0.5f + 0.5f, y = (floor[1] + manned.aim()[1]) * 0.5f + 0.5f;
        // The slit straight down the approach: the one beside the middle stand.
        int[] middle = manned.stands().get(manned.stands().size() / 2);
        int[] slit = manned.slits().get(0);
        for (int[] c : manned.slits()) {
            if (Math.abs(c[0] - middle[0]) + Math.abs(c[1] - middle[1])
                    < Math.abs(slit[0] - middle[0]) + Math.abs(slit[1] - middle[1])) slit = c;
        }
        return List.of(still(context, sim, fx, style.key + "-gate-tower-garrison", x, y, 20f, false),
                still(context, sim, fx, style.key + "-gate-tower-garrison-open", x, y, 20f, true),
                still(context, sim, fx, style.key + "-gate-tower-slit", slit[0] + 0.5f, slit[1] + 0.5f, 7f, false),
                still(context, sim, fx, style.key + "-gate-tower-slit-open", slit[0] + 0.5f, slit[1] + 0.5f, 7f, true));
    }

    /**
     * One close frame on each structure a fort and a defence lot stand: the fort's buildings by aspect, its first gate,
     * the bastion nest farthest from its middle, its centrepiece, and the light and medium posts in {@code small}.
     */
    private static List<SnapshotArtifact> structureStills(SnapshotContext context, BattleSimulation sim,
                                                          ReviewImpactFx fx, EmplacementStyle style,
                                                          List<DefensePost> posts, List<DefensePost> small)
            throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        String prefix = style.key + "-structure-";
        for (Fort fort : sim.forts().forts()) {
            FortFrame frame = fort.frame();
            if (frame == null) continue;
            for (Fort.Place place : fort.places()) {
                int[] b = place.floorBounds(frame);
                out.add(still(context, sim, fx, prefix + place.aspect().name().toLowerCase().replace('_', '-'),
                        (b[0] + b[2] + 1) * 0.5f, (b[1] + b[3] + 1) * 0.5f, 18f));
            }
            int[] centre = frame.plan().centrepiece();
            float[] at = frame.point(centre[0] + 0.5f, centre[1] + 0.5f);
            out.add(still(context, sim, fx, prefix + "centrepiece", at[0], at[1], 16f));
            break;
        }
        float[] gate = gateCentre(sim);
        if (gate != null) out.add(still(context, sim, fx, prefix + "gate", gate[0], gate[1], 14f));
        float[] fort = fortCentre(posts);
        if (fort != null) {
            out.add(still(context, sim, fx, prefix + "bastion-nest", 2 * fort[2] - fort[0], 2 * fort[3] - fort[1], 12f));
        }
        for (DefensePost post : small) {
            out.add(still(context, sim, fx, prefix + post.tier.name().toLowerCase() + "-post",
                    post.anchorX + 0.5f, post.anchorY + 0.5f, 10f));
        }
        return out;
    }

    /**
     * A yard of the kit's loose pieces on open country, all one lot in {@code style}: a light post (its vent ring), a
     * fence round a pen, a trench with a branch and a row of hedgehogs. Returns the light post.
     */
    private static DefensePost kitYard(MapResult map, int[] at, EmplacementStyle style) {
        int x0 = at[0], y0 = at[1];
        for (int x = x0; x < x0 + 14; x++) for (int y = y0; y < y0 + 9; y++) FortWorks.mark(map.topology, x, y, style);
        DefensePost light = DefensePostStamper.stampPost(map.grid, map.topology, map.doodads,
                DefensePostLayoutRegistry.requireInstalled().require(DefensePostKind.LIGHT, "default"), x0 + 3, y0 + 4,
                style);
        TileRegistry registry = TileRegistry.installed();
        int left = x0 + 7, right = x0 + 12, bottom = y0, top = y0 + 2;
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                if (x != left && x != right && y != bottom && y != top) continue;
                String piece = x == left && y == top ? "corner-nw" : x == right && y == top ? "corner-ne"
                        : x == left && y == bottom ? "corner-sw" : x == right && y == bottom ? "corner-se"
                        : y == top || y == bottom ? "straight-h" : "straight-v";
                map.doodads.add(new Doodad(x, y, registry.doodad("doodad.industrial-fence-" + piece)));
            }
        }
        Set<Long> trench = new HashSet<>();
        for (int x = left; x <= right; x++) trench.add(key(x, y0 + 5));
        trench.add(key(x0 + 9, y0 + 6));
        trench.add(key(x0 + 9, y0 + 7));
        for (long cell : trench) {
            int x = (int) (cell >> 32), y = (int) cell;
            int mask = (trench.contains(key(x, y + 1)) ? 1 : 0) | (trench.contains(key(x + 1, y)) ? 2 : 0)
                    | (trench.contains(key(x, y - 1)) ? 4 : 0) | (trench.contains(key(x - 1, y)) ? 8 : 0);
            map.doodads.add(new Doodad(x, y, registry.doodad("doodad.fort-trench-" + mask)));
        }
        for (int x = x0 + 1; x <= x0 + 5; x += 2) map.doodads.add(new Doodad(x, y0 + 8, registry.doodad("doodad.fort-hedgehog")));
        return light;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * The kit's pieces in place: the yard ({@link #kitYard}), the fort's wall breached beside its gate, and the first
     * fort building with a hole in its wall, so its openings and both walls' rubble show.
     */
    private static List<SnapshotArtifact> kitStills(SnapshotContext context, BattleSimulation sim, ReviewImpactFx fx,
                                                    EmplacementStyle style, int[] yard) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        out.add(still(context, sim, fx, style.key + "-kit-yard", yard[0] + 7f, yard[1] + 4.5f, 16f));
        float[] gate = gateCentre(sim);
        CellTopology topology = sim.getTopology();
        if (gate != null) {
            int broken = 0;
            for (int r = 4; r <= 7 && broken < 6; r++) {
                for (int dx = -r; dx <= r && broken < 6; dx++) {
                    for (int dy = -r; dy <= r && broken < 6; dy++) {
                        int x = (int) gate[0] + dx, y = (int) gate[1] + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r || !topology.isWall(x, y)
                                || FortWorks.styleAt(topology, x, y) == null) continue;
                        sim.mapEditor().damageWall(x, y, Integer.MAX_VALUE / 2);
                        broken++;
                    }
                }
            }
            out.add(still(context, sim, fx, style.key + "-kit-breach", gate[0], gate[1], 16f, true));
        }
        for (Fort fort : sim.forts().forts()) {
            FortFrame frame = fort.frame();
            if (frame == null || !fort.places().iterator().hasNext()) continue;
            int[] b = fort.places().iterator().next().floorBounds(frame);
            int midX = (b[0] + b[2]) / 2;
            for (int x = midX - 1; x <= midX; x++) {
                if (topology.isWall(x, b[1] - 1)) sim.mapEditor().damageWall(x, b[1] - 1, Integer.MAX_VALUE / 2);
            }
            out.add(still(context, sim, fx, style.key + "-kit-building", (b[0] + b[2] + 1) * 0.5f,
                    (b[1] + b[3] + 1) * 0.5f, 16f, true));
            break;
        }
        return out;
    }

    /** The middle of the fort's first gate, between its towers; null when no fort stood. */
    private static float[] gateCentre(BattleSimulation sim) {
        for (Fort fort : sim.forts().forts()) {
            FortFrame frame = fort.frame();
            if (frame == null) continue;
            for (FortPlan.Piece piece : frame.plan().gatehouse()) {
                if (piece.kind() != FortPlan.PieceKind.GATE) continue;
                int[] b = frame.bounds(piece.x(), piece.y(), piece.width(), piece.height());
                return new float[]{(b[0] + b[2] + 1) * 0.5f, (b[1] + b[3] + 1) * 0.5f};
            }
        }
        return null;
    }

    /**
     * Battle-worn works within {@code radius} of a point, for the damage-state look: every parapet and barricade piece
     * there is hit, alternately to its damaged state and through it to its remains.
     */
    private static void wear(BattleSimulation sim, float x, float y, float radius) {
        int width = sim.getGrid().getWidth();
        int n = 0;
        for (Doodad doodad : new ArrayList<>(sim.getDoodads())) {
            String id = doodad.definitionId();
            if (id == null || !(id.startsWith("doodad.fort-emplacement-") || id.contains("sandbag-"))) continue;
            float dx = doodad.cellX + 0.5f - x, dy = doodad.cellY + 0.5f - y;
            if (dx * dx + dy * dy > radius * radius) continue;
            BitSet cell = new BitSet();
            cell.set(doodad.cellY * width + doodad.cellX);
            sim.mapEditor().damageSelectedCells(cell, n++ % 3 == 2 ? 2 * CoverPropDamage.STARTING_HP
                    : CoverPropDamage.STARTING_HP / 2);
        }
    }

    /**
     * The middle of the fort's posts and, for the close view, the wall nest farthest from it: the corner of the
     * works where wall, nest and belt meet. Null on a map whose fort did not stand.
     */
    private static float[] fortCentre(List<DefensePost> posts) {
        float sx = 0, sy = 0;
        int n = 0;
        for (DefensePost post : posts) {
            if (post.fort == null) continue;
            sx += post.anchorX;
            sy += post.anchorY;
            n++;
        }
        if (n == 0) return null;
        float cx = sx / n, cy = sy / n;
        DefensePost far = null;
        float best = -1;
        for (DefensePost post : posts) {
            if (post.fort == null) continue;
            float d = (post.anchorX - cx) * (post.anchorX - cx) + (post.anchorY - cy) * (post.anchorY - cy);
            if (d > best) {
                best = d;
                far = post;
            }
        }
        return new float[]{cx + 0.5f, cy + 0.5f, (far.anchorX + cx) / 2f + 0.5f, (far.anchorY + cy) / 2f + 0.5f};
    }


    /**
     * The corner of the {@code w x h} window of untouched country (walkable, unpaved, no prop, outside every building
     * and lot) nearest {@code (nearX, nearY)}: where a post that stands alone is built.
     */
    private static int[] openField(MapResult map, int w, int h, float nearX, float nearY) {
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        boolean[][] taken = new boolean[MAP_W][MAP_H];
        for (Doodad doodad : map.doodads) {
            if (map.grid.inBounds(doodad.cellX, doodad.cellY)) taken[doodad.cellX][doodad.cellY] = true;
        }
        for (int y0 = 8; y0 + h < MAP_H - 8; y0 += 2) {
            for (int x0 = 8; x0 + w < MAP_W - 8; x0 += 2) {
                float distance = (x0 + w / 2f - nearX) * (x0 + w / 2f - nearX) + (y0 + h / 2f - nearY) * (y0 + h / 2f - nearY);
                if (distance >= bestDistance || !country(map, taken, x0, y0, w, h)) continue;
                bestDistance = distance;
                best = new int[]{x0, y0};
            }
        }
        if (best == null) throw new IllegalStateException("no open country for the free-standing post");
        return best;
    }

    private static boolean country(MapResult map, boolean[][] taken, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (!map.grid.isWalkable(x, y) || taken[x][y] || map.topology.getSurface(x, y) != 0
                        || map.topology.getWorksStyle(x, y) != 0) return false;
                CellTopology.GroundKind kind = map.topology.getGroundKind(x, y);
                if (kind != CellTopology.GroundKind.GRASS && kind != CellTopology.GroundKind.DIRT
                        && kind != CellTopology.GroundKind.SAND) return false;
            }
        }
        return true;
    }

}
