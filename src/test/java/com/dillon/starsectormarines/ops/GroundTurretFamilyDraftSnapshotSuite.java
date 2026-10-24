package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
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
 * Opt-in look study for the midline and high-tech emplacement-style drafts (`ground-turret-tiers.md`): one picture
 * per art-direction option, the option's prepared variant on the west half (a hardpoint and two point emplacements)
 * and its militia variant on the east half, a marine squad between them for scale, on real generated ground at play
 * zoom (30 cells across) through {@link LookReview}.
 *
 * <p>The drafts are not shipped and are not emplacement styles: {@code art-source/ground-turrets/family_drafts.py
 * export} cuts them into {@code build/ground-turret-art/<option>-<variant>/}, and this suite registers throwaway
 * mounts and structures that point there, so nothing reaches the catalog or generation. Options without exported art
 * are skipped. {@code -Dsnapshot.groundTurrets.drafts=} and {@code -Dsnapshot.groundTurrets.biomes=} narrow the run.
 */
public final class GroundTurretFamilyDraftSnapshotSuite implements SnapshotSuite {

    private static final List<String> OPTIONS = List.of("league-standard", "diktat-export", "commercial-grade",
            "tritach-white", "graphite-stealth", "field-emitter");
    private static final List<String> VARIANTS = List.of("prepared", "militia");
    private static final String ART = "../build/ground-turret-art/";

    @Override public String id() { return "ground-turret-family-draft"; }

    @Override public String label() { return "Ground turret midline / high-tech drafts (look study)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<String> options = new ArrayList<>();
        for (String o : System.getProperty("snapshot.groundTurrets.drafts", String.join(",", OPTIONS)).split(",")) {
            String option = o.trim();
            boolean complete = VARIANTS.stream().allMatch(v ->
                    Files.isRegularFile(context.modRoot().resolve(ART + option + "-" + v + "/plinth.png")));
            if (complete) options.add(option);
            else System.out.println("    [ground-turret-family-draft] no exported art for " + option + ", skipped");
        }
        register(options);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String biome : System.getProperty("snapshot.groundTurrets.biomes", "verdant").split(",")) {
            SurfacePalette surface = SurfacePalette.valueOf(biome.trim().toUpperCase());
            for (String option : options) {
                artifacts.addAll(LookReview.record(context.modRoot(), shot(option, biome.trim(), surface)));
            }
        }
        return artifacts;
    }

    /** Mounts and structures for each option's two variants, pointing at their exported art. Idempotent in a JVM. */
    private static void register(List<String> options) throws Exception {
        TurretCatalogRegistry registry = TurretCatalogRegistry.installed();
        JSONArray mounts = new JSONArray();
        JSONArray structures = new JSONArray();
        for (String option : options) {
            for (String variant : VARIANTS) {
                String set = option + "-" + variant;
                if (registry.getStructure(structureId(set, "autogun")) != null) continue;
                mounts.put(mount(set, "autogun", "weapon.turret-heavy-mg", 1.25f, ART + set + "/autogun.png"));
                mounts.put(mount(set, "cannon", "weapon.turret-arbalest", 1.25f, ART + set + "/cannon.png"));
                mounts.put(mount(set, "heavy", "weapon.turret-hephaestus", 3.0f, ART + set + "/heavy.png"));
                structures.put(structure(set, "autogun", 1, ART + set + "/collar.png", ART + set + "/autogun.png"));
                structures.put(structure(set, "cannon", 1, ART + set + "/collar.png", ART + set + "/cannon.png"));
                structures.put(structure(set, "heavy", 3, ART + set + "/plinth.png", ART + set + "/heavy.png"));
            }
        }
        if (mounts.length() > 0) {
            registry.ingest(new JSONObject().put("mounts", mounts).put("structures", structures),
                    WeaponRegistry.installed());
        }
    }

    private static String structureId(String set, String role) {
        return "structure.gtd-" + set + "-" + role;
    }

    private static JSONObject mount(String set, String role, String weapon, float cells, String sprite)
            throws Exception {
        return new JSONObject().put("id", "mount.gtd-" + set + "-" + role).put("weapon", weapon)
                .put("sim", new JSONObject().put("ammoCapacity", 999).put("turnRateDegPerSec", 90.0))
                .put("render", new JSONObject().put("visualCells", cells).put("muzzleOffsetCells", cells * 0.45f)
                        .put("sprite", sprite));
    }

    /**
     * A draft structure. Its art is also given as the default style's {@code looks} entry, because the review's sprite
     * catalog reads mount sprites once, before this suite registers anything, while a look is resolved by path.
     */
    private static JSONObject structure(String set, String role, int cells, String base, String head)
            throws Exception {
        JSONObject physics = new JSONObject().put("radius", cells == 1 ? 0.45 : 1.3).put("hitHalfHeight", 0.6)
                .put("footprintCells", new JSONArray().put(cells).put(cells));
        if (cells > 1) physics.put("plinthTiers", 1);
        return new JSONObject().put("id", structureId(set, role)).put("mount", "mount.gtd-" + set + "-" + role)
                .put("catalog", new JSONObject().put("displayName", set + " " + role))
                .put("durability", new JSONObject().put("structure", 100.0).put("armorCapacity", 100.0)
                        .put("armorRating", 10.0))
                .put("physics", physics)
                .put("forceScore", 10.0)
                .put("art", new JSONObject().put("base", base))
                .put("looks", new JSONObject().put("brutalist",
                        new JSONObject().put("base", base).put("sprite", head)));
    }

    /**
     * A 40x22 window: on the west the prepared hardpoint with an autogun and a cannon point emplacement, on the east
     * the militia set mirrored, and a six-marine squad on the centre line, framed 30 cells across (a close play zoom).
     * Only tick zero is drawn, so the heads hold the bearings set here.
     */
    private static LookReview.Shot shot(String option, String biome, SurfacePalette surface) {
        LookGround ground = LookGround.of(surface, 7L);
        return new LookReview.Shot(option + "-" + biome, ground, 40, 22, placement -> {
            SceneBuilder builder = placement.builder();
            float[] squad = placement.openNear(20f, 12f);
            builder.squad("squad").faction(Faction.MARINE).size(6)
                    .at((int) squad[0] - placement.site().x0(), (int) squad[1] - placement.site().y0())
                    .primary("weapon.field-rifle").stationary().done();
            placement.holdouts(builder);
            BattleSimulation sim = builder.build().sim();
            Object[][] layout = {
                    {"prepared", "heavy", 12f, 11f, 20f}, {"prepared", "autogun", 8f, 5f, 330f},
                    {"prepared", "cannon", 15f, 16f, 120f},
                    {"militia", "heavy", 28f, 11f, 340f}, {"militia", "autogun", 32f, 5f, 30f},
                    {"militia", "cannon", 25f, 16f, 240f}};
            int i = 0;
            for (Object[] spot : layout) {
                float[] at = placement.openNear((Float) spot[2], (Float) spot[3]);
                int x = (int) at[0];
                int y = (int) at[1];
                String set = option + "-" + spot[0];
                long id = sim.spawn(MapTurret.create("gtd" + i++, Faction.MARINE, structureId(set, (String) spot[1]),
                        x, y));
                sim.getGrid().setWalkable(x, y, false);
                sim.turretState().setFacingDegrees(id, (Float) spot[4]);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.close("close", placement.site().centreX(), placement.site().centreY(), 30f)),
                1, 1, 1);
    }
}
