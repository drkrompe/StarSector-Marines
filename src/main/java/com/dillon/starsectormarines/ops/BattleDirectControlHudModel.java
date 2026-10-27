package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.ui.panel.AmmoStrip;
import com.dillon.starsectormarines.battle.ui.panel.ChargeGauge;
import com.dillon.starsectormarines.battle.ui.panel.MagazineGauge;
import com.dillon.starsectormarines.battle.ui.panel.Ordnance;
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
import java.util.Map;
import java.util.function.Function;

/**
 * The direct-control HUD as two panels, after the game's own combat HUD: a status panel
 * (bottom left) reading the body's hull, armor, flux and stamina, and a weapon-groups panel
 * (bottom right) listing equipment under the button that fires it. Values and commands remain
 * owned by the battle.
 */
public final class BattleDirectControlHudModel {
    /** The flux gauge's resting fill, mech or warship: the violet the game's own flux reads in. */
    private static final String FLUX_FILL = "#a594f9";
    private static final String HIDDEN_ROW = "row row-hidden";

    /**
     * What the weapons panel's clicks ask for. {@code select} is a number key; a mech's trigger
     * headers cycle and its rows pick, which a host without triggers may ignore.
     */
    interface WeaponActions {
        void select(int key);
        default void cycle(FireTrigger trigger) { }
        /** {@code member} is LINKED or a hardpoint ({@code slot.ordinal() + 1}) of that trigger's group. */
        default void pick(FireTrigger trigger, int member) { }
    }

    /** How far a member its trigger's pick leaves out fades: it holds fire. */
    private static final float UNPICKED_OPACITY = .45f;

    private final Reactor reactor;
    private final WeaponActions select;
    private final Function<String, String> copy;
    private final Map<String, Object> statusProps = new LinkedHashMap<>();
    private final Map<String, Object> weaponsProps = new LinkedHashMap<>();
    private final Map<String, Object> actionsProps = new LinkedHashMap<>();
    private final Map<String, MutableSignal<String>> text = new LinkedHashMap<>();
    private final MutableSignal<List<WeaponGroup>> groups;
    private String exitLabelKey = "battleDirectHudExit";
    private String structureKey = "";
    private Carrier carrier = Carrier.NONE;

    BattleDirectControlHudModel(Reactor reactor, WeaponActions select, Runnable exit, Runnable pause) {
        this(reactor, select, exit, pause, Strings::get);
    }

