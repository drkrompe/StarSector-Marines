package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
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

/** Equipment-aware action HUD. Values and commands remain owned by the battle. */
final class BattleDirectControlHudModel {
    private final Reactor reactor;
    private final IntConsumer select;
    private final Function<String, String> copy;
    private final Map<String, Object> props = new LinkedHashMap<>();
    private final Map<String, MutableSignal<String>> text = new LinkedHashMap<>();
    private final MutableSignal<List<WeaponCard>> weapons;
    private final MutableSignal<Boolean> allDisabled;

    BattleDirectControlHudModel(Reactor reactor, IntConsumer select, Runnable exit, Runnable pause) {
        this(reactor, select, exit, pause, Strings::get);
    }

    BattleDirectControlHudModel(Reactor reactor, IntConsumer select, Runnable exit,
                               Runnable pause, Function<String, String> copy) {
        this.reactor = reactor;
        this.select = select;
        this.copy = copy;
        for (String key : List.of("identity", "carrier", "healthLabel", "healthValue", "healthStyle",
                "healthClasses", "armorLabel", "armorValue", "armorStyle", "armorClasses",
                "allLabel", "allClasses", "allStyle", "selectionHint", "controls", "exitLabel", "pauseLabel")) {
            MutableSignal<String> signal = reactor.signal("");
            text.put(key, signal);
            props.put(key, signal);
        }
        weapons = reactor.signal(List.of());
        allDisabled = reactor.signal(true);
        props.put("weapons", weapons);
        props.put("allDisabled", allDisabled);
        props.put("allAction", (Runnable) () -> select.accept(0));
        props.put("exitAction", exit);
        props.put("pauseAction", pause);
    }

    Map<String, Object> props() { return props; }

    void update(BattleDirectControlStatus.Snapshot snapshot, int selected, boolean paused) {
        boolean mech = snapshot.carrier() == BattleDirectControlStatus.Carrier.MECH;
        var durability = snapshot.durability();
        reactor.untracked(() -> {
            set("identity", snapshot.name());
            set("carrier", snapshot.carrier() == BattleDirectControlStatus.Carrier.NONE
                    ? "" : label(snapshot.carrier().name()));
            set("healthLabel", label(snapshot.carrier() == BattleDirectControlStatus.Carrier.MARINE
                    ? "Health" : "Structure"));
            set("healthValue", capacity(durability.hp(), durability.maxHp()));
            set("healthStyle", width(durability.hpFraction()));
            set("healthClasses", "health-fill" + (durability.hpFraction() <= .25f ? " capacity-low" : ""));
            set("armorLabel", label("Armor"));
            set("armorValue", durability.maxArmor() > 0f
                    ? capacity(durability.armor(), durability.maxArmor())
                    + (durability.armor() <= 0f ? "  ·  " + label("Exposed") : "")
                    : label("Unarmored"));
            set("armorStyle", width(durability.armorFraction()));
            set("armorClasses", "armor-fill");
            set("allLabel", mech ? label("AllDirect") : label("Primary"));
            set("allClasses", "all" + (selected == 0 ? " selected" : ""));
            set("allStyle", "opacity: 1;");
            allDisabled.set(!mech);
            set("selectionHint", label(mech ? "SelectHint" : "AimHint"));
            set("controls", label(snapshot.carrier() == BattleDirectControlStatus.Carrier.VEHICLE
                    ? "DriveHint" : "MoveHint"));
            set("exitLabel", label("Exit"));
            set("pauseLabel", copy.apply(paused ? "battleSpeed1x" : "battleSpeedPause"));
            List<WeaponCard> cards = new ArrayList<>();
            for (var weapon : snapshot.weapons()) {
                boolean enabled = weapon.directEligible();
                boolean chosen = enabled && (!mech || selected == 0 || selected == weapon.number());
                String classes = "weapon" + (chosen ? " weapon-selected selected" : "")
                        + (!enabled ? " weapon-unsupported" : "");
                String state = enabled ? label(weapon.state().name()) : label("Indirect");
                if (enabled && weapon.state() == BattleDirectControlStatus.WeaponState.BURST) {
                    state += " " + weapon.burstRemaining();
                }
                if (enabled && weapon.state() == BattleDirectControlStatus.WeaponState.CYCLING) {
                    state = String.format(Locale.ROOT, "%.1fs", weapon.cooldownSeconds());
                }
                String stateClasses = "weapon-state";
                if (weapon.state() == BattleDirectControlStatus.WeaponState.EMPTY) stateClasses += " weapon-empty";
                else if (weapon.state() != BattleDirectControlStatus.WeaponState.LOADED) stateClasses += " weapon-busy";
                String slot = (mech ? "[" + (weapon.number() + 1) + "] " : "") + label(weapon.slotKey());
                String style = "opacity: " + (enabled ? "1" : "0.6") + ";";
                String name = (mech ? "[" + (weapon.number() + 1) + "] " : "") + weapon.name();
                cards.add(new WeaponCard("direct-hud-weapon-" + weapon.slotKey(), classes, style,
                        !enabled || !mech, slot, name, behavior(weapon.behavior()),
                        ammo(weapon.ammo()), state, stateClasses, width(weapon.cycleFraction()),
                        () -> select.accept(weapon.number())));
            }
            weapons.set(List.copyOf(cards));
        });
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
        if (ammo.unit() == BattleDirectControlStatus.AmmoUnit.UNTRACKED) return label("Untracked");
        if (ammo.unlimited()) return label("Unlimited");
        return ammo.remaining() + " / " + ammo.capacity() + " " + label(ammo.unit().name());
    }

    private String label(String suffix) { return copy.apply("battleDirectHud" + suffix); }
    private void set(String key, String value) { text.get(key).set(value); }
    private static String capacity(float current, float maximum) {
        return number(current) + " / " + number(maximum);
    }
    private static String number(float value) { return Integer.toString(Math.round(Math.max(0f, value))); }
    private static String width(float fraction) { return "width: " + Math.round(fraction * 100f) + "%;"; }

    record WeaponCard(String id, String classes, String style, boolean disabled, String slot, String name,
                      String behavior, String ammo, String state, String stateClasses,
                      String cycleStyle, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> id;
                case "classes" -> classes;
                case "style" -> style;
                case "disabled" -> disabled;
                case "slot" -> slot;
                case "name" -> name;
                case "behavior" -> behavior;
                case "ammo" -> ammo;
                case "state" -> state;
                case "stateClasses" -> stateClasses;
                case "cycleStyle" -> cycleStyle;
                case "action" -> action;
                case "topId" -> id + "-top";
                case "dataId" -> id + "-data";
                case "slotId" -> id + "-slot";
                case "stateId" -> id + "-state";
                case "nameId" -> id + "-name";
                case "behaviorId" -> id + "-behavior";
                case "ammoId" -> id + "-ammo";
                case "cycleId" -> id + "-cycle";
                case "fillId" -> id + "-fill";
                default -> throw new IllegalArgumentException("Unknown weapon card property: " + key);
            };
        }
    }
}
