package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Function;

/**
 * The section paper-doll: a schematic of one mech, head on top, the shoulder mounts either side
 * of the torso (the chassis's left on the viewer's left), the arms mount beneath the torso and the
 * legs below. Each section shows its armor and structure as two bars and its state as a colour:
 * intact, armor worn, structure damaged, or destroyed (dark and crossed out). A mount the chassis
 * was deployed without is drawn as an empty outline. See {@code mech-locational-damage.md}.
 *
 * <p>Reads a mutable {@link View} the host refreshes in place, so a frame allocates nothing.
 */
public final class MechSectionDollCanvas implements CanvasProducer {

    public static final int SURFACE_WIDTH = 150;
    public static final int SURFACE_HEIGHT = 124;

    private static final int SECTIONS = MechSection.values().length;
    private static final BitmapFont FONT = Fonts.ORBITRON_10;

    /** Surface rectangles per section ordinal: x, y, width, height. */
    private static final float[][] BOUNDS = new float[SECTIONS][];
    static {
        BOUNDS[MechSection.HEAD.ordinal()] = new float[] {51f, 0f, 48f, 26f};
        BOUNDS[MechSection.LEFT_SHOULDER.ordinal()] = new float[] {0f, 29f, 36f, 36f};
        BOUNDS[MechSection.TORSO.ordinal()] = new float[] {40f, 29f, 70f, 36f};
        BOUNDS[MechSection.RIGHT_SHOULDER.ordinal()] = new float[] {114f, 29f, 36f, 36f};
        BOUNDS[MechSection.ARMS.ordinal()] = new float[] {40f, 68f, 70f, 26f};
        BOUNDS[MechSection.LEGS.ordinal()] = new float[] {40f, 97f, 70f, 26f};
    }

    private static final Color INTACT = new Color(0x6F, 0xB8, 0x8C);
    private static final Color ARMOR_WORN = new Color(0xD9, 0xC4, 0x6A);
    private static final Color STRUCTURE_HIT = new Color(0xEA, 0x8E, 0x55);
    private static final Color DESTROYED = new Color(0xF0, 0x6A, 0x5E);
    private static final Color ABSENT = new Color(0x4A, 0x56, 0x50);
    private static final Color INTACT_FILL = new Color(0x6F, 0xB8, 0x8C, 0x30);
    private static final Color ARMOR_WORN_FILL = new Color(0xD9, 0xC4, 0x6A, 0x30);
    private static final Color STRUCTURE_HIT_FILL = new Color(0xEA, 0x8E, 0x55, 0x38);
    private static final Color DESTROYED_FILL = new Color(0x14, 0x0C, 0x0C, 0xF0);
    private static final Color BAR_TRACK = new Color(0x25, 0x33, 0x44);
    private static final Color ARMOR_BAR = new Color(0xA7, 0xCC, 0xC4);
    private static final Color STRUCTURE_BAR = new Color(0x7C, 0xD8, 0x9C);
    private static final Color STRUCTURE_LOW = new Color(0xF0, 0x7C, 0x72);
    private static final Color LABEL = new Color(0xDC, 0xE6, 0xDE);
    private static final Color LABEL_DIM = new Color(0x8A, 0x6A, 0x66);

    private final View view;
    private final String[] labels = new String[SECTIONS];

    /**
     * @param view the state to draw; refreshed in place by the host
     * @param copy resolves a strings key, read once here
     */
    public MechSectionDollCanvas(View view, Function<String, String> copy) {
        this.view = Objects.requireNonNull(view, "view");
        for (MechSection section : MechSection.values()) {
            labels[section.ordinal()] = copy.apply("battleMechSection" + section.name());
        }
    }

    public View view() {
        return view;
    }

    @Override
    public void draw(CanvasContext context) {
        if (!view.present) return;
        for (int i = 0; i < SECTIONS; i++) drawSection(context, i);
    }

