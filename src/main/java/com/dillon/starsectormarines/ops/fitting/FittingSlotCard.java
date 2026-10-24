package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/**
 * A socket as the specifications column lists it, drawn by
 * {@code fitting-slot-card}: its name with a badge, what is in it, a line of
 * figures and its kind, and a small picture of the fitted piece on its bricks
 * (the canvas at {@link #gridId()}, which the room fills).
 */
public record FittingSlotCard(String id, String classes, String name, String badge, String badgeClasses,
                              String component, String telemetry, String type,
                              boolean canStrip, String stripClasses, Runnable select, Runnable stripAction)
        implements MarkupPropertySource {

    public String gridId() { return id + ":grid"; }

    @Override public Object markupProperty(String p) { return switch (p) {
        case "id" -> id; case "classes" -> classes;
        case "contentId" -> id + ":content"; case "buttonId" -> id + ":select"; case "stripId" -> id + ":strip";
        case "copyId" -> id + ":copy"; case "headerId" -> id + ":header"; case "nameId" -> id + ":name";
        case "badgeId" -> id + ":badge"; case "componentId" -> id + ":component";
        case "telemetryId" -> id + ":telemetry"; case "typeId" -> id + ":type";
        case "gridId" -> gridId(); case "hardwareId" -> id + ":hardware";
        case "name" -> name; case "badge" -> badge; case "badgeClasses" -> badgeClasses;
        case "component" -> component; case "telemetry" -> telemetry; case "type" -> type;
        case "canStrip" -> canStrip; case "stripClasses" -> stripClasses;
        case "stripDisabled" -> !canStrip;
        case "select" -> select; case "stripAction" -> stripAction;
        default -> throw new IllegalArgumentException("Unknown fitting-slot-card property: " + p); }; }
}
