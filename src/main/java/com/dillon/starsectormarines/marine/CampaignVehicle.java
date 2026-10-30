package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleGunCard;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent identity of one player-owned vehicle standing on a mech-bay gantry.
 *
 * <p>It shares the gantry with mechs and nothing else: no chassis or sockets. Its hull, turret and kinematics are
 * its {@link VehicleType}'s; what is fitted to it, as to a mech, is the engine at its engine core and the plating at
 * its armor point (each a {@link MechInternal} of its role), the weapon in its gun card (a
 * {@link MechWeaponComponent}), and on its rack the {@link VehicleKit}s beside the capacitor bank and vent arrays
 * that are its flux reservoir ({@link VehicleRack}). An armed hull is built with its stock bank.
 */
public final class CampaignVehicle implements Serializable {

    private final String id;
    private String displayName;
    private final VehicleType type;
    /** Kit ids on the rack, in fitted order; null in a save from before kits (an empty rack). */
    private List<String> kitIds = new ArrayList<>();
    /**
     * The plating part at its armor point; null is the hull's own plate, which is no inventory part
     * and is what a stripped armor point reads as.
     */
    private String platingId;
    /**
     * The engine part at its engine core; null is the hull's own drive, which is no inventory part and is what a
     * stripped engine core reads as. Null in a save from before engines.
     */
    private String engineId;
    /**
     * The weapon component in its gun card; null is the hull's own gun, which is no inventory part and is what a
     * stripped gun card reads as. Null in a save from before gun fitting.
     */
    private String gunId;
    /** The capacitor bank and vent arrays on the rack, as internal ids in fitted order. */
    private List<String> rackInternalIds = new ArrayList<>();

