package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.List;

/**
 * A door gun a yard can put on one of a ship's boat's hardpoints.
 *
 * <p>The fourth kind of boat fitting, and the one that is not a slot: a boat
 * has as many hardpoints as its pattern says and each takes one gun, so a gun
 * is fitted to a hardpoint by index rather than to a {@link BoatFittingSlot}.
 * Like the rest of a boat's parts it is built into stores as a spare, and one
 * taken off goes back there ({@link BoatWorkshop}).
 *
 * <p><b>The gun a boat leaves the yard with is the sortie kit.</b> A hardpoint
 * nobody has refitted carries what {@link ShuttleType#kitFor} gives an
 * air-to-ground sortie for that mount, and putting that gun back costs
 * nothing. So a company that never opens the hardpoints flies exactly the
 * boats it always did, and one that does has chosen.
 *
 * @param structureId the turret structure the battle mounts, from the turret
 *     catalog the airframe's kit already names
 * @param bill what the yard consumes when this is not the hardpoint's own
 *     yard-standard gun
 * @param effect one line on what the gun does in play, for its catalog row
 */
public record BoatGun(
        String id,
        String displayName,
        String structureId,
        int tier,
        FabricationCost bill,
        String effect,
        String provenance) {

    public static final BoatGun HEAVY_MG = new BoatGun(
            "door_heavy_mg", "@boatGunHeavyMg", TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 1,
            cost(5, 0, 10, 0), "@boatGunHeavyMgEffect", "@boatGunHeavyMgProvenance");

    public static final BoatGun ARBALEST = new BoatGun(
            "door_arbalest", "@boatGunArbalest", TurretCatalogRegistry.ARBALEST_STRUCTURE_ID, 2,
            cost(10, 5, 20, 0), "@boatGunArbalestEffect", "@boatGunArbalestProvenance");

    public static final BoatGun GRENADE_LAUNCHER = new BoatGun(
            "door_grenade_launcher", "@boatGunGrenadeLauncher",
            TurretCatalogRegistry.GRENADE_LAUNCHER_STRUCTURE_ID, 2,
            cost(15, 5, 15, 0), "@boatGunGrenadeLauncherEffect", "@boatGunGrenadeLauncherProvenance");

    public static final BoatGun HEPHAESTUS = new BoatGun(
            "door_hephaestus", "@boatGunHephaestus", TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID, 3,
            cost(20, 10, 30, 5), "@boatGunHephaestusEffect", "@boatGunHephaestusProvenance");

    private static final List<BoatGun> CATALOG = List.of(HEAVY_MG, ARBALEST, GRENADE_LAUNCHER, HEPHAESTUS);

    public BoatGun {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Boat gun id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Boat gun display name is required");
        }
        if (structureId == null || structureId.isBlank()) {
            throw new IllegalArgumentException("Boat gun turret structure is required");
        }
        if (tier < 1 || tier > 3) throw new IllegalArgumentException("Boat gun tier must be 1..3: " + tier);
        if (bill == null) throw new IllegalArgumentException("Boat gun bill is required");
    }

    public String displayName() { return Strings.resolve(displayName); }
    public String effect() { return Strings.resolve(effect); }
    public String provenance() { return Strings.resolve(provenance); }

    /** Every authored door gun, cheapest first. */
    public static List<BoatGun> catalog() {
        return CATALOG;
    }

    /**
     * The gun hardpoint {@code index} of a {@code pattern} boat leaves the yard
     * with: the air-to-ground sortie kit for that mount, or null when the
     * pattern has no such hardpoint or its kit names a gun this catalog does
     * not carry.
     */
    public static BoatGun standardFor(ShuttleType pattern, int index) {
        if (pattern == null || index < 0 || index >= pattern.hardpoints()) return null;
        String[] kit = ShuttleType.kitFor(TurretRole.A2G, pattern.hardpoints());
        return index < kit.length ? byStructure(kit[index]) : null;
    }

    /** Whether fitting this to hardpoint {@code index} is putting the yard's own gun back. */
    public boolean standardOn(ShuttleType pattern, int index) {
        return this == standardFor(pattern, index);
    }

    /**
     * Whether a {@code pattern} boat can take this gun on a refit. The Hermes is
     * a patched courier with no upgrade path, so it keeps the gun it has.
     */
    public boolean supports(ShuttleType pattern) {
        return pattern != null && pattern.hardpoints() > 0 && pattern != ShuttleType.HERMES;
    }

    /** Returns null for an unknown persisted gun id. */
    public static BoatGun findById(String id) {
        if (id == null) return null;
        for (BoatGun gun : CATALOG) {
            if (gun.id.equals(id)) return gun;
        }
        return null;
    }

    private static BoatGun byStructure(String structureId) {
        for (BoatGun gun : CATALOG) {
            if (gun.structureId.equals(structureId)) return gun;
        }
        return null;
    }

    private static FabricationCost cost(int supplies, int machinery, int metals, int rareMetals) {
        List<FabricationCost.Line> lines = new ArrayList<>(4);
        if (supplies > 0) lines.add(new FabricationCost.Line(Commodities.SUPPLIES, supplies));
        if (machinery > 0) lines.add(new FabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery));
        if (metals > 0) lines.add(new FabricationCost.Line(Commodities.METALS, metals));
        if (rareMetals > 0) lines.add(new FabricationCost.Line(Commodities.RARE_METALS, rareMetals));
        return new FabricationCost(lines);
    }
}
