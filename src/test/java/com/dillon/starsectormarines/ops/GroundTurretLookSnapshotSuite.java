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
import java.util.Map;

/**
 * Opt-in look study for the ground turrets: the catalog's point emplacements ({@code structure.point-autogun},
 * {@code -cannon}, {@code -grenade}, {@code -pd}) and hardpoints ({@code structure.hardpoint-cannon}, {@code -locust})
 * stood on real generated ground through {@link LookReview}, with a marine squad beside them for scale, once per look.
 *
 * <p>{@code brutalist} is the catalog's default art. Every other look ({@code earthwork}, {@code derelict}) is read
 * from each structure's {@code looks} object in the turret catalog: until the runtime resolves a look itself, this
 * suite registers a copy of each structure and its mount with that look's {@code base}, {@code sprite} and
 * {@code recoilSprite} swapped in. {@code -Dsnapshot.groundTurrets.looks=} and {@code -Dsnapshot.groundTurrets.biomes=}
 * narrow the run.
 */
public final class GroundTurretLookSnapshotSuite implements SnapshotSuite {

    private static final String CATALOG = "data/marines/turret-emplacements.turret.json";
    private static final String DEFAULT_LOOK = "brutalist";
    /** Role in the scene's layout to the catalog structure that fills it. */
    private static final Map<String, String> ROLES = Map.of(
            "heavy", "structure.hardpoint-cannon",
            "locust", "structure.hardpoint-locust",
            "autogun", "structure.point-autogun",
            "cannon", "structure.point-cannon",
            "grenade", "structure.point-grenade",
            "pd", "structure.point-pd");

    @Override public String id() { return "ground-turret-look"; }

    @Override public String label() { return "Ground turret looks (look study)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<String> looks = new ArrayList<>();
        for (String look : System.getProperty("snapshot.groundTurrets.looks", "brutalist,earthwork,derelict").split(",")) {
            looks.add(look.trim());
        }
        registerLooks(context, looks);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String biome : System.getProperty("snapshot.groundTurrets.biomes", "verdant,arid,frozen").split(",")) {
            SurfacePalette surface = SurfacePalette.valueOf(biome.trim().toUpperCase());
            for (String look : looks) {
                artifacts.addAll(LookReview.record(context.modRoot(), shot(look, biome.trim(), surface)));
            }
        }
        return artifacts;
    }

    private static String structureId(String look, String role) {
        String id = ROLES.get(role);
        return look.equals(DEFAULT_LOOK) ? id : id.replace("structure.", "structure.look-" + look + "-");
    }

    /** Copies of each ground-turret structure and its mount with a look's art swapped in. Idempotent within a JVM. */
    private static void registerLooks(SnapshotContext context, List<String> looks) throws Exception {
        TurretCatalogRegistry registry = TurretCatalogRegistry.installed();
        JSONObject catalog = new JSONObject(Files.readString(context.modRoot().resolve(CATALOG)));
        JSONArray mounts = new JSONArray();
        JSONArray structures = new JSONArray();
        for (String look : looks) {
            if (look.equals(DEFAULT_LOOK) || registry.getStructure(structureId(look, "heavy")) != null) continue;
            for (String role : ROLES.keySet()) {
                JSONObject structure = find(catalog.getJSONArray("structures"), ROLES.get(role));
                JSONObject override = structure.getJSONObject("looks").getJSONObject(look);
                JSONObject mount = new JSONObject(find(catalog.getJSONArray("mounts"), structure.getString("mount")).toString());
                String mountId = mount.getString("id").replace("mount.", "mount.look-" + look + "-");
                mount.put("id", mountId);
                JSONObject render = mount.getJSONObject("render");
                if (override.has("sprite")) render.put("sprite", override.getString("sprite"));
                if (override.has("recoilSprite")) render.put("recoilSprite", override.getString("recoilSprite"));
                JSONObject copy = new JSONObject(structure.toString());
                copy.remove("looks");
                copy.put("id", structureId(look, role)).put("mount", mountId);
                copy.getJSONObject("art").put("base", override.getString("base"));
                mounts.put(mount);
                structures.put(copy);
            }
        }
        if (mounts.length() > 0) {
            registry.ingest(new JSONObject().put("mounts", mounts).put("structures", structures),
                    WeaponRegistry.installed());
        }
    }

    private static JSONObject find(JSONArray entries, String id) throws Exception {
        for (int i = 0; i < entries.length(); i++) {
            if (entries.getJSONObject(i).getString("id").equals(id)) return entries.getJSONObject(i);
        }
        throw new IllegalStateException("No catalog entry " + id);
    }

    /**
     * A 40x22 window: a heavy-cannon hardpoint and a Locust hardpoint side by side, four point emplacements (autogun,
     * light cannon, grenade launcher, point-defence pod) round them, and a six-marine squad standing between the
     * hardpoints. The hardpoints' guns are laid away from their stairs. Only tick zero is drawn, so the heads hold
     * the bearings set here and no launch smoke covers them.
     */
    private static LookReview.Shot shot(String look, String biome, SurfacePalette surface) {
        LookGround ground = LookGround.of(surface, 7L);
        return new LookReview.Shot(look + "-" + biome, ground, 40, 22, placement -> {
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
                    {"heavy", 12f, 11f, 25f}, {"locust", 28f, 11f, 330f},
                    {"autogun", 17f, 4f, 330f}, {"cannon", 23f, 18f, 120f},
                    {"grenade", 8f, 5f, 290f}, {"pd", 31f, 17f, 60f}};
            for (Object[] spot : layout) {
                float[] at = placement.openNear((Float) spot[1], (Float) spot[2]);
                int x = (int) at[0];
                int y = (int) at[1];
                long id = sim.spawn(MapTurret.create("gt" + i++, Faction.MARINE, structureId(look, (String) spot[0]),
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
