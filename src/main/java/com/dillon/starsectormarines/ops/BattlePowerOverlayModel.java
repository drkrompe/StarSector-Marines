package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
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

/** Live, render-free projection for the MLX-authored command-power tray. */
final class BattlePowerOverlayModel {

    private static final String TARGETING_HIDDEN = "power-targeting power-targeting-hidden";
    private static final String TARGETING_VISIBLE = "power-targeting";
    private static final String READY = "power-card power-ready";
    private static final String DISABLED = "power-card power-disabled";
    private static final String ARMED = "power-card power-armed";
    private static final String NORMAL_STYLE = "";
    private static final String ARMED_STYLE =
            "background-color: #3a3018; border-color: #ffd464; color: #ffd464;";

    private final MutableSignal<String> commandPoints;
    private final MutableSignal<String> commandPointFillStyle;
    private final MutableSignal<String> supplies;
    private final MutableSignal<String> suppliesIcon;
    private final MutableSignal<List<PowerCard>> powerCards;
    private final MutableSignal<String> targetingClasses;
    private final MutableSignal<String> targetingLabel;
    private final Consumer<String> targetingToggle;
    private final Map<String, Runnable> actions = new HashMap<>();

    BattlePowerOverlayModel(Reactor reactor, Consumer<String> targetingToggle) {
        this(reactor, targetingToggle, CommodityPresentation.NONE);
    }

    BattlePowerOverlayModel(Reactor reactor, Consumer<String> targetingToggle,
                            CommodityPresentation commodities) {
        this.targetingToggle = targetingToggle;
        commandPoints = reactor.signal("CP 0 / 0");
        commandPointFillStyle = reactor.signal("width: 0%;");
        supplies = reactor.signal("SUP 0");
        suppliesIcon = reactor.signal(commodities.commodityIcon(Commodities.SUPPLIES));
        powerCards = reactor.signal(List.of());
        targetingClasses = reactor.signal(TARGETING_HIDDEN);
        targetingLabel = reactor.signal("");
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
        return props;
    }

    Presentation update(CommandPowerService service, String targetingPowerId) {
        if (service == null) return updateProjected(0f, 0f, 0, List.of(), null);
        List<PowerState> states = new ArrayList<>();
        for (CommandPower power : service.getAvailablePowers()) {
            states.add(new PowerState(power.id, power.displayName, power.cpCost,
                    power.supplyCost, service.getCooldownRemaining(power.id),
                    service.getChargesRemaining(power.id)));
        }
        return updateProjected(service.getCommandPoints(), service.getMaxCommandPoints(),
                service.getAvailableSupplies(), states, targetingPowerId);
    }

    Presentation updateProjected(float cp, float maxCp, int availableSupplies,
                                 List<PowerState> powers, String targetingPowerId) {
        List<PowerState> stable = powers == null ? List.of() : List.copyOf(powers);
        float cpPercent = maxCp <= 0f ? 0f : clamp01(cp / maxCp) * 100f;
        commandPoints.set("CP " + number(cp) + " / " + number(maxCp));
        commandPointFillStyle.set("width: " + Math.round(cpPercent) + "%;");
        supplies.set(availableSupplies == Integer.MAX_VALUE
                ? "SUP --" : "SUP " + Math.max(0, availableSupplies));

        List<PowerCard> cards = new ArrayList<>(stable.size());
        String armedName = null;
        for (PowerState power : stable) {
            boolean armed = power.id().equals(targetingPowerId);
            boolean ready = power.cooldownSeconds() <= 0f
                    && power.charges() != 0
                    && cp >= power.cpCost()
                    && availableSupplies >= power.supplyCost();
            String status;
            if (armed) {
                status = "TARGET";
                armedName = power.name();
            } else if (power.cooldownSeconds() > 0f) {
                status = (int) Math.ceil(power.cooldownSeconds()) + "S";
            } else if (power.charges() == 0) {
                status = "SPENT";
            } else if (cp < power.cpCost()) {
                status = "LOW CP";
            } else if (availableSupplies < power.supplyCost()) {
                status = "LOW SUP";
            } else {
                status = power.supplyCost() > 0
                        ? power.supplyCost() + " SUP"
                        : power.charges() > 0 ? power.charges() + "x" : "READY";
            }
            String cost = number(power.cpCost()) + " CP";
            Runnable action = actions.computeIfAbsent(power.id(),
                    id -> () -> targetingToggle.accept(id));
            String cardId = "battle-power-" + power.id();
            cards.add(new PowerCard(cardId, cardId + "-name", cardId + "-meta",
                    cardId + "-cost", cardId + "-status",
                    compactName(power.name()), cost, status,
                    armed ? ARMED : ready ? READY : DISABLED,
                    armed ? ARMED_STYLE : NORMAL_STYLE, !ready && !armed, action));
        }
        powerCards.set(List.copyOf(cards));
        boolean targeting = armedName != null;
        targetingClasses.set(targeting ? TARGETING_VISIBLE : TARGETING_HIDDEN);
        targetingLabel.set(targeting ? "TARGETING · " + armedName.toUpperCase(Locale.ROOT) : "");
        return new Presentation(!stable.isEmpty(), stable.size(), targeting);
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String compactName(String name) {
        String upper = name == null ? "" : name.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "MECH SUPPORT" -> "MECH DROP";
            case "ORBITAL BARRAGE" -> "BARRAGE";
            case "MARINE DROP" -> "MARINES";
            default -> upper;
        };
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    record Presentation(boolean visible, int powerCount, boolean targeting) {}

    record PowerState(String id, String name, float cpCost, int supplyCost,
                      float cooldownSeconds, int charges) {}

    record PowerCard(String id, String nameId, String metaId,
                     String costId, String statusId,
                     String name, String cost, String status,
                     String classes, String style, boolean disabled, Runnable action)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
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
