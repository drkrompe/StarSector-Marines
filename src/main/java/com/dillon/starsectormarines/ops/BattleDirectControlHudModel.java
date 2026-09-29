package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.DirectControlAbility;
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
    private final Map<String, Object> actionsProps = new LinkedHashMap<>();
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
        for (String key : List.of("healthStyle", "healthClasses", "armorStyle", "weaponsClasses",
                "allLabel", "allClasses", "exitLabel", "pauseLabel")) {
            MutableSignal<String> signal = reactor.signal("");
            text.put(key, signal);
            if (key.equals("exitLabel") || key.equals("pauseLabel")) actionsProps.put(key, signal);
            else props.put(key, signal);
        }
        weapons = reactor.signal(List.of());
        allDisabled = reactor.signal(true);
        props.put("weapons", weapons);
        props.put("allDisabled", allDisabled);
        props.put("allAction", (Runnable) () -> select.accept(0));
        actionsProps.put("exitAction", exit);
        actionsProps.put("pauseAction", pause);
    }

    Map<String, Object> props() { return props; }
    Map<String, Object> actionsProps() { return actionsProps; }

    void update(BattleDirectControlStatus.Snapshot snapshot, int selected, boolean paused) {
        boolean mech = snapshot.carrier() == BattleDirectControlStatus.Carrier.MECH;
        var durability = snapshot.durability();
        reactor.untracked(() -> {
            set("healthStyle", width(durability.hpFraction()));
            set("healthClasses", "health-fill" + (durability.hpFraction() <= .25f ? " capacity-low" : ""));
            set("armorStyle", width(durability.armorFraction()));
            set("weaponsClasses", mech ? "weapons" : "weapons weapons-single");
            set("allLabel", label("AllDirect"));
            set("allClasses", !mech ? "all all-hidden" : "all" + (selected == 0 ? " selected" : ""));
            allDisabled.set(!mech);
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
                String style = "opacity: " + (enabled ? "1" : "0.6") + ";";
                String name = (mech ? "[" + (weapon.number() + 1) + "] " : "") + weapon.name();
                cards.add(new WeaponCard("direct-hud-weapon-" + weapon.slotKey(), classes, style,
                        !enabled || !mech, name, behavior(weapon.behavior()),
                        ammo(weapon.ammo()), state, stateClasses, width(weapon.cycleFraction()),
                        () -> select.accept(weapon.number())));
            }
            for (var ability : snapshot.abilities()) cards.add(abilityCard(ability));
            weapons.set(List.copyOf(cards));
        });
    }

    private WeaponCard abilityCard(BattleDirectControlStatus.AbilityStatus ability) {
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
        return new WeaponCard("direct-hud-ability-" + ability.ability().name(), "weapon ability",
                "opacity: 1;", true, (shield ? "[E] " : "[G] ") + label(shield ? "Shield" : "Smoke"),
                detail, resource, state, "weapon-state" + (broken || empty ? " weapon-empty"
                : !ability.ready() ? " weapon-busy" : ""), width(fill), () -> {});
    }

    private static String seconds(float value) { return String.format(Locale.ROOT, "%.1fs", value); }
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
        if (ammo.unit() == BattleDirectControlStatus.AmmoUnit.UNTRACKED) return label("Untracked");
        if (ammo.unlimited()) return label("Unlimited");
        return ammo.remaining() + " / " + ammo.capacity() + " " + label(ammo.unit().name());
    }

    private String label(String suffix) { return copy.apply("battleDirectHud" + suffix); }
    private void set(String key, String value) { text.get(key).set(value); }
    private static String width(float fraction) { return "width: " + Math.round(fraction * 100f) + "%;"; }

    record WeaponCard(String id, String classes, String style, boolean disabled, String name,
                      String behavior, String ammo, String state, String stateClasses,
                      String cycleStyle, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> id;
                case "classes" -> classes;
                case "style" -> style;
                case "disabled" -> disabled;
                case "name" -> name;
                case "behavior" -> behavior;
                case "ammo" -> ammo;
                case "state" -> state;
                case "stateClasses" -> stateClasses;
                case "cycleStyle" -> cycleStyle;
                case "action" -> action;
                case "topId" -> id + "-top";
                case "dataId" -> id + "-data";
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
