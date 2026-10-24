package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ops.CompanyResources;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.List;

/**
 * One part in the stores, as the {@code fitting-card} component draws it: its
 * picture, its name with a badge, three lines of figures, and a strip of what
 * it costs with an optional button beside it.
 *
 * <p>Every element id is the card's {@code id} with a suffix, so a room names
 * a card once.
 *
 * @param previewClasses the picture's classes; {@code catalog-preview hidden} for a card with none
 * @param fabricates     whether the button builds a spare, which wears the fabricator's cog
 * @param chipIcon       a small symbol on a coloured block beside the detail line, or null
 * @param chipTone       that block's colour, or null
 */
public record FittingCard(String id, String classes, String previewClasses,
                          String name, String badge, String badgeClasses,
                          String stock, String telemetry, String delta, String deltaClasses,
                          String detail, String actionLabel, String actionHint, boolean actionDisabled,
                          boolean fabricates, List<CompanyResources.Row> materials, String materialsClasses,
                          Runnable action, String chipIcon, String chipTone)
        implements MarkupPropertySource {

    private static final Runnable NO_ACTION = () -> { };

    public FittingCard {
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public String previewId() { return id + ":preview"; }

    public String actionId() { return id + ":action"; }

    private boolean hasAction() { return actionLabel != null && !actionLabel.isEmpty(); }

    /** The strip under the card: material costs and the button, when there is either. */
    private boolean hasFooter() { return hasAction() || !materials.isEmpty(); }

    @Override public Object markupProperty(String p) { return switch (p) {
        case "id" -> id; case "classes" -> classes;
        case "copyId" -> id + ":copy"; case "headerId" -> id + ":header"; case "nameId" -> id + ":name";
        case "badgeId" -> id + ":badge"; case "stockId" -> id + ":stock";
        case "telemetryId" -> id + ":telemetry"; case "deltaId" -> id + ":delta";
        case "detailId" -> id + ":detail"; case "actionId" -> actionId();
        case "materialsId" -> id + ":materials"; case "bodyId" -> id + ":body";
        case "previewId" -> previewId(); case "previewClasses" -> previewClasses;
        case "name" -> name; case "badge" -> badge; case "badgeClasses" -> badgeClasses;
        case "stock" -> stock; case "telemetry" -> telemetry;
        case "delta" -> delta; case "deltaClasses" -> deltaClasses;
        case "detail" -> detail; case "actionLabel" -> actionLabel;
        case "actionDisabled" -> actionDisabled; case "materials" -> materials;
        case "materialsStyle" -> CompanyResources.billHeight(materials);
        case "cardStyle" -> hasFooter() ? "height: 138px;" : "height: 110px;";
        case "footerId" -> id + ":footer";
        case "footerClasses" -> hasFooter() ? "catalog-card-footer" : "hidden";
        case "actionClasses" -> !hasAction() ? "hidden"
                : actionDisabled ? "catalog-fab console-action short" : "catalog-fab console-action";
        case "actionIconId" -> id + ":action:icon";
        case "actionTextId" -> id + ":action:text";
        case "actionIconClasses" -> fabricates ? "catalog-fab-icon" : "hidden";
        case "actionText" -> actionLabel;
        case "materialsClasses" -> materialsClasses;
        // A card with no button still binds its hidden one, to nothing.
        case "action" -> action != null ? action : NO_ACTION;
        case "detailRowId" -> id + ":detail-row";
        case "chipId" -> id + ":weight";
        case "chipIconId" -> id + ":weight-icon";
        case "chipBlockId" -> id + ":weight-chip";
        case "chipClasses" -> chipIcon != null ? "weight-slot" : "hidden";
        case "chipIcon" -> chipIcon != null ? chipIcon : "";
        case "chipStyle" -> chipTone != null ? "background-color: " + chipTone + ";" : "";
        default -> throw new IllegalArgumentException("Unknown fitting-card property: " + p); }; }
}
