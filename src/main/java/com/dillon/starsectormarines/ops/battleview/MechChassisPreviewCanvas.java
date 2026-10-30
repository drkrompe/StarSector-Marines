package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/** Static silhouette of one chassis: weapon-free for a pattern, or wearing a built machine's fitted guns. */
public final class MechChassisPreviewCanvas implements CanvasProducer {

    private static final float LARGEST_RENDER_SCALE = 1.60f;
    private static final Color BACKGROUND = new Color(0x08, 0x12, 0x1B);
    private static final Color PAD = new Color(0x0D, 0x20, 0x2C);
    private static final Color GUIDE = new Color(0x25, 0x4A, 0x5D, 150);

    private final MechVariant variant;
    private final Supplier<LayeredMechAssets> assets;
    private final int arms;
    private final int leftArm;
    private final int rightArm;
    private final int leftShoulder;
    private final int rightShoulder;

    public MechChassisPreviewCanvas(MechVariant variant,
                                    Supplier<LayeredMechAssets> assets) {
        if (variant == null || assets == null) {
            throw new IllegalArgumentException("variant and mech assets are required");
        }
        this.variant = variant;
        this.assets = assets;
        this.arms = LayeredMechAppearance.ARMS_NONE;
        this.leftArm = LayeredMechAppearance.ARMS_NONE;
        this.rightArm = LayeredMechAppearance.ARMS_NONE;
        this.leftShoulder = LayeredMechAppearance.POD_NONE;
        this.rightShoulder = LayeredMechAppearance.POD_NONE;
    }

    /** A built machine as it stands on its pad, every fitted weapon drawn. */
    public MechChassisPreviewCanvas(CampaignMech mech, Supplier<LayeredMechAssets> assets) {
        if (mech == null || assets == null) {
            throw new IllegalArgumentException("mech and mech assets are required");
        }
        this.variant = mech.variant();
        this.assets = assets;
        this.arms = look(mech, MechMountSlot.ARMS);
        this.leftArm = look(mech, MechMountSlot.LEFT_ARM);
        this.rightArm = look(mech, MechMountSlot.RIGHT_ARM);
        this.leftShoulder = look(mech, MechMountSlot.LEFT_SHOULDER);
        this.rightShoulder = look(mech, MechMountSlot.RIGHT_SHOULDER);
    }

    private static int look(CampaignMech mech, MechMountSlot slot) {
        MechWeaponComponent component = mech.weaponAt(slot);
        return component != null ? component.appearanceSelector(slot) : 0;
    }

    @Override
    public void draw(CanvasContext context) {
        LayeredMechAssets sprites = assets.get();
        if (sprites == null) return;

        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float inset = Math.max(5f, Math.min(width, height) * 0.055f);
        context.fillRect(0f, 0f, width, height, BACKGROUND);
        context.fillRect(inset, inset, width - inset * 2f, height - inset * 2f, PAD);
        context.line(width * 0.5f, inset, width * 0.5f, height - inset,
                GUIDE, Math.max(1f, width * 0.006f));
        context.line(inset, height * 0.5f, width - inset, height * 0.5f,
                GUIDE, Math.max(1f, width * 0.006f));

        float largestHull = Math.min(width * 0.78f, height * 0.68f);
        drawAt(context, width * 0.5f, height * 0.5f, largestHull * variant.renderScale / LARGEST_RENDER_SCALE, 1f);
    }

    /** Draws the machine standing nose-up at a canvas point (y down), at the given hull width. */
    void drawAt(CanvasContext context, float centerX, float centerY, float hullWidth, float alpha) {
        LayeredMechAssets sprites = assets.get();
        if (sprites == null) return;
        float height = context.metrics().surfaceHeight();
        LayeredMechComposer.emit(new CanvasSink(context, height), sprites,
                centerX, height - centerY, hullWidth,
                0f, 0f, 0f, 0f, 0f, 0f, 0,
                variant.chassisAppearance,
                arms, leftArm, rightArm, leftShoulder, rightShoulder,
                alpha);
    }

    private record CanvasSink(CanvasContext context, float surfaceHeight)
            implements LayeredMechComposer.Sink {
        @Override
        public void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                           float width, float height, float angleDegrees, float alpha) {
            context.sprite(sprite.sourcePath, sprite.sprite,
                    centerX, surfaceHeight - centerY, width, height,
                    angleDegrees, new Color(1f, 1f, 1f, alpha));
        }
    }
}
