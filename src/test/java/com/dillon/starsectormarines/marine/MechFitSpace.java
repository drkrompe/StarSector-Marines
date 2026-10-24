package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every physically legal fit of one chassis: weapons its sockets accept by type and footprint,
 * crossed with every internal its sockets hold. Pure arithmetic over {@link MechFrameBudget} and
 * the authored doll ({@link MechFittingLayout#forVariant}); no world, no battle. The same rules
 * {@code MechBay.canInstallWeapon} and {@code MechBay.canInstallInternal} apply. Compartments are
 * enumerated with the standard loader or none (a faster loader changes missile cadence, not the
 * frame) and as many vent arrays as pack beside them ({@link MechCompartment}).
 */
public final class MechFitSpace {

    private MechFitSpace() {
    }

    /** Weapons this chassis's socket for {@code slot} accepts, by family, type and footprint. */
    public static List<MechWeaponComponent> weaponsFor(MechVariant variant, MechMountSlot slot) {
        SocketDef socket = MechFittingLayout.forVariant(variant).socket(SocketId.valueOf(slot.name()));
        List<MechWeaponComponent> out = new ArrayList<>();
        if (socket == null || socket.factoryLocked()) return out;
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            if (!component.accepts(slot)) continue;
            if (!socket.accommodates(component.footprintColumns, component.footprintRows)) continue;
            if (socket.type() != MechFittingLayout.SocketType.OMNI
                    && !socket.type().name().equals(component.hardpointType.name())) continue;
            out.add(component);
        }
        return out;
    }

    /**
     * Internals of {@code role} this chassis can hold, each judged alone against its socket's grid;
     * a null entry means the role may be left out (a vent array or a loader).
     */
    public static List<MechInternal> internalsFor(MechVariant variant, MechInternal.Role role) {
        List<MechInternal> out = new ArrayList<>();
        if (role == MechInternal.Role.VENT_ARRAY || role == MechInternal.Role.LOADER) out.add(null);
        MechFittingLayout layout = MechFittingLayout.forVariant(variant);
        for (MechInternal internal : MechInternal.values()) {
            if (internal.role != role) continue;
            SocketId socketId = role.socket();
            if (socketId != null) {
                SocketDef socket = layout.socket(socketId);
                if (socket == null || !socket.accommodates(internal.footprintColumns, internal.footprintRows)) continue;
            }
            out.add(internal);
        }
        return out;
    }

    /** Every footprint-legal fit, mounts left empty included. */
    public static List<MechFrameBudget.Fit> all(MechVariant variant) {
        // A two-armed chassis is enumerated with the same gun on both arms: the pair a player fits.
        List<MechWeaponComponent> arms = withEmpty(weaponsFor(variant, variant.armMounts()[0]));
        List<MechWeaponComponent> left = withEmpty(weaponsFor(variant, MechMountSlot.LEFT_SHOULDER));
        List<MechWeaponComponent> right = withEmpty(weaponsFor(variant, MechMountSlot.RIGHT_SHOULDER));
        List<MechFrameBudget.Fit> out = new ArrayList<>();
        for (MechWeaponComponent a : arms) {
            for (MechWeaponComponent l : left) {
                for (MechWeaponComponent r : right) {
                    out.addAll(internalFits(variant, a, l, r));
                }
            }
        }
        return out;
    }

    /** Every internal combination around one fixed set of weapons. */
    public static List<MechFrameBudget.Fit> internalFits(MechVariant variant, MechWeaponComponent arms,
                                                         MechWeaponComponent left, MechWeaponComponent right) {
        List<MechFrameBudget.Fit> out = new ArrayList<>();
        for (MechInternal engine : internalsFor(variant, MechInternal.Role.ENGINE)) {
            for (MechInternal plating : internalsFor(variant, MechInternal.Role.PLATING)) {
                for (List<MechInternal> compartment : compartmentsFor(variant)) {
                    out.add(new MechFrameBudget.Fit(variant, arms, left, right, engine, plating, compartment));
                }
            }
        }
        return out;
    }

    /**
     * Every compartment this chassis packs: each capacitor bank, with the standard loader or none,
     * and zero or more vent arrays.
     */
    public static List<List<MechInternal>> compartmentsFor(MechVariant variant) {
        List<List<MechInternal>> out = new ArrayList<>();
        for (MechInternal bank : internalsFor(variant, MechInternal.Role.CAPACITOR_BANK)) {
            for (MechInternal loader : new MechInternal[] {MechInternal.STANDARD_LOADER, null}) {
                List<MechInternal> parts = new ArrayList<>();
                parts.add(bank);
                if (loader != null) parts.add(loader);
                while (MechCompartment.fits(variant, parts)) {
                    out.add(List.copyOf(parts));
                    parts.add(MechInternal.AUXILIARY_VENT_ARRAY);
                }
            }
        }
        return out;
    }

    /** The chassis's own stock fit: its factory weapons on the stock frame. */
    public static MechFrameBudget.Fit stock(MechVariant variant) {
        return MechFrameBudget.Fit.stock(variant);
    }

    /**
     * The top of every ladder this chassis can physically take: the heaviest legal weapon in each
     * mount (highest tier first) and the highest-tier internal in each role.
     */
    public static MechFrameBudget.Fit bestOfEverything(MechVariant variant) {
        return new MechFrameBudget.Fit(variant,
                heaviest(weaponsFor(variant, variant.armMounts()[0])),
                heaviest(weaponsFor(variant, MechMountSlot.LEFT_SHOULDER)),
                heaviest(weaponsFor(variant, MechMountSlot.RIGHT_SHOULDER)),
                top(variant, MechInternal.Role.ENGINE), top(variant, MechInternal.Role.PLATING),
                topCompartment(variant));
    }

    /** The top internal of every role this chassis holds, around its factory weapons. */
    public static MechFrameBudget.Fit fullInternalRefit(MechVariant variant) {
        return new MechFrameBudget.Fit(variant, variant.stockArmWeapon(), variant.leftShoulder, variant.rightShoulder,
                top(variant, MechInternal.Role.ENGINE), top(variant, MechInternal.Role.PLATING),
                topCompartment(variant));
    }

    /**
     * A full refit's compartment: the highest-tier bank that packs beside the standard loader (a
     * refit keeps its racks refilling), and a vent array too if it still packs.
     */
    private static List<MechInternal> topCompartment(MechVariant variant) {
        List<MechInternal> banks = new ArrayList<>(internalsFor(variant, MechInternal.Role.CAPACITOR_BANK));
        banks.sort(Comparator.comparingInt((MechInternal bank) -> bank.tier.ordinal()).reversed());
        for (MechInternal bank : banks) {
            List<MechInternal> parts = new ArrayList<>(List.of(bank, MechInternal.STANDARD_LOADER));
            if (!MechCompartment.fits(variant, parts)) continue;
            parts.add(top(variant, MechInternal.Role.VENT_ARRAY));
            return MechCompartment.fits(variant, parts) ? parts : parts.subList(0, 2);
        }
        return MechFrameBudget.Fit.stockCompartment();
    }

    /**
     * The strongest frame that fits under budget around the factory weapons: armor first, then
     * speed, then dissipation, then reservoir. This is "a full refit" as a player could actually build it.
     */
    public static MechFrameBudget.Fit bestRefitAroundStockWeapons(MechVariant variant) {
        return internalFits(variant, variant.stockArmWeapon(), variant.leftShoulder, variant.rightShoulder).stream()
                .filter(fit -> !fit.overBudget())
                .max(FRAME_ORDER)
                .orElseThrow();
    }

    public static final Comparator<MechFrameBudget.Fit> FRAME_ORDER = Comparator
            .comparingDouble((MechFrameBudget.Fit f) -> f.armorCapacity())
            .thenComparingDouble(MechFrameBudget.Fit::moveSpeed)
            .thenComparingDouble(MechFrameBudget.Fit::dissipationPerSecond)
            .thenComparingDouble(MechFrameBudget.Fit::fluxCapacity);

    /** What every chassis stood at before the stock-frame cut (2026-10-16): the reference a refit is read against. */
    public static MechFrame preCut(MechVariant variant) {
        return switch (variant) {
            case BULWARK -> new MechFrame(950f, 1.15f, 100f, 8f);
            case HOUND -> new MechFrame(500f, 1.70f, 100f, 8f);
            case WARDEN -> new MechFrame(400f, 1.45f, 100f, 8f);
        };
    }

    private static MechInternal top(MechVariant variant, MechInternal.Role role) {
        MechInternal best = null;
        for (MechInternal internal : internalsFor(variant, role)) {
            if (internal == null) continue;
            if (best == null || internal.tier.ordinal() > best.tier.ordinal()) best = internal;
        }
        return best;
    }

    private static MechWeaponComponent heaviest(List<MechWeaponComponent> legal) {
        MechWeaponComponent best = null;
        for (MechWeaponComponent component : legal) {
            if (best == null || MechFrameBudget.mass(component) > MechFrameBudget.mass(best)) best = component;
        }
        return best;
    }

    private static List<MechWeaponComponent> withEmpty(List<MechWeaponComponent> legal) {
        List<MechWeaponComponent> out = new ArrayList<>(legal);
        out.add(null);
        return out;
    }
}
