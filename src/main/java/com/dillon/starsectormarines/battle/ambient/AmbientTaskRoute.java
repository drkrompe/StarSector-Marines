package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.task.TaskPoint;

import java.util.List;

/**
 * One reusable loop of authored work stations for a live battle actor.
 *
 * <p>A route is immutable data. Scene builders and mission setup may share the
 * same sampler and assignment service while choosing different stations,
 * activities, phase offsets, and threat policy.</p>
 */
public record AmbientTaskRoute(
        String id,
        float phaseOffsetSeconds,
        float walkSpeedCellsPerSecond,
        float threatRadiusCells,
        AmbientThreatPolicy threatPolicy,
        List<Stop> stops) {

    public AmbientTaskRoute {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ambient route requires id");
        if (!Float.isFinite(phaseOffsetSeconds)) {
            throw new IllegalArgumentException("ambient route phase must be finite");
        }
        if (!Float.isFinite(walkSpeedCellsPerSecond) || walkSpeedCellsPerSecond <= 0f) {
            throw new IllegalArgumentException("ambient route walk speed must be positive");
        }
        if (!Float.isFinite(threatRadiusCells) || threatRadiusCells < 0f) {
            throw new IllegalArgumentException("ambient route threat radius must be non-negative");
        }
        if (threatPolicy == null) throw new IllegalArgumentException("ambient route requires threat policy");
        stops = List.copyOf(stops);
        if (stops.isEmpty()) throw new IllegalArgumentException("ambient route requires stops");
    }

    /**
     * One destination to dwell at, its authored activity, and facing focus.
     * A non-null {@code pointGroup} asks the battle to reserve one free
     * {@link TaskPoint} from that group; the coordinates remain the deterministic
     * static-sampling fallback used by headless pose-only hosts. With
     * {@code specificPoint}, the registered stand at those coordinates is
     * required: a room work round can visit several fixtures in the same group
     * without claiming the same nearest bench again. Busy points are skipped.
     */
    public record Stop(float worldX, float worldY, float dwellSeconds,
                       AmbientActivity activity, float focusX, float focusY,
                       String pointGroup, boolean specificPoint) {
        /** Ordinary stops accept any free point in their group. */
        public Stop(float worldX, float worldY, float dwellSeconds,
                    AmbientActivity activity, float focusX, float focusY, String pointGroup) {
            this(worldX, worldY, dwellSeconds, activity, focusX, focusY, pointGroup, false);
        }
        public Stop(float worldX, float worldY, float dwellSeconds,
                    AmbientActivity activity, float focusX, float focusY) {
            this(worldX, worldY, dwellSeconds, activity, focusX, focusY, null);
        }

        public Stop {
            if (!Float.isFinite(worldX) || !Float.isFinite(worldY)
                    || !Float.isFinite(focusX) || !Float.isFinite(focusY)) {
                throw new IllegalArgumentException("ambient stop coordinates must be finite");
            }
            if (!Float.isFinite(dwellSeconds) || dwellSeconds <= 0f) {
                throw new IllegalArgumentException("ambient stop dwell must be positive");
            }
            if (activity == null || activity == AmbientActivity.WALKING) {
                throw new IllegalArgumentException("ambient stop requires a stationary activity");
            }
            if (pointGroup != null && pointGroup.isBlank()) {
                throw new IllegalArgumentException("ambient point group must be null or non-blank");
            }
            if (specificPoint && pointGroup == null)
                throw new IllegalArgumentException("a specific work point requires a claim group");
        }
    }
}
