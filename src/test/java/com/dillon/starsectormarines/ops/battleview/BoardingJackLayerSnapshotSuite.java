package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.setup.GreyhookRigIssue;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Boarding Jack's layered art through the shipping infantry compositor: its separate parts,
 * the rig beside the Specter it shares a registration with, the articulated poses, the baked
 * paints, the boarding shield's sweep, and the Greyhook raider's rig at world scale.
 */
public final class BoardingJackLayerSnapshotSuite implements SnapshotSuite {
    private static final String ROOT = "graphics/battle/marine-modular-topdown/";
    private static final String JACK = ROOT + "variants/armor/boarding-jack/";
    private static final String SPECTER = ROOT + "variants/armor/specter-heavy/";
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;
    private UnitLayerLayouts layouts;

    @Override public String id() { return "boarding-jack-layers"; }
    @Override public String label() { return "Boarding Jack layered rig review"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        modRoot = context.modRoot();
        images.clear();
        HeadlessArmoryPreviewRenderer.installCatalogs(modRoot);
        layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                modRoot.resolve(UnitLayerLayouts.CONTENT_PATH))));
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < 12; i++) frames.add(review(i / 12f));
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("boarding-jack-layers-contact.png", frames.get(3)));
        artifacts.add(SnapshotArtifact.colorAnimation("boarding-jack-layers-motion.gif", frames, 100));
        for (int i = 0; i < frames.size(); i++) {
            artifacts.add(new SnapshotArtifact("frames/pose-%02d.png".formatted(i), frames.get(i)));
        }
        return artifacts;
    }

    private BufferedImage review(float phase) throws Exception {
        BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        text(g, "BOARDING JACK / PIRATE-REBUILT BOARDING FRAME", 40, 48, 28);
        text(g, "1920 × 1080 · 100% UI scale · production infantry composition · the low-tech shield is the left boarding shield", 40, 79, 17);

        MarineArmorCatalogDef jackArmor = MarineArmorCatalogRegistry.require(GreyhookRigIssue.PATTERN);
        MarineArmorCatalogDef specterArmor = MarineArmorCatalogRegistry.require("armor.specter-heavy");
        LayeredUnitAssets jack = assets(JACK, jackArmor);
        LayeredUnitAssets specter = assets(SPECTER, specterArmor);
        IntegralSystemDef latch = jackArmor.integralSystem();

        text(g, "Separate exported parts · magnified 2×", 40, 125, 20);
        String[] parts = {"body", "shoulder-left", "shoulder-right"};
        String[] partLabels = {"Torso · power pack · joint backing", "Left boarding shield (low-tech shield)", "Right cargo-frame cap"};
        for (int i = 0; i < parts.length; i++) {
            BufferedImage part = image(JACK + parts[i] + ".png");
            int x = 40 + i * 320;
            g.drawImage(part, x, 145, part.getWidth() * 2, part.getHeight() * 2, null);
            text(g, partLabels[i], x, 360, 15);
        }
        BufferedImage head = image(JACK + "head.png");
        BufferedImage foot = image(JACK + "foot.png");
        g.drawImage(head, 40, 380, head.getWidth() * 2, head.getHeight() * 2, null);
        g.drawImage(foot, 220, 380, foot.getWidth() * 2, foot.getHeight() * 2, null);
        text(g, "Helmet · foot", 40, 535, 15);
        String[] sweep = {"idle", "walking", "aiming", "firing"};
        for (int i = 0; i < sweep.length; i++) {
            actor(g, jack, jackArmor, 400 + i * 150, 450, 95, sweep[i], i == 3 ? 0.4f : phase, 0,
                    WeaponRegistry.STARTER_PRIMARY_ID);
            text(g, sweep[i], 370 + i * 150, 535, 15);
        }
        text(g, "Shield sweep: the slab leads the torso's turn", 360, 385, 15);

        text(g, "Same registration, different rig · magnified", 1000, 125, 20);
        LayeredUnitAssets raider = assets(JACK + "paints/pirate/", jackArmor);
        actor(g, specter, specterArmor, 1100, 300, 150, "idle", phase, 0, WeaponRegistry.STARTER_PRIMARY_ID);
        actor(g, jack, jackArmor, 1420, 300, 150, "idle", phase, 0, WeaponRegistry.STARTER_PRIMARY_ID);
        actor(g, raider, jackArmor, 1740, 300, 150, "idle", phase, 0, WeaponRegistry.STARTER_PRIMARY_ID);
        text(g, "Specter (Tri-Tachyon)", 1020, 470, 17);
        text(g, "Boarding Jack · company issue", 1310, 470, 17);
        text(g, "Boarding Jack · Greyhook paint", 1630, 470, 17);
        g.setColor(new Color(0x2A3640));
        g.fillRect(40, 560, 1840, 2);

        text(g, "Articulated poses · magnified", 40, 600, 20);
        String[] clips = {"walking", "aiming", "firing", "amr-brace", "rocket-fire"};
        String[] labels = {"Walk", "Aim / independent helmet", "Fire / rifle recoil", "Brace / anti-materiel", "Rocket / overlap"};
        String[] weapons = {WeaponRegistry.SMG_ID, WeaponRegistry.PULSE_RIFLE_ID,
                WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.STARTER_PRIMARY_ID};
        for (int i = 0; i < clips.length; i++) {
            actor(g, jack, jackArmor, 150 + i * 255, 715, 110, clips[i], phase,
                    i == 1 ? 50f * (float) Math.sin(phase * Math.PI * 2) : 0, weapons[i]);
            text(g, labels[i], 70 + i * 255, 830, 15);
        }
        text(g, "Paints · one silhouette · pirate is Greyhook's", 1330, 600, 20);
        String[] paints = {"rust-orange", "pirate"};
        for (int i = 0; i < paints.length; i++) {
            LayeredUnitAssets painted = i == 0 ? jack : assets(JACK + "paints/" + paints[i] + "/", jackArmor);
            actor(g, painted, jackArmor, 1385 + i * 110, 715, 80, "walking", phase, 0,
                    WeaponRegistry.STARTER_PRIMARY_ID);
            text(g, paints[i], 1350 + i * 110, 830, 14);
        }
        g.setColor(new Color(0x2A3640));
        g.fillRect(40, 855, 1840, 2);

        float normalSw = UnitRenderService.layeredInfantryShoulderWidth(17.142857f, UnitType.MARINE.renderScale);
        text(g, "Normal world scale · %.1fpx shoulder width · walking at three headings".formatted(normalSw), 40, 890, 18);
        LayeredArmorFamily greyhook = GreyhookRigIssue.drawnFamily(jackArmor);
        for (int i = 0; i < 3; i++) {
            actor(g, specter, specterArmor, 90 + i * 80, 960, normalSw, "walking", phase + i / 3f, 0,
                    WeaponRegistry.STARTER_PRIMARY_ID, i * 90f);
            actor(g, jack, jackArmor, 420 + i * 80, 960, normalSw, "walking", phase + i / 3f, 0,
                    WeaponRegistry.STARTER_PRIMARY_ID, i * 90f);
        }
        text(g, "Specter", 90, 1025, 15);
        text(g, "Boarding Jack", 420, 1025, 15);
        text(g, "Greyhook raiders draw " + greyhook + (greyhook == GreyhookRigIssue.RAIDER_PAINT
                ? " (Greyhook paint)" : " (stand-in)"), 760, 890, 18);
        for (int i = 0; i < 6; i++) {
            actor(g, raider, jackArmor, 800 + i * 75, 965, normalSw, i % 2 == 0 ? "walking" : "firing",
                    phase + i * 0.17f, 0, WeaponRegistry.STARTER_PRIMARY_ID, 180f + i * 25f);
        }
        text(g, "Small-scale legibility · 24px · issue above, Greyhook below", 1360, 890, 18);
        for (int i = 0; i < 8; i++) {
            actor(g, i < 4 ? jack : raider, jackArmor, 1440 + i % 4 * 100, 940 + i / 4 * 70, 24, "walking",
                    phase + i * 0.11f, 0, WeaponRegistry.STARTER_PRIMARY_ID, 0);
        }
        text(g, latch.displayName() + " (" + latch.id() + ") is the left boarding shield: directional soak over " + Math.round(latch.breacherAssist().shieldedArcDegrees()) + "° on that side", 40, 1060, 14);
        g.dispose();
        return output;
    }

    private void actor(Graphics2D g, LayeredUnitAssets assets, MarineArmorCatalogDef armor,
                       float x, float y, float sw, String clip, float phase, float headLook, String weaponId) {
        actor(g, assets, armor, x, y, sw, clip, phase, headLook, weaponId, 0f);
    }

    private void actor(Graphics2D g, LayeredUnitAssets assets, MarineArmorCatalogDef armor,
                       float x, float y, float sw, String clip, float phase,
                       float headLook, String weaponId, float facing) {
        if (clip.equals("walking")) phase += 0.75f;
        boolean amr = clip.equals("amr-brace");
        boolean rocket = clip.equals("rocket-fire");
        String variant = amr ? "anti-materiel" : rocket ? "rocket" : "rifle";
        String animation = amr ? "aiming" : rocket ? "firing" : clip;
        LayerPose pose = layouts.clip("marine-line", variant, animation).sample(
                amr ? 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2)
                        : clip.equals("aiming") ? 1f : phase);
        LayeredArmorFamily family = armor.appearanceFamily();
        pose = layouts.applyArmorMastering(pose, family, family);
        int action = amr ? LayeredAppearance.POSE_AMR_AIM
                : rocket ? LayeredAppearance.POSE_ROCKET_FIRE
                : clip.equals("firing") ? LayeredAppearance.POSE_FIRING
                : clip.equals("idle") || clip.equals("walking") ? LayeredAppearance.POSE_IDLE
                : LayeredAppearance.POSE_AIMED;
        int flags = clip.equals("walking") ? LayeredAppearance.FLAG_MOVING : 0;
        if ((clip.equals("firing") || rocket) && phase % 1f < 0.25f) flags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
        WeaponDef primary = WeaponRegistry.require(weaponId);
        SpecialEquipmentDef special = amr
                ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)
                : rocket ? SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID) : null;
        LayeredUnitComposer.emit((sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        }, assets, assets.head, primary, true, special, EquipmentGrade.SERVICE,
                x, 1080f - y, sw, facing, headLook, phase, phase, action, flags, 1f, pose, armor.integralSystem());
    }

    private LayeredUnitAssets assets(String armor, MarineArmorCatalogDef def) throws Exception {
        String weapons = ROOT + "variants/weapons/";
        LayeredUnitAssets result = new LayeredUnitAssets(layer(armor + "body.png"), layer(armor + "head.png"),
                layer(armor + "foot.png"), null, layer(weapons + "rifle.png"), layer(weapons + "laser-gun.png"),
                layer(weapons + "smg.png"), layer(weapons + "dmr.png"),
                layer(weapons + "srm-1.png"), layer(weapons + "anti-materiel-rifle.png"),
                layer(ROOT + "marine-muzzle-flash.png"), null, null,
                layer(armor + "shoulder-left.png"), layer(armor + "shoulder-right.png"));
        result.registerSpecialEquipment(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                layer(weapons + "anti-materiel-rifle.png"));
        result.registerSpecialEquipment(SpecialEquipmentRegistry.SRM_1_ID,
                layer(weapons + "srm-1.png"));
        IntegralSystemDef system = def.integralSystem();
        if (system != null && system.carrierLayer() != null) {
            result.registerIntegralSystem(system.id(), layer(system.carrierLayer().spritePath()));
        }
        return result;
    }

    private LayeredSpriteCache layer(String path) throws Exception {
        BufferedImage image = image(path);
        return LayeredSpriteCache.headless(path, image.getWidth(), image.getHeight());
    }

    private BufferedImage image(String path) throws Exception {
        BufferedImage result = images.get(path);
        if (result == null) {
            result = ImageIO.read(modRoot.resolve(path).toFile());
            if (result == null) throw new IllegalArgumentException("Unreadable sprite: " + path);
            images.put(path, result);
        }
        return result;
    }

    private static void text(Graphics2D g, String value, int x, int y, int size) {
        g.setColor(new Color(0xD3DFE5));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }
}
