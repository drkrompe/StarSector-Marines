package com.dillon.starsectormarines.battle.ambient;

import java.util.function.BiPredicate;

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

    /**
     * Closest reachable cell toward the body from an authored access point.
     * Keeps the worker on the same side of the machine and outside its collision
     * clearance. Cell centres match ordinary ambient path arrival; no render-only
     * relocation or final teleport is needed. If access is blocked, keep the
     * original stand.
     */
    public Focus standFrom(float workX, float workY, float clearance,
                           BiPredicate<Integer, Integer> available) {
        Focus best = new Focus(workX, workY);
        Focus originalContact = focusFrom(workX, workY);
        float bestDistance = length(workX - originalContact.worldX(), workY - originalContact.worldY());
        for (int y = (int) Math.floor(Math.min(workY, centerY));
             y <= (int) Math.floor(Math.max(workY, centerY)); y++) {
            for (int x = (int) Math.floor(Math.min(workX, centerX));
                 x <= (int) Math.floor(Math.max(workX, centerX)); x++) {
                float px = x + .5f, py = y + .5f;
                if (!sameSide(px - centerX, workX - centerX)
                        || !sameSide(py - centerY, workY - centerY)
                        || length(px - centerX, py - centerY) < clearance
                        || !available.test(x, y)) continue;
                float approach = (px - centerX) * (workX - centerX)
                        + (py - centerY) * (workY - centerY);
                if (approach < .8f * length(px - centerX, py - centerY)
                        * length(workX - centerX, workY - centerY)) continue;
                Focus contact = focusFrom(px, py);
                float surfaceRadius = length(contact.worldX() - centerX,
                        contact.worldY() - centerY) / PAINTED_BODY_INSET;
                if (length(px - centerX, py - centerY) + EPSILON < surfaceRadius) continue;
                float distance = length(px - contact.worldX(), py - contact.worldY());
                if (distance < bestDistance) {
                    best = new Focus(px, py);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static boolean sameSide(float candidate, float authored) {
        return Math.abs(authored) < EPSILON ? Math.abs(candidate) < EPSILON
                : candidate * authored >= 0f;
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    /** One resolved point on the body being worked. */
    public record Focus(float worldX, float worldY) { }
}
