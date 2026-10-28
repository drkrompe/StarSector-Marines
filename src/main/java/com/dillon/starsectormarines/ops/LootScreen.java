package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.loot.LootCapacityBucket;
import com.dillon.starsectormarines.ops.loot.LootFleetHold;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.loot.LootSelection;
import com.dillon.starsectormarines.ops.loot.LootSettlementPlan;
import com.dillon.starsectormarines.ops.loot.LootSettlementService;
import com.dillon.starsectormarines.ops.loot.LootStack;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/** MLX-authored budget-aware picker for the frozen recovery manifest. */
public final class LootScreen extends MissionFlowMlxScreen {

    static final String ROOT_COMPONENT = "mission-loot";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/mission-loot.mlx");

    private LootSelection selection;

    public LootScreen() {
        super(ROOT_COMPONENT, COMPONENT_PATHS);
    }

    @Override
    protected void onAttach() {
        LootManifest manifest = context != null ? context.getLootManifest() : LootManifest.EMPTY;
        if (selection == null || selection.manifest() != manifest) {
            selection = new LootSelection(manifest);
        }
    }

    @Override
    protected Map<String, Object> props() {
        LootManifest manifest = selection != null ? selection.manifest() : LootManifest.EMPTY;
        LootSettlementPlan preview = selection != null
                ? LootSettlementService.preview(selection) : null;
        MissionOutcome outcome = context != null ? context.getLastOutcome() : null;
        Map<String, Object> props = baseProps();
        props.put("missionName", outcome != null && outcome.missionName != null
                ? outcome.missionName : Strings.get("lootScreenResolvedOperation"));
        props.put("budgetLabel", MessageFormat.format(Strings.get("lootBudgetFmt"),
                number(selection != null ? selection.selectedValue() : 0),
                number(manifest.selectionBudget)));
        props.put("budgetTone", "label title "
                + (selection != null && selection.remainingBudget() == 0
                ? "tone-accent" : "tone-good"));
        props.put("poolLabel", MessageFormat.format(Strings.get("lootPoolFmt"),
                manifest.stacks.size(), number(manifest.totalValue)));
        props.put("instructions", Strings.get(manifest.stacks.stream().anyMatch(s -> s.fieldRecovered)
                ? "lootScreenInstructionsFieldFinds" : "lootInstructions"));
        props.put("lootRows", lootRows(selection, this::toggle, Strings::get));
        putSettlement(props, preview);
        props.put("holdLabel", holdLabel(LootSettlementService.fleetHold(), preview, Strings::get));
        props.put("backAction", (Runnable) () -> context.goTo(ScreenId.RESULTS));
        props.put("confirmDisabled", preview == null);
        props.put("confirmAction", (Runnable) this::confirm);
        props.put("confirmLabel", Strings.get(context.refittingAfterSettlement() ? "lootScreenConfirmRefit"
                : context.preparingAfterSettlement() ? "lootScreenConfirmPrepare" : "lootScreenConfirm"));
        return props;
    }

    static Map<String, Object> previewProps() {
        Runnable none = () -> { };
        Map<String, Object> props = baseProps();
        props.put("title", "Recovered Materiel");
        props.put("missionName", "SABOTAGE — First Contract");
        props.put("budgetLabel", "Selected: Cr. 9,800 / Cr. 14,200");
        props.put("budgetTone", "label title tone-good");
        props.put("poolLabel", "8 recovered stacks · Cr. 31,400 total pool value");
        props.put("instructions", "Tick what to keep, up to the negotiated claim budget. Unticked stacks are left behind, and what will not fit the hold is fenced.");
        props.put("lootRows", List.of(
                previewRow(0, "graphics/icons/cargo/heavymachinery.png", "Heavy Machinery", "18 recovered", "Cr. 4,500", "KEEP", true, false, none),
                previewRow(1, "graphics/icons/cargo/supplies.png", "Supplies", "40 recovered", "Cr. 4,000", "KEEP", true, false, none),
                previewRow(2, null, "Heavy Autocannon", "1 recovered", "Cr. 1,300", "KEEP", true, false, none),
                previewRow(3, "graphics/icons/cargo/lobster.png", "Volturnian Lobster", "12 recovered", "Cr. 2,400", "LEAVE BEHIND", false, false, none),
                previewRow(4, "graphics/icons/cargo/ai_core_alpha.png", "AI Core", "1 recovered", "Cr. 8,000", "OVER BUDGET", false, true, none),
                previewRow(5, "graphics/icons/cargo/fuel.png", "Fuel", "70 recovered", "Cr. 1,750", "LEAVE BEHIND", false, false, none),
                previewRow(6, "graphics/icons/cargo/marine.png", "Marines", "10 recovered", "Cr. 1,000", "LEAVE BEHIND", false, false, none),
                previewRow(7, null, "Field Armour Pattern", "1 recovered", "Cr. 8,450", "OVER BUDGET", false, true, none)));
        props.put("carryLabel", "59 units · Cr. 9,800 value");
        props.put("fenceLabel", "0 units · Cr. 0 paid");
        props.put("holdLabel", "Cargo 340 / 1,200 · 399 after");
        props.put("noticeClasses", "label tone-muted");
        props.put("notice", "4,400 credits of claim budget remain.");
        props.put("backAction", none);
        props.put("confirmDisabled", false);
        props.put("confirmAction", none);
        return props;
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", Strings.get("lootHeader"));
        props.put("confirmLabel", Strings.get("lootScreenConfirm"));
        return props;
    }

