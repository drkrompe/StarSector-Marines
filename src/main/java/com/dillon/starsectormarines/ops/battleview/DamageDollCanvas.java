package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.HullTurretSection;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Function;

/**
 * The damage doll: one machine's line-art outline, a side elevation above a top plan, with each
 * location filled in the colour of its state, the way MechWarrior reads damage. Green is whole;
 * worn armor runs to yellow; a location whose armor is gone shows orange, running to red as its
 * structure goes; a destroyed location is dark red; a location the machine does not have is
 * outline only. The drawings are {@code mod/graphics/ui/dolls/*.svg}, one per silhouette, with
 * each location a {@code part-*} group. See {@code damage-doll.md}.
 *
 * <p>Reads a mutable {@link View} the host refreshes in place.
 */
public final class DamageDollCanvas implements CanvasProducer {

    public static final int SURFACE_WIDTH = 240;
    public static final int SURFACE_HEIGHT = 170;
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

    public DamageDollCanvas(View view) {
        this.view = Objects.requireNonNull(view, "view");
        this.partColor = this::colorOf;
    }

    public View view() {
        return view;
    }

    @Override
    public void draw(CanvasContext context) {
        if (!view.present || view.asset == null) return;
        view.asset.drawParts(context, 0f, 0f, SURFACE_WIDTH, SURFACE_HEIGHT, partColor);
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
            case BREAKTHROUGH_TANK -> "tank";
            case HEAVY_GUN_TANK -> "tank-heavy-gun";
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
            // Until the torso splits front and rear, the back plate reads the torso's pool.
            int torso = DollLocation.TORSO.ordinal();
            set(DollLocation.TORSO_REAR.ordinal(), true, true, armor[torso], structure[torso], destroyed[torso]);
            crippled = sections != null && sections.crippled();
            sensorsLost = sections != null && sections.sensorsLost();
        }

        /**
         * Shows a vehicle from its hull pools and its turret section (null for a hull whose gun
         * cannot be shot away). Until located armor lands the four plates read the hull's one
         * armor pool, the hull its structure, and the drive is whole. A plate is armor alone: its
         * colour never reads the hull's structure, which is the hull's own location.
         */
        public void readVehicle(VehicleType type, float hp, float maxHp, float hullArmor, float maxHullArmor,
                                HullTurretSection turret) {
            begin(dollPath(type));
            boolean armored = maxHullArmor > 0f;
            float plate = armored ? fraction(hullArmor, maxHullArmor) : 0f;
            float hull = fraction(hp, maxHp);
            boolean dead = hp <= 0f;
            for (DollLocation location : PLATES) {
                set(location.ordinal(), true, armored, plate, 1f, dead);
            }
            set(DollLocation.HULL.ordinal(), true, false, 0f, hull, dead);
            set(DollLocation.DRIVE.ordinal(), true, false, 0f, 1f, false);
            if (turret != null) {
                set(DollLocation.TURRET.ordinal(), true, turret.maxArmor() > 0f,
                        fraction(turret.armor(), turret.maxArmor()),
                        fraction(turret.structure(), turret.maxStructure()), turret.destroyed());
            } else {
                // A gun that cannot be shot away on its own shares the hull's fate.
                set(DollLocation.TURRET.ordinal(), true, false, 0f, hull, dead);
            }
            crippled = false;
            sensorsLost = false;
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
