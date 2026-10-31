package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleGunCard;
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
 * {@link MechWeaponComponent}), and in its auxiliary compartment the internals of the one part catalog: the capacitor
 * bank and vent arrays that are its flux reservoir, its loader, and any liner, ready rack, sensor drones or active
 * protection ({@link VehicleRack}). An armed hull is built with its stock bank.
 */
public final class CampaignVehicle implements Serializable {

    private final String id;
    private String displayName;
    private final VehicleType type;
    /**
     * Kit ids from a save from before every kit became an internal: migration input only, read and emptied by
     * {@link #readResolve}; null in a save from before kits.
     */
    private List<String> kitIds = new ArrayList<>();
    /**
     * The plating part at its armor point: every hull is built with factory plating, which is only ever replaced, as a
     * mech's is. Null only in a save from before it was a part (the hull's own plate, read as factory plating).
     */
    private String platingId;
    /**
     * The engine part at its engine core: every hull is built with the industrial engine. Null is an empty engine core,
     * which means no engine, as on a mech: the hull is incomplete and stays off the drop.
     */
    private String engineId;
    /**
     * The weapon component in its gun card; null is the hull's own gun, which is no inventory part and is what a
     * stripped gun card reads as. Null in a save from before gun fitting.
     */
    private String gunId;
    /** The internals in the auxiliary compartment (bank, vent arrays, loader), as ids in fitted order. */
    private List<String> rackInternalIds = new ArrayList<>();
    /**
     * Which fitting rules the saved compartment was written under: 0 in a save from before its loader was a part, when
     * every armed hull had a sealed stock loader; {@link #FIT_VERSION} for a hull built since.
     */
    private int fitVersion;

    /**
     * The current fitting rules: the loader is a part in the compartment (1), and the engine and plating are parts at
     * their points (2); an empty socket means nothing.
     */
    static final int FIT_VERSION = 2;

