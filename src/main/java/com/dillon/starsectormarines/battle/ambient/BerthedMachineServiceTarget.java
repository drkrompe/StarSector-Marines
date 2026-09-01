package com.dillon.starsectormarines.battle.ambient;

/**
 * The oriented body a technician works on while it occupies a berth.
 *
 * <p>This is deliberately a machine envelope rather than a mech profile. A
 * walker, wheeled vehicle, tracked hull, aircraft, or later heavy asset can
 * publish its own beam and length while the bay keeps authoring only the safe
 * places people stand. The focus is pulled slightly inside the envelope so a
 * torch lands on painted body rather than exactly on a transparent sprite edge.
 */
public record BerthedMachineServiceTarget(
        float centerX,
        float centerY,
        float forwardX,
        float forwardY,
        float halfBeam,
        float halfLength) {

    private static final float PAINTED_BODY_INSET = 0.78f;
    private static final float EPSILON = 0.0001f;

    public BerthedMachineServiceTarget {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(forwardX) || !Float.isFinite(forwardY)
                || !Float.isFinite(halfBeam) || !Float.isFinite(halfLength)) {
            throw new IllegalArgumentException("service target values must be finite");
        }
        float forwardLength = length(forwardX, forwardY);
        if (forwardLength <= EPSILON) {
            throw new IllegalArgumentException("service target requires a facing");
        }
        if (halfBeam <= 0f || halfLength <= 0f) {
            throw new IllegalArgumentException("service target extents must be positive");
        }
        forwardX /= forwardLength;
        forwardY /= forwardLength;
    }

    /**
     * Point on the occupant's painted body nearest this authored work stand.
     *
     * <p>The envelope is an ellipse in the occupant's local forward/right
     * frame. That is conservative for irregular sprites, continuous under any
     * heading, and lets an elongated vehicle expose its flanks and ends without
     * teaching the ambient system what kind of machine it is.
     */
    public Focus focusFrom(float workX, float workY) {
        if (!Float.isFinite(workX) || !Float.isFinite(workY)) {
            throw new IllegalArgumentException("service stand must be finite");
        }
        float dx = workX - centerX;
        float dy = workY - centerY;
        float rightX = forwardY;
        float rightY = -forwardX;
        float lateral = dx * rightX + dy * rightY;
        float longitudinal = dx * forwardX + dy * forwardY;
        if (Math.abs(lateral) <= EPSILON && Math.abs(longitudinal) <= EPSILON) {
            longitudinal = 1f;
        }
        float normalized = (float) Math.sqrt(
                lateral * lateral / (halfBeam * halfBeam)
                        + longitudinal * longitudinal / (halfLength * halfLength));
        float scale = PAINTED_BODY_INSET / Math.max(EPSILON, normalized);
        float localX = lateral * scale;
        float localY = longitudinal * scale;
        return new Focus(
                centerX + rightX * localX + forwardX * localY,
                centerY + rightY * localX + forwardY * localY);
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    /** One resolved point on the body being worked. */
    public record Focus(float worldX, float worldY) { }
}
