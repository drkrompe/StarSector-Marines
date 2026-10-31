package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.mech.MachineSystems;
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
    /**
     * A tethered pair of sensor drones the hull launches and flies ahead of its bow: sight past cover the hull
     * cannot see over, which is what an indirect-fire hull otherwise needs a spotter for. Hulls with no bay of
     * their own.
     */
    SENSOR_PAIR("kit.sensor-pair", "@vehicleKitSensorPair", 1f, 1f, 1f, 0f),
    /**
     * Active protection: interceptor launchers on the hull's rear flanks fed by a sensor panel on each cheek, a
     * point-defence mount carried by the hull that takes the arcing, top-attack rounds coming down on it
     * ({@link #activeProtectionStructureId}). It is the tank line's answer to a missile mech, and costs a little
     * speed for the launchers and their power ({@link #speedFactor}). The gun tanks only.
     */
    ACTIVE_PROTECTION("kit.active-protection", "@vehicleKitActiveProtection", 1f, 1f, 1f, 0f);

    /** {@code false} is the control: the active-protection kit mounts nothing. */
    public static final String ACTIVE_PROTECTION_PROPERTY = "battle.vehicle.activeProtection";

    /** The active-protection kit's turret-catalog structure, or null under the control. */
    private static final String ACTIVE_PROTECTION_STRUCTURE =
            Boolean.parseBoolean(System.getProperty(ACTIVE_PROTECTION_PROPERTY, "true")) ? "structure.tank-aps" : null;

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

    /** The drones this kit adds to the hull's bay, in launch order; empty for a kit that adds none. */
    public List<DroneRole> droneBay() {
        return this == SENSOR_PAIR ? List.of(DroneRole.SENSOR, DroneRole.SENSOR) : List.of();
    }

    /**
     * The turret-catalog structure of the hull-borne point-defence mount this kit gives its hull, or null for a kit
     * that gives none. The mount ({@code PointDefenseService#mountHull}) engages hostile arcing, top-attack rounds
     * coming down on the hull, inside its reach, at its rate, from a finite magazine; flat rockets, gun rounds and
     * rounds falling on anybody else are left alone. {@code -Dbattle.vehicle.activeProtection=false} is the control.
     */
    public String activeProtectionStructureId() {
        return this == ACTIVE_PROTECTION ? ACTIVE_PROTECTION_STRUCTURE : null;
    }

    /** What the kit multiplies the hull's top speed and acceleration by: the launchers' weight and power. */
    public float speedFactor() {
        return this == ACTIVE_PROTECTION ? ACTIVE_PROTECTION_SPEED : 1f;
    }

    /** The retired Parapet's 2.9 cells a second against the Onager's 3.1. */
    private static final float ACTIVE_PROTECTION_SPEED = .94f;

    /** The first active-protection structure among {@code kits}, or null for a rack that carries none. */
    public static String activeProtectionStructureId(List<VehicleKit> kits) {
        for (VehicleKit kit : kits) if (kit.activeProtectionStructureId() != null) return kit.activeProtectionStructureId();
        return null;
    }

    /** What a rack of kits multiplies the hull's top speed and acceleration by. */
    public static float speedFactor(List<VehicleKit> kits) {
        float factor = 1f;
        for (VehicleKit kit : kits) factor *= kit.speedFactor();
        return factor;
    }

    /**
     * Whether the hull has what this kit works on: no bay of its own for the sensor drones, an armed gun tank (the Onager and the laser
     * tank, not the dozer) for active protection, whose launchers stand on its rear flanks.
     */
    public boolean suits(VehicleType type) {
        if (type == null) return false;
        return switch (this) {
            case SENSOR_PAIR -> type.droneBay().isEmpty();
            case ACTIVE_PROTECTION -> type.isTank() && type.weight() == VehicleType.Weight.HEAVY
                    && type.hasTurretWeapon();
        };
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

    /**
     * The rows the hull's stock compartment parts take ({@link HullReservoir#stockFrame}): its bank (a military bank is
     * 2x2), a row for each stock vent array and a row for its standard loader; none on an unarmed hull.
     */
    public static int stockRackSlots(VehicleType type) {
        HullReservoir.Frame bank = HullReservoir.stockBank(type);
        if (bank == null) return 0;
        return (bank == HullReservoir.MILITARY_BANK ? 2 : 1) + HullReservoir.stockVentArrays(type) + 1;
    }

    /**
     * Whether {@code kits} fit {@code type}'s rack beside its stock flux parts, as a hull the company does not own is set
     * down: within the slots left, no kit twice, each suiting the hull. A company rack packs by `VehicleRack`.
     */
    public static boolean fits(VehicleType type, List<VehicleKit> kits) {
        if (kits.size() > slots(type) - stockRackSlots(type)) return false;
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
            return of(kits, VehiclePlating.FACTORY, null);
        }

        /** This effect with what the hull's compartment adds ({@link MachineSystems}): its liner's structure, its ready rack's rounds. */
        public Effect with(MachineSystems systems) {
            if (systems == null || systems.equals(MachineSystems.NONE)) return this;
            return new Effect(armorFactor, structureFactor * systems.structureFactor(), ammoFactor * systems.ammoFactor(),
                    armorRatingBonus);
        }

        /** As above, in the plating the hull wears: its armor factor and, on a tracked hull, its skirts. */
        public static Effect of(List<VehicleKit> kits, VehiclePlating plating, VehicleType type) {
            float armor = 1f, structure = 1f, ammo = 1f, rating = 0f;
            for (VehicleKit kit : kits) {
                armor *= kit.armorFactor;
                structure *= kit.structureFactor;
                ammo *= kit.ammoFactor;
                rating += kit.armorRatingBonus;
            }
            if (plating != null) {
                armor *= plating.armorFactor();
                rating += plating.armorRatingBonus(type);
            }
            return new Effect(armor, structure, ammo, rating);
        }
    }
}
