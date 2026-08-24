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
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Immersive Fleet Armory preview: equipment sockets beside the same layered
 * actor composition used by battlefield infantry.
 */
public final class ArmoryLoadoutPreviewCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x09, 0x10, 0x18);
    private static final Color SOCKET = new Color(0x0E, 0x18, 0x24);
    private static final Color EDGE = new Color(0x40, 0x5A, 0x78);
    private static final Color ACCENT = new Color(0x76, 0xB9, 0xD4);
    private static final Color WHITE = Color.WHITE;

    private final Supplier<FireTeamBillet> selectedBillet;
    private final BattleSprites sprites = new BattleSprites();
    private final Map<String, LayeredSpriteCache> catalogIcons = new LinkedHashMap<>();
    private boolean loadAttempted;

    public ArmoryLoadoutPreviewCanvas(Supplier<FireTeamBillet> selectedBillet) {
        if (selectedBillet == null) throw new IllegalArgumentException("selected billet is required");
        this.selectedBillet = selectedBillet;
    }

    @Override
    public void draw(CanvasContext context) {
        FireTeamBillet billet = selectedBillet.get();
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        if (billet == null || width <= 0f || height <= 0f) return;
        ensureLoaded();

        context.fillRect(0f, 0f, width, height, BACKGROUND);
        float divider = width * 0.57f;
        context.line(divider, 12f, divider, height - 12f, EDGE, 1f);
        drawEquipmentDoll(context, billet, divider, height);
        drawSampleSoldier(context, billet, divider, width, height);
    }

    private void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        sprites.ensureLayeredUnitSprites();
    }

    private void drawEquipmentDoll(CanvasContext context, FireTeamBillet billet,
                                   float divider, float height) {
        float gap = 10f;
        float margin = 14f;
        float socketWidth = (divider - margin * 2f - gap * 2f) / 3f;
        float socketHeight = Math.max(36f, height - margin * 2f);

        LayeredUnitAssets assets = assetsFor(billet.armor());
        LayeredSpriteCache armor = icon(billet.armor().iconPath);
        LayeredSpriteCache primary = assets != null
                ? assets.weapon(LayeredWeaponFamily.fromPrimary(billet.primary()), billet.grade())
                : null;
        SpecialEquipmentDef special = billet.secondary() != null
                ? billet.secondary().specialDef() : null;
        LayeredSpriteCache specialIcon = special != null
                ? icon(special.armoryIconPath()) : null;

        drawSocket(context, armor, margin, margin, socketWidth, socketHeight, 0f);
        drawSocket(context, primary, margin + socketWidth + gap, margin,
                socketWidth, socketHeight, 0f);
        drawSocket(context, specialIcon, margin + (socketWidth + gap) * 2f, margin,
                socketWidth, socketHeight, 0f);
    }

    private static void drawSocket(CanvasContext context, LayeredSpriteCache sprite,
                                   float x, float y, float width, float height,
                                   float angleDegrees) {
        context.fillRect(x, y, width, height, SOCKET);
        context.strokeRect(x, y, width, height, EDGE, 1f);
        if (sprite == null) {
            float inset = Math.min(width, height) * 0.28f;
            context.line(x + inset, y + inset, x + width - inset,
                    y + height - inset, EDGE, 2f);
            context.line(x + width - inset, y + inset, x + inset,
                    y + height - inset, EDGE, 2f);
            return;
        }
        float availableWidth = Math.max(1f, width - 14f);
        float availableHeight = Math.max(1f, height - 14f);
        float scale = Math.min(availableWidth / sprite.pxWidth,
                availableHeight / sprite.pxHeight);
        context.sprite(sprite.sprite, x + width * 0.5f, y + height * 0.5f,
                sprite.pxWidth * scale, sprite.pxHeight * scale,
                angleDegrees, WHITE);
    }

    private void drawSampleSoldier(CanvasContext context, FireTeamBillet billet,
                                   float divider, float width, float height) {
        LayeredUnitAssets assets = assetsFor(billet.armor());
        if (assets == null) return;
        MarineSecondary special = billet.secondary();
        SpecialEquipmentPresentationDef.Preview preview = special != null
                ? special.specialDef().presentation().preview() : null;
        float phase = preview != null ? preview.phase() : 1f;
        int pose = poseFor(special, preview);
        float shoulderPx = Math.min(height * 0.54f, (width - divider) * 0.48f);
        float actorX = divider + (width - divider) * 0.57f;
        float actorY = height * 0.48f;

        context.fillRect(divider + 16f, height - 22f,
                width - divider - 32f, 3f, ACCENT);
        LayeredUnitComposer.emit(
                (sprite, centerX, centerY, spriteWidth, spriteHeight, angle,
                 red, green, blue, alpha) -> context.sprite(sprite, centerX,
                        height - centerY, spriteWidth, spriteHeight, angle,
                        color(red, green, blue, alpha)),
                assets, assets.head, billet.primary(), true, special, billet.grade(),
                actorX, actorY, shoulderPx, 0f, 0f, 0f,
                phase, pose, 0, 1f);
    }

    static int poseFor(MarineSecondary special,
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

    static LayeredArmorFamily armorFamily(MarineArmorPattern armor) {
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

    private LayeredUnitAssets assetsFor(MarineArmorPattern armor) {
        return sprites.layeredUnitSprites().get(armorFamily(armor));
    }

    private LayeredSpriteCache icon(String path) {
        if (path == null) return null;
        return catalogIcons.computeIfAbsent(path, sprites::loadLayeredSprite);
    }

    private static Color color(float red, float green, float blue, float alpha) {
        return new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
