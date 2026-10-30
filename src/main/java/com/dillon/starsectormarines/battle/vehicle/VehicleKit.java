package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A bolt-on part a company fits to a vehicle's kit rack (mechs-nouns.md, vehicle kit). A vehicle's
 * gun and bay are sealed; its plating and engine have their own points on the doll ({@link VehiclePlating},
 * {@link VehicleEngine}), and a kit is the one rack part, and it changes only what the hull is set down with: its numbers, or a drone bay on a hull that has none ({@link #SENSOR_PAIR}). It adds no behaviour
 * of its own; the bay's drones fly as any bay's do.
 *
 * <p>A kit takes one slot of the rack ({@link #slots}), beside the capacitor bank and vent arrays the rack also
 * holds (a vehicle is a mech for parts: `VehicleRack`); a rack holds at most one of each kit, and a kit may need
 * something of the hull ({@link #suits}).</p>
 */
public enum VehicleKit {
    /** A fifth more structure. */
    SPALL_LINER("kit.spall-liner", "@vehicleKitSpallLiner", 1f, 1.2f, 1f, 0f),
    /** Half again the rounds aboard the turret at the drop. Armed hulls only. */
    READY_RACK("kit.ready-rack", "@vehicleKitReadyRack", 1f, 1f, 1.5f, 0f),
    /**
     * A tethered pair of sensor drones the hull launches and flies ahead of its bow: sight past cover the hull
     * cannot see over, which is what an indirect-fire hull otherwise needs a spotter for. Hulls with no bay of
     * their own.
     */
    SENSOR_PAIR("kit.sensor-pair", "@vehicleKitSensorPair", 1f, 1f, 1f, 0f),
    /**
     * A faster feed on every mount's loader: a pack comes back in three quarters of the stock loader's time, as a
     * mech's accelerated feed does. Armed hulls only.
     */
    ACCELERATED_LOADER("kit.accelerated-loader", "@vehicleKitAcceleratedLoader", 1f, 1f, 1f, 0f, .75f);

    public final String id;
    private final String label;
    public final float armorFactor;
    public final float structureFactor;
    public final float ammoFactor;
    public final float armorRatingBonus;
    /** What the kit multiplies the loader's seconds per pack by: under 1 is a faster feed. */
    public final float loaderFactor;

    VehicleKit(String id, String label, float armorFactor, float structureFactor, float ammoFactor,
               float armorRatingBonus) {
        this(id, label, armorFactor, structureFactor, ammoFactor, armorRatingBonus, 1f);
    }

    VehicleKit(String id, String label, float armorFactor, float structureFactor, float ammoFactor,
               float armorRatingBonus, float loaderFactor) {
        this.id = id;
        this.label = label;
        this.armorFactor = armorFactor;
        this.structureFactor = structureFactor;
        this.ammoFactor = ammoFactor;
        this.armorRatingBonus = armorRatingBonus;
        this.loaderFactor = loaderFactor;
    }

    public String displayName() { return Strings.resolve(label); }

    /** The drones this kit adds to the hull's bay, in launch order; empty for a kit that adds none. */
    public List<DroneRole> droneBay() {
        return this == SENSOR_PAIR ? List.of(DroneRole.SENSOR, DroneRole.SENSOR) : List.of();
    }

    /**
     * Whether the hull has what this kit works on: a magazine for the ready rack or the loader (a belt-fed rotary gun
     * has none), no bay of its own for the sensor drones.
     */
    public boolean suits(VehicleType type) {
        if (type == null) return false;
        return switch (this) {
            case READY_RACK, ACCELERATED_LOADER -> type.hasTurretWeapon() && ownGun(type).mount.ammoCapacity > 0;

            case SENSOR_PAIR -> type.droneBay().isEmpty();
            case SPALL_LINER -> true;
        };
    }

    /** The hull's own gun as it is set down: its turret, or the mech weapon its turret carries ({@link VehicleType#ownGun}). */
    private static StructureDef ownGun(VehicleType type) {
        return VehicleGun.fit(type, type.ownGun());
    }

    /**
     * Slots on a hull's rack, a slot a two-column row: two on a Light hull (one 2x2 bay), three on a Medium (one 2x3
     * bay), four on a Heavy (two 2x2 bays). The capacitor bank and vent arrays take their own rows of it.
     */
    public static int slots(VehicleType type) {
        if (type == null) return 0;
        return switch (type.weight()) {
            case LIGHT -> 2;
            case MEDIUM -> 3;
            case HEAVY -> 4;
        };
    }

    /** The rows the hull's stock capacitor bank takes ({@link HullReservoir#stockFrame}): a military bank is 2x2. */
    public static int stockBankSlots(VehicleType type) {
        HullReservoir.Frame bank = HullReservoir.stockFrame(type);
        return bank == null ? 0 : bank == HullReservoir.MILITARY_BANK ? 2 : 1;
    }

    /**
     * Whether {@code kits} fit {@code type}'s rack beside its stock bank, as a hull the company does not own is set
     * down: within the slots left, no kit twice, each suiting the hull. A company rack packs by `VehicleRack`.
     */
    public static boolean fits(VehicleType type, List<VehicleKit> kits) {
        if (kits.size() > slots(type) - stockBankSlots(type)) return false;
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
    public record Effect(float armorFactor, float structureFactor, float ammoFactor, float armorRatingBonus,
                         float loaderFactor) {
        public static final Effect NONE = new Effect(1f, 1f, 1f, 0f, 1f);

        public static Effect of(List<VehicleKit> kits) {
            return of(kits, VehiclePlating.FACTORY, null);
        }

        /** As above, in the plating the hull wears: its armor factor and, on a tracked hull, its skirts. */
        public static Effect of(List<VehicleKit> kits, VehiclePlating plating, VehicleType type) {
            float armor = 1f, structure = 1f, ammo = 1f, rating = 0f, loader = 1f;
            for (VehicleKit kit : kits) {
                armor *= kit.armorFactor;
                structure *= kit.structureFactor;
                ammo *= kit.ammoFactor;
                rating += kit.armorRatingBonus;
                loader *= kit.loaderFactor;
            }
            if (plating != null) {
                armor *= plating.armorFactor();
                rating += plating.armorRatingBonus(type);
            }
            return new Effect(armor, structure, ammo, rating, loader);
        }
    }
}
