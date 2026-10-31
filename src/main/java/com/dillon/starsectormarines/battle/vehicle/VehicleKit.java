package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MachineSystems;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The retired vehicle kit rack (mechs-nouns.md, one machine, one part catalog). Every kit became an internal of the
 * one part catalog ({@code MechInternal.RETIRED_KITS}): the spall liner, ready rack, sensor drone pair and active
 * protection are fitted in a machine's auxiliary compartment, a mech's or a hull's alike. No kit remains; this shell
 * keeps the rack's slot arithmetic and the drop effect's record until the kit plumbing is removed.
 */
public enum VehicleKit {
    ;

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

    /** No kit suits any hull: every part is an internal now. */
    public boolean suits(VehicleType type) {
        return false;
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

    /** What a hull's fit makes of it at the drop, every factor multiplied and every bonus added. */
    public record Effect(float armorFactor, float structureFactor, float ammoFactor, float armorRatingBonus) {
        public static final Effect NONE = new Effect(1f, 1f, 1f, 0f);

        public static Effect of(List<VehicleKit> kits) {
            return of(kits, VehiclePlating.FACTORY, null);
        }

        /** This effect with what the hull's compartment adds ({@link MachineSystems}): its liner's structure, its ready rack's rounds. */
        public Effect with(MachineSystems systems) {
            if (systems == null) return this;
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
