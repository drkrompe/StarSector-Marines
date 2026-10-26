package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechCarriedWear;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.HullTurretSection;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Function;

/**
 * The damage doll: one machine's line-art outline seen from the front and from behind, side by
 * side and captioned {@code R FRONT L} / {@code L REAR R}, with each location filled in the colour
 * of its state, the way MechWarrior reads damage. A side's state shows on the parts at that edge of
 * both views. Green is whole;
 * worn armor runs to yellow; a location whose armor is gone shows orange, running to red as its
 * structure goes; a destroyed location is dark red; a location the machine does not have is
 * outline only. The drawings are {@code mod/graphics/ui/dolls/*.svg}, one per silhouette, with
 * each location a {@code part-*} group. See {@code damage-doll.md}.
 *
 * <p>Reads a mutable {@link View} the host refreshes in place.
 */
public final class DamageDollCanvas implements CanvasProducer {

    /** The canvas surface: the doll drawings' 240x170 viewBox at the size the HUD shows them. */
    public static final int SURFACE_WIDTH = 166;
    public static final int SURFACE_HEIGHT = 118;
    private static final float VIEWBOX_WIDTH = 240f;
    private static final float FRONT_CENTRE = 60f;
    private static final float BACK_CENTRE = 180f;
    private static final BitmapFont CAPTION_FONT = Fonts.ORBITRON_10;
    private static final Color CAPTION = new Color(0x8C, 0x99, 0x93);
    static final String DOLL_DIR = "graphics/ui/dolls/";

    private static final int LOCATIONS = DollLocation.values().length;
    private static final int STEPS = 16;
    private static final DollLocation[] PLATES = {
            DollLocation.FRONT, DollLocation.LEFT, DollLocation.RIGHT, DollLocation.REAR};

    static final Color INTACT = new Color(0x6F, 0xD0, 0x8A);
    static final Color ARMOR_LOW = new Color(0xE3, 0xC0, 0x4E);
    static final Color STRUCTURE_HIGH = new Color(0xF0, 0x8A, 0x3A);
    static final Color STRUCTURE_LOW = new Color(0xE0, 0x45, 0x3A);
    static final Color DESTROYED = new Color(0x6A, 0x1A, 0x16);

    /** Armor ramp, index 0 = no armor left, STEPS = whole; likewise the structure ramp. */
    private static final Color[] ARMOR_RAMP = ramp(ARMOR_LOW, INTACT);
    private static final Color[] STRUCTURE_RAMP = ramp(STRUCTURE_LOW, STRUCTURE_HIGH);

    private final View view;
    private final Function<String, Color> partColor;
    private final String frontCaption;
    private final String rearCaption;

    /**
     * @param copy resolves a strings key, read once here for the captions
     */
    public DamageDollCanvas(View view, Function<String, String> copy) {
        this.view = Objects.requireNonNull(view, "view");
        this.partColor = this::colorOf;
        String left = copy.apply("battleDamageDollLeft");
        String right = copy.apply("battleDamageDollRight");
        frontCaption = right + "  " + copy.apply("battleDamageDollFront") + "  " + left;
        rearCaption = left + "  " + copy.apply("battleDamageDollRear") + "  " + right;
    }

    private DamageDollCanvas(View view) {
        this.view = Objects.requireNonNull(view, "view");
        this.partColor = this::colorOf;
        frontCaption = null;
        rearCaption = null;
    }

    /** A captionless doll that fills whatever canvas it is given: a small picture whose words live in a hover. */
    public static DamageDollCanvas bare(View view) {
        return new DamageDollCanvas(view);
    }

    public View view() {
        return view;
    }

    @Override
    public void draw(CanvasContext context) {
        if (!view.present || view.asset == null) return;
        if (frontCaption == null) {
            view.asset.drawParts(context, 0f, 0f, context.metrics().surfaceWidth(),
                    context.metrics().surfaceHeight(), partColor);
            return;
        }
        view.asset.drawParts(context, 0f, 0f, SURFACE_WIDTH, SURFACE_HEIGHT, partColor);
        float scale = SURFACE_WIDTH / VIEWBOX_WIDTH;
        float y = SURFACE_HEIGHT - CAPTION_FONT.getLineHeight() - 1f;
        caption(context, frontCaption, FRONT_CENTRE * scale, y);
        caption(context, rearCaption, BACK_CENTRE * scale, y);
    }

    private static void caption(CanvasContext context, String text, float centreX, float y) {
        context.text(CAPTION_FONT, text, centreX - CAPTION_FONT.measureWidth(text) * .5f, y, CAPTION);
    }

    private Color colorOf(String part) {
        DollLocation location = DollLocation.ofPart(part);
        return location == null ? null : view.color(location.ordinal());
    }

