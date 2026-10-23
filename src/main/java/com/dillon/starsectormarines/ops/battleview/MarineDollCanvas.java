package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.WoundService;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Function;

/**
 * A controlled Marine's doll: one front figure, the counterpart of a machine's {@link DamageDollCanvas}
 * in the same line-art style and colours. A person has one armor pool and one health pool, so the head
 * and torso read those together, green whole through yellow as armor wears to orange and red as health
 * goes. The arms and legs are the only parts the simulation keeps wounds on ({@link WoundService}), and
 * they read those alone: green unwounded, orange at the first wound, running to red as wounds stack to
 * the floor. A limb never reads the body's pools, so a hurt Marine with sound legs still shows them sound. A power suit draws as
 * the broader shell. The drawings are {@code mod/graphics/ui/dolls/marine*.svg}.
 */
public final class MarineDollCanvas implements CanvasProducer {

    /** The canvas surface; the figure keeps its own aspect inside it. */
    public static final int SURFACE_WIDTH = 52;
    public static final int SURFACE_HEIGHT = 118;
    static final String MARINE = DamageDollCanvas.DOLL_DIR + "marine.svg";
    static final String POWER_SUIT = DamageDollCanvas.DOLL_DIR + "marine-suit.svg";

    private final View view;
    private final Function<String, Color> partColor;

    public MarineDollCanvas(View view) {
        this.view = Objects.requireNonNull(view, "view");
        this.partColor = this::colorOf;
    }

    @Override
    public void draw(CanvasContext context) {
        if (!view.present || view.asset == null) return;
        view.asset.drawParts(context, 0f, 0f, SURFACE_WIDTH, SURFACE_HEIGHT, partColor);
    }

    private Color colorOf(String part) {
        DollLocation location = DollLocation.ofPart(part);
        if (location == null) return null;
        return switch (location) {
            case ARMS -> view.limb(view.armWounds);
            case LEGS -> view.limb(view.legWounds);
            default -> view.body();
        };
    }

    /**
     * The colour of a limb carrying {@code wounds}: orange at one, red once the stack reaches the
     * wound floor; null when it carries none.
     */
    static Color woundColor(int wounds) {
        if (wounds <= 0) return null;
        float first = WoundService.multiplierFor(1);
        float t = (WoundService.multiplierFor(wounds) - WoundService.WOUND_FLOOR) / (first - WoundService.WOUND_FLOOR);
        return DamageDollCanvas.structureColor(t);
    }

    /** What the figure shows; mutable and refreshed in place. */
    public static final class View {
        private boolean present;
        private boolean powerSuit;
        private SvgAsset asset;
        private boolean hasArmor;
        private float armor;
        private float health;
        private int armWounds;
        private int legWounds;

        /**
         * Shows a Marine: armor and health as fractions of their pools, and the wounds on each limb.
         */
        public void read(boolean powerSuit, boolean hasArmor, float armor, float health, int armWounds, int legWounds) {
            this.present = true;
            if (asset == null || this.powerSuit != powerSuit) asset = SvgAsset.load(powerSuit ? POWER_SUIT : MARINE);
            this.powerSuit = powerSuit;
            this.hasArmor = hasArmor;
            this.armor = armor;
            this.health = health;
            this.armWounds = armWounds;
            this.legWounds = legWounds;
        }

        public void clear() {
            present = false;
        }

        public boolean present() {
            return present;
        }

        Color body() {
            return DamageDollCanvas.color(true, health <= 0f, hasArmor, armor, health);
        }

        /** A limb reads its wounds alone: green unwounded, whatever the body's pools say. */
        Color limb(int wounds) {
            if (health <= 0f) return DamageDollCanvas.DESTROYED;
            Color wound = woundColor(wounds);
            return wound != null ? wound : DamageDollCanvas.INTACT;
        }
    }
}
