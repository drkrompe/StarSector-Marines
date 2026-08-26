package com.dillon.starsectormarines.battle.task;

/**
 * One exclusive place at which a battle actor may perform low-level work.
 *
 * <p>The point is simulation data, not UI or scene choreography. Generated
 * fixtures, mission setup, and bounded shipboard scenes may all publish task
 * points. A task chooses a {@link #group}; the claim service chooses one free
 * member of that group and owns its exclusivity until the actor moves on.</p>
 */
public record TaskPoint(
        String id,
        String group,
        float worldX,
        float worldY,
        float focusX,
        float focusY) {

    public TaskPoint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("task point requires id");
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("task point requires group");
        }
        if (!Float.isFinite(worldX) || !Float.isFinite(worldY)
                || !Float.isFinite(focusX) || !Float.isFinite(focusY)) {
            throw new IllegalArgumentException("task point coordinates must be finite");
        }
    }

    public int cellX() { return (int) Math.floor(worldX); }
    public int cellY() { return (int) Math.floor(worldY); }
}
