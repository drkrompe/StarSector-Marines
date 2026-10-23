package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in look study for the ground-turret art direction (phase 1): candidate point emplacements (1x1 collar with an
 * autogun or light-cannon head) and hardpoints (3x3 raised plinth carrying a heavy cannon, and one carrying the
 * vanilla Locust) stood on real generated ground through {@link LookReview}, with a marine squad beside them for scale.
 *
 * <p>The default direction, {@code shipped}, stands the catalog's own structures ({@code structure.hardpoint-cannon},
 * {@code structure.hardpoint-locust}, {@code structure.point-autogun}, {@code -cannon}, {@code -grenade},
 * {@code -pd}). The phase-1 candidate directions ({@code brutalist}, {@code colony}, {@code earthwork}) are not shipped:
 * {@code art-source/ground-turrets/export.py} cuts them into {@code build/ground-turret-art/<direction>/}, and this suite
 * registers throwaway mounts and structures that point there (a candidate has no grenade or point-defence head, so
 * those spots carry its autogun and cannon). Candidates whose art has not been exported are skipped.
 * {@code -Dsnapshot.groundTurrets.directions=} and {@code -Dsnapshot.groundTurrets.biomes=} narrow the run.
 */
public final class GroundTurretLookSnapshotSuite implements SnapshotSuite {

    private static final String SHIPPED = "shipped";
    private static final String ART = "../build/ground-turret-art/";

    @Override public String id() { return "ground-turret-look"; }

    @Override public String label() { return "Ground turret art directions (look study)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<String> directions = new ArrayList<>();
        for (String d : System.getProperty("snapshot.groundTurrets.directions", SHIPPED).split(",")) {
            if (d.trim().equals(SHIPPED)) directions.add(SHIPPED);
            else if (Files.isRegularFile(context.modRoot().resolve(ART + d + "/plinth.png"))) directions.add(d.trim());
            else System.out.println("    [ground-turret-look] no exported art for " + d + ", skipped");
        }
        registerCandidates(directions);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String biome : System.getProperty("snapshot.groundTurrets.biomes", "verdant,arid,frozen").split(",")) {
            SurfacePalette surface = SurfacePalette.valueOf(biome.trim().toUpperCase());
            for (String direction : directions) {
                artifacts.addAll(LookReview.record(context.modRoot(), shot(direction, biome.trim(), surface)));
            }
        }
        return artifacts;
    }

    /** Mounts and structures for each direction, pointing at its exported art. Idempotent within a JVM. */
    private static void registerCandidates(List<String> directions) throws Exception {
        TurretCatalogRegistry registry = TurretCatalogRegistry.installed();
        JSONArray mounts = new JSONArray();
        JSONArray structures = new JSONArray();
        for (String d : directions) {
            if (d.equals(SHIPPED)) continue;
            if (registry.getStructure(structureId(d, "autogun")) != null) continue;
            mounts.put(mount(d, "autogun", "weapon.turret-heavy-mg", 1.25f, ART + d + "/autogun.png"));
            mounts.put(mount(d, "cannon", "weapon.turret-hephaestus", 1.25f, ART + d + "/cannon.png"));
            mounts.put(mount(d, "heavy", "weapon.turret-arbalest", 3.0f, ART + d + "/heavy.png"));
            mounts.put(mount(d, "locust", "weapon.turret-locust", 2.4f, "graphics/weapons/locust_turret.png"));
            structures.put(structure(d, "autogun", 1, ART + d + "/collar.png"));
            structures.put(structure(d, "cannon", 1, ART + d + "/collar.png"));
            structures.put(structure(d, "heavy", 3, ART + d + "/plinth.png"));
            structures.put(structure(d, "locust", 3, ART + d + "/plinth.png"));
        }
        if (mounts.length() > 0) {
            registry.ingest(new JSONObject().put("mounts", mounts).put("structures", structures),
                    WeaponRegistry.installed());
        }
    }

    private static String structureId(String direction, String role) {
        if (direction.equals(SHIPPED)) {
            if (role.equals("heavy")) return "structure.hardpoint-cannon";
            if (role.equals("locust")) return "structure.hardpoint-locust";
            return "structure.point-" + role;
        }
        if (role.equals("grenade")) role = "autogun";
        if (role.equals("pd")) role = "cannon";
        return "structure.gt-" + direction + "-" + role;
    }

    private static JSONObject mount(String direction, String role, String weapon, float cells, String sprite)
            throws Exception {
        return new JSONObject().put("id", "mount.gt-" + direction + "-" + role).put("weapon", weapon)
                .put("sim", new JSONObject().put("ammoCapacity", 999).put("turnRateDegPerSec", 90.0))
                .put("render", new JSONObject().put("visualCells", cells).put("muzzleOffsetCells", cells * 0.45f)
                        .put("sprite", sprite));
    }

    private static JSONObject structure(String direction, String role, int cells, String base) throws Exception {
        return new JSONObject().put("id", structureId(direction, role)).put("mount", "mount.gt-" + direction + "-" + role)
                .put("catalog", new JSONObject().put("displayName", direction + " " + role))
                .put("durability", new JSONObject().put("structure", 100.0).put("armorCapacity", 100.0)
                        .put("armorRating", 10.0))
                .put("physics", new JSONObject().put("radius", cells == 1 ? 0.45 : 1.1).put("hitHalfHeight", 0.6)
                        .put("footprintCells", new JSONArray().put(cells).put(cells)))
                .put("forceScore", 10.0)
                .put("art", new JSONObject().put("base", base));
    }

    /**
     * A 40x22 window: a heavy-cannon hardpoint and a Locust hardpoint side by side, four point emplacements (autogun,
     * light cannon, grenade launcher, point-defence pod) round them, and a six-marine squad standing between the hardpoints. Only tick zero
     * is drawn, so the heads hold the bearings set here and no launch smoke covers them.
     */
    private static LookReview.Shot shot(String direction, String biome, SurfacePalette surface) {
        LookGround ground = LookGround.of(surface, 7L);
        return new LookReview.Shot(direction + "-" + biome, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            float[] squad = placement.openNear(20f, 12f);
            builder.squad("squad").faction(Faction.MARINE).size(6)
                    .at((int) squad[0] - placement.site().x0(), (int) squad[1] - placement.site().y0())
                    .primary("weapon.field-rifle").stationary().done();
            placement.holdouts(builder);
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            int i = 0;
            Object[][] layout = {
                    {"heavy", 12f, 11f, 200f}, {"locust", 28f, 11f, 150f},
                    {"autogun", 17f, 4f, 330f}, {"cannon", 23f, 18f, 120f},
                    {"grenade", 8f, 5f, 290f}, {"pd", 31f, 17f, 60f}};
            for (Object[] spot : layout) {
                float[] at = placement.openNear((Float) spot[1], (Float) spot[2]);
                int x = (int) at[0];
                int y = (int) at[1];
                long id = sim.spawn(MapTurret.create("gt" + i++, Faction.MARINE, structureId(direction, (String) spot[0]),
                        x, y));
                sim.getGrid().setWalkable(x, y, false);
                sim.turretState().setFacingDegrees(id, (Float) spot[3]);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.play(placement.site().centreX(), placement.site().centreY()),
                LookReview.View.close("close", placement.site().centreX(), placement.site().centreY(), 30f)),
                1, 2, 1);
    }
}