    /** The colour of one location's state; null when the machine does not have it. */
    static Color color(boolean fitted, boolean destroyed, boolean hasArmor, float armor, float structure) {
        if (!fitted) return null;
        if (destroyed) return DESTROYED;
        if (hasArmor && armor > 0.005f) {
            if (structure >= 0.995f) return ARMOR_RAMP[step(armor)];
            return STRUCTURE_RAMP[step(structure)];
        }
        // Armor gone (or never had any): the structure ramp, orange whole to red nearly gone.
        // A structure-only location (hull, drive) that is still whole reads as intact instead.
        if (!hasArmor && structure >= 0.995f) return INTACT;
        if (!hasArmor && structure >= 0.5f) return ARMOR_RAMP[step((structure - 0.5f) * 2f)];
        return STRUCTURE_RAMP[step(hasArmor ? structure : structure * 2f)];
    }

    /** The structure ramp at {@code fraction}: red at none left, orange whole. */
    static Color structureColor(float fraction) {
        return STRUCTURE_RAMP[step(fraction)];
    }

    private static int step(float fraction) {
        return Math.round(Math.max(0f, Math.min(1f, fraction)) * STEPS);
    }

    private static Color[] ramp(Color from, Color to) {
        Color[] ramp = new Color[STEPS + 1];
        for (int i = 0; i <= STEPS; i++) {
            float t = i / (float) STEPS;
            ramp[i] = new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                    Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                    Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
        }
        return ramp;
    }

    /** The doll a mech chassis draws as. */
    public static String dollPath(MechVariant variant) {
        return DOLL_DIR + "mech-" + variant.id + ".svg";
    }

    /** The doll a vehicle line draws as; variants of a family share one. */
    public static String dollPath(VehicleType type) {
        String name = switch (type) {
            case HEAVY_APC, UP_ARMORED_APC, FIRE_SUPPORT_APC, ESCORT_APC -> "apc";
            case BREAKTHROUGH_TANK, LASER_TANK, PD_LASER_TANK -> "tank";
            case SCREENED_TANK -> "tank-screened";
            case DOZER_BREACHER -> "tank-dozer";
            case MILITIA_TECHNICAL -> "technical";
            case SRM_TECHNICAL -> "technical-srm";
            case SUPPLY_TRUCK -> "truck";
            case SCOUT_CAR -> "scout-car";
            case HOVER_SCOUT -> "hover-scout";
            case DRONE_SKIMMER -> "drone-skimmer";
            case LRM_CARRIER -> "lrm-carrier";
            case CARGO_CARRIER -> "hauler";
        };
        return DOLL_DIR + name + ".svg";
    }

    /**
     * What the doll shows: which drawing, and per location whether the machine has it, its armor
     * and structure as fractions of its own pool, and whether it was destroyed; plus the flags.
     * Mutable and refreshed in place.
     */
    public static final class View {
        private boolean present;
        private String path;
        private SvgAsset asset;
        private final boolean[] fitted = new boolean[LOCATIONS];
        private final boolean[] hasArmor = new boolean[LOCATIONS];
        private final float[] armor = new float[LOCATIONS];
        private final float[] structure = new float[LOCATIONS];
        private final boolean[] destroyed = new boolean[LOCATIONS];
        private boolean crippled;
        private boolean sensorsLost;
        private boolean immobile;

        /** Shows {@code loadout}: its sections once hit, every fitted section whole before. */
        public void read(MechLoadoutComponent loadout) {
            if (loadout == null) {
                clear();
                return;
            }
            begin(dollPath(loadout.variant));
            MechSections sections = loadout.sections();
            for (MechSection section : MechSection.values()) {
                int i = DollLocation.of(section).ordinal();
                if (sections != null) {
                    set(i, sections.fitted(section), true,
                            fraction(sections.armor(section), sections.maxArmor(section)),
                            fraction(sections.structure(section), sections.maxStructure(section)),
                            sections.destroyed(section));
                } else {
                    MechMountSlot slot = section.mount();
                    set(i, slot == null || loadout.mounts()[slot.ordinal()] != null, true, 1f, 1f, false);
                }
            }
            // The back plate: its own armor in front of the torso's one structure pool. Under the control,
            // or before the chassis has sections, it reads as the torso does.
            int torso = DollLocation.TORSO.ordinal();
            float rear = sections != null && sections.maxRearArmor() > 0f
                    ? fraction(sections.rearArmor(), sections.maxRearArmor()) : armor[torso];
            set(DollLocation.TORSO_REAR.ordinal(), true, true, rear, structure[torso], destroyed[torso]);
            crippled = sections != null && sections.crippled();
            sensorsLost = sections != null && sections.sensorsLost();
        }

