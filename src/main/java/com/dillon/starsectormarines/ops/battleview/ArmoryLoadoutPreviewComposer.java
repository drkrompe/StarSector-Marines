package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialUsePose;

import java.awt.Color;
import java.util.List;

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

    /** Portrait equipment mannequin used four-up for a complete fire-team template. */
    public static void composeMannequin(Sink sink, Assets assets, FireTeamBillet billet,
                                        float width, float height) {
        if (sink == null || assets == null) {
            throw new IllegalArgumentException("preview sink and assets are required");
        }
        if (billet == null || width <= 0f || height <= 0f) return;

        sink.fillRect(0f, 0f, width, height, BACKGROUND);
        float socketSize = Math.max(34f, Math.min(width * 0.24f, height * 0.19f));
        float socketX = 10f;
        float usable = Math.max(0f, height - socketSize - 20f);
        float[] socketY = {10f, 10f + usable * 0.5f, 10f + usable};

        LayeredUnitAssets layered = assets.layered(billet.armorDef().appearanceFamily());
        LayeredSpriteCache armor = assets.icon(billet.armorDef().iconPath());
        LayeredSpriteCache primary = layered != null
                ? layered.weapon(LayeredWeaponFamily.fromPrimary(billet.primaryDef()), billet.grade())
                : null;
        SpecialEquipmentDef special = billet.specialDef();
        LayeredSpriteCache specialIcon = special != null
                ? assets.icon(special.armoryIconPath()) : null;
        drawSocket(sink, armor, socketX, socketY[0], socketSize, socketSize);
        drawSocket(sink, primary, socketX, socketY[1], socketSize, socketSize);
        drawSocket(sink, specialIcon, socketX, socketY[2], socketSize, socketSize);

        float actorLeft = socketX + socketSize + 8f;
        float actorWidth = Math.max(1f, width - actorLeft - 8f);
        sink.line(actorLeft, height - 16f, width - 8f, height - 16f, ACCENT, 2f);
        drawSoldier(sink, assets, billet, actorLeft + actorWidth * 0.52f,
                height * 0.48f, Math.min(height * 0.48f, actorWidth * 0.78f), height, 0f);
    }

    /** Soldier-first portrait for the selected fire team's named marine cards. */
    public static void composeMarinePortrait(Sink sink, Assets assets, FireTeamBillet billet,
                                             float width, float height) {
        composeMarinePortrait(sink, assets, billet, width, height, 0f);
    }

    /** Soldier-first portrait at one elapsed point in the authored idle loop. */
    public static void composeMarinePortrait(Sink sink, Assets assets, FireTeamBillet billet,
                                             float width, float height, float idleSeconds) {
        if (sink == null || assets == null) {
            throw new IllegalArgumentException("preview sink and assets are required");
        }
        if (billet == null || width <= 0f || height <= 0f) return;

        sink.fillRect(0f, 0f, width, height, BACKGROUND);
        sink.line(12f, height - 14f, width - 12f, height - 14f, ACCENT, 2f);
        drawSoldier(sink, assets, billet, width * 0.5f, height * 0.48f,
                Math.min(height * 0.50f, width * 0.44f), height, idleSeconds);
    }

    /** Compact at-a-glance composition of all four template billets. */
    public static void composeFireTeam(Sink sink, Assets assets,
                                       List<FireTeamBillet> billets,
                                       float width, float height) {
        if (sink == null || assets == null) {
            throw new IllegalArgumentException("preview sink and assets are required");
        }
        if (billets == null || billets.isEmpty() || width <= 0f || height <= 0f) return;

        sink.fillRect(0f, 0f, width, height, BACKGROUND);
        float margin = 8f;
        float baseline = height - 12f;
        sink.line(margin, baseline, width - margin, baseline, ACCENT, 2f);
        float cellWidth = width / MarineSquad.TEAM_SIZE;
        int count = Math.min(MarineSquad.TEAM_SIZE, billets.size());
        for (int index = 0; index < count; index++) {
            float actorX = cellWidth * (index + 0.5f);
            float shoulder = Math.min(height * 0.39f, cellWidth * 0.58f);
            if (index > 0) {
                float divider = cellWidth * index;
                sink.line(divider, 9f, divider, baseline - 5f, EDGE, 1f);
            }
            drawSoldier(sink, assets, billets.get(index), actorX,
                    height * 0.48f, shoulder, height, 0f);
        }
    }

    private static void drawEquipmentDoll(Sink sink, Assets assets,
                                          FireTeamBillet billet,
                                          float divider, float height) {
        float gap = 10f;
        float margin = 14f;
        float socketWidth = (divider - margin * 2f - gap * 2f) / 3f;
        float socketHeight = Math.max(36f, height - margin * 2f);

        LayeredUnitAssets layered = assets.layered(billet.armorDef().appearanceFamily());
        LayeredSpriteCache armor = assets.icon(billet.armorDef().iconPath());
        LayeredSpriteCache primary = layered != null
                ? layered.weapon(LayeredWeaponFamily.fromPrimary(billet.primaryDef()), billet.grade())
                : null;
        SpecialEquipmentDef special = billet.specialDef();
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
        float shoulderPx = Math.min(height * 0.54f, (width - divider) * 0.48f);
        float actorX = divider + (width - divider) * 0.57f;
        float actorY = height * 0.48f;

        sink.fillRect(divider + 16f, height - 22f,
                width - divider - 32f, 3f, ACCENT);
        drawSoldier(sink, assets, billet, actorX, actorY, shoulderPx, height, 0f);
    }

    private static void drawSoldier(Sink sink, Assets assets, FireTeamBillet billet,
                                    float actorX, float actorY, float shoulderPx,
                                    float surfaceHeight, float idleSeconds) {
        LayeredUnitAssets layered = assets.layered(billet.armorDef().appearanceFamily());
        if (layered == null) return;
        SpecialEquipmentDef special = billet.specialDef();
        SpecialEquipmentPresentationDef.Preview preview = special != null
                ? special.presentation().preview() : null;
        float phase = preview != null ? preview.phase() : 1f;
        int pose = poseForDef(special, preview);
        LayerPose authoredPose = pose == LayeredAppearance.POSE_IDLE
                ? idlePose(assets.unitLayerLayouts(), idleSeconds)
                : UnitRenderService.infantryPoseDef(
                assets.unitLayerLayouts(), true, special, pose, 0f, phase, 0);
        LayeredUnitComposer.emit(
                (layer, centerX, centerY, spriteWidth, spriteHeight, angle,
                 red, green, blue, alpha) -> sink.sprite(layer, centerX,
                        surfaceHeight - centerY, spriteWidth, spriteHeight, angle,
                        color(red, green, blue, alpha)),
                layered, layered.head, billet.primaryDef(), true, special, billet.grade(),
                actorX, actorY, shoulderPx, 0f, 0f, 0f,
                phase, pose, 0, 1f, authoredPose);
    }

    static LayerPose idlePose(UnitLayerLayouts layouts, float elapsedSeconds) {
        UnitLayerLayouts.AnimationClip clip = layouts != null
                ? layouts.clip("marine-line", "rifle", "idle") : null;
        if (clip == null) return null;
        float phase = Math.max(0f, elapsedSeconds) * 1000f / clip.totalDurationMs();
        return clip.sample(phase);
    }

    public static int poseForDef(SpecialEquipmentDef special,
                                 SpecialEquipmentPresentationDef.Preview preview) {
        if (special == null || preview == null || !"using".equals(preview.state())) {
            return LayeredAppearance.POSE_IDLE;
        }
        SpecialUsePose usePose = special.presentation().usePose();
        return switch (usePose) {
            case SHOULDER_LAUNCHER -> LayeredAppearance.POSE_ROCKET_AIM;
            case BRACED_RIFLE -> LayeredAppearance.POSE_AMR_AIM;
            case THROW -> LayeredAppearance.POSE_SMOKE_THROW;
            case PLANT -> LayeredAppearance.POSE_SATCHEL_PLANT;
        };
    }

    /** Compatibility bridge for built-in armor callers and older preview tests. */
    public static LayeredArmorFamily armorFamily(MarineArmorPattern armor) {
        return armor != null ? armor.layeredFamily() : LayeredArmorFamily.ARMORLESS;
    }

    private static Color color(float red, float green, float blue, float alpha) {
        return new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public interface Assets {
        LayeredUnitAssets layered(LayeredArmorFamily armor);
        default LayeredUnitAssets layered(MarineArmorPattern armor) {
            return layered(armorFamily(armor));
        }
        LayeredSpriteCache icon(String path);

        default UnitLayerLayouts unitLayerLayouts() {
            return UnitLayerLayouts.get();
        }
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
