package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
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
                ? outcome.missionName : "Resolved operation");
        props.put("budgetLabel", MessageFormat.format(Strings.get("lootBudgetFmt"),
                number(selection != null ? selection.selectedValue() : 0),
                number(manifest.selectionBudget)));
        props.put("budgetTone", "label title "
                + (selection != null && selection.remainingBudget() == 0
                ? "tone-accent" : "tone-good"));
        props.put("poolLabel", MessageFormat.format(Strings.get("lootPoolFmt"),
                manifest.stacks.size(), number(manifest.totalValue)));
        props.put("instructions", Strings.get("lootInstructions"));
        props.put("lootRows", lootRows(manifest));
        putSettlement(props, preview);
        props.put("backAction", (Runnable) () -> context.goTo(ScreenId.RESULTS));
        props.put("confirmDisabled", preview == null);
        props.put("confirmAction", (Runnable) this::confirm);
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
        props.put("instructions", "Choose complete recovered stacks up to the negotiated claim budget. Overflow that does not fit cargo is fenced automatically at settlement.");
        props.put("lootRows", List.of(
                previewRow(0, "Heavy Machinery", "18 recovered", "Cr. 4,500", "SELECTED", true, false, none),
                previewRow(1, "Supplies", "40 recovered", "Cr. 4,000", "SELECTED", true, false, none),
                previewRow(2, "Heavy Autocannon", "1 recovered", "Cr. 1,300", "SELECTED", true, false, none),
                previewRow(3, "Volturnian Lobster", "12 recovered", "Cr. 2,400", "AVAILABLE", false, false, none),
                previewRow(4, "AI Core", "1 recovered", "Cr. 8,000", "OVER BUDGET", false, true, none),
                previewRow(5, "Fuel", "70 recovered", "Cr. 1,750", "AVAILABLE", false, false, none),
                previewRow(6, "Marines", "10 recovered", "Cr. 1,000", "AVAILABLE", false, false, none),
                previewRow(7, "Field Armour Pattern", "1 recovered", "Cr. 8,450", "OVER BUDGET", false, true, none)));
        props.put("carryLabel", "59 units · Cr. 9,800 value");
        props.put("fenceLabel", "0 units · Cr. 0 paid");
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
        return props;
    }

    private List<LootRow> lootRows(LootManifest manifest) {
        List<LootRow> rows = new ArrayList<>();
        for (int i = 0; i < manifest.stacks.size(); i++) {
            LootStack stack = manifest.stacks.get(i);
            boolean selected = selection.isSelected(i);
            boolean canSelect = selection.canSelect(i);
            int index = i;
            rows.add(new LootRow("loot-stack-" + i,
                    "loot-card" + (selected ? " selected" : !canSelect ? " blocked" : ""),
                    stack.iconPath, stack.displayName,
                    MessageFormat.format(Strings.get("lootQuantityFmt"), stack.quantity),
                    MessageFormat.format(Strings.get("lootValueFmt"), number(stack.totalValue())),
                    selected ? Strings.get("lootSelected")
                            : !canSelect ? Strings.get("lootOverBudget") : "Available",
                    "label heading "
                            + (selected ? "tone-good" : !canSelect ? "tone-danger" : "tone-edge"),
                    !canSelect, () -> toggle(index)));
        }
        return List.copyOf(rows);
    }

    private static LootRow previewRow(int index, String name, String quantity,
                                      String value, String status, boolean selected,
                                      boolean blocked, Runnable action) {
        String id = "loot-preview-" + index;
        return new LootRow(id, "loot-card" + (selected ? " selected" : blocked ? " blocked" : ""),
                null, name, quantity, value, status,
                "label heading "
                        + (selected ? "tone-good" : blocked ? "tone-danger" : "tone-edge"),
                blocked, action);
    }

    private void putSettlement(Map<String, Object> props, LootSettlementPlan preview) {
        if (preview == null) {
            props.put("carryLabel", "Unavailable");
            props.put("fenceLabel", "Unavailable");
            props.put("noticeClasses", "label tone-danger");
            props.put("notice", Strings.get("lootCargoUnavailable"));
            return;
        }
        props.put("carryLabel", preview.keptUnits + " units · Cr. "
                + number(preview.keptValue) + " value");
        props.put("fenceLabel", preview.fencedUnits + " units · Cr. "
                + number(preview.fencedCredits) + " paid");
        if (preview.isEmpty()) {
            props.put("noticeClasses", "label tone-accent");
            props.put("notice", Strings.get("lootNothingSelected"));
        } else {
            props.put("noticeClasses", "label tone-muted");
            props.put("notice", number(selection.remainingBudget())
                    + " credits of claim budget remain.");
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
        context.clearResolvedMission();
        context.goTo(ScreenId.MISSION_SELECT);
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
                case "disabled" -> disabled; case "action" -> action; default -> null;
            };
        }
    }
}
