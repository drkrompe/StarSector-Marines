package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.LiftBudget;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Live, render-free projection for the MLX-authored command-power tray. While an armor drop
 * is armed it also projects the bay manifest's picker rows into chips: what each machine
 * is, and whether it is the pick, available, queued, already called, or why lift refuses it.
 */
final class BattlePowerOverlayModel {

    private static final String TARGETING_HIDDEN = "power-targeting power-targeting-hidden";
    private static final String TARGETING_VISIBLE = "power-targeting";
    private static final String READY = "power-card power-ready";
    private static final String DISABLED = "power-card power-disabled";
    private static final String ARMED = "power-card power-armed";
    private static final String PICKER_HIDDEN = "power-picker power-picker-hidden";
    private static final String PICKER_VISIBLE = "power-picker";
    private static final String CHIP_SELECTED = "pick-chip pick-selected";
    private static final String CHIP_AVAILABLE = "pick-chip pick-available";
    private static final String CHIP_REFUSED = "pick-chip pick-refused";
    private static final String NORMAL_STYLE = "";
    private static final String ARMED_STYLE =
            "background-color: #d6c27a24; border-color: #d6c27a99; color: #ffd464;";

    private final MutableSignal<String> commandPoints;
    private final MutableSignal<String> commandPointFillStyle;
    private final MutableSignal<String> supplies;
    private final MutableSignal<String> suppliesIcon;
    private final MutableSignal<List<PowerCard>> powerCards;
    private final MutableSignal<String> targetingClasses;
    private final MutableSignal<String> targetingLabel;
    private final MutableSignal<String> targetingHint;
    private final MutableSignal<String> pickerClasses;
    private final MutableSignal<List<PickerChip>> pickerChips;
    private final Consumer<String> targetingToggle;
    private final IntConsumer pickToggle;
    private final Map<String, Runnable> actions = new HashMap<>();
    private final Map<Integer, Runnable> pickActions = new HashMap<>();

    BattlePowerOverlayModel(Reactor reactor, Consumer<String> targetingToggle) {
        this(reactor, targetingToggle, CommodityPresentation.NONE);
    }

    BattlePowerOverlayModel(Reactor reactor, Consumer<String> targetingToggle,
                            CommodityPresentation commodities) {
        this(reactor, targetingToggle, index -> { }, commodities);
    }

    /** @param pickToggle picks a bay-manifest entry for the armed armor drop */
    BattlePowerOverlayModel(Reactor reactor, Consumer<String> targetingToggle,
                            IntConsumer pickToggle, CommodityPresentation commodities) {
        this.targetingToggle = targetingToggle;
        this.pickToggle = pickToggle;
        commandPoints = reactor.signal(Strings.format("battleCpReadout", "0", "0"));
        commandPointFillStyle = reactor.signal("width: 0%;");
        supplies = reactor.signal(Strings.format("battleSupReadout", "0"));
        suppliesIcon = reactor.signal(commodities.commodityIcon(Commodities.SUPPLIES));
        powerCards = reactor.signal(List.of());
        targetingClasses = reactor.signal(TARGETING_HIDDEN);
        targetingLabel = reactor.signal("");
        targetingHint = reactor.signal(Strings.get("battleTargetHint"));
        pickerClasses = reactor.signal(PICKER_HIDDEN);
        pickerChips = reactor.signal(List.of());
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("commandPoints", commandPoints);
        props.put("commandPointFillStyle", commandPointFillStyle);
        props.put("supplies", supplies);
        props.put("suppliesIcon", suppliesIcon);
        props.put("powerCards", powerCards);
        props.put("targetingClasses", targetingClasses);
        props.put("targetingLabel", targetingLabel);
        props.put("targetingHint", targetingHint);
        props.put("pickerClasses", pickerClasses);
        props.put("pickerChips", pickerChips);
        return props;
    }

    Presentation update(CommandPowerService service, String targetingPowerId) {
        return update(service, targetingPowerId, false);
    }

    /** As above, with whether Orbital Barrage is held back by a hostile fortress battery. */
    Presentation update(CommandPowerService service, String targetingPowerId, boolean barrageLocked) {
        return update(service, targetingPowerId, barrageLocked, List.of());
    }

    /**
     * As above, with the armed armor drop's picker rows (empty when another power, or
     * nothing, is armed).
     */
    Presentation update(CommandPowerService service, String targetingPowerId, boolean barrageLocked,
                        List<ArmorDropPower.PickerRow> pickerRows) {
        Presentation cards = updateCards(service, targetingPowerId, barrageLocked);
        return withPicker(cards, cards.targeting() ? pickerRows : List.of());
    }