        /**
         * Shows a mech as it would drop: every fitted section whole, less the wear an unfinished repair
         * still leaves on it ({@link MechCarriedWear}). An empty mount reads as the battle reads it,
         * outline only.
         */
        public void readDeployment(MechDeploymentSpec mech) {
            if (mech == null) {
                clear();
                return;
            }
            begin(dollPath(mech.variant()));
            MechCarriedWear wear = mech.wear();
            for (MechSection section : MechSection.values()) {
                MechMountSlot slot = section.mount();
                float structureLost = wear.structureLost(section);
                set(DollLocation.of(section).ordinal(), slot == null || mech.weaponAt(slot) != null, true,
                        1f - wear.armorLost(section), 1f - structureLost, structureLost >= 1f);
            }
            int torso = DollLocation.TORSO.ordinal();
            set(DollLocation.TORSO_REAR.ordinal(), true, true, armor[torso], structure[torso], destroyed[torso]);
            crippled = false;
            sensorsLost = false;
            immobile = false;
        }

        /** Shows a vehicle whole: the bay keeps no damage on one. */
        public void readStock(VehicleType type) {
            begin(dollPath(type));
            for (DollLocation location : DollLocation.values()) set(location.ordinal(), true, false, 0f, 1f, false);
            crippled = false;
            sensorsLost = false;
            immobile = false;
        }

        /**
         * Shows a vehicle: each plate, the drive and the hull from their own pools ({@link HullLocations}), and its
         * turret section (null for a hull whose gun cannot be shot away). A plate is armor alone: its colour never
         * reads the hull's structure, which is the hull's own location.
         */
        public void readVehicle(VehicleType type, float hp, float maxHp, HullTurretSection turret,
                                HullLocations locations) {
            begin(dollPath(type));
            boolean dead = hp <= 0f;
            for (HullLocations.Plate plate : HullLocations.Plate.values()) {
                float max = locations.maxArmor(plate);
                set(PLATES[plate.ordinal()].ordinal(), true, max > 0f, fraction(locations.armor(plate), max), 1f, dead);
            }
            float turretStructure = turret != null ? turret.structure() : 0f;
            float turretMax = turret != null ? turret.maxStructure() : 0f;
            float hull = fraction(hp - turretStructure - locations.drive(),
                    maxHp - turretMax - locations.maxDrive());
            set(DollLocation.HULL.ordinal(), true, false, 0f, hull, dead);
            set(DollLocation.DRIVE.ordinal(), true, false, 0f, fraction(locations.drive(), locations.maxDrive()),
                    locations.immobilised());
            readTurret(turret, hull, dead);
            immobile = locations.immobilised();
            crippled = false;
            sensorsLost = false;
        }

        private void readTurret(HullTurretSection turret, float hull, boolean dead) {
            if (turret != null) {
                set(DollLocation.TURRET.ordinal(), true, turret.maxArmor() > 0f,
                        fraction(turret.armor(), turret.maxArmor()),
                        fraction(turret.structure(), turret.maxStructure()), turret.destroyed());
            } else {
                // A gun that cannot be shot away on its own shares the hull's fate.
                set(DollLocation.TURRET.ordinal(), true, false, 0f, hull, dead);
            }
        }

        /** Writes one location directly; fixtures and future located-armor readers use this. */
        public void set(DollLocation location, boolean fitted, boolean hasArmor, float armor, float structure,
                        boolean destroyed) {
            set(location.ordinal(), fitted, hasArmor, armor, structure, destroyed);
        }

        public void flags(boolean crippled, boolean sensorsLost) {
            this.crippled = crippled;
            this.sensorsLost = sensorsLost;
        }

        /** Starts a fresh doll drawing {@code dollPath}, every location unfitted until set. */
        public void begin(String dollPath) {
            present = true;
            if (!dollPath.equals(path)) {
                path = dollPath;
                asset = SvgAsset.load(dollPath);
            }
            for (int i = 0; i < LOCATIONS; i++) set(i, false, false, 0f, 0f, false);
        }

        public void clear() {
            present = false;
            immobile = false;
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

        /** Whether a vehicle's drive is gone. */
        public boolean immobile() {
            return immobile;
        }

        Color color(int i) {
            return DamageDollCanvas.color(fitted[i], destroyed[i], hasArmor[i], armor[i], structure[i]);
        }

        private void set(int i, boolean fitted, boolean hasArmor, float armor, float structure, boolean destroyed) {
            this.fitted[i] = fitted;
            this.hasArmor[i] = hasArmor;
            this.armor[i] = armor;
            this.structure[i] = structure;
            this.destroyed[i] = destroyed;
        }

        private static float fraction(float value, float max) {
            return max > 0f ? Math.max(0f, Math.min(1f, value / max)) : 0f;
        }
    }
}
