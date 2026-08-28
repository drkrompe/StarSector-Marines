package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * Simulation tuning for one close-contact special-equipment activation.
 *
 * <p>The item owns only <em>how the contact is committed</em>: how far the
 * carrier's reach extends, how long the commitment is held before its payload
 * applies, and how long the carrier waits before it may commit again. What the
 * payload does — damage, penetration, wall damage, audio, effects — belongs to
 * the referenced {@code WeaponDef}, so a close-contact item never becomes a
 * second stat catalogue.
 */
public record CloseContactSpec(
        float contactRange,
        float channelSeconds,
        float cooldownSeconds) implements Serializable {
}
