package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * The ground a compound is taken on: its footprint, fixed when the map is built.
 *
 * <p>Kept apart from navigation zones on purpose. A zone says what is connected to what, and a breach rewrites that:
 * blow a wall out of a building and its room joins the street, and the zone the compound was taken in becomes half the
 * map. The place has not moved. Capture presence, the clear before a capture and the hold after it all ask about this
 * area, so a compound with its walls down is still taken by standing in it, and securing it means clearing it rather
 * than the outdoors it now opens onto.
 */
public record ObjectiveArea(int left, int top, int right, int bottom) {

    /** The footprint of a compound node. */
    public static ObjectiveArea of(TacticalNode node) {
        return new ObjectiveArea(node.left, node.top, node.right, node.bottom);
    }

    public boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}