    public CampaignVehicle(String id, String displayName, VehicleType type) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Vehicle id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Vehicle name is required");
        }
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.fitVersion = FIT_VERSION;
        this.engineId = MechInternal.INDUSTRIAL_ENGINE.id;
        this.platingId = MechInternal.FACTORY_PLATING.id;
        for (MechInternal part : VehicleRack.stockFit(type)) rackInternalIds.add(part.id);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public VehicleType type() { return type; }

    /** The plating part fitted at the armor point. */
    public MechInternal plating() {
        MechInternal plating = MechInternal.findById(platingId);
        return plating != null && plating.role == MechInternal.Role.PLATING ? plating : null;
    }

    /** The engine part fitted at the engine core; null for an empty core, which leaves the hull incomplete. */
    public MechInternal engine() {
        MechInternal engine = MechInternal.findById(engineId);
        return engine != null && engine.role == MechInternal.Role.ENGINE ? engine : null;
    }

    /** Whether {@code engine} goes at the engine core: any engine a mech runs. */
    public static boolean takesEngine(MechInternal engine) {
        return engine != null && engine.role == MechInternal.Role.ENGINE;
    }

    /** Fits {@code engine} at the engine core, replacing what was there; false for a part that does not go. */
    boolean fitEngine(MechInternal engine) {
        if (!takesEngine(engine)) return false;
        engineId = engine.id;
        return true;
    }

    /** Takes the engine off the engine core, leaving it empty and the hull incomplete; false when none was fitted. */
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
        return VehicleEngine.of(engine());
    }

    /** Whether {@code plating} goes at the armor point: any plating a mech wears. */
    public static boolean takesPlating(MechInternal plating) {
        return plating != null && plating.role == MechInternal.Role.PLATING;
    }

    /** Fits {@code plating} at the armor point, replacing what was there; false for a part that does not go. */
    boolean fitPlating(MechInternal plating) {
        if (!takesPlating(plating)) return false;
        platingId = plating.id;
        return true;
    }

    /** Plating covers the hull and is only ever replaced, as a mech's is: it never comes off. */
    boolean stripPlating() {
        return false;
    }

    /** What the plating at the armor point makes of the hull at the drop. */
    public VehiclePlating platingTier() {
        return VehiclePlating.of(plating());
    }

    /** The internals in the auxiliary compartment, in fitted order. */
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
     * Whether {@code part} would go in the compartment: a part the hull meets the requirements of, with room for it. A bank or loader where
     * one is fitted goes in its place, as on a mech.
     */
    public boolean canFitRack(MechInternal part) {
        if (!VehicleRack.takes(type, gun(), part)) return false;
        return VehicleRack.fits(type, gun(), withRackPart(part));
    }

    /** Puts {@code part} in the compartment, a bank replacing the bank fitted; false when it would not go. */
    boolean fitRack(MechInternal part) {
        if (!canFitRack(part)) return false;
        List<String> ids = new ArrayList<>();
        for (MechInternal internal : withRackPart(part)) ids.add(internal.id);
        rackInternalIds = ids;
        return true;
    }

    /** Takes one {@code part} out of the compartment; false when it was not there. Without a bank the hull is incomplete. */
    boolean removeRack(MechInternal part) {
        return part != null && rackInternalIds != null && rackInternalIds.remove(part.id);
    }

    private List<MechInternal> withRackPart(MechInternal part) {
        List<MechInternal> parts = new ArrayList<>(rackInternals());
        if (MechCompartment.limit(part.role) == 1) parts.removeIf(p -> p.role == part.role);
        parts.add(part);
        return parts;
    }

    /** Whether the hull may drop: an engine in its core and, on an armed hull, a capacitor bank in its compartment. */
    public boolean complete() {
        return missingInternals().isEmpty();
    }

    /** What the hull lacks to drop, as on a mech: its engine, and on an armed hull its capacitor bank. */
    public List<MechInternal.Role> missingInternals() {
        List<MechInternal.Role> missing = new ArrayList<>();
        if (engine() == null) missing.add(MechInternal.Role.ENGINE);
        if (!VehicleRack.complete(type, rackInternals())) missing.add(MechInternal.Role.CAPACITOR_BANK);
        return List.copyOf(missing);
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
        return new VehicleDeploymentSpec(id, displayName, type, platingTier(), engineTier(), gun(),
                rackInternals());
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
        if (plating() == null || plating().stock()) {
            if (skirts) platingId = MechInternal.HEAVY_PLATING.id;
            else if (applique) platingId = MechInternal.COMPOSITE_PLATING.id;
        }
        retireKitsToInternals();
        if (fitVersion < 1) fitTheSealedLoader();
        if (fitVersion < 2) fitTheHullsOwnEngineAndPlate();
        kitIds.clear();
        fitVersion = FIT_VERSION;
        return this;
    }

    /**
     * A save from before the engine and plating were parts read an empty engine core as the hull's own drive and an
     * empty armor point as its own plate: those are the industrial engine and factory plating, now fitted.
     */
    private void fitTheHullsOwnEngineAndPlate() {
        if (engine() == null) engineId = MechInternal.INDUSTRIAL_ENGINE.id;
        if (plating() == null) platingId = MechInternal.FACTORY_PLATING.id;
    }

    /** A kit that became an internal moves from the rack's kit list to its compartment, as that internal. */
    private void retireKitsToInternals() {
        for (String kitId : List.copyOf(kitIds)) {
            MechInternal internal = MechInternal.RETIRED_KITS.get(kitId);
            if (internal == null) continue;
            kitIds.remove(kitId);
            List<MechInternal> parts = rackInternals();
            if (parts.stream().noneMatch(p -> p.role == internal.role && MechCompartment.limit(p.role) == 1)) {
                rackInternalIds.add(internal.id);
            }
        }
    }

    /**
     * A save from before the loader was a part: an armed hull had a sealed stock loader, so it now carries the standard
     * loader in its compartment unless a retired accelerated-loader kit already put one there. Where that no longer packs,
     * the compartment's other parts come off last-fitted first (vent arrays, and the
     * liner or ready rack a retired kit became), as spares in stores (the bay owns what it held), so nothing owned is
     * lost and the hull keeps the bank and loader it always had.
     */
    private void fitTheSealedLoader() {
        if (!type.hasTurretWeapon()) return;
        if (rackInternals().stream().anyMatch(p -> p.role == MechInternal.Role.LOADER)) return;
        rackInternalIds.add(MechInternal.STANDARD_LOADER.id);
        while (!VehicleRack.fits(type, rackInternals())) {
            int spare = -1;
            for (int i = rackInternalIds.size() - 1; i >= 0 && spare < 0; i--) {
                MechInternal part = MechInternal.findById(rackInternalIds.get(i));
                if (part == null || part.role != MechInternal.Role.LOADER && part.role != MechInternal.Role.CAPACITOR_BANK) {
                    spare = i;
                }
            }
            if (spare < 0) break;
            rackInternalIds.remove(spare);
        }
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
