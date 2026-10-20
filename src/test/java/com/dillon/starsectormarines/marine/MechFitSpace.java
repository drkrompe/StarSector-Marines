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
 * {@code MechBay.canInstallWeapon} and {@code MechBay.canInstallInternal} apply, with a standard
 * loader assumed (a faster loader would only take the vent array's socket away).
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

    /** Internals of {@code role} this chassis can hold; a null entry is an empty utility socket. */
    public static List<MechInternal> internalsFor(MechVariant variant, MechInternal.Role role) {
        List<MechInternal> out = new ArrayList<>();
        if (role == MechInternal.Role.VENT_ARRAY) out.add(null);
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
        List<MechWeaponComponent> arms = withEmpty(weaponsFor(variant, MechMountSlot.ARMS));
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
                for (MechInternal capacitors : internalsFor(variant, MechInternal.Role.CAPACITOR_BANK)) {
                    for (MechInternal ventArray : internalsFor(variant, MechInternal.Role.VENT_ARRAY)) {
                        out.add(new MechFrameBudget.Fit(variant, arms, left, right, engine, plating, capacitors, ventArray));
                    }
                }
            }
        }
        return out;
    }

    /** The chassis's own stock fit: its factory weapons on the stock frame. */
    public static MechFrameBudget.Fit stock(MechVariant variant) {
        return new MechFrameBudget.Fit(variant, variant.arms, variant.leftShoulder, variant.rightShoulder,
                null, null, null, null);
    }

    /**
     * The top of every ladder this chassis can physically take: the heaviest legal weapon in each
     * mount (highest tier first) and the highest-tier internal in each role.
     */
    public static MechFrameBudget.Fit bestOfEverything(MechVariant variant) {
        return new MechFrameBudget.Fit(variant,
                heaviest(weaponsFor(variant, MechMountSlot.ARMS)),
                heaviest(weaponsFor(variant, MechMountSlot.LEFT_SHOULDER)),
                heaviest(weaponsFor(variant, MechMountSlot.RIGHT_SHOULDER)),
                top(variant, MechInternal.Role.ENGINE), top(variant, MechInternal.Role.PLATING),
                top(variant, MechInternal.Role.CAPACITOR_BANK), top(variant, MechInternal.Role.VENT_ARRAY));
    }

    /** The top internal of every role this chassis holds, around its factory weapons. */
    public static MechFrameBudget.Fit fullInternalRefit(MechVariant variant) {
        return new MechFrameBudget.Fit(variant, variant.arms, variant.leftShoulder, variant.rightShoulder,
                top(variant, MechInternal.Role.ENGINE), top(variant, MechInternal.Role.PLATING),
                top(variant, MechInternal.Role.CAPACITOR_BANK), top(variant, MechInternal.Role.VENT_ARRAY));
    }

    /**
     * The strongest frame that fits under budget around the factory weapons: armor first, then
     * speed, then dissipation, then reservoir. This is "a full refit" as a player could actually build it.
     */
    public static MechFrameBudget.Fit bestRefitAroundStockWeapons(MechVariant variant) {
        return internalFits(variant, variant.arms, variant.leftShoulder, variant.rightShoulder).stream()
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
            case SIROCCO -> new MechFrame(400f, 1.45f, 100f, 8f);
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