    private Presentation updateCards(CommandPowerService service, String targetingPowerId,
                                     boolean barrageLocked) {
        if (service == null) return updateProjected(0f, 0f, 0, List.of(), null);
        List<PowerState> states = new ArrayList<>();
        for (CommandPower power : service.getAvailablePowers()) {
            int charges = service.getChargesRemaining(power.id);
            states.add(new PowerState(power.id, power.cardLabel(charges), power.cpCost,
                    power.supplyCost, service.getCooldownRemaining(power.id), charges,
                    barrageLocked && OrbitalBarrage.ID.equals(power.id)));
        }
        return updateProjected(service.getCommandPoints(), service.getMaxCommandPoints(),
                service.getAvailableSupplies(), states, targetingPowerId);
    }

    Presentation updateProjected(float cp, float maxCp, int availableSupplies,
                                 List<PowerState> powers, String targetingPowerId) {
        List<PowerState> stable = powers == null ? List.of() : List.copyOf(powers);
        float cpPercent = maxCp <= 0f ? 0f : clamp01(cp / maxCp) * 100f;
        commandPoints.set(Strings.format("battleCpReadout", number(cp), number(maxCp)));
        commandPointFillStyle.set("width: " + Math.round(cpPercent) + "%;");
        supplies.set(availableSupplies == Integer.MAX_VALUE
                ? Strings.get("battleSupNone") : Strings.format("battleSupReadout", Math.max(0, availableSupplies)));

        List<PowerCard> cards = new ArrayList<>(stable.size());
        String armedName = null;
        for (PowerState power : stable) {
            boolean armed = power.id().equals(targetingPowerId);
            boolean ready = power.cooldownSeconds() <= 0f
                    && !power.locked()
                    && power.charges() != 0
                    && cp >= power.cpCost()
                    && availableSupplies >= power.supplyCost();
            String status;
            if (armed) {
                status = Strings.get("battlePowerStatusTarget");
                armedName = power.name();
            } else if (power.locked()) {
                status = Strings.get("battlePowerStatusLocked");
            } else if (power.cooldownSeconds() > 0f) {
                status = Strings.format("battlePowerStatusCooldown", (int) Math.ceil(power.cooldownSeconds()));
            } else if (power.charges() == 0) {
                status = Strings.get("battlePowerStatusSpent");
            } else if (cp < power.cpCost()) {
                status = Strings.get("battlePowerStatusLowCp");
            } else if (availableSupplies < power.supplyCost()) {
                status = Strings.get("battlePowerStatusLowSup");
            } else {
                status = power.supplyCost() > 0
                        ? Strings.format("battlePowerStatusSupplyCost", power.supplyCost())
                        : power.charges() > 0 ? Strings.format("battlePowerStatusCharges", power.charges())
                        : Strings.get("battlePowerStatusReady");
            }
            String cost = Strings.format("battlePowerCost", number(power.cpCost()));
            Runnable action = actions.computeIfAbsent(power.id(),
                    id -> () -> targetingToggle.accept(id));
            String cardId = "battle-power-" + power.id();
            cards.add(new PowerCard(cardId, cardId + "-name", cardId + "-meta",
                    cardId + "-cost", cardId + "-status",
                    compactName(power.id(), power.name()), cost, status,
                    armed ? ARMED : ready ? READY : DISABLED,
                    armed ? ARMED_STYLE : NORMAL_STYLE, !ready && !armed, action, SpecSheets.commandPowerIcon(power.id())));
        }
        powerCards.set(List.copyOf(cards));
        boolean targeting = armedName != null;
        targetingClasses.set(targeting ? TARGETING_VISIBLE : TARGETING_HIDDEN);
        targetingLabel.set(targeting ? Strings.format("battleTargeting", upper(armedName)) : "");
        return new Presentation(!stable.isEmpty(), stable.size(), targeting);
    }

