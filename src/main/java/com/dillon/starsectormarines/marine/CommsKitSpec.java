package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * Simulation tuning for a comms (forward-observer) kit: the radio and the training that let its carrier direct their
 * side's air pool onto ground they can see or have fair reason to believe in.
 *
 * <p>The kit owns only <em>who can ask and what stops them</em>. What answers the call (which airframes, how many, how
 * they fly and what they carry) belongs to the side's air pool and the aircraft, so the kit never becomes a second
 * aircraft catalogue. UNTUNED: every figure is a first pass.
 *
 * @param spotRangeCells    how far the carrier's own eyes designate a target: an unobstructed line to ground inside
 *                          this range counts as sight
 * @param radioRangeCells   how far from the carrier a target may be and still be called on a believed position
 * @param callSeconds       how long the carrier must stay alive and unsuppressed on the net before the call goes out
 * @param cooldownSeconds   how long the carrier waits after a call before they may place another
 * @param suppressionPressure incoming-fire pressure at or above which the carrier is too busy to work the radio
 */
public record CommsKitSpec(
        float spotRangeCells,
        float radioRangeCells,
        float callSeconds,
        float cooldownSeconds,
        float suppressionPressure) implements Serializable {
}
