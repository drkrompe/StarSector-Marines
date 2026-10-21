package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Tone;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.WeaponState;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.WeaponStatus;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * The direct-control HUD as two panels, after the game's own combat HUD: a status panel
 * (bottom left) reading the body's hull, armor, flux and stamina, and a weapon-groups panel
 * (bottom right) listing equipment under the button that fires it. Values and commands remain
 * owned by the battle.
 */
final class BattleDirectControlHudModel {
    /** The flux gauge's resting fill, mech or warship: the violet the game's own flux reads in. */
    private static final String FLUX_FILL = "#a594f9";
    private static final String HIDDEN_ROW = "row row-hidden";

    private final Reactor reactor;
    private final IntConsumer select;
    private final Function<String, String> copy;
    private final Map<String, Object> statusProps = new LinkedHashMap<>();
    private final Map<String, Object> weaponsProps = new LinkedHashMap<>();
    private final Map<String, Object> actionsProps = new LinkedHashMap<>();
    private final Map<String, MutableSignal<String>> text = new LinkedHashMap<>();
    private final MutableSignal<List<WeaponGroup>> groups;
    private String exitLabelKey = "battleDirectHudExit";
    private String structureKey = "";
    private Carrier carrier = Carrier.NONE;

    BattleDirectControlHudModel(Reactor reactor, IntConsumer select, Runnable exit, Runnable pause) {
        this(reactor, select, exit, pause, Strings::get);
    }

    BattleDirectControlHudModel(Reactor reactor, IntConsumer select, Runnable exit,
                               Runnable pause, Function<String, String> copy) {
        this.reactor = reactor;
        this.select = select;
        this.copy = copy;
        for (String key : List.of("title", "carrier", "hullLabel", "hullValue", "hullStyle", "hullClasses",
                "armorRowClasses", "armorValue", "armorStyle", "fluxRowClasses", "fluxValue", "fluxStyle",
                "fluxStateClasses", "fluxState", "staminaRowClasses", "staminaValue", "staminaStyle")) {
            statusProps.put(key, signal(key));
        }
        statusProps.put("armorLabel", label("Armor"));
        statusProps.put("fluxLabel", label("Flux"));
        statusProps.put("staminaLabel", label("StaminaLabel"));
        weaponsProps.put("title", signal("weaponsTitle"));
        groups = reactor.signal(List.of());
        weaponsProps.put("groups", groups);
        actionsProps.put("exitKey", label("KeyTab"));
        actionsProps.put("exitLabel", signal("exitLabel"));
        actionsProps.put("pauseKey", label("KeySpace"));
        actionsProps.put("pauseLabel", signal("pauseLabel"));
        actionsProps.put("pauseClasses", signal("pauseClasses"));
        actionsProps.put("exitAction", exit);
        actionsProps.put("pauseAction", pause);
    }

    private MutableSignal<String> signal(String key) {
        MutableSignal<String> signal = reactor.signal("");
        text.put(key, signal);
        return signal;
    }

    Map<String, Object> statusProps() { return statusProps; }
    Map<String, Object> weaponsProps() { return weaponsProps; }
    Map<String, Object> actionsProps() { return actionsProps; }
    void setExitLabelKey(String key) { exitLabelKey = key; }

    /** Changes exactly when a panel gains or loses a row, so its host knows to re-measure it. */
    String structureKey() { return structureKey; }

    /** The carrier of the last update; the host stands the section doll beside a mech's status. */
    Carrier carrier() { return carrier; }