    /**
     * Projects the armed power's picker rows into chips, after {@link #updateProjected};
     * empty rows hide the picker.
     */
    Presentation withPicker(Presentation cards, List<ArmorDropPower.PickerRow> rows) {
        List<ArmorDropPower.PickerRow> stable = rows == null ? List.of() : rows;
        List<PickerChip> chips = new ArrayList<>(stable.size());
        String picked = null;
        for (ArmorDropPower.PickerRow row : stable) {
            BayManifest.Entry entry = row.entry();
            int index = entry.index();
            if (row.selected()) picked = entry.typeName();
            // Only a machine that can be ordered now can be picked.
            boolean clickable = row.selected() || row.callable();
            Runnable action = pickActions.computeIfAbsent(index, i -> () -> pickToggle.accept(i));
            String chipId = "battle-power-pick-" + index;
            String callsign = entry.callsign();
            chips.add(new PickerChip(chipId,
                    upper(entry.typeName()),
                    Strings.format(entry.isVehicle() ? "battlePickerVehicle" : "battlePickerMech", liftName(entry.weightClass())),
                    callsign == null ? "" : upper(callsign),
                    pickState(row),
                    row.selected() ? CHIP_SELECTED : clickable ? CHIP_AVAILABLE : CHIP_REFUSED,
                    !clickable, action));
        }
        boolean picking = !chips.isEmpty();
        pickerChips.set(List.copyOf(chips));
        pickerClasses.set(picking ? PICKER_VISIBLE : PICKER_HIDDEN);
        targetingHint.set(Strings.get(picking ? "battlePickHint" : "battleTargetHint"));
        if (picking && picked != null) {
            targetingLabel.set(Strings.format("battleTargetingPicked", targetingLabel.get(), upper(picked)));
        }
        return new Presentation(cards.visible(), cards.powerCount(), cards.targeting(), chips.size());
    }

    /** A chip's state line: queued, already called, a lift refusal, else whether it is the pick. */
    static String pickState(ArmorDropPower.PickerRow row) {
        if (row.status() == ArmorDropPower.Status.QUEUED) return Strings.get("battlePickQueued");
        if (row.status() == ArmorDropPower.Status.CALLED) return Strings.get("battlePickCalled");
        LiftBudget.Refusal refusal = row.refusal();
        if (refusal != null) {
            return switch (refusal) {
                case NO_LIFT -> Strings.format("battlePickNoLift", liftName(row.entry().weightClass()));
                case LIFT_SPENT -> Strings.get("battlePickLiftSpent");
                case NO_CRAFT -> Strings.get("battlePickNoCraft");
            };
        }
        return Strings.get(row.selected() ? "battlePickSelected" : "battlePickAvailable");
    }

    private static String liftName(LiftClass weightClass) {
        return weightClass == null ? "" : Strings.get("battleLift" + weightClass.name());
    }

    private static String upper(String text) {
        return text == null ? "" : Strings.upper(text);
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Strings.locale(), "%.1f", value);
    }

    /** The tray's short card name for a power when the copy gives one, else its name in capitals. */
    private static String compactName(String id, String name) {
        String key = "battlePowerCompact_" + id;
        String compact = Strings.get(key);
        return compact.equals(key) ? upper(name) : compact;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** @param pickerChips manifest chips shown above the strip (zero: no picker) */
    record Presentation(boolean visible, int powerCount, boolean targeting, int pickerChips) {
        Presentation(boolean visible, int powerCount, boolean targeting) {
            this(visible, powerCount, targeting, 0);
        }

        boolean picking() {
            return pickerChips > 0;
        }
    }

    record PickerChip(String id, String name, String weight, String callsign, String state,
                      String classes, boolean disabled, Runnable action)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> id + "-name";
                case "weightId" -> id + "-weight";
                case "callsignId" -> id + "-callsign";
                case "stateId" -> id + "-state";
                case "name" -> name;
                case "weight" -> weight;
                case "callsign" -> callsign;
                case "state" -> state;
                case "classes" -> classes;
                case "disabled" -> disabled;
                case "action" -> action;
                default -> throw new IllegalArgumentException(
                        "Unknown picker-chip property: " + property);
            };
        }
    }

    /** @param locked whether something on the ground is withholding the power (a fortress battery's fire control) */
    record PowerState(String id, String name, float cpCost, int supplyCost,
                      float cooldownSeconds, int charges, boolean locked) {
        PowerState(String id, String name, float cpCost, int supplyCost, float cooldownSeconds, int charges) {
            this(id, name, cpCost, supplyCost, cooldownSeconds, charges, false);
        }
    }

    record PowerCard(String id, String nameId, String metaId,
                     String costId, String statusId,
                     String name, String cost, String status,
                     String classes, String style, boolean disabled, Runnable action, String icon)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "headerId" -> id + "-header";
                case "iconId" -> id + "-icon";
                case "icon" -> icon;
                case "metaId" -> metaId;
                case "costId" -> costId;
                case "statusId" -> statusId;
                case "name" -> name;
                case "cost" -> cost;
                case "status" -> status;
                case "classes" -> classes;
                case "style" -> style;
                case "disabled" -> disabled;
                case "action" -> action;
                default -> throw new IllegalArgumentException(
                        "Unknown power-card property: " + property);
            };
        }
    }
}