    public CampaignVehicle(String id, String displayName, VehicleType type) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Vehicle id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Vehicle name is required");
        }
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        for (MechInternal part : VehicleRack.stockFit(type)) rackInternalIds.add(part.id);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public VehicleType type() { return type; }

    /** The plating part fitted at the armor point; null when the hull wears its own plate. */
    public MechInternal plating() {
        MechInternal plating = MechInternal.findById(platingId);
        return plating != null && plating.role == MechInternal.Role.PLATING && !plating.stock() ? plating : null;
    }

    /** The engine part fitted at the engine core; null when the hull runs its own drive. */
    public MechInternal engine() {
        MechInternal engine = MechInternal.findById(engineId);
        return engine != null && engine.role == MechInternal.Role.ENGINE && !engine.stock() ? engine : null;
    }

    /** Whether {@code engine} goes at the engine core: an engine above the industrial one, which is the hull's own. */
    public static boolean takesEngine(MechInternal engine) {
        return engine != null && engine.role == MechInternal.Role.ENGINE && !engine.stock();
    }

    /** Fits {@code engine} at the engine core, replacing what was there; false for a part that does not go. */
    boolean fitEngine(MechInternal engine) {
        if (!takesEngine(engine)) return false;
        engineId = engine.id;
        return true;
    }

    /** Takes the engine off the engine core, back to the hull's own drive; false when none was fitted. */
    boolean stripEngine() {
        boolean fitted = engine() != null;
        engineId = null;
        return fitted;
    }

    /** The weapon fitted in the gun card; null when the hull fires its own gun. */
    public MechWeaponComponent gun() {
        MechWeaponComponent gun = MechWeaponComponent.findById(gunId);
        return gun != null && takesGun(type, gun) ? gun : null;
    }

    /** Whether {@code gun} goes in {@code type}'s gun card: an armed hull, and a weapon the card accepts. */
    public static boolean takesGun(VehicleType type, MechWeaponComponent gun) {
        VehicleGunCard card = VehicleGunCard.of(type);
        return card != null && card.accepts(gun);
    }

    /** Fits {@code gun} in the gun card, replacing what was there; false for a weapon that does not go. */
    boolean fitGun(MechWeaponComponent gun) {
        if (!takesGun(type, gun)) return false;
        gunId = gun.id;
        return true;
    }

    /** Takes the weapon out of the gun card, back to the hull's own gun; false when none was fitted. */
    boolean stripGun() {
        boolean fitted = gun() != null;
        gunId = null;
        return fitted;
    }

    /** What the engine at the engine core makes of the hull at the drop. */
    public VehicleEngine engineTier() {
        MechInternal engine = engine();
        if (engine == null) return VehicleEngine.STOCK;
        return engine == MechInternal.MILITARY_ENGINE ? VehicleEngine.MILITARY : VehicleEngine.UPRATED;
    }

    /** Whether {@code plating} goes at the armor point: a plating tier above the hull's own plate. */
    public static boolean takesPlating(MechInternal plating) {
        return plating != null && plating.role == MechInternal.Role.PLATING && !plating.stock();
    }

    /** Fits {@code plating} at the armor point, replacing what was there; false for a part that does not go. */
    boolean fitPlating(MechInternal plating) {
        if (!takesPlating(plating)) return false;
        platingId = plating.id;
        return true;
    }

    /** Takes the plating off the armor point, back to the hull's own plate; false when none was fitted. */
    boolean stripPlating() {
        boolean fitted = plating() != null;
        platingId = null;
        return fitted;
    }

    /** What the plating at the armor point makes of the hull at the drop. */
    public VehiclePlating platingTier() {
        MechInternal plating = plating();
        if (plating == null) return VehiclePlating.FACTORY;
        return plating == MechInternal.HEAVY_PLATING ? VehiclePlating.HEAVY : VehiclePlating.COMPOSITE;
    }

    /** The kits on the rack, in fitted order. */
    public List<VehicleKit> kits() {
        List<VehicleKit> kits = new ArrayList<>();
        if (kitIds != null) {
            for (String id : kitIds) {
                VehicleKit kit = VehicleKit.findById(id);
                if (kit != null) kits.add(kit);
            }
        }
        return List.copyOf(kits);
    }

    /** Whether {@code kit} would go on the rack: room for it, not already fitted, and suiting the hull. */
    public boolean canFit(VehicleKit kit) {
        if (kit == null) return false;
        List<VehicleKit> proposed = new ArrayList<>(kits());
        proposed.add(kit);
        return VehicleRack.fits(type, proposed, rackInternals());
    }

    /** The capacitor bank and vent arrays on the rack, in fitted order. */
    public List<MechInternal> rackInternals() {
        List<MechInternal> parts = new ArrayList<>();
        if (rackInternalIds != null) {
            for (String id : rackInternalIds) {
                MechInternal part = MechInternal.findById(id);
                if (part != null) parts.add(part);
            }
        }
        return List.copyOf(parts);
    }

    /**
     * Whether {@code part} would go on the rack: a bank or vent array with room for it. A bank where one is fitted
     * goes in its place, as on a mech.
     */
    public boolean canFitRack(MechInternal part) {
        if (!VehicleRack.takes(type, part)) return false;
        return VehicleRack.fits(type, kits(), withRackPart(part));
    }

    /** Puts {@code part} on the rack, a bank replacing the bank fitted; false when it would not go. */
    boolean fitRack(MechInternal part) {
        if (!canFitRack(part)) return false;
        List<String> ids = new ArrayList<>();
        for (MechInternal internal : withRackPart(part)) ids.add(internal.id);
        rackInternalIds = ids;
        return true;
    }

    /** Takes one {@code part} off the rack; false when it was not there. Without a bank the hull is incomplete. */
    boolean removeRack(MechInternal part) {
        return part != null && rackInternalIds != null && rackInternalIds.remove(part.id);
    }

    private List<MechInternal> withRackPart(MechInternal part) {
        List<MechInternal> parts = new ArrayList<>(rackInternals());
        if (part.role == MechInternal.Role.CAPACITOR_BANK) parts.removeIf(p -> p.role == MechInternal.Role.CAPACITOR_BANK);
        parts.add(part);
        return parts;
    }

    /** Whether the hull may drop: an armed hull needs a capacitor bank on its rack. */
    public boolean complete() {
        return VehicleRack.complete(type, rackInternals());
    }

    /** Puts {@code kit} on the rack; false when it would not go. */
    public boolean fit(VehicleKit kit) {
        if (!canFit(kit)) return false;
        if (kitIds == null) kitIds = new ArrayList<>();
        kitIds.add(kit.id);
        return true;
    }

    /** Takes {@code kit} off the rack; false when it was not there. */
    public boolean remove(VehicleKit kit) {
        return kit != null && kitIds != null && kitIds.remove(kit.id);
    }

    /** Changes only the player-facing callsign; identity and hull stay intact. */
    public boolean rename(String value) {
        String candidate = CampaignMech.normalizeCallSign(value);
        if (candidate == null || candidate.isEmpty()
                || candidate.length() > CampaignMech.CALLSIGN_MAX_LENGTH) return false;
        displayName = candidate;
        return true;
    }

    public MechPattern.WeightClass weightClass() {
        return MechPattern.weightClass(type);
    }

    /** The frozen value Mech Support carries; the campaign object stays in the bay. */
    public VehicleDeploymentSpec freezeForDeployment() {
        return new VehicleDeploymentSpec(id, displayName, type, kits(), platingTier(), engineTier(), gun(),
                VehicleRack.frame(rackInternals()));
    }

    /**
     * Saves from before the armor point kept armor as two rack kits: the appliqué plate is composite plating
     * and the spaced skirts heavy plating, the heavier of the two winning when a hull wore both.
     */
    private Object readResolve() {
        if (kitIds == null) kitIds = new ArrayList<>();
        if (rackInternalIds == null) rackInternalIds = new ArrayList<>();
        boolean applique = kitIds.remove(RETIRED_APPLIQUE_PLATE);
        boolean skirts = kitIds.remove(RETIRED_SPACED_SKIRTS);
        if (platingId == null) {
            if (skirts) platingId = MechInternal.HEAVY_PLATING.id;
            else if (applique) platingId = MechInternal.COMPOSITE_PLATING.id;
        }
        return this;
    }

    /** The retired armor kits' ids: migration input only ({@link #readResolve}, {@link MechBay}). */
    static final String RETIRED_APPLIQUE_PLATE = "kit.applique-plate";
    static final String RETIRED_SPACED_SKIRTS = "kit.spaced-skirts";

    /** Player-facing name of a vehicle hull: its role and weapon, short ("LRM Standoff Carrier"). */
    public static String typeName(VehicleType type) {
        return type == null ? "" : Strings.get("vehicleTypeName" + type.name());
    }

    /** The hull's in-universe class name ("Longshot"), which also names the hulls a bay fabricates. */
    public static String className(VehicleType type) {
        return type == null ? "" : Strings.get("vehicleTypeClass" + type.name());
    }
}