    BattleDirectControlHudModel(Reactor reactor, WeaponActions select, Runnable exit,
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
            boolean stamina = marine || mech;
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
            // A hull with more than one gun has a mech's two buttons.
            boolean groupedHull = carrier == Carrier.VEHICLE && snapshot.weapons().size() > 1;
            List<WeaponGroup> next = mech || groupedHull ? mechGroups(snapshot, selected) : otherGroups(snapshot, ship);
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
        // A mech always has a reservoir; a hull shows its gun's flux when the gun builds any.
        if (!mech && snapshot.flux() == null) {
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
     * A mech's (or a many-gunned hull's) mounts under the button whose list they are in: the left button's guns, the right
     * button's racks, and whatever neither lists. Each header names its trigger's pick and the key
     * that cycles it; the picked rows carry the wash and the rest, which hold fire, dim.
     */
    private List<WeaponGroup> mechGroups(BattleDirectControlStatus.Snapshot snapshot, int selected) {
        boolean unknown = weaponTriggerUnknown(snapshot);
        List<WeaponStatus> left = new ArrayList<>(), right = new ArrayList<>(), other = new ArrayList<>();
        for (WeaponStatus weapon : snapshot.weapons()) {
            String listed = weapon.listedOn();
            if (unknown ? weapon.directEligible() : listed.contains("L")) left.add(weapon);
            else if (listed.contains("R")) right.add(weapon);
            else other.add(weapon);
        }
        List<WeaponGroup> result = new ArrayList<>();
        result.add(triggerGroup(snapshot, FireTrigger.LEFT, left, unknown, selected, true));
        if (!right.isEmpty()) result.add(triggerGroup(snapshot, FireTrigger.RIGHT, right, false, 0, true));
        if (!other.isEmpty()) {
            List<WeaponRow> rows = new ArrayList<>();
            for (WeaponStatus weapon : other) rows.add(weaponRow(weapon, true, false, false, "", () -> { }));
            result.add(new WeaponGroup("direct-hud-group-other", label("BadgeAI"), "badge badge-ai",
                    label("GroupOther"), label("GroupOtherHint"), "group-header", false, () -> {}, rows));
        }
        return result;
    }

    /**
     * One trigger's header and rows. A mech's list is LINKED and then each member, so a left member's
     * number key is its place after key 1; a group of one has only key 1. A Marine's left list has no
     * LINKED ({@code linkedFirst} false): the primary is key 1 and each special the next.
     */
    private WeaponGroup triggerGroup(BattleDirectControlStatus.Snapshot snapshot, FireTrigger trigger,
                                     List<WeaponStatus> members, boolean unknown, int selected,
                                     boolean linkedFirst) {
        boolean leftButton = trigger == FireTrigger.LEFT;
        String letter = leftButton ? "L" : "R";
        List<Boolean> picked = new ArrayList<>(members.size());
        int pickedCount = 0;
        for (WeaponStatus weapon : members) {
            boolean on = unknown ? selected == 0 || selected == weapon.number() : weapon.trigger().contains(letter);
            picked.add(on);
            if (on) pickedCount++;
        }
        boolean cycles = members.size() > 1;
        boolean linked = cycles && pickedCount == members.size();
        String pickName = label("PickNone");
        if (members.size() == 1) pickName = members.get(0).name();
        else if (linked) pickName = label("PickLinked");
        else for (int i = 0; i < members.size(); i++) {
            if (picked.get(i)) {
                pickName = pickName(members, i);
                break;
            }
        }
        List<WeaponRow> rows = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            WeaponStatus weapon = members.get(i);
            int keyIndex = cycles && linkedFirst ? i + 1 : i;
            String key = leftButton ? String.valueOf(keyIndex + 1) : "";
            Runnable action = leftButton ? () -> select.select(keyIndex)
                    : () -> select.pick(trigger, weapon.number());
            boolean on = picked.get(i);
            rows.add(weaponRow(weapon, true, on && cycles, cycles && !on, key, action));
        }
        // The header reads LMB  LINKED  [Q]: the pick, then the key that cycles it (owner, 2026-10-23).
        String hint = cycles ? label(leftButton ? "KeyCycleLeft" : "KeyCycleRight") : "";
        boolean close = anyTooClose(snapshot, leftButton);
        String id = leftButton ? "direct-hud-group-lmb" : "direct-hud-group-rmb";
        return new WeaponGroup(id, label(leftButton ? "BadgeLMB" : "BadgeRMB"),
                leftButton ? "badge badge-lmb" : "badge badge-rmb", pickName,
                close ? label("TOO_CLOSE") : hint, close ? HINT_WARN : HINT,
                cycles ? "group-header group-header-action" : "group-header", !cycles,
                () -> select.cycle(trigger), rows);
    }

    /**
     * A picked member's name, with its hardpoint when another member shares it (a Bulwark's two
     * chainguns). The heading font has no middle dot and draws parentheses thinly, so the hardpoint
     * leads with a space.
     */
    private String pickName(List<WeaponStatus> members, int index) {
        String name = members.get(index).name();
        for (int i = 0; i < members.size(); i++) {
            if (i != index && members.get(i).name().equals(name)) {
                return label("Side" + members.get(index).slotKey()) + " " + name;
            }
        }
        return name;
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
        if (snapshot.carrier() == Carrier.MARINE && snapshot.weapons().size() > 1) {
            // A Marine with a direct-fire special: Q cycles the left button from the primary to it.
            result.add(triggerGroup(snapshot, FireTrigger.LEFT, snapshot.weapons(), false, 0, false));
            addAbilities(snapshot, result);
            return result;
        }
        List<WeaponRow> fire = new ArrayList<>();
        for (WeaponStatus weapon : snapshot.weapons()) {
            fire.add(weaponRow(weapon, false, false, false, "", () -> select.select(weapon.number())));
        }
        if (!fire.isEmpty()) {
            // One weapon on the button: the header names it, as a cycled pick's header does.
            String name = snapshot.weapons().size() == 1 ? snapshot.weapons().get(0).name() : label("GroupFire");
            result.add(new WeaponGroup("direct-hud-group-lmb", label("BadgeLMB"), "badge badge-lmb",
                    name, "", "group-header", false, () -> {}, fire));
        }
        if (ship) {
            List<WeaponRow> systems = new ArrayList<>();
            for (var card : snapshot.warship().cards()) systems.add(shipRow(card));
            result.add(new WeaponGroup("direct-hud-group-systems", "", "badge badge-none",
                    label("GroupSystems"), "", "group-header", false, () -> {}, systems));
        }
        addAbilities(snapshot, result);
        return result;
    }

