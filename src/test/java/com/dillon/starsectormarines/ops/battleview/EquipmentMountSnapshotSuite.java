package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Catalog-equipped mount review through the live compositor and headless asset source. */
public final class EquipmentMountSnapshotSuite implements SnapshotSuite {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String[] ARMOR_IDS = {
            "armor.aegis-composite", "armor.arbalest", "armor.specter-heavy", "armor.line"
    };
    private static final String[] LABELS = {"Aegis", "Arbalest", "Specter", "Legionary control"};
    private final Map<String, BufferedImage> images = new HashMap<>();
    private Path modRoot;
    private ArmoryLoadoutPreviewComposer.Assets assets;

    @Override public String id() { return "equipment-mounts"; }
    @Override public String label() { return "Equipped suit hardware and weapon clearance"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        modRoot = context.modRoot();
        images.clear();
        assets = new HeadlessArmoryPreviewRenderer(modRoot).assets();
        // Resolve the catalog's own families and hardware; never graft another suit's pod.
        for (String id : ARMOR_IDS) {
            MarineArmorCatalogDef armor = MarineArmorCatalogRegistry.require(id);
            for (LayeredSpriteCache sprite : assets.layered(armor.appearanceFamily()).layers()) {
                if (sprite != null) image(sprite.sourcePath);
            }
        }
        List<BufferedImage> frames = new ArrayList<>();
        for (int frame = 0; frame < 12; frame++) frames.add(review(frame / 12f));
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("equipment-mounts-contact.png", frames.get(3)));
        artifacts.add(SnapshotArtifact.colorAnimation("equipment-mounts-motion.gif", frames, 100));
        for (int frame = 0; frame < frames.size(); frame++) {
            artifacts.add(new SnapshotArtifact("frames/pose-%02d.png".formatted(frame), frames.get(frame)));
        }
        // The comms radio's pack is persistent hardware and its handset is a calling-only layer.
        List<BufferedImage> radioFrames = new ArrayList<>();
        for (int frame = 0; frame < 20; frame++) radioFrames.add(radioReview(frame / 20f));
        artifacts.add(new SnapshotArtifact("equipment-mounts-radio-contact.png", radioFrames.get(10)));
        artifacts.add(SnapshotArtifact.colorAnimation("equipment-mounts-radio-motion.gif", radioFrames, 100));
        for (int frame = 0; frame < radioFrames.size(); frame++) {
            artifacts.add(new SnapshotArtifact("frames/radio-%02d.png".formatted(frame), radioFrames.get(frame)));
        }
        return artifacts;
    }

    private BufferedImage review(float phase) {
        BufferedImage output = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        text(g, "EQUIPPED SUIT HARDWARE / LEFT PAULDRON", 40, 48, 28);
        text(g, "1920 × 1080 · 100% UI scale · catalog equipment · production infantry compositor", 40, 79, 17);
        text(g, "Enlarged samples · matching primary rifle · idle equipment remains fitted", 40, 125, 20);
        for (int column = 0; column < ARMOR_IDS.length; column++) {
            int x = 250 + column * 465;
            actor(g, ARMOR_IDS[column], x, 270, 160f, "idle", phase, 0f, 0f, null);
            text(g, LABELS[column], x - 155, 430, 21);
            text(g, column < 2 ? "Integral micro-missile pod"
                    : column == 2 ? "Integral screen emitter" : "No authored hardware layer", x - 155, 459, 16);
        }
        divider(g, 493);
        text(g, "Enlarged articulation and clearance · fitted hardware alongside rifle and special use", 40, 528, 20);
        actor(g, ARMOR_IDS[0], 250, 651, 130f, "walking", phase, 0f, 0f, null);
        actor(g, ARMOR_IDS[1], 715, 651, 130f, "firing", phase, 0f, 0f, null);
        actor(g, ARMOR_IDS[2], 1180, 651, 130f, "aiming", phase,
                55f * (float) Math.sin(phase * Math.PI * 2), 0f, null);
        actor(g, ARMOR_IDS[2], 1645, 651, 130f, "rocket-fire", phase, 0f, 0f,
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID));
        text(g, "Aegis / walking", 95, 786, 19);
        text(g, "Arbalest / primary firing", 560, 786, 19);
        text(g, "Specter / independent head look", 1025, 786, 19);
        text(g, "Specter / launcher bracing", 1490, 786, 19);
        divider(g, 820);
        float normalSw = UnitRenderService.layeredInfantryShoulderWidth(17.142857f, UnitType.MARINE.renderScale);
        text(g, "Normal world-scale samples · 17.142857px cells · %.1fpx shoulder width".formatted(normalSw), 40, 860, 20);
        for (int column = 0; column < ARMOR_IDS.length; column++) {
            for (int sample = 0; sample < 3; sample++) {
                actor(g, ARMOR_IDS[column], 140 + column * 465 + sample * 112, 927,
                        normalSw, "walking", phase + sample / 3f, 0f, sample * 90f, null);
            }
            text(g, LABELS[column], 95 + column * 465, 995, 17);
        }
        text(g, "Mounts use their actual suit registration; activity and ammunition do not hide fitted hardware.", 40, 1048, 17);
        g.dispose();
        return output;
    }

    private BufferedImage radioReview(float phase) {
        BufferedImage output = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x111920));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        SpecialEquipmentDef radio = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.COMMS_RADIO_ID);
        text(g, "COMMS RADIO / WORN PACK AND HAND-HELD HANDSET", 40, 48, 28);
        text(g, "1920 × 1080 · 100% UI scale · catalog equipment · production infantry compositor", 40, 79, 17);
        text(g, "Enlarged · the pack rides the back in every pose; the handset and its cord exist only while calling", 40, 125, 20);
        String[] poses = {"idle", "walking", "firing", "calling", "calling"};
        String[] captions = {"Idle", "Walking", "Primary firing", "Calling (animated)", "Calling / on the net"};
        for (int column = 0; column < poses.length; column++) {
            int x = 190 + column * 385;
            float shown = column == 4 ? 0.5f : phase;
            actor(g, ARMOR_IDS[3], x, 270, 160f, poses[column], shown, 0f, 0f, radio);
            text(g, captions[column], x - 110, 430, 19);
        }
        divider(g, 470);
        text(g, "Armor families · idle pack beside the same marine raising and holding the handset", 40, 505, 20);
        for (int column = 0; column < ARMOR_IDS.length; column++) {
            int x = 140 + column * 465;
            actor(g, ARMOR_IDS[column], x, 620, 125f, "idle", phase, 0f, 0f, radio);
            actor(g, ARMOR_IDS[column], x + 210, 620, 125f, "calling", phase, 0f, 0f, radio);
            text(g, LABELS[column], x - 60, 725, 18);
        }
        divider(g, 780);
        float normalSw = UnitRenderService.layeredInfantryShoulderWidth(17.142857f, UnitType.MARINE.renderScale);
        text(g, "Normal world-scale samples · 17.142857px cells · %.1fpx shoulder width · idle / walking / calling at three headings"
                .formatted(normalSw), 40, 815, 20);
        String[] normalPoses = {"idle", "walking", "calling"};
        for (int column = 0; column < ARMOR_IDS.length; column++) {
            for (int sample = 0; sample < 3; sample++) {
                actor(g, ARMOR_IDS[column], 150 + column * 465 + sample * 112, 905,
                        normalSw, normalPoses[sample], sample == 2 ? 0.5f : phase + sample / 3f,
                        0f, sample * 70f, radio);
            }
            text(g, LABELS[column], 95 + column * 465, 975, 17);
        }
        text(g, "Handset order: over the helmet; cord order: over the pack, under the helmet.", 40, 1048, 17);
        g.dispose();
        return output;
    }

    private void actor(Graphics2D g, String armorId, float x, float y, float sw,
                       String animation, float phase, float headLook, float facing,
                       SpecialEquipmentDef special) {
        MarineArmorCatalogDef armor = MarineArmorCatalogRegistry.require(armorId);
        LayeredUnitAssets layered = assets.layered(armor.appearanceFamily());
        UnitLayerLayouts layouts = assets.unitLayerLayouts();
        boolean rocket = animation.equals("rocket-fire");
        boolean calling = animation.equals("calling");
        LayerPose pose = calling
                ? UnitRenderService.infantryPoseDef(layouts, true, special,
                        LayeredAppearance.POSE_SATCHEL_PLANT, phase, phase, 0)
                : layouts.clip("marine-line", rocket ? "rocket" : "rifle",
                        rocket ? "firing" : animation)
                .sample(animation.equals("aiming") ? 1f : phase);
        pose = layouts.applyArmorMastering(pose, armor.appearanceFamily(), armor.appearanceFamily());
        int action = calling ? LayeredAppearance.POSE_SATCHEL_PLANT
                : rocket ? LayeredAppearance.POSE_ROCKET_FIRE
                : animation.equals("firing") ? LayeredAppearance.POSE_FIRING
                : animation.equals("aiming") ? LayeredAppearance.POSE_AIMED : LayeredAppearance.POSE_IDLE;
        int flags = animation.equals("walking") ? LayeredAppearance.FLAG_MOVING : 0;
        if ((animation.equals("firing") || rocket) && phase < .25f) {
            flags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
        }
        LayeredUnitComposer.emit((sprite, cx, cy, width, height, angle, red, green, blue, alpha) -> {
            BufferedImage source = images.get(sprite.sourcePath);
            if (source == null) throw new IllegalStateException("Unloaded review sprite " + sprite.sourcePath);
            AffineTransform transform = new AffineTransform();
            transform.translate(cx, HEIGHT - cy);
            transform.rotate(-Math.toRadians(angle));
            transform.scale(width / source.getWidth(), height / source.getHeight());
            transform.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g.drawImage(source, transform, null);
        }, layered, layered.head, WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                true, special, EquipmentGrade.SERVICE, x, HEIGHT - y, sw, facing,
                headLook, phase, phase, action, flags, 1f, pose, armor.integralSystem());
    }

    private BufferedImage image(String path) throws IOException {
        BufferedImage result = images.get(path);
        if (result == null) {
            result = ImageIO.read(modRoot.resolve(path).toFile());
            if (result == null) throw new IOException("Unreadable review sprite " + path);
            images.put(path, result);
        }
        return result;
    }

    private static void divider(Graphics2D g, int y) {
        g.setColor(new Color(0x344755));
        g.drawLine(40, y, WIDTH - 40, y);
    }

    private static void text(Graphics2D g, String value, int x, int y, int size) {
        g.setColor(new Color(0xD3DFE5));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }
}
