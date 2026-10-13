package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
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

/** First-pass layered art review; uses the shipping infantry compositor. */
public final class SpecterLayerSnapshotSuite implements SnapshotSuite {
    private static final String ROOT = "graphics/battle/marine-modular-topdown/";
    private static final String SPECTER = ROOT + "variants/armor/specter-heavy/";
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;
    private UnitLayerLayouts layouts;

    @Override public String id() { return "specter-layers"; }
    @Override public String label() { return "Specter layered sprite first pass"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        modRoot = context.modRoot();
        images.clear();
        HeadlessArmoryPreviewRenderer.installCatalogs(modRoot);
        layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                modRoot.resolve(UnitLayerLayouts.CONTENT_PATH))));
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < 12; i++) frames.add(review(i / 12f));
        return List.of(new SnapshotArtifact("specter-layers-contact.png", frames.get(3)),
                SnapshotArtifact.colorAnimation("specter-layers-motion.gif", frames, 100));
    }

    private BufferedImage review(float phase) throws Exception {
        BufferedImage output = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, 1920, 1080);
        text(g, "SPECTER / LAYERED FIRST PASS", 40, 48, 28);
        text(g, "1920 × 1080 · 100% UI scale · production infantry composition", 40, 79, 17);
        LayeredUnitAssets slate = assets(SPECTER);
        text(g, "Separate source parts · magnified 2×", 40, 125, 20);
        String[] parts = {"body", "head", "foot", "rifle"};
        int[] partPositions = {50, 390, 650, 880};
        for (int i = 0; i < parts.length; i++) {
            BufferedImage part = image(i == 3 ? ROOT + "variants/weapons/rifle.png"
                    : SPECTER + parts[i] + ".png");
            int x = partPositions[i];
            g.drawImage(part, x, 145, part.getWidth() * 2, part.getHeight() * 2, null);
            text(g, parts[i] + " · " + part.getWidth() + "×" + part.getHeight(), x, 445, 16);
        }
        text(g, "Same shoulder unit · magnified comparison", 1120, 125, 20);
        actor(g, assets(ROOT + "variants/armor/army-green/"), LayeredArmorFamily.ARMY_GREEN,
                1250, 290, 160, "idle", phase, 0, WeaponRegistry.STARTER_PRIMARY_ID);
        actor(g, slate, LayeredArmorFamily.SPECTER_HEAVY,
                1630, 290, 160, "idle", phase, 0, WeaponRegistry.STARTER_PRIMARY_ID);
        text(g, "Legionary", 1190, 445, 17);
        text(g, "Specter", 1585, 445, 17);
        text(g, "Weapon clearance and articulated poses · magnified", 40, 500, 20);
        String[] clips = {"idle", "aiming", "firing", "walking", "aiming"};
        String[] labels = {"Carry / rifle", "Aim / pulse", "Fire / DMR", "Walk / SMG", "Independent head look"};
        String[] weapons = {WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.PULSE_RIFLE_ID,
                WeaponRegistry.DMR_ID, WeaponRegistry.SMG_ID, WeaponRegistry.STARTER_PRIMARY_ID};
        for (int i = 0; i < clips.length; i++) {
            actor(g, slate, LayeredArmorFamily.SPECTER_HEAVY,
                    190 + i * 375, 630, 125, clips[i], phase,
                    i == 4 ? 50f * (float) Math.sin(phase * Math.PI * 2) : 0, weapons[i]);
            text(g, labels[i], 70 + i * 375, 770, 17);
        }
        text(g, "Baked paint variants · shared geometry · magnified", 40, 825, 20);
        String[] paints = {"slate", "olive", "oxide", "sand"};
        for (int i = 0; i < paints.length; i++) {
            LayeredUnitAssets painted = i == 0 ? slate : assets(SPECTER + "paints/" + paints[i] + "/");
            actor(g, painted, LayeredArmorFamily.SPECTER_HEAVY,
                    170 + i * 300, 925, 100, "walking", phase, 0, WeaponRegistry.STARTER_PRIMARY_ID);
            text(g, paints[i], 145 + i * 300, 1035, 17);
        }
        text(g, "Small-scale legibility · 24px shoulder unit", 1300, 825, 18);
        for (int i = 0; i < 12; i++) actor(g, slate, LayeredArmorFamily.SPECTER_HEAVY,
                1340 + i % 6 * 88, 900 + i / 6 * 75, 24,
                "walking", phase + i * 0.11f, 0, WeaponRegistry.STARTER_PRIMARY_ID);
        text(g, "Asset paints; campaign palette selection is not implemented.", 1300, 1045, 15);
        g.dispose();
        return output;
    }

    private void actor(Graphics2D g, LayeredUnitAssets assets, LayeredArmorFamily family,
                       float x, float y, float sw, String clip, float phase,
                       float headLook, String weaponId) {
        if (clip.equals("walking")) phase += 0.75f;
        LayerPose pose = layouts.clip("marine-line", "rifle", clip).sample(
                clip.equals("aiming") ? 1f : phase);
        pose = layouts.applyArmorMastering(pose, family, family);
        int action = clip.equals("firing") ? LayeredAppearance.POSE_FIRING
                : clip.equals("idle") || clip.equals("walking") ? LayeredAppearance.POSE_IDLE
                : LayeredAppearance.POSE_AIMED;
        int flags = clip.equals("walking") ? LayeredAppearance.FLAG_MOVING : 0;
        if (clip.equals("firing") && phase < 0.25f) flags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
        WeaponDef primary = WeaponRegistry.require(weaponId);
        LayeredUnitComposer.emit((sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, 1080f - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        }, assets, assets.head, primary, true, null, EquipmentGrade.SERVICE,
                x, 1080f - y, sw, 0, headLook, phase, phase, action, flags, 1f, pose);
    }

    private LayeredUnitAssets assets(String armor) throws Exception {
        String weapons = ROOT + "variants/weapons/";
        return new LayeredUnitAssets(layer(armor + "body.png"), layer(armor + "head.png"),
                layer(armor.startsWith(SPECTER) ? armor + "foot.png" : ROOT + "marine-foot.png"),
                null, layer(weapons + "rifle.png"), layer(weapons + "laser-gun.png"),
                layer(weapons + "smg.png"), layer(weapons + "dmr.png"),
                layer(weapons + "rocket-launcher.png"), layer(weapons + "anti-materiel-rifle.png"),
                layer(ROOT + "marine-muzzle-flash.png"), null, null);
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
