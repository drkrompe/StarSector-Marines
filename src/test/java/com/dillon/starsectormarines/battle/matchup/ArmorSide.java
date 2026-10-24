package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.MechFitSpace;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One side of an armor matchup: machines, written the way the command line writes them.
 *
 * <pre>
 *   side    := unit ('+' unit)*
 *   unit    := [count 'x'] (mech | vehicle)          2xhound:refit, heavy-apc
 *   mech    := ['mech:'] chassis [':' item (',' item)*]
 *   item    := stock | refit | role=&lt;role&gt;
 *            | arms|la|ra|ls|rs = &lt;weapon&gt;|none   arms=chaingun (arms: every arm mount; la/ra: one arm)
 *            | engine|plating|capacitors|vents = &lt;internal&gt;|none
 *   vehicle := ['vehicle:'] &lt;VehicleType, kebab case&gt;  breakthrough-tank (technical, scout, dozer accepted)
 * </pre>
 *
 * Every mech starts from its factory fit: its own weapons on the stock frame. {@code refit} fills the internals with
 * the strongest set the engine budget actually allows around whatever weapons the fit carries at that point (armor,
 * then speed, then dissipation, as {@link MechFitSpace#bestRefitAroundStockWeapons} ranks it), so it is applied after the
 * weapon items whatever order they are written in. Weapons are checked against the chassis's sockets; a fit over the
 * engine budget is allowed (the instrument is for asking what-if) and marked {@code !over-budget} in its label.
 */
public record ArmorSide(List<Unit> units) {

    public ArmorSide {
        if (units == null || units.isEmpty()) throw new IllegalArgumentException("A side needs at least one machine");
        units = List.copyOf(units);
    }

    /** One line of a side: {@code count} identical machines. */
    public sealed interface Unit permits Mech, Vehicle {
        int count();

        /** Canonical text, without the count; parsing it back gives the same unit. */
        String label();
    }

    /** A mech lance of {@code count} machines, all fitted as {@code fit}. */
    public record Mech(int count, MechFrameBudget.Fit fit, MechRole role) implements Unit {

        public Mech {
            Objects.requireNonNull(fit, "fit");
            if (count < 1) throw new IllegalArgumentException("count must be at least 1");
            if (role == null) role = fit.variant().defaultRole;
        }

        public MechVariant variant() {
            return fit.variant();
        }

        /** The stock frame when every internal is the factory part, so a stock mech is exactly the one missions field. */
        public MechFrame frame() {
            return isFactoryFrame(fit) ? MechFrame.stock(fit.variant()) : fit.frame();
        }

        public MechDeploymentSpec deployment() {
            return new MechDeploymentSpec(fit.variant(), role, MissileReplenisherComponent.STANDARD,
                    fit.arms(), fit.leftArm(), fit.rightArm(), fit.leftShoulder(), fit.rightShoulder(),
                    null, frame(), null);
        }

        @Override
        public String label() {
            MechVariant variant = fit.variant();
            List<String> items = new ArrayList<>();
            boolean stockArms = fit.arms() == variant.arms && fit.leftArm() == variant.leftArm
                    && fit.rightArm() == variant.rightArm;
            boolean stockWeapons = stockArms && fit.leftShoulder() == variant.leftShoulder
                    && fit.rightShoulder() == variant.rightShoulder;
            boolean refit = !isFactoryFrame(fit) && sameInternals(fit,
                    bestRefit(MechFrameBudget.Fit.stock(variant).withWeaponsOf(fit)));
            if (!stockWeapons) {
                if (!stockArms) {
                    if (fit.leftArm() == fit.rightArm()) {
                        items.add("arms=" + weaponName(fit.armWeapon()));
                    } else {
                        items.add("la=" + weaponName(fit.leftArm()));
                        items.add("ra=" + weaponName(fit.rightArm()));
                    }
                }
                if (fit.leftShoulder() != variant.leftShoulder) items.add("ls=" + weaponName(fit.leftShoulder()));
                if (fit.rightShoulder() != variant.rightShoulder) items.add("rs=" + weaponName(fit.rightShoulder()));
            }
            if (refit) {
                items.add("refit");
            } else {
                for (MechInternal.Role r : MechInternal.Role.values()) {
                    MechInternal part = fit.internal(r);
                    if (part != r.stock()) items.add(roleKey(r) + "=" + internalName(part));
                }
            }
            if (role != variant.defaultRole) items.add("role=" + kebab(role.name()));
            String text = variant.id + (items.isEmpty() ? "" : ":" + String.join(",", items));
            return fit.overBudget() ? text + "!over-budget" : text;
        }
    }

    /** {@code count} hulls of one vehicle variant. */
    public record Vehicle(int count, VehicleType type) implements Unit {

        public Vehicle {
            Objects.requireNonNull(type, "type");
            if (count < 1) throw new IllegalArgumentException("count must be at least 1");
        }

        @Override
        public String label() {
            return vehicleId(type);
        }
    }

    /** Total machines on this side. */
    public int size() {
        int n = 0;
        for (Unit unit : units) n += unit.count();
        return n;
    }

    public String label() {
        List<String> parts = new ArrayList<>();
        for (Unit unit : units) parts.add((unit.count() == 1 ? "" : unit.count() + "x") + unit.label());
        return String.join("+", parts);
    }

    // ---- parsing ----

    public static ArmorSide parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty armor side");
        List<Unit> units = new ArrayList<>();
        for (String part : text.split("\\+")) units.add(parseUnit(part.trim()));
        return new ArmorSide(units);
    }

    static Unit parseUnit(String text) {
        if (text.isEmpty()) throw new IllegalArgumentException("Empty unit in armor side");
        int count = 1;
        Matcher counted = COUNT.matcher(text);
        if (counted.matches()) {
            count = Integer.parseInt(counted.group(1));
            text = counted.group(2);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("vehicle:")) return new Vehicle(count, vehicle(lower.substring("vehicle:".length())));
        if (lower.startsWith("mech:")) lower = lower.substring("mech:".length());
        int colon = lower.indexOf(':');
        String head = colon < 0 ? lower : lower.substring(0, colon);
        MechVariant variant = chassis(head);
        if (variant == null) {
            if (colon >= 0) throw new IllegalArgumentException("Unknown mech chassis '" + head + "'; known: "
                    + chassisIds());
            return new Vehicle(count, vehicle(head));
        }
        return parseMech(count, variant, colon < 0 ? "" : lower.substring(colon + 1));
    }

    private static final Pattern COUNT = Pattern.compile("(\\d+)\\s*[x*]\\s*(.+)");

    private static Mech parseMech(int count, MechVariant variant, String items) {
        MechFrameBudget.Fit fit = MechFitSpace.stock(variant);
        boolean refit = false;
        MechRole role = variant.defaultRole;
        for (String raw : items.split(",")) {
            String item = raw.trim();
            if (item.isEmpty() || item.equals("stock")) continue;
            if (item.equals("refit")) {
                refit = true;
                continue;
            }
            int eq = item.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("Unknown mech fit item '" + item
                    + "'; expected stock, refit or key=value");
            String key = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            switch (key) {
                case "arms" -> fit = fit.withArmWeapon(weapon(variant, variant.armMounts()[0], value));
                case "la" -> fit = fit.withWeapon(MechMountSlot.LEFT_ARM,
                        weapon(variant, MechMountSlot.LEFT_ARM, value));
                case "ra" -> fit = fit.withWeapon(MechMountSlot.RIGHT_ARM,
                        weapon(variant, MechMountSlot.RIGHT_ARM, value));
                case "ls" -> fit = fit.withWeapon(MechMountSlot.LEFT_SHOULDER,
                        weapon(variant, MechMountSlot.LEFT_SHOULDER, value));
                case "rs" -> fit = fit.withWeapon(MechMountSlot.RIGHT_SHOULDER,
                        weapon(variant, MechMountSlot.RIGHT_SHOULDER, value));
                case "engine" -> fit = fit.withInternal(MechInternal.Role.ENGINE,
                        internal(variant, MechInternal.Role.ENGINE, value));
                case "plating" -> fit = fit.withInternal(MechInternal.Role.PLATING,
                        internal(variant, MechInternal.Role.PLATING, value));
                case "capacitors" -> fit = fit.withInternal(MechInternal.Role.CAPACITOR_BANK,
                        internal(variant, MechInternal.Role.CAPACITOR_BANK, value));
                case "vents" -> fit = fit.withInternal(MechInternal.Role.VENT_ARRAY,
                        internal(variant, MechInternal.Role.VENT_ARRAY, value));
                case "loader" -> fit = fit.withInternal(MechInternal.Role.LOADER,
                        internal(variant, MechInternal.Role.LOADER, value));
                case "role" -> role = role(value);
                default -> throw new IllegalArgumentException("Unknown mech fit key '" + key
                        + "'; known: arms, la, ra, ls, rs, engine, plating, capacitors, vents, loader, role");
            }
        }
        if (refit) fit = bestRefit(fit);
        return new Mech(count, fit, role);
    }

    /** The strongest internals under budget around {@code fit}'s weapons; the factory frame when none fits. */
    static MechFrameBudget.Fit bestRefit(MechFrameBudget.Fit fit) {
        return MechFitSpace.internalFits(fit.variant(), fit.armWeapon(), fit.leftShoulder(), fit.rightShoulder())
                .stream()
                .map(f -> f.withWeaponsOf(fit))
                .filter(f -> !f.overBudget())
                .max(MechFitSpace.FRAME_ORDER)
                .orElse(fit);
    }

    private static MechWeaponComponent weapon(MechVariant variant, MechMountSlot slot, String value) {
        if (value.equals("none") || value.equals("-")) return null;
        List<MechWeaponComponent> legal = MechFitSpace.weaponsFor(variant, slot);
        for (MechWeaponComponent c : MechWeaponComponent.values()) {
            if (!matchesWeapon(c, value)) continue;
            if (!legal.contains(c)) {
                throw new IllegalArgumentException(weaponName(c) + " does not fit the " + variant.id + "'s "
                        + slotKey(slot) + " socket; legal: " + legal.stream().map(ArmorSide::weaponName).toList());
            }
            return c;
        }
        throw new IllegalArgumentException("Unknown mech weapon '" + value + "'; legal in the " + variant.id + "'s "
                + slotKey(slot) + ": " + legal.stream().map(ArmorSide::weaponName).toList());
    }

    private static boolean matchesWeapon(MechWeaponComponent c, String value) {
        return c.id.equals(value) || weaponName(c).equals(value) || c.id.equals("component." + value)
                || c.name().equalsIgnoreCase(value.replace('-', '_'));
    }

    private static MechInternal internal(MechVariant variant, MechInternal.Role role, String value) {
        if (value.equals("none") || value.equals("-")) {
            if (role != MechInternal.Role.VENT_ARRAY && role != MechInternal.Role.LOADER) {
                throw new IllegalArgumentException("Only vents and the loader may be left out; use the factory part");
            }
            return null;
        }
        List<MechInternal> legal = MechFitSpace.internalsFor(variant, role);
        for (MechInternal internal : MechInternal.values()) {
            if (internal.role != role) continue;
            if (!(internal.id.equals(value) || internal.id.equals("internal." + value)
                    || internalName(internal).equals(value) || internal.name().equalsIgnoreCase(value.replace('-', '_')))) {
                continue;
            }
            if (!legal.contains(internal)) {
                throw new IllegalArgumentException(internal.id + " does not fit the " + variant.id);
            }
            return internal;
        }
        List<String> known = new ArrayList<>();
        for (MechInternal internal : legal) if (internal != null) known.add(internalName(internal));
        throw new IllegalArgumentException("Unknown " + roleKey(role) + " '" + value + "'; known: " + known);
    }

    private static MechRole role(String value) {
        for (MechRole r : MechRole.values()) if (r.name().equalsIgnoreCase(value.replace('-', '_'))) return r;
        List<String> known = new ArrayList<>();
        for (MechRole r : MechRole.values()) known.add(kebab(r.name()));
        throw new IllegalArgumentException("Unknown mech role '" + value + "'; known: " + known);
    }

    private static MechVariant chassis(String id) {
        for (MechVariant v : MechVariant.values()) if (v.id.equals(id)) return v;
        return null;
    }

    static VehicleType vehicle(String id) {
        switch (id) {
            case "technical" -> { return VehicleType.MILITIA_TECHNICAL; }
            case "scout" -> { return VehicleType.SCOUT_CAR; }
            case "dozer" -> { return VehicleType.DOZER_BREACHER; }
            default -> { }
        }
        for (VehicleType type : VehicleType.values()) if (vehicleId(type).equals(id)) return type;
        List<String> known = new ArrayList<>();
        for (VehicleType type : VehicleType.values()) known.add(vehicleId(type));
        throw new IllegalArgumentException("Unknown machine '" + id + "'; mechs: " + chassisIds()
                + ", vehicles: " + known);
    }

    private static List<String> chassisIds() {
        List<String> ids = new ArrayList<>();
        for (MechVariant v : MechVariant.values()) ids.add(v.id);
        return ids;
    }

    // ---- names ----

    static String vehicleId(VehicleType type) {
        return kebab(type.name());
    }

    static String weaponName(MechWeaponComponent c) {
        if (c == null) return "none";
        return c.id.startsWith("component.mech-") ? c.id.substring("component.mech-".length()) : c.id;
    }

    static String internalName(MechInternal internal) {
        if (internal == null) return "none";
        return internal.id.substring(internal.id.lastIndexOf('-') + 1);
    }

    private static String roleKey(MechInternal.Role role) {
        return switch (role) {
            case ENGINE -> "engine";
            case PLATING -> "plating";
            case CAPACITOR_BANK -> "capacitors";
            case VENT_ARRAY -> "vents";
            case LOADER -> "loader";
        };
    }

    private static String slotKey(MechMountSlot slot) {
        return switch (slot) {
            case ARMS -> "arms";
            case LEFT_ARM -> "la";
            case RIGHT_ARM -> "ra";
            case LEFT_SHOULDER -> "ls";
            case RIGHT_SHOULDER -> "rs";
        };
    }

    private static String kebab(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean isFactoryFrame(MechFrameBudget.Fit fit) {
        for (MechInternal.Role r : MechInternal.Role.values()) if (fit.internal(r) != r.stock()) return false;
        return true;
    }

    private static boolean sameInternals(MechFrameBudget.Fit a, MechFrameBudget.Fit b) {
        for (MechInternal.Role r : MechInternal.Role.values()) if (a.internal(r) != b.internal(r)) return false;
        return true;
    }
}