    static List<LootRow> lootRows(LootSelection selection, IntConsumer toggle, UnaryOperator<String> copy) {
        LootManifest manifest = selection.manifest();
        List<LootRow> rows = new ArrayList<>();
        for (int i = 0; i < manifest.stacks.size(); i++) {
            LootStack stack = manifest.stacks.get(i);
            boolean selected = selection.isSelected(i);
            boolean canSelect = selection.canSelect(i);
            int index = i;
            rows.add(new LootRow("loot-stack-" + i,
                    "loot-card console-toggle" + (selected ? " selected" : !canSelect ? " blocked" : ""),
                    stack.iconPath, stack.displayName,
                    MessageFormat.format(copy.apply(stack.fieldRecovered
                            ? "lootScreenQuantityFieldFindFmt" : "lootQuantityFmt"), stack.quantity),
                    MessageFormat.format(copy.apply("lootValueFmt"), number(stack.totalValue())),
                    selected ? copy.apply("lootScreenKeep")
                            : !canSelect ? copy.apply("lootOverBudget")
                            : copy.apply("lootScreenLeave"),
                    "label heading "
                            + (selected ? "tone-good" : !canSelect ? "tone-danger" : "tone-edge"),
                    !canSelect, () -> toggle.accept(index)));
        }
        return List.copyOf(rows);
    }

    /**
     * The fleet's hold now and after the kept lines go aboard: cargo always,
     * fuel and crew only when something kept lands in them.
     */
    static String holdLabel(LootFleetHold hold, LootSettlementPlan plan, UnaryOperator<String> copy) {
        if (hold == null) return copy.apply("lootScreenUnavailable");
        List<String> parts = new ArrayList<>();
        for (LootCapacityBucket bucket : LootCapacityBucket.values()) {
            float added = plan != null ? plan.keptSpace(bucket) : 0f;
            if (bucket != LootCapacityBucket.CARGO && added <= 0f) continue;
            String name = copy.apply(switch (bucket) {
                case CARGO -> "lootScreenHoldCargo";
                case FUEL -> "lootScreenHoldFuel";
                case PERSONNEL -> "lootScreenHoldCrew";
            });
            parts.add(MessageFormat.format(copy.apply("lootScreenHoldFmt"), name,
                    number(Math.round(hold.used(bucket))), number(Math.round(hold.max(bucket))),
                    number(Math.round(hold.used(bucket) + added))));
        }
        return String.join(" · ", parts);
    }

    private static LootRow previewRow(int index, String icon, String name, String quantity,
                                      String value, String status, boolean selected,
                                      boolean blocked, Runnable action) {
        String id = "loot-preview-" + index;
        return new LootRow(id, "loot-card console-toggle" + (selected ? " selected" : blocked ? " blocked" : ""),
                icon, name, quantity, value, status,
                "label heading "
                        + (selected ? "tone-good" : blocked ? "tone-danger" : "tone-edge"),
                blocked, action);
    }

    private void putSettlement(Map<String, Object> props, LootSettlementPlan preview) {
        if (preview == null) {
            props.put("carryLabel", Strings.get("lootScreenUnavailable"));
            props.put("fenceLabel", Strings.get("lootScreenUnavailable"));
            props.put("noticeClasses", "label tone-danger");
            props.put("notice", Strings.get("lootCargoUnavailable"));
            return;
        }
        props.put("carryLabel", Strings.format("lootScreenCarryValue",
                preview.keptUnits, number(preview.keptValue)));
        props.put("fenceLabel", Strings.format("lootScreenFencePaid",
                preview.fencedUnits, number(preview.fencedCredits)));
        if (preview.isEmpty()) {
            props.put("noticeClasses", "label tone-accent");
            props.put("notice", Strings.get("lootNothingSelected"));
        } else {
            props.put("noticeClasses", "label tone-muted");
            props.put("notice", Strings.format("lootScreenBudgetRemains",
                    number(selection.remainingBudget())));
        }
    }

    private void toggle(int index) {
        if (selection.toggle(index)) rebuildDocument();
    }

    private void confirm() {
        LootSettlementPlan result = LootSettlementService.settle(context, selection);
        if (result == null) {
            rebuildDocument();
            return;
        }
        context.finishOperation(context.preparingAfterSettlement());
    }

    private static String number(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    @Override protected void onCancel() {
        if (context != null) context.goTo(ScreenId.RESULTS);
    }

    @Override
    protected List<String> requiredElementIds() {
        return List.of("mission-loot-root", "mission-loot-header",
                "mission-loot-summary", "mission-loot-budget", "mission-loot-grid",
                "mission-loot-settlement", "mission-loot-notice", "mission-loot-actions",
                "mission-loot-back", "mission-loot-confirm");
    }

    record LootRow(String id, String classes, String icon, String name,
                   String quantity, String value, String status, String tone,
                   boolean disabled, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "icon" -> icon;
                case "iconId" -> id + "-icon"; case "copyId" -> id + "-copy";
                case "name" -> name;
                case "nameId" -> id + "-name"; case "quantity" -> quantity;
                case "quantityId" -> id + "-quantity"; case "value" -> value;
                case "valueId" -> id + "-value"; case "status" -> status;
                case "statusId" -> id + "-status"; case "tone" -> tone;
                case "checkId" -> id + "-check"; case "checkMarkId" -> id + "-check-mark";
                case "checkClasses" -> "loot-check" + (classes.contains(" selected") ? " checked" : "");
                case "disabled" -> disabled; case "action" -> action; default -> null;
            };
        }
    }
}