    private void drawSection(CanvasContext context, int i) {
        float[] b = BOUNDS[i];
        float x = b[0], y = b[1], w = b[2], h = b[3];
        if (!view.fitted[i]) {
            context.strokeRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, ABSENT, 1f);
            context.line(x + w * 0.35f, y + h * 0.5f, x + w * 0.65f, y + h * 0.5f, ABSENT, 1f);
            return;
        }
        Color edge;
        Color fill;
        if (view.destroyed[i]) {
            edge = DESTROYED;
            fill = DESTROYED_FILL;
        } else if (view.structure[i] < 0.995f) {
            edge = STRUCTURE_HIT;
            fill = STRUCTURE_HIT_FILL;
        } else if (view.armor[i] < 0.995f) {
            edge = ARMOR_WORN;
            fill = ARMOR_WORN_FILL;
        } else {
            edge = INTACT;
            fill = INTACT_FILL;
        }
        box(context, x, y, w, h, fill, edge);
        String label = labels[i];
        float labelX = x + (w - FONT.measureWidth(label)) * 0.5f;
        if (view.destroyed[i]) {
            context.line(x + 2f, y + 2f, x + w - 2f, y + h - 2f, DESTROYED, 1.5f);
            context.line(x + w - 2f, y + 2f, x + 2f, y + h - 2f, DESTROYED, 1.5f);
            context.text(FONT, label, labelX, y + 2f, LABEL_DIM);
            return;
        }
        context.text(FONT, label, labelX, y + 2f, LABEL);
        float barX = x + 4f;
        float barW = w - 8f;
        float structureY = y + h - 5f;
        float armorY = structureY - 5f;
        bar(context, barX, armorY, barW, view.armor[i], ARMOR_BAR);
        bar(context, barX, structureY, barW, view.structure[i],
                view.structure[i] <= 0.25f ? STRUCTURE_LOW : STRUCTURE_BAR);
    }

    private static void box(CanvasContext context, float x, float y, float w, float h,
                            Color fill, Color edge) {
        context.fillRect(x, y, w, h, fill);
        context.strokeRect(x + 0.5f, y + 0.5f, w - 1f, h - 1f, edge, 1f);
    }

    private static void bar(CanvasContext context, float x, float y, float w, float fraction,
                            Color color) {
        context.fillRect(x, y, w, 3f, BAR_TRACK);
        float filled = w * Math.max(0f, Math.min(1f, fraction));
        if (filled > 0f) context.fillRect(x, y, filled, 3f, color);
    }

    /**
     * What the doll shows: per section whether it exists, its armor and structure as fractions of
     * its own pool, and whether it was destroyed; plus the chassis flags. Mutable and refreshed in
     * place so a frame allocates nothing.
     */
    public static final class View {
        private boolean present;
        private final boolean[] fitted = new boolean[SECTIONS];
        private final float[] armor = new float[SECTIONS];
        private final float[] structure = new float[SECTIONS];
        private final boolean[] destroyed = new boolean[SECTIONS];
        private boolean crippled;
        private boolean sensorsLost;

        /** Shows {@code loadout}: its sections once hit, intact in every fitted section before. */
        public void read(MechLoadoutComponent loadout) {
            if (loadout == null) {
                clear();
                return;
            }
            MechSections sections = loadout.sections();
            if (sections != null) read(sections);
            else intact(loadout.mounts());
        }

        /** Shows a mech nobody has hit: every section whole, an empty mount absent. */
        public void intact(MechWeaponMount[] mounts) {
            present = true;
            for (MechSection section : MechSection.values()) {
                int i = section.ordinal();
                MechMountSlot slot = section.mount();
                fitted[i] = slot == null || mounts[slot.ordinal()] != null;
                armor[i] = 1f;
                structure[i] = 1f;
                destroyed[i] = false;
            }
            crippled = false;
            sensorsLost = false;
        }

        public void read(MechSections sections) {
            present = true;
            for (MechSection section : MechSection.values()) {
                int i = section.ordinal();
                fitted[i] = sections.fitted(section);
                armor[i] = fraction(sections.armor(section), sections.maxArmor(section));
                structure[i] = fraction(sections.structure(section), sections.maxStructure(section));
                destroyed[i] = sections.destroyed(section);
            }
            crippled = sections.crippled();
            sensorsLost = sections.sensorsLost();
        }

        public void clear() {
            present = false;
            crippled = false;
            sensorsLost = false;
        }

        public boolean present() {
            return present;
        }

        public boolean crippled() {
            return crippled;
        }

        public boolean sensorsLost() {
            return sensorsLost;
        }

        private static float fraction(float value, float max) {
            return max > 0f ? Math.max(0f, Math.min(1f, value / max)) : 0f;
        }
    }
}