    void update(BattleDirectControlStatus.Snapshot snapshot, int selected, boolean paused) {
        carrier = snapshot.carrier();
        boolean mech = carrier == Carrier.MECH;
        boolean ship = (carrier == Carrier.WARSHIP || carrier == Carrier.TENDER) && snapshot.warship() != null;
        boolean marine = carrier == Carrier.MARINE;
        var durability = snapshot.durability();
        reactor.untracked(() -> {
            set("title", snapshot.name());
            set("carrier", label(carrier.name()));
            set("hullLabel", label(marine ? "HealthLabel" : "Hull"));
            set("hullValue", percent(durability.hpFraction()));
            set("hullStyle", width(durability.hpFraction()));
            set("hullClasses", "hull-fill" + (durability.hpFraction() <= .25f ? " capacity-low" : ""));
            boolean armored = durability.hasArmor();
            set("armorRowClasses", armored ? "row" : HIDDEN_ROW);
            set("armorValue", ship ? String.valueOf(Math.round(durability.armorRating()))
                    : percent(durability.armorFraction()));
            set("armorStyle", width(ship ? 1f : durability.armorFraction()));
            updateFlux(snapshot, mech, ship);
            boolean stamina = marine;
            set("staminaRowClasses", stamina ? "row" : HIDDEN_ROW);
            float staminaFraction = Math.max(0f, Math.min(1f, snapshot.sprint().staminaFraction()));
            set("staminaValue", snapshot.sprint().exhausted() ? label("SprintResting")
                    : snapshot.sprint().sprinting() ? label("SprintRunning") : percent(staminaFraction));
            set("staminaStyle", width(staminaFraction) + (snapshot.sprint().exhausted()
                    ? " background-color: #8a7440;" : ""));
            set("weaponsTitle", label(mech ? "WeaponGroups" : ship ? "ShipSystems" : "Weapons"));
            set("exitLabel", copy.apply(exitLabelKey));
            set("pauseLabel", label(paused ? "Paused" : "Pause"));
            set("pauseClasses", paused ? "action action-pause action-paused" : "action action-pause");
            List<WeaponGroup> next = mech ? mechGroups(snapshot, selected) : otherGroups(snapshot, ship);
            groups.set(List.copyOf(next));
            StringBuilder key = new StringBuilder(carrier.name()).append(armored).append(stamina)
                    .append(text.get("fluxRowClasses").get()).append(text.get("fluxStateClasses").get().contains("hidden"));
            for (WeaponGroup group : next) key.append('|').append(group.id()).append(group.weapons().size());
            structureKey = key.toString();
        });
    }

    private void updateFlux(BattleDirectControlStatus.Snapshot snapshot, boolean mech, boolean ship) {
        if (ship) {
            var gauge = snapshot.warship().flux();
            set("fluxRowClasses", "row");
            set("fluxValue", gauge.limited() ? percent(gauge.fraction()) : "—");
            set("fluxState", gauge.state());
            set("fluxStateClasses", "state " + stateTone(gauge.tone()));
            set("fluxStyle", width(gauge.fraction()) + " background-color: "
                    + (gauge.tone() == Tone.READY ? FLUX_FILL : toneColor(gauge.tone())) + ";");
            return;
        }
        if (!mech) {
            set("fluxRowClasses", HIDDEN_ROW);
            set("fluxStateClasses", "state state-hidden");
            set("fluxValue", "");
            set("fluxState", "");
            set("fluxStyle", width(0f));
            return;
        }
        var flux = snapshot.flux();
        float value = flux == null ? 0f : flux.value();
        float capacity = flux == null ? MechFlux.CAPACITY : flux.capacity();
        float fraction = capacity > 0f ? Math.max(0f, Math.min(1f, value / capacity)) : 0f;
        boolean venting = flux != null && flux.venting();
        boolean high = fraction >= MechFlux.WARNING / MechFlux.CAPACITY;
        set("fluxRowClasses", "row");
        set("fluxValue", percent(fraction));
        set("fluxState", label(venting ? "FluxVenting" : high ? "FluxHigh" : "FluxReserve"));
        set("fluxStateClasses", "state " + (venting ? "state-alarm" : high ? "state-warn" : "state-calm"));
        set("fluxStyle", width(fraction) + " background-color: "
                + (venting ? "#ef7962" : high ? "#eab152" : FLUX_FILL) + ";");
    }