    /** A Marine's right-button items under RMB; another body's keyed abilities under KEYS, when it has any. */
    private void addAbilities(BattleDirectControlStatus.Snapshot snapshot, List<WeaponGroup> result) {
        if (snapshot.carrier() == Carrier.MARINE) {
            if (!snapshot.abilities().isEmpty()) result.add(marineRightGroup(snapshot.abilities()));
            return;
        }
        List<WeaponRow> abilities = new ArrayList<>();
        for (var ability : snapshot.abilities()) abilities.add(abilityRow(ability, false, false, () -> {}));
        if (!abilities.isEmpty()) {
            result.add(new WeaponGroup("direct-hud-group-abilities", label("BadgeKeys"), "badge badge-keys",
                    label("GroupAbilities"), "", "group-header", false, () -> {}, abilities));
        }
    }

    /** How a weapon row's state reads: ready, busy cycling or reloading, or unable to fire. */
    public enum StateTone {
        READY(""), BUSY("weapon-busy"), EMPTY("weapon-empty");

        final String className;

        StateTone(String className) {
            this.className = className;
        }
    }

    /**
     * A marine primary's HUD row at one moment, for showing it outside battle.
     *
     * @param state the row's state text
     * @param tone  how the state text is coloured
     * @param cycleFraction the cycle bar's fill
     * @param strip the load under the bar, or null for none
     */
    public record PrimaryRow(String state, StateTone tone, float cycleFraction, AmmoStrip strip) {}

    /**
     * The row the HUD would show for a marine's primary in this state, through the same status, state
     * text and strip a marine in battle gets ({@code moddable-weapons-nouns.md}, catalog preview).
     */
    public static PrimaryRow marinePrimaryRow(WeaponDef weapon, EquipmentGrade grade,
                                              float cooldown, float cycleDuration, int burstRemaining,
                                              float burstTimer, int roundsLoaded, float reloadRemaining,
                                              float reloadDuration) {
        WeaponStatus status = BattleDirectControlStatus.marineWeapon(weapon, grade, cooldown, cycleDuration,
                burstRemaining, burstTimer, roundsLoaded, reloadRemaining, reloadDuration);
        return new PrimaryRow(stateText(Strings::get, status, false), stateTone(status),
                status.cycleFraction(), strip(status));
    }

    private static String stateText(Function<String, String> copy, WeaponStatus weapon, boolean mech) {
        Function<String, String> label = suffix -> copy.apply("battleDirectHud" + suffix);
        boolean enabled = weapon.directEligible();
        String state = enabled ? label.apply(weapon.state().name()) : label.apply("Indirect");
        if (enabled && weapon.state() == WeaponState.BURST) state += " " + weapon.burstRemaining();
        if (enabled && weapon.state() == WeaponState.RELOADING) {
            state = label.apply("RELOADING") + " " + seconds(weapon.cooldownSeconds());
        }
        if (enabled && weapon.state() == WeaponState.SPINNING) {
            state = label.apply("SPINNING") + " " + Math.round(weapon.cycleFraction() * 100f) + "%";
        }
        if (enabled && weapon.state() == WeaponState.CYCLING) {
            state = (mech && !weapon.mechanism().isEmpty() ? weapon.mechanism() + " " : "")
                    + seconds(weapon.cooldownSeconds());
        }
        if (fluxHeld(weapon)) state = label.apply("FluxHold");
        return state;
    }

    private static StateTone stateTone(WeaponStatus weapon) {
        if (fluxHeld(weapon) || weapon.state() == WeaponState.EMPTY || weapon.state() == WeaponState.TOO_CLOSE) {
            return StateTone.EMPTY;
        }
        return weapon.state() != WeaponState.LOADED ? StateTone.BUSY : StateTone.READY;
    }

    private static boolean fluxHeld(WeaponStatus weapon) {
        return weapon.directEligible() && weapon.fluxBlocked()
                && weapon.state() != WeaponState.BURST && weapon.state() != WeaponState.EMPTY;
    }

