package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A bolt-on part a company fits to a vehicle's kit rack (mechs-nouns.md, vehicle kit). A vehicle's
 * gun, plate, drive and bay are sealed; a kit is the one thing that changes, and it changes only the
 * numbers the hull is set down with, so it adds no behaviour of its own.
 *
 * <p>A rack holds one kit per slot ({@link #slots}) and at most one of each kit; a kit may need
 * something of the hull ({@link #suits}).</p>
 */
public enum VehicleKit {
    /** A quarter more armor capacity on every plate and the turret. */
    APPLIQUE_PLATE("kit.applique-plate", "@vehicleKitAppliquePlate", 1.25f, 1f, 1f, 0f),
    /** A fifth more structure. */
    SPALL_LINER("kit.spall-liner", "@vehicleKitSpallLiner", 1f, 1.2f, 1f, 0f),
    /** Half again the rounds aboard the turret at the drop. Armed hulls only. */
    READY_RACK("kit.ready-rack", "@vehicleKitReadyRack", 1f, 1f, 1.5f, 0f),
    /** +2 armor rating. Tracked hulls only. */
    SPACED_SKIRTS("kit.spaced-skirts", "@vehicleKitSpacedSkirts", 1f, 1f, 1f, 2f);

    public final String id;
    private final String label;
    public final float armorFactor;
    public final float structureFactor;
    public final float ammoFactor;
    public final float armorRatingBonus;

    VehicleKit(String id, String label, float armorFactor, float structureFactor, float ammoFactor,
               float armorRatingBonus) {
        this.id = id;
        this.label = label;
        this.armorFactor = armorFactor;
        this.structureFactor = structureFactor;
        this.ammoFactor = ammoFactor;
        this.armorRatingBonus = armorRatingBonus;
    }

    public String displayName() { return Strings.resolve(label); }

    /** Whether the hull has what this kit works on: a magazine for the ready rack, tracks for the skirts. */
    public boolean suits(VehicleType type) {
        if (type == null) return false;
        return switch (this) {
            case READY_RACK -> type.hasTurretWeapon();
            case SPACED_SKIRTS -> type.hasAnimatedTreads();
            case APPLIQUE_PLATE, SPALL_LINER -> true;
        };
    }

    /** Kit slots on a hull: one for a Light hull, two for a Medium, three for a Heavy. */
    public static int slots(VehicleType type) {
        return type == null ? 0 : type.weight().ordinal() + 1;
    }

    /** Whether {@code kits} fit {@code type}'s rack: within its slots, no kit twice, each suiting the hull. */
    public static boolean fits(VehicleType type, List<VehicleKit> kits) {
        if (kits.size() > slots(type)) return false;
        Set<VehicleKit> seen = EnumSet.noneOf(VehicleKit.class);
        for (VehicleKit kit : kits) {
            if (kit == null || !kit.suits(type) || !seen.add(kit)) return false;
        }
        return true;
    }

    public static VehicleKit findById(String id) {
        for (VehicleKit kit : values()) if (kit.id.equals(id)) return kit;
        return null;
    }

    /** What a rack of kits makes of a hull at the drop, every factor multiplied and every bonus added. */
    public record Effect(float armorFactor, float structureFactor, float ammoFactor, float armorRatingBonus) {
        public static final Effect NONE = new Effect(1f, 1f, 1f, 0f);

        public static Effect of(List<VehicleKit> kits) {
            float armor = 1f, structure = 1f, ammo = 1f, rating = 0f;
            for (VehicleKit kit : kits) {
                armor *= kit.armorFactor;
                structure *= kit.structureFactor;
                ammo *= kit.ammoFactor;
                rating += kit.armorRatingBonus;
            }
            return new Effect(armor, structure, ammo, rating);
        }
    }
}