    /**
     * A mech's mounts under the button that fires them: the left button's group (or the one mount
     * keys 2-4 pointed it at), the right button's racks, and whatever no button fires. A mount fired
     * by both buttons is listed once, under the left.
     */
    private List<WeaponGroup> mechGroups(BattleDirectControlStatus.Snapshot snapshot, int selected) {
        boolean unknown = weaponTriggerUnknown(snapshot);
        List<WeaponRow> left = new ArrayList<>(), right = new ArrayList<>(), other = new ArrayList<>();
        for (WeaponStatus weapon : snapshot.weapons()) {
            String trigger = weapon.trigger();
            boolean onLeft = unknown ? weapon.directEligible() && (selected == 0 || selected == weapon.number())
                    : trigger.contains("L");
            boolean onRight = !unknown && trigger.contains("R");
            WeaponRow row = weaponRow(weapon, true, onLeft, onLeft && onRight ? "LR" : "");
            if (onLeft) left.add(row);
            else if (onRight) right.add(row);
            else other.add(row);
        }
        List<WeaponGroup> result = new ArrayList<>();
        boolean single = selected != 0;
        boolean leftClose = anyTooClose(snapshot, true);
        result.add(new WeaponGroup("direct-hud-group-lmb", label("BadgeLMB"), "badge badge-lmb",
                label(single ? "GroupSingle" : "GroupPrimary"),
                leftClose ? label("TOO_CLOSE") : label(single ? "GroupSingleHint" : "GroupPrimaryHint"),
                leftClose ? HINT_WARN : HINT,
                "group-header group-header-action", false, () -> select.accept(0), left));
        if (!right.isEmpty()) {
            boolean close = anyTooClose(snapshot, false);
            result.add(new WeaponGroup("direct-hud-group-rmb", label("BadgeRMB"), "badge badge-rmb",
                    label("GroupSecondary"), close ? label("TOO_CLOSE") : label("GroupSecondaryHint"),
                    close ? HINT_WARN : HINT, "group-header", false, () -> {}, right));
        }
        if (!other.isEmpty()) {
            result.add(new WeaponGroup("direct-hud-group-other", label("BadgeAI"), "badge badge-ai",
                    label("GroupOther"), label("GroupOtherHint"), "group-header", false, () -> {}, other));
        }
        return result;
    }

    /** Whether a mount the left (or right) button fires is refusing the cursor as too close. */
    private static boolean anyTooClose(BattleDirectControlStatus.Snapshot snapshot, boolean leftButton) {
        for (WeaponStatus weapon : snapshot.weapons()) {
            if (weapon.tooClose() && weapon.trigger().contains(leftButton ? "L" : "R")) return true;
        }
        return false;
    }

    /** Single-weapon carriers: the left button's weapon, then keyed abilities or a ship's systems. */
    private List<WeaponGroup> otherGroups(BattleDirectControlStatus.Snapshot snapshot, boolean ship) {
        List<WeaponGroup> result = new ArrayList<>();
        List<WeaponRow> fire = new ArrayList<>();
        for (WeaponStatus weapon : snapshot.weapons()) fire.add(weaponRow(weapon, false, false, ""));
        if (!fire.isEmpty()) {
            result.add(new WeaponGroup("direct-hud-group-lmb", label("BadgeLMB"), "badge badge-lmb",
                    label("GroupFire"), "", "group-header", false, () -> {}, fire));
        }
        if (ship) {
            List<WeaponRow> systems = new ArrayList<>();
            for (var card : snapshot.warship().cards()) systems.add(shipRow(card));
            result.add(new WeaponGroup("direct-hud-group-systems", "", "badge badge-none",
                    label("GroupSystems"), "", "group-header", false, () -> {}, systems));
        }
        List<WeaponRow> abilities = new ArrayList<>();
        for (var ability : snapshot.abilities()) abilities.add(abilityRow(ability));
        if (!abilities.isEmpty()) {
            result.add(new WeaponGroup("direct-hud-group-abilities", label("BadgeKeys"), "badge badge-keys",
                    label("GroupAbilities"), "", "group-header", false, () -> {}, abilities));
        }
        return result;
    }

    private WeaponRow weaponRow(WeaponStatus weapon, boolean mech, boolean highlighted, String tag) {
        boolean enabled = weapon.directEligible();
        String state = enabled ? label(weapon.state().name()) : label("Indirect");
        if (enabled && weapon.state() == WeaponState.BURST) state += " " + weapon.burstRemaining();
        if (enabled && weapon.state() == WeaponState.CYCLING) {
            state = (mech && !weapon.mechanism().isEmpty() ? weapon.mechanism() + " " : "")
                    + seconds(weapon.cooldownSeconds());
        }
        boolean fluxHeld = enabled && weapon.fluxBlocked()
                && weapon.state() != WeaponState.BURST && weapon.state() != WeaponState.EMPTY;
        if (fluxHeld) state = label("FluxHold");
        String stateClasses = "weapon-state";
        if (fluxHeld || weapon.state() == WeaponState.EMPTY || weapon.state() == WeaponState.TOO_CLOSE) {
            stateClasses += " weapon-empty";
        }
        else if (weapon.state() != WeaponState.LOADED) stateClasses += " weapon-busy";
        String classes = "weapon" + (highlighted ? " weapon-selected selected" : "")
                + (!enabled ? " weapon-unsupported" : "");
        String key = mech ? String.valueOf(weapon.number() + 1) : "";
        String name = weapon.name() + (tag.isEmpty() ? "" : " · " + tag);
        return new WeaponRow("direct-hud-weapon-" + weapon.slotKey(), classes,
                rowStyle(highlighted, enabled ? 1f : .6f), !enabled || !mech, key,
                key.isEmpty() ? "key key-none" : "key", name, behavior(weapon.behavior()),
                ammo(weapon.ammo()), state, stateClasses, width(weapon.cycleFraction()),
                () -> select.accept(weapon.number()));
    }