    /** {@code dimmed}: a member its trigger's pick leaves out, which holds fire. */
    private WeaponRow weaponRow(WeaponStatus weapon, boolean mech, boolean highlighted, boolean dimmed,
                                String key, Runnable action) {
        boolean enabled = weapon.directEligible();
        String state = stateText(copy, weapon, mech);
        StateTone tone = stateTone(weapon);
        String stateClasses = tone == StateTone.READY ? "weapon-state" : "weapon-state " + tone.className;
        String classes = "weapon" + (highlighted ? " weapon-selected selected" : "")
                + (!enabled ? " weapon-unsupported" : "");
        String name = weapon.name();
        String id = "direct-hud-weapon-" + weapon.slotKey();
        AmmoStrip strip = strip(weapon);
        // A folded strip is read to the burst, so its exact count stays beside it.
        boolean counted = strip instanceof MagazineGauge gauge && !gauge.folded()
                || strip != null && weapon.ammo().unlimited();
        return new WeaponRow(id, classes + (strip != null ? " weapon-magazine" : ""),
                rowStyle(highlighted, !enabled ? .6f : dimmed ? UNPICKED_OPACITY : 1f)
                        + (strip != null ? " height: 74px;" : ""),
                !enabled || !mech, key,
                key.isEmpty() ? "key key-none" : "key", name, behavior(weapon.behavior()),
                counted ? "" : ammo(weapon.ammo()), state, stateClasses, width(weapon.cycleFraction()),
                action, roundBlocks(id, strip),
                strip != null ? "weapon-rounds" : "weapon-rounds weapon-rounds-none");
    }

    /**
     * The load drawn under a row: an energy weapon's charge cells or a flamer's fuel, or a counted load round by round
     * (a Marine's magazine, a mech's racks in salvos, a vehicle's belt). Null for a weapon with nothing
     * to count, and for a load too long to draw even folded, which keeps its number.
     */
    static AmmoStrip strip(WeaponStatus weapon) {
        var behavior = weapon.behavior();
        var ammo = weapon.ammo();
        if (ammo.unit() == BattleDirectControlStatus.AmmoUnit.FUEL) {
            return ChargeGauge.fuel(ammo.capacity(), ammo.remaining());
        }
        if (behavior.ordnance() == Ordnance.ENERGY) {
            return ChargeGauge.of(behavior.projectilesPerTrigger(), weapon.burstRemaining(),
                    weapon.cycleFraction(), weapon.fluxBlocked());
        }
        if (ammo.unit() == BattleDirectControlStatus.AmmoUnit.UNTRACKED || ammo.unlimited()
                || ammo.capacity() <= 0) {
            // A gun with nothing to count still shows the burst one click sends.
            return behavior.ordnance() == Ordnance.BALLISTIC
                    ? ChargeGauge.burst(behavior.projectilesPerTrigger(), weapon.burstRemaining(),
                            weapon.cycleFraction())
                    : null;
        }
        MagazineGauge gauge;
        if (ammo.unit() == BattleDirectControlStatus.AmmoUnit.TRIGGER_PACKS) {
            int perPack = Math.max(1, behavior.projectilesPerTrigger());
            gauge = MagazineGauge.of(behavior.ordnance(), behavior.projectilesPerShot(), perPack,
                    ammo.capacity() * perPack, ammo.remaining() * perPack, 0f, 0f);
        } else {
            gauge = MagazineGauge.of(behavior.ordnance(), behavior.projectilesPerShot(),
                    behavior.projectilesPerTrigger(), ammo.capacity(), ammo.remaining(),
                    weapon.reloading() ? weapon.cooldownSeconds() : 0f, weapon.cycleDurationSeconds());
        }
        gauge = gauge.withHeld(weapon.heldRounds());
        return gauge.fits() ? gauge : null;
    }

    /** The strip under the bar, in its groups: bursts or salvos, otherwise one block of everything. */
    private static List<RoundBlock> roundBlocks(String rowId, AmmoStrip strip) {
        if (strip == null) return List.of();
        String kind = strip.iconKind();
        List<RoundBlock> blocks = new ArrayList<>();
        List<RoundIcon> current = new ArrayList<>();
        for (int i = 0; i < strip.icons(); i++) {
            current.add(new RoundIcon(rowId + "-round-" + i, "round round-" + kind,
                    strip.iconPath(i)));
            if (strip.iconGroupEnd(i) || i == strip.icons() - 1) {
                // This layout sizes a row to its own width rather than its children's, so a block is told it.
                int width = current.size() * strip.iconWidth() + (current.size() - 1) * strip.iconGap();
                blocks.add(new RoundBlock(rowId + "-block-" + blocks.size(), "round-block round-block-" + kind,
                        "width: " + width + "px; gap: " + strip.iconGap() + "px;", current));
                current = new ArrayList<>();
            }
        }
        return blocks;
    }

