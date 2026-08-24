package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialUsePose;

import java.awt.Color;

/** Backend-neutral composition of the Fleet Armory's selected-billet preview. */
public final class ArmoryLoadoutPreviewComposer {

    public static final int SURFACE_WIDTH = 640;
    public static final int SURFACE_HEIGHT = 230;

    private static final Color BACKGROUND = new Color(0x09, 0x10, 0x18);
    private static final Color SOCKET = new Color(0x0E, 0x18, 0x24);
    private static final Color EDGE = new Color(0x40, 0x5A, 0x78);
    private static final Color ACCENT = new Color(0x76, 0xB9, 0xD4);
    private static final Color WHITE = Color.WHITE;

    private ArmoryLoadoutPreviewComposer() {}

    public static void compose(Sink sink, Assets assets, FireTeamBillet billet,
                               float width, float height) {
        if (sink == null || assets == null) {
            throw new IllegalArgumentException("preview sink and assets are required");
        }
        if (billet == null || width <= 0f || height <= 0f) return;

        sink.fillRect(0f, 0f, width, height, BACKGROUND);
        float divider = width * 0.57f;
        sink.line(divider, 12f, divider, height - 12f, EDGE, 1f);
        drawEquipmentDoll(sink, assets, billet, divider, height);
        drawSampleSoldier(sink, assets, billet, divider, width, height);
    }

    private static void drawEquipmentDoll(Sink sink, Assets assets,
                                          FireTeamBillet billet,
                                          float divider, float height) {
        float gap = 10f;
        float margin = 14f;
        float socketWidth = (divider - margin * 2f - gap * 2f) / 3f;
        float socketHeight = Math.max(36f, height - margin * 2f);

        LayeredUnitAssets layered = assets.layered(billet.armor());
        LayeredSpriteCache armor = assets.icon(billet.armor().iconPath);
        LayeredSpriteCache primary = layered != null
                ? layered.weapon(LayeredWeaponFamily.fromPrimary(billet.primary()), billet.grade())
                : null;
        SpecialEquipmentDef special = billet.secondary() != null
                ? billet.secondary().specialDef() : null;
        LayeredSpriteCache specialIcon = special != null
                ? assets.icon(special.armoryIconPath()) : null;

        drawSocket(sink, armor, margin, margin, socketWidth, socketHeight);
        drawSocket(sink, primary, margin + socketWidth + gap, margin,
                socketWidth, socketHeight);
        drawSocket(sink, specialIcon, margin + (socketWidth + gap) * 2f, margin,
                socketWidth, socketHeight);
    }

    private static void drawSocket(Sink sink, LayeredSpriteCache sprite,
                                   float x, float y, float width, float height) {
        sink.fillRect(x, y, width, height, SOCKET);
        sink.strokeRect(x, y, width, height, EDGE, 1f);
        if (sprite == null) {
            float inset = Math.min(width, height) * 0.28f;
            sink.line(x + inset, y + inset, x + width - inset,
                    y + height - inset, EDGE, 2f);
            sink.line(x + width - inset, y + inset, x + inset,
                    y + height - inset, EDGE, 2f);
            return;
        }
        float availableWidth = Math.max(1f, width - 14f);
        float availableHeight = Math.max(1f, height - 14f);
        float scale = Math.min(availableWidth / sprite.pxWidth,
                availableHeight / sprite.pxHeight);
        sink.sprite(sprite, x + width * 0.5f, y + height * 0.5f,
                sprite.pxWidth * scale, sprite.pxHeight * scale, 0f, WHITE);
    }

    private static void drawSampleSoldier(Sink sink, Assets assets,
                                          FireTeamBillet billet,
                                          float divider, float width, float height) {
        LayeredUnitAssets layered = assets.layered(billet.armor());
        if (layered == null) return;
        MarineSecondary special = billet.secondary();
        SpecialEquipmentPresentationDef.Preview preview = special != null
                ? special.specialDef().presentation().preview() : null;
        float phase = preview != null ? preview.phase() : 1f;
        int pose = poseFor(special, preview);
        float shoulderPx = Math.min(height * 0.54f, (width - divider) * 0.48f);
        float actorX = divider + (width - divider) * 0.57f;
        float actorY = height * 0.48f;

        sink.fillRect(divider + 16f, height - 22f,
                width - divider - 32f, 3f, ACCENT);
        LayeredUnitComposer.emit(
                (layer, centerX, centerY, spriteWidth, spriteHeight, angle,
                 red, green, blue, alpha) -> sink.sprite(layer, centerX,
                        height - centerY, spriteWidth, spriteHeight, angle,
                        color(red, green, blue, alpha)),
                layered, layered.head, billet.primary(), true, special, billet.grade(),
                actorX, actorY, shoulderPx, 0f, 0f, 0f,
                phase, pose, 0, 1f);
    }

    public static int poseFor(MarineSecondary special,
                              SpecialEquipmentPresentationDef.Preview preview) {
        if (special == null || preview == null || !"using".equals(preview.state())) {
            return LayeredAppearance.POSE_IDLE;
        }
        SpecialUsePose usePose = special.specialDef().presentation().usePose();
        return switch (usePose) {
            case SHOULDER_LAUNCHER -> LayeredAppearance.POSE_ROCKET_AIM;
            case BRACED_RIFLE -> LayeredAppearance.POSE_AMR_AIM;
            case THROW -> LayeredAppearance.POSE_SMOKE_THROW;
            case PLANT -> LayeredAppearance.POSE_SATCHEL_PLANT;
        };
    }

    public static LayeredArmorFamily armorFamily(MarineArmorPattern armor) {
        if (armor == null) return LayeredArmorFamily.ARMORLESS;
        return switch (armor) {
            case ARMORLESS -> LayeredArmorFamily.ARMORLESS;
            case CHARCOAL -> LayeredArmorFamily.CHARCOAL;
            case BLUE_SCOUT -> LayeredArmorFamily.BLUE_SCOUT;
            case RED_ELITE -> LayeredArmorFamily.RED_ELITE;
            case OUTLAW -> LayeredArmorFamily.OUTLAW;
            case ARMY_GREEN -> LayeredArmorFamily.ARMY_GREEN;
            case MILITIA -> LayeredArmorFamily.MILITIA;
        };
    }

    private static Color color(float red, float green, float blue, float alpha) {
        return new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public interface Assets {
        LayeredUnitAssets layered(MarineArmorPattern armor);
        LayeredSpriteCache icon(String path);
    }

    public interface Sink {
        void fillRect(float x, float y, float width, float height, Color color);
        void strokeRect(float x, float y, float width, float height,
                        Color color, float strokeWidth);
        void line(float x1, float y1, float x2, float y2,
                  Color color, float strokeWidth);
        void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                    float width, float height, float angleDegrees, Color tint);
    }
}