    private WeaponRow abilityRow(BattleDirectControlStatus.AbilityStatus ability) {
        boolean shield = ability.ability() == DirectControlAbility.SHIELD;
        boolean active = ability.activeSeconds() > 0f;
        boolean broken = shield && ability.broken();
        boolean empty = !shield && ability.remaining() <= 0 && !active;
        String state = broken ? label("Broken") : active ? label(shield ? "Active" : "Throwing")
                : empty ? label("EMPTY")
                : ability.cooldownSeconds() > 0f ? seconds(ability.cooldownSeconds())
                : label(ability.ready() ? "Ready" : "Busy");
        String detail = active ? seconds(ability.activeSeconds()) : label(shield ? "Directional" : "CursorThrow");
        String resource = shield ? (active ? Math.round(ability.soakRemaining()) + " / "
                + Math.round(ability.soakCapacity()) : "")
                : ability.remaining() + " / " + ability.capacity();
        float fill = broken ? 0f : shield && active ? fraction(ability.soakRemaining(), ability.soakCapacity())
                : active ? 1f - fraction(ability.activeSeconds(), ability.durationSeconds())
                : ability.cooldownSeconds() > 0f ? 1f - fraction(ability.cooldownSeconds(), ability.cooldownDurationSeconds())
                : ability.ready() ? 1f : 0f;
        return new WeaponRow("direct-hud-ability-" + ability.ability().name(), "weapon ability",
                rowStyle(false, 1f), true, shield ? "E" : "G", "key", label(shield ? "Shield" : "Smoke"),
                detail, resource, state, "weapon-state" + (broken || empty ? " weapon-empty"
                : !ability.ready() ? " weapon-busy" : ""), width(fill), () -> {});
    }

    /**
     * A row's look, inline because the shared theme is applied after a component's own sheet and
     * would otherwise give every row its raised button face. The HUD floats over the battle, so a
     * row is see-through and only the selected one carries a gold wash and edge.
     */
    private static String rowStyle(boolean selected, float opacity) {
        return "opacity: " + (opacity >= 1f ? "1" : String.valueOf(opacity)) + "; background-image: none;"
                + " border-style: solid; background-color: " + (selected ? "#d6c27a24" : "transparent") + ";"
                + " border-color: " + (selected ? "#d6c27a99" : "transparent") + ";";
    }

    /** A warship card: read-only, like the Marine's ability rows. */
    private WeaponRow shipRow(BattleDirectControlStatus.ShipCard card) {
        String stateClasses = "weapon-state" + switch (card.tone()) {
            case READY -> "";
            case BUSY -> " weapon-busy";
            case WARN -> " weapon-empty";
        };
        return new WeaponRow("direct-hud-ship-" + card.key(), "weapon ship", rowStyle(false, 1f), true, "",
                "key key-none", card.name(), card.detail(), card.resource(), card.state(), stateClasses,
                width(card.fill()) + " background-color: " + toneColor(card.tone()) + ";", () -> {});
    }

    /** A status assembled without button assignments (no weapon carries one) falls back to the selection alone. */
    private static boolean weaponTriggerUnknown(BattleDirectControlStatus.Snapshot snapshot) {
        for (var weapon : snapshot.weapons()) if (!weapon.trigger().isEmpty()) return false;
        return true;
    }

    private static String toneColor(Tone tone) {
        return switch (tone) {
            case READY -> "#b6c79b";
            case BUSY -> "#eab152";
            case WARN -> "#ef7962";
        };
    }

