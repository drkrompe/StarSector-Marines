package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/** Static true-overhead bay projection of the selected campaign mech. */
public final class MechLabDollCanvas implements CanvasProducer {

    private static final String ROOT = "graphics/battle/mech-modular-topdown/";
    private static final Color BACKGROUND = new Color(0x07, 0x0D, 0x14);
    private static final Color GRID = new Color(0x19, 0x2A, 0x38);
    private static final Color EDGE = new Color(0x38, 0x66, 0x80);
    private static final Color ACCENT = new Color(0x76, 0xB9, 0xD4);
    private static final Color WHITE = Color.WHITE;

    private final Supplier<MechVariant> variant;
    private final Supplier<LayeredMechAssets> assets;

    public MechLabDollCanvas(Supplier<MechVariant> variant,
                             Supplier<LayeredMechAssets> assets) {
        if (variant == null || assets == null) {
            throw new IllegalArgumentException("variant and assets are required");
        }
        this.variant = variant;
        this.assets = assets;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        context.fillRect(0f, 0f, width, height, BACKGROUND);
        drawGrid(context, width, height);
        MechVariant selected = variant.get();
        LayeredMechAssets sprites = assets.get();
        if (selected == null || sprites == null) return;

        float hull = Math.min(width * 0.58f, height * 0.62f);
        float centerX = width * 0.5f;
        float centerY = height * 0.49f;
        float scale = hull / 208f;

        // A static maintenance pose: feet and weapon assemblies remain legible
        // around the chassis instead of reproducing battle animation state.
        sprite(context, sprites.foot, centerX - hull * 0.21f,
                centerY + hull * 0.35f, scale, 0f);
        sprite(context, sprites.foot, centerX + hull * 0.21f,
                centerY + hull * 0.35f, scale, 0f);
        drawArms(context, sprites, selected.arms, centerX, centerY, hull, scale);
        drawPod(context, sprites, selected.leftShoulder,
                centerX - hull * 0.34f, centerY - hull * 0.12f, scale);
        drawPod(context, sprites, selected.rightShoulder,
                centerX + hull * 0.34f, centerY - hull * 0.12f, scale);
        sprite(context, chassis(sprites, selected.chassisAppearance),
                centerX, centerY, scale, 0f);

        float bracket = Math.min(width, height) * 0.06f;
        float left = centerX - hull * 0.61f;
        float right = centerX + hull * 0.61f;
        float top = centerY - hull * 0.58f;
        float bottom = centerY + hull * 0.58f;
        corner(context, left, top, bracket, 1f, 1f);
        corner(context, right, top, bracket, -1f, 1f);
        corner(context, left, bottom, bracket, 1f, -1f);
        corner(context, right, bottom, bracket, -1f, -1f);
        context.line(centerX - 18f, centerY, centerX + 18f, centerY, ACCENT, 1f);
        context.line(centerX, centerY - 18f, centerX, centerY + 18f, ACCENT, 1f);
    }

    private static void drawGrid(CanvasContext context, float width, float height) {
        float step = 32f;
        for (float x = step; x < width; x += step) {
            context.line(x, 0f, x, height, GRID, 1f);
        }
        for (float y = step; y < height; y += step) {
            context.line(0f, y, width, y, GRID, 1f);
        }
        context.strokeRect(1f, 1f, Math.max(0f, width - 2f),
                Math.max(0f, height - 2f), EDGE, 1f);
    }

    private static void drawArms(CanvasContext context, LayeredMechAssets assets,
                                 MechWeaponComponent arms, float x, float y,
                                 float hull, float scale) {
        LayeredSpriteCache sprite = switch (arms.appearanceSelector) {
            case LayeredMechAppearance.ARMS_LINEAR_CANNON -> assets.linearCannon;
            case LayeredMechAppearance.ARMS_HEAVY_CANNON -> assets.heavyCannon;
            default -> assets.chaingunArm;
        };
        if (arms.appearanceSelector == LayeredMechAppearance.ARMS_NOSE_CHAINGUN
                || arms.appearanceSelector == LayeredMechAppearance.ARMS_HEAVY_CANNON) {
            sprite(context, sprite, x, y - hull * 0.31f, scale, 0f);
            return;
        }
        sprite(context, sprite, x - hull * 0.37f, y - hull * 0.28f, scale, 0f);
        sprite(context, sprite, x + hull * 0.37f, y - hull * 0.28f, scale, 0f);
    }

    private static void drawPod(CanvasContext context, LayeredMechAssets assets,
                                MechWeaponComponent component, float x, float y,
                                float scale) {
        if (component == null) return;
        LayeredSpriteCache sprite = component == MechWeaponComponent.SRM_5
                || component == MechWeaponComponent.LRM_5
                ? assets.srmPod : assets.lrmPod;
        sprite(context, sprite, x, y, scale, 0f);
    }

    private static LayeredSpriteCache chassis(LayeredMechAssets assets, int selector) {
        return switch (selector) {
            case LayeredMechAppearance.CHASSIS_SOCKETED -> assets.socketedChassis;
            case LayeredMechAppearance.CHASSIS_HOUND -> assets.houndChassis;
            case LayeredMechAppearance.CHASSIS_SIROCCO -> assets.siroccoChassis;
            default -> assets.chassis;
        };
    }

    private static void sprite(CanvasContext context, LayeredSpriteCache sprite,
                               float x, float y, float scale, float angle) {
        if (sprite == null) return;
        context.sprite(sprite.sourcePath, sprite.sprite, x, y,
                sprite.pxWidth * scale, sprite.pxHeight * scale,
                angle, WHITE);
    }

    private static void corner(CanvasContext context, float x, float y,
                               float length, float xDirection, float yDirection) {
        context.line(x, y, x + length * xDirection, y, ACCENT, 2f);
        context.line(x, y, x, y + length * yDirection, ACCENT, 2f);
    }

    /** Asset identity for snapshot rendering without live Starsector sprites. */
    public static LayeredMechAssets headlessAssets() {
        return new LayeredMechAssets(
                token("chassis.png", 208, 208),
                token("chassis-socketed-variant.png", 208, 208),
                token("chassis-hound.png", 208, 208),
                token("chassis-sirocco.png", 208, 208),
                token("foot.png", 44, 38),
                token("thigh-bone.png", 40, 112),
                token("chaingun-arm.png", 62, 112),
                token("linear-cannon-variant.png", 58, 138),
                token("heavy-cannon.png", 64, 128),
                token("srm-pod.png", 62, 88),
                token("lrm-pod.png", 76, 96),
                LayeredSpriteCache.headless(
                        "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png",
                        48, 48));
    }

    private static LayeredSpriteCache token(String name, int width, int height) {
        return LayeredSpriteCache.headless(ROOT + name, width, height);
    }
}
