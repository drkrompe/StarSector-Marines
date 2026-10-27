package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warship weapons up close, every sprite layer their specs name (under-plate, base, recoiling barrel, loaded
 * missiles, glow; {@link VanillaMountLook}): Dormer's Enforcer and Wolf and the Greyhook from Black Sluice, an
 * Onslaught and a Paragon, each at rest with its turrets laid on one bearing, firing (barrels back, glows lit,
 * muzzle flashes on the barrel that fired, a rack's fired missile gone), and a GIF of the firing cycle. 1920x1080 at
 * 100%.
 *
 * <p>Posed rather than played: the hulls stand still and the suite sets each mount's aim and time since firing, so
 * the frame shows the drawing and nothing about who wins. Before drawing it audits every hull the warship scenes and
 * missions fit and fails if a layer a spec names cannot be found in the install or the mod.
 */
public final class WarshipMountsSnapshotSuite implements SnapshotSuite {

    /** Hulls drawn close up: hull id, variant id, caption name. */
    private static final String[][] SHOWN = {
            {"enforcer", "enforcer_Balanced", "RUSK'S ENFORCER (enforcer_Balanced)"},
            {"wolf_hegemony", "wolf_hegemony_Rusk", "RUSK'S WOLF (wolf_hegemony_Rusk)"},
            {"venture", "venture_Greyhook", "THE GREYHOOK (venture_Greyhook)"},
            {"onslaught", "onslaught_Standard", "ONSLAUGHT (onslaught_Standard)"},
            {"paragon", "paragon_Elite", "PARAGON (paragon_Elite)"},
    };

    /** Every hull the warship scenes and missions fit, audited for their layers whether or not drawn here. */
    private static final String[][] AUDITED = {
            {"enforcer", "enforcer_Balanced"}, {"enforcer", "enforcer_Assault"},
            {"wolf_hegemony", "wolf_hegemony_Rusk"}, {"wolf_hegemony", "wolf_hegemony_Assault"},
            {"venture", "venture_Greyhook"}, {"venture", "venture_Balanced"},
            {"hound_d_pirates", "hound_d_pirates_Standard"}, {"hound", "hound_Assault"}, {"hound", "hound_Standard"},
            {"onslaught", "onslaught_Standard"}, {"eagle", "eagle_Assault"},
            {"dominator", "dominator_Assault"}, {"dominator", "dominator_Support"},
            {"lasher", "lasher_Standard"}, {"paragon", "paragon_Elite"}, {"medusa", "medusa_CS"},
            {"hammerhead", "hammerhead_Balanced"}, {"sunder", "sunder_Assault"},
            {"vigilance", "vigilance_Strike"}, {"vigilance", "vigilance_Standard"},
            {"conquest", "conquest_Standard"}, {"shrike", "shrike_Attack"},
            {"brawler", "brawler_Elite"}, {"heron", "heron_Strike"}, {"drover", "drover_Strike"},
    };

    /** Seconds between shots in the firing GIF, and its frame step. */
    private static final float CYCLE_SECONDS = 0.6f, STEP_SECONDS = 0.04f;
    /** Close-up width in hull lengths. */
    private static final float ACROSS_LENGTHS = 2.3f;
    /** Bearing every turret is laid on, off the bow to starboard (degrees, hull-facing convention). */
    private static final float AIM_OFF_BOW = -35f;

    public String id() { return "warship-mounts"; }

    public String label() { return "Warship mounts: every weapon sprite layer, at rest, firing and the recoil cycle"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        audit(context.modRoot());

        List<Warship> ships = new ArrayList<>();
        float x = 0f, height = 0f;
        for (String[] hull : SHOWN) {
            Warship ship = Warship.fit(hull[0], hull[1], Faction.DEFENDER, 0f, 0f, 0f);
            float span = Math.max(ship.lengthCells, ship.widthCells);
            x += span * ACROSS_LENGTHS * 0.6f;
            ship.x = x;
            x += span * ACROSS_LENGTHS * 0.6f;
            height = Math.max(height, span * ACROSS_LENGTHS);
            ships.add(ship);
        }
        int width = (int) Math.ceil(x) + 8;
        int tall = (int) Math.ceil(height) + 8;
        SceneBuilder builder = SceneBuilder.openGround(width, tall).missionCompletion(false);
        builder.squad("ally-ground").faction(Faction.MARINE).size(1).at(2, 2).unarmed().stationary().done();
        builder.squad("defender-ground").faction(Faction.DEFENDER).size(1).at(width - 3, tall - 3).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        for (int yy = 0; yy < tall; yy++) {
            for (int xx = 0; xx < width; xx++) sim.getTopology().setGroundKind(xx, yy, GroundKind.GRASS);
        }
        for (Warship ship : ships) {
            ship.y = tall * 0.5f;
            ship.stationTo(ship.x, ship.y);
            ship.limitFlux();
            ship.groundFire(Warship.GroundFire.HOLD);
            sim.addWarship(ship);
        }

        List<SnapshotArtifact> out = new ArrayList<>();
        for (int i = 0; i < ships.size(); i++) {
            Warship ship = ships.get(i);
            String name = SHOWN[i][2];
            String slug = String.format(Locale.ROOT, "%02d-%s", i + 1, SHOWN[i][0]);
            float across = Math.max(ship.lengthCells, ship.widthCells) * ACROSS_LENGTHS;
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                    ship.centerX(), ship.centerY(), across);
            String layers = layerSummary(ship);

            pose(ships, 1000f, 0);
            out.add(new SnapshotArtifact(slug + "-rest.png", renderer.render(sim,
                    "WARSHIP MOUNTS / " + name + " / AT REST, TURRETS LAID 35 DEG OFF THE BOW / " + layers)));

            pose(ships, 0.02f, 1);
            out.add(new SnapshotArtifact(slug + "-firing.png", renderer.render(sim,
                    "WARSHIP MOUNTS / " + name + " / FIRING: 0.02 s AFTER THE SHOT, BARRELS BACK, GLOWS LIT / "
                            + layers)));

            List<BufferedImage> cycle = new ArrayList<>();
            int steps = Math.round(2f * CYCLE_SECONDS / STEP_SECONDS);
            for (int s = 0; s < steps; s++) {
                float t = s * STEP_SECONDS;
                int shots = 1 + (int) (t / CYCLE_SECONDS);
                float since = t % CYCLE_SECONDS;
                pose(ships, since, shots);
                cycle.add(renderer.render(sim, String.format(Locale.ROOT,
                        "WARSHIP MOUNTS / %s / FIRING CYCLE / shot %d, %.2f s after it / %s",
                        name, shots, since, layers)));
            }
            out.add(SnapshotArtifact.animation(slug + "-cycle.gif", cycle, Math.round(STEP_SECONDS * 1000f * 2f)));
        }
        return out;
    }

    /** Lays every turret toward one bearing off its hull's bow, inside its arc, and dates its last shot. */
    private static void pose(List<Warship> ships, float secondsSinceFired, int shotsFired) {
        for (Warship ship : ships) {
            for (Warship.Mount m : ship.mounts()) {
                float rest = ship.facingDegrees + m.slot.angleDegrees();
                float want = WarshipFacing.wrap(ship.facingDegrees + AIM_OFF_BOW - rest);
                float half = Math.max(0f, m.slot.arcDegrees() * 0.5f);
                m.headingDegrees = rest + Math.max(-half, Math.min(half, want));
                m.secondsSinceFired = secondsSinceFired;
                m.shotsFired = shotsFired;
            }
        }
    }

    /** "N mounts: x under, y barrel, ..." for a hull's fit, as the captions say. */
    private static String layerSummary(Warship ship) {
        Map<VanillaMountLook.Kind, Integer> counts = new EnumMap<>(VanillaMountLook.Kind.class);
        for (Warship.Mount m : ship.mounts()) {
            VanillaMountLook look = VanillaWeaponLooks.mount(m.weapon.id());
            if (look == null) continue;
            for (VanillaMountLook.Layer l : look.layers(m.slot.hardpoint())) counts.merge(l.kind(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder().append(ship.mounts().size()).append(" mounts, layers");
        for (Map.Entry<VanillaMountLook.Kind, Integer> e : counts.entrySet()) {
            sb.append(' ').append(e.getValue()).append(' ').append(e.getKey().name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /**
     * Every weapon each audited hull fits: every layer its spec names must be found in the mod or the install, or
     * the drawing would silently leave it out. Prints the weapons and their layers.
     */
    private static void audit(Path modRoot) throws Exception {
        String dir = System.getProperty("starsectorDir");
        Path core = dir == null || dir.isBlank() ? null : Paths.get(dir, "starsector-core");
        TreeSet<String> lines = new TreeSet<>();
        List<String> missing = new ArrayList<>();
        for (String[] hull : AUDITED) {
            Warship ship;
            try {
                ship = Warship.fit(hull[0], hull[1], Faction.DEFENDER, 0f, 0f, 0f);
            } catch (Exception e) {
                missing.add(hull[1] + ": could not be fitted (" + e.getMessage() + ")");
                continue;
            }
            for (Warship.Mount m : ship.mounts()) {
                boolean hp = m.slot.hardpoint();
                VanillaMountLook look = VanillaWeaponLooks.mount(m.weapon.id());
                if (look == null) {
                    missing.add(hull[1] + " " + m.weapon.id() + ": spec unreadable");
                    continue;
                }
                StringBuilder kinds = new StringBuilder();
                for (VanillaMountLook.Layer l : look.layers(hp)) {
                    kinds.append(kinds.length() == 0 ? "" : ",").append(l.kind().name().toLowerCase(Locale.ROOT));
                    String first = l.kind() == VanillaMountLook.Kind.LOADED_MISSILES ? look.missileSprite() : l.sprite();
                    // An animated layer is every one of its frames.
                    for (int f = 0; f < look.numFrames(); f++) {
                        String path = look.frameSprite(first, f);
                        boolean found = Files.isRegularFile(modRoot.resolve(path))
                                || core != null && Files.isRegularFile(core.resolve(path));
                        if (!found) missing.add(hull[1] + " " + m.weapon.id() + " " + l.kind() + ": " + path);
                    }
                    if (look.numFrames() > 1 && kinds.indexOf("frames") < 0) {
                        kinds.append("(").append(look.numFrames()).append(" frames)");
                    }
                }
                lines.add(String.format(Locale.ROOT, "%-22s %-9s %s", m.weapon.id(), hp ? "hardpoint" : "turret",
                        kinds));
            }
        }
        System.out.println("warship-mounts: weapons fitted by " + AUDITED.length + " audited variants, with layers drawn:");
        for (String line : lines) System.out.println("  " + line);
        assertTrue(missing.isEmpty(), "every layer a fitted weapon's spec names is found: " + missing);
    }
}