    private static String stateTone(Tone tone) {
        return switch (tone) {
            case READY -> "state-calm";
            case BUSY -> "state-warn";
            case WARN -> "state-alarm";
        };
    }

    private static String seconds(float value) { return String.format(Strings.locale(), "%.1fs", value); }
    private static String percent(float fraction) {
        return Math.round(Math.max(0f, Math.min(1f, fraction)) * 100f) + "%";
    }
    private static float fraction(float value, float maximum) {
        return maximum > 0f ? Math.max(0f, Math.min(1f, value / maximum)) : 0f;
    }

    private String behavior(BattleDirectControlStatus.Behavior behavior) {
        String value = label(behavior.kind().name());
        int count = behavior.kind() == BattleDirectControlStatus.FireKind.PACKET
                ? behavior.projectilesPerShot() : behavior.projectilesPerTrigger();
        if (count > 1) value += " ×" + count;
        if (behavior.kind() == BattleDirectControlStatus.FireKind.BURST && behavior.projectilesPerShot() > 1) {
            value += " · " + label("PACKET") + " ×" + behavior.projectilesPerShot();
        }
        return value;
    }

    private String ammo(BattleDirectControlStatus.Ammo ammo) {
        if (ammo.unit() == BattleDirectControlStatus.AmmoUnit.UNTRACKED) return "";
        if (ammo.unlimited()) return label("Unlimited");
        return ammo.remaining() + " / " + ammo.capacity() + " " + label(ammo.unit().name());
    }

    private static final String HINT = "group-hint";
    /** A group header hint that is a warning: a mount on that button refuses the cursor. */
    private static final String HINT_WARN = "group-hint group-hint-warn";

    private String label(String suffix) { return copy.apply("battleDirectHud" + suffix); }
    private void set(String key, String value) { text.get(key).set(value); }
    private static String width(float fraction) { return "width: " + Math.round(fraction * 100f) + "%;"; }

    /** One button's worth of equipment; the header resets the left button to its group where that applies. */
    record WeaponGroup(String id, String badge, String badgeClasses, String name, String hint,
                       String hintClasses, String headerClasses, boolean headerDisabled, Runnable action,
                       List<WeaponRow> weapons) implements MarkupPropertySource {
        WeaponGroup {
            weapons = List.copyOf(weapons);
        }

        WeaponGroup(String id, String badge, String badgeClasses, String name, String hint,
                    String headerClasses, boolean headerDisabled, Runnable action, List<WeaponRow> weapons) {
            this(id, badge, badgeClasses, name, hint, HINT, headerClasses, headerDisabled, action, weapons);
        }

        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> id;
                case "badge" -> badge;
                case "badgeClasses" -> badgeClasses;
                case "name" -> name;
                case "hint" -> hint;
                case "hintClasses" -> hintClasses;
                case "headerClasses" -> headerClasses;
                case "headerDisabled" -> headerDisabled;
                case "action" -> action;
                case "weapons" -> weapons;
                case "headerId" -> id + "-header";
                case "rowsId" -> id + "-rows";
                case "badgeId" -> id + "-badge";
                case "nameId" -> id + "-name";
                case "hintId" -> id + "-hint";
                default -> throw new IllegalArgumentException("Unknown weapon group property: " + key);
            };
        }
    }

    record WeaponRow(String id, String classes, String style, boolean disabled, String key, String keyClasses,
                     String name, String behavior, String ammo, String state, String stateClasses,
                     String cycleStyle, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "style" -> style;
                case "disabled" -> disabled;
                case "key" -> key;
                case "keyClasses" -> keyClasses;
                case "name" -> name;
                case "behavior" -> behavior;
                case "ammo" -> ammo;
                case "state" -> state;
                case "stateClasses" -> stateClasses;
                case "cycleStyle" -> cycleStyle;
                case "action" -> action;
                case "topId" -> id + "-top";
                case "keyId" -> id + "-key";
                case "dataId" -> id + "-data";
                case "stateId" -> id + "-state";
                case "nameId" -> id + "-name";
                case "behaviorId" -> id + "-behavior";
                case "ammoId" -> id + "-ammo";
                case "cycleId" -> id + "-cycle";
                case "fillId" -> id + "-fill";
                default -> throw new IllegalArgumentException("Unknown weapon row property: " + property);
            };
        }
    }
}
