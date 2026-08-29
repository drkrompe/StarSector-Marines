package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * The authored parameters of one {@link SpecialAiPolicy} as a particular
 * integral system spends it — the numbers that decide when that policy's moment
 * has arrived for this suit ({@code progression-nouns.md}).
 *
 * <p><b>Per system, not per policy.</b> The threat radius that suits an
 * industrial rig charging a doorway is not obviously the radius that suits a
 * corporate suit reacting to contact, so the numbers are authored on the
 * catalog entry rather than fixed in the sweep that reads them. That is the
 * whole reason this type exists: the previous trigger was a constant in a
 * system class, which meant every system that would ever exist shared one
 * author's judgement about one suit.
 *
 * <p>Two systems declaring different policies share nothing here. Each policy
 * has its own spec with its own fields, so one suit's numbers cannot reach
 * another's even by accident, and adding a third policy adds a record rather
 * than a nullable field to a widening bag.
 */
public sealed interface IntegralSystemPolicySpec extends Serializable
        permits ApproachingDeadGroundSpec, ExposedUnderFireSpec, FieldAidSpec,
                SightedStandoffSpec {

    /** The closed policy these parameters belong to. */
    SpecialAiPolicy aiPolicy();
}
