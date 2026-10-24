package com.dillon.starsectormarines.battle.command.allied;

/**
 * A commander's ask of its producer (`allied-tools.md`, Code seam): a tool, the place it is for (a lane, a redoubt,
 * a sector, by the mission's own name), the cell it goes to, and why (`uncovered`, `breached`, `stalled`...), which
 * the producer reports but does not read.
 */
public record AlliedRequest(AlliedTool tool, String toPlace, int x, int y, String reason) {
}