    /**
     * A Marine's right button: its items in list order (smoke, a hand grenade, the shield) under a header that
     * names the pick and, when there is more than one, the E that cycles it, as a mech's trigger does. The pick
     * carries the wash. The others do not dim: G and C still use smoke and the shield whatever the pick is.
     */
    private WeaponGroup marineRightGroup(List<BattleDirectControlStatus.AbilityStatus> items) {
        boolean cycles = items.size() > 1;
        String pickName = abilityName(items.get(0));
        List<WeaponRow> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (item.picked()) pickName = abilityName(item);
            int member = i;
            rows.add(abilityRow(item, cycles && item.picked(), cycles,
                    cycles ? () -> select.pick(FireTrigger.RIGHT, member) : () -> {}));
        }
        return new WeaponGroup("direct-hud-group-rmb", label("BadgeRMB"), "badge badge-rmb", pickName,
                cycles ? label("KeyCycleRight") : "", HINT,
                cycles ? "group-header group-header-action" : "group-header", !cycles,
                () -> select.cycle(FireTrigger.RIGHT), rows);
    }

    /** Each item by its kind; a grenade's name ("GRN-FRAG") goes on its row's second line. */
    private String abilityName(BattleDirectControlStatus.AbilityStatus ability) {
        return switch (ability.ability()) {
            case SHIELD -> label("Shield");
            case SMOKE -> label("Smoke");
            case GRENADE -> label("Grenade");
            default -> ability.name();
        };
    }

    /** {@code picked}: the right button's pick, which carries the wash; {@code clickable} rows pick themselves through {@code action}. */
    private WeaponRow abilityRow(BattleDirectControlStatus.AbilityStatus ability, boolean picked,
                                 boolean clickable, Runnable action) {
        boolean shield = ability.ability() == DirectControlAbility.SHIELD;
        boolean active = ability.activeSeconds() > 0f;
        boolean broken = shield && ability.broken();
        boolean empty = !shield && ability.remaining() <= 0 && !active;
        String state = broken ? label("Broken") : active ? label(shield ? "Active" : "Throwing")
                : empty ? label("EMPTY")
                : ability.cooldownSeconds() > 0f ? seconds(ability.cooldownSeconds())
                : label(ability.ready() ? "Ready" : "Busy");
        String detail = active ? seconds(ability.activeSeconds())
                : ability.ability() == DirectControlAbility.GRENADE ? ability.name()
                : label(shield ? "Directional" : "CursorThrow");
        List<RoundBlock> items = itemBlocks("direct-hud-ability-" + ability.ability().name(), ability, broken, active);
        // Drawn throws need no count beside them; a load too long to draw keeps its number.
        String resource = shield ? (active ? Math.round(ability.soakRemaining()) + " / "
                + Math.round(ability.soakCapacity()) : "")
                : items.isEmpty() ? ability.remaining() + " / " + ability.capacity() : "";
        float fill = broken ? 0f : shield && active ? fraction(ability.soakRemaining(), ability.soakCapacity())
                : active ? 1f - fraction(ability.activeSeconds(), ability.durationSeconds())
                : ability.cooldownSeconds() > 0f ? 1f - fraction(ability.cooldownSeconds(), ability.cooldownDurationSeconds())
                : ability.ready() ? 1f : 0f;
        String key = shield ? "C" : ability.ability() == DirectControlAbility.SMOKE ? "G" : "";
        return new WeaponRow("direct-hud-ability-" + ability.ability().name(),
                "weapon ability" + (picked ? " weapon-selected selected" : "") + (items.isEmpty() ? "" : " weapon-magazine"),
                rowStyle(picked, 1f) + (items.isEmpty() ? "" : " height: 74px;"), !clickable, key,
                key.isEmpty() ? "key key-none" : "key",
                abilityName(ability), detail, resource, state, "weapon-state" + (broken || empty ? " weapon-empty"
                : !ability.ready() ? " weapon-busy" : ""), width(fill), action, items,
                items.isEmpty() ? "weapon-rounds weapon-rounds-none" : "weapon-rounds");
    }

    /**
     * A right-button item drawn under its row, as a magazine's rounds are: each smoke or hand grenade carried, the
     * ones thrown as outlines and the rest dimmed while a throw is under way or the button is not free; the shield
     * as one plate, up or ready lit, recharging dimmed, broken an outline. A load too long to draw keeps its count.
     */
    static List<RoundBlock> itemBlocks(String rowId, BattleDirectControlStatus.AbilityStatus ability,
                                       boolean broken, boolean active) {
        String kind = switch (ability.ability()) {
            case SHIELD -> "shield";
            case SMOKE -> "smoke";
            case GRENADE -> "grenade";
            default -> null;
        };
        if (kind == null) return List.of();
        boolean shield = ability.ability() == DirectControlAbility.SHIELD;
        int count = shield ? 1 : ability.capacity();
        if (count <= 0 || count > MAX_ITEM_ICONS) return List.of();
        String held = shield
                ? broken ? "spent" : active || ability.ready() ? "live" : "loading"
                : active || !ability.ready() ? "loading" : "live";
        List<RoundIcon> icons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String state = !shield && i >= ability.remaining() ? "spent" : held;
            icons.add(new RoundIcon(rowId + "-round-" + i, "round round-" + kind,
                    "graphics/ui/direct-control/round-" + kind + "-" + state + ".svg"));
        }
        int width = count * itemWidth(kind) + (count - 1) * ITEM_GAP;
        return List.of(new RoundBlock(rowId + "-block-0", "round-block round-block-" + kind,
                "width: " + width + "px; gap: " + ITEM_GAP + "px;", icons));
    }

    /** Items a right-button row draws as icons; more than this keeps its count. */
    static final int MAX_ITEM_ICONS = 8;
    private static final int ITEM_GAP = 4;

    /** An item icon's width in the strip, as the component's sheet sizes it. */
    static int itemWidth(String kind) {
        return switch (kind) {
            case "shield" -> 16;
            case "grenade" -> 12;
            default -> 10;
        };
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
        for (var weapon : snapshot.weapons()) if (!weapon.listedOn().isEmpty()) return false;
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
        // A rotary gun has no pull to count: it fires while held.
        if (behavior.kind() == BattleDirectControlStatus.FireKind.ROTARY) return label("ROTARY");
        if (behavior.kind() == BattleDirectControlStatus.FireKind.STREAM) return label("STREAM");
        String value = label(behavior.kind().name());
        int count = behavior.kind() == BattleDirectControlStatus.FireKind.PACKET
                ? behavior.projectilesPerShot() : behavior.projectilesPerTrigger();
        if (count > 1) value += " ×" + count;
        if (behavior.kind() == BattleDirectControlStatus.FireKind.BURST && behavior.projectilesPerShot() > 1) {
            value += " · " + label("PACKET") + " ×" + behavior.projectilesPerShot();
        }
        // A magazine names its trigger first; a semi-automatic single round needs no other word.
        if (behavior.trigger() != null) {
            String trigger = label("Trigger" + behavior.trigger().name());
            value = behavior.kind() == BattleDirectControlStatus.FireKind.SINGLE ? trigger : trigger + " · " + value;
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
                     String cycleStyle, Runnable action, List<RoundBlock> roundBlocks, String roundsClasses)
            implements MarkupPropertySource {
        WeaponRow {
            roundBlocks = List.copyOf(roundBlocks);
        }

        /** A row with no magazine strip. */
        WeaponRow(String id, String classes, String style, boolean disabled, String key, String keyClasses,
                  String name, String behavior, String ammo, String state, String stateClasses,
                  String cycleStyle, Runnable action) {
            this(id, classes, style, disabled, key, keyClasses, name, behavior, ammo, state, stateClasses,
                    cycleStyle, action, List.of(), "weapon-rounds weapon-rounds-none");
        }

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
                case "roundBlocks" -> roundBlocks;
                case "roundsClasses" -> roundsClasses;
                case "roundsId" -> id + "-rounds";
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
    /** One block of the strip: a burst's worth of belt, or the whole of a short magazine. */
    record RoundBlock(String id, String classes, String style, List<RoundIcon> rounds) implements MarkupPropertySource {
        RoundBlock {
            rounds = List.copyOf(rounds);
        }

        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "style" -> style;
                case "rounds" -> rounds;
                default -> throw new IllegalArgumentException("Unknown round block property: " + property);
            };
        }
    }

    record RoundIcon(String id, String classes, String src) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "src" -> src;
                default -> throw new IllegalArgumentException("Unknown round property: " + property);
            };
        }
    }
}
