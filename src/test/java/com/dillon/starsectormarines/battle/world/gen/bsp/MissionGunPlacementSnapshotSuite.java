package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.DustLedgerCrossing;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The missions' direct-fire guns outside the Raid, through the look preset, after they were moved to stand where they
 * have open ground to shoot over (a wall blocks direct fire whole): the Conquest fortress's west-to-east wall towers
 * and the declared defence nests on that map, The Dust Ledger's three gun nests outside the compound wall, and a
 * Cordon picket line's nests. Each place whole, then the same frame with every gun's facing drawn as its lane
 * ({@link DefenceDoctrine#LANE} cells at its bearing, the arc's two edges either side), then a marked close-up of
 * every gun. Opt-in. 1920x1080.
 */
public final class MissionGunPlacementSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float CLOSE_CELLS = 40f;
    private static final long CONQUEST_SEED = 5L;
    private static final long DUST_SEED = 6150L;

    @Override public String id() { return "mission-gun-placement"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Mission guns outside the Raid: towers and nests with open ground ahead"; }

    /** A gun to draw: where it stands, the bearing it is laid on (degrees, the post's rest facing) and what it is. */
    private record Gun(float x, float y, float facing, String what) {}

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        conquest(look, out);
        dustLedger(look, out);
        cordon(look, out);
        return out;
    }

    private static void conquest(BattleReviewFrameRenderer look, List<SnapshotArtifact> out) throws Exception {
        TargetProfile profile = TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(0, false, 6));
        MapResult map = new BspCityGenerator().generate(240, 160, CONQUEST_SEED, TraversalAxis.WEST_TO_EAST, profile);
        int[] ahead = FortressWallStamper.towerFacing(TraversalAxis.WEST_TO_EAST);
        float towerFacing = facing(ahead[0], ahead[1]);
        List<Gun> towers = new ArrayList<>();
        for (TacticalNode node : map.tacticalMap.all()) {
            if (node.kind == TacticalNode.Kind.HEAVY_TOWER) towers.add(new Gun(node.anchorX, node.anchorY, towerFacing, "Wall tower"));
        }
        List<Gun> nests = new ArrayList<>();
        for (DefensePost post : map.defensePosts) {
            if (post.restFacing == null || post.turrets.size() != 1) continue;
            String gun = post.turrets.get(0).structureId;
            if (gun.equals(DefenceNestStage.NEST_GUNS[0]) || gun.equals(DefenceNestStage.NEST_GUNS[1])) {
                nests.add(new Gun(post.anchorX, post.anchorY, post.restFacing, "Defence nest"));
            }
        }
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, CONQUEST_SEED).sim()) {
            if (!towers.isEmpty()) {
                float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                for (Gun t : towers) {
                    x0 = Math.min(x0, t.x()); x1 = Math.max(x1, t.x());
                    y0 = Math.min(y0, t.y()); y1 = Math.max(y1, t.y());
                }
                float cx = (x0 + x1) / 2f - 4f, cy = (y0 + y1) / 2f;
                float across = Math.max(60f, (y1 - y0 + 16f) * 16f / 9f);
                whole(out, look, sim, "01-conquest-fortress-front", cx, cy, across, towers);
            }
            int n = 2;
            for (Gun t : towers) close(out, look, sim, String.format("%02d-conquest-tower-%d-%d", n++, (int) t.x(), (int) t.y()), t);
            if (!nests.isEmpty()) {
                List<Gun> all = new ArrayList<>(towers);
                all.addAll(nests);
                whole(out, look, sim, String.format("%02d-conquest-nests", n++), 120f, 80f, 240f, all);
            }
            for (Gun g : nests) close(out, look, sim, String.format("%02d-conquest-nest-%d-%d", n++, (int) g.x(), (int) g.y()), g);
        }
    }

    private static void dustLedger(BattleReviewFrameRenderer look, List<SnapshotArtifact> out) throws Exception {
        TargetProfile asharu = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD);
        DustLedgerCrossing.Layout layout = DustLedgerCrossing.generate(DUST_SEED, asharu);
        List<Gun> nests = new ArrayList<>();
        for (DustLedgerCrossing.Nest nest : layout.nests()) {
            nests.add(new Gun(nest.cell().x(), nest.cell().y(), facing(nest.facing().dx, nest.facing().dy),
                    nest.post().turrets.get(0).structureId.contains("hephaestus") ? "Nest: light cannon" : "Nest: heavy MG"));
        }
        try (BattleSimulation sim = BattleSetup.buildMap(layout.map(), List.of(), layout.map().defensePosts, DUST_SEED).sim()) {
            DustLedgerCrossing.Area compound = layout.compound();
            whole(out, look, sim, "20-dust-ledger-compound", compound.centre().x() - 4f, compound.centre().y(), 120f, nests);
            int n = 21;
            for (Gun g : nests) close(out, look, sim, String.format("%02d-dust-ledger-nest-%d-%d", n++, (int) g.x(), (int) g.y()), g);
        }
    }

    private static void cordon(BattleReviewFrameRenderer look, List<SnapshotArtifact> out) throws Exception {
        SurfacePalette biome = SurfacePalette.ARID;
        CordonSetup.CordonMap cordon = CordonSetup.map(CordonSetup.variantOf(StoryMap.CORDON_LINE), biome,
                StoryMapPools.pool(StoryMap.CORDON_LINE).entries(biome).get(0));
        MapResult map = cordon.map();
        List<Gun> nests = new ArrayList<>();
        for (Laid laid : map.layout.pieces()) {
            if (laid.kind() != Laid.Kind.LINE) continue;
            for (int[] c : laid.nests()) {
                for (DefensePost post : map.defensePosts) {
                    if (post.anchorX == c[0] && post.anchorY == c[1] && post.restFacing != null) {
                        nests.add(new Gun(c[0], c[1], post.restFacing, "Line nest (" + laid.name() + ")"));
                    }
                }
            }
        }
        if (nests.isEmpty()) return;
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, cordon.battleSeed()).sim()) {
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, ySum = 0f;
            for (Gun g : nests) { x0 = Math.min(x0, g.x()); x1 = Math.max(x1, g.x()); ySum += g.y(); }
            whole(out, look, sim, "30-cordon-line-nests", (x0 + x1) / 2f, ySum / nests.size(), x1 - x0 + 40f, nests);
            int n = 31;
            for (Gun g : nests) {
                if (n > 39) break;
                close(out, look, sim, String.format("%02d-cordon-nest-%d-%d", n++, (int) g.x(), (int) g.y()), g);
            }
        }
    }

    /** The place as it looks, then the same frame with every gun's lane drawn. */
    private static void whole(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              String name, float x, float y, float across, List<Gun> guns) throws Exception {
        look.refocus(x + .5f, y + .5f, across);
        out.add(new SnapshotArtifact(name + ".png", look.render(sim, "", ReviewAnnotations.NONE)));
        out.add(new SnapshotArtifact(name + "-facing.png", look.renderMarked(sim, lanes(guns, false))));
    }

    /** One gun close, its lane and arc drawn. */
    private static void close(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              String name, Gun gun) throws Exception {
        double a = Math.toRadians(gun.facing() + 90f);
        look.refocus(gun.x() + .5f + (float) Math.cos(a) * 5f, gun.y() + .5f + (float) Math.sin(a) * 5f, CLOSE_CELLS);
        out.add(new SnapshotArtifact(name + ".png", look.renderMarked(sim, lanes(List.of(gun), true))));
    }

    /** Each gun's lane: an arrow along its bearing, and with {@code arc} the arc's two edges. */
    private static ReviewAnnotations lanes(List<Gun> guns, boolean arc) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (Gun g : guns) {
            float x = g.x() + .5f, y = g.y() + .5f;
            double a = Math.toRadians(g.facing() + 90f);
            marks.arrow(x, y, x + (float) Math.cos(a) * DefenceDoctrine.LANE, y + (float) Math.sin(a) * DefenceDoctrine.LANE,
                    g.what(), ReviewStyle.FRONT);
            if (!arc) continue;
            for (int turn = -1; turn <= 1; turn += 2) {
                double e = a + Math.toRadians(DefenceDoctrine.ARC_DEGREES * turn);
                marks.polyline(List.of(new ReviewAnnotation.Point(x, y), new ReviewAnnotation.Point(
                        x + (float) Math.cos(e) * DefenceDoctrine.LANE, y + (float) Math.sin(e) * DefenceDoctrine.LANE)),
                        "arc", ReviewStyle.NOTE);
            }
        }
        return marks.build();
    }

    private static float facing(int dx, int dy) {
        return LayeredAppearance.facingDegrees(dx, dy);
    }
}
