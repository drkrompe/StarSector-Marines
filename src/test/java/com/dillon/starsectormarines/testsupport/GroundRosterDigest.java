package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Random;

/**
 * A canonical, comparable description of a {@link GroundRosterProfile}.
 *
 * <p>A profile's weighted tables are deliberately package-private, so two
 * profiles cannot be compared field by field from outside. Rolling every table
 * from the same seed is the honest equivalent: two profiles that agree on this
 * string issue the same kit at the same rates, and one whose weights moved by a
 * point does not.
 */
public final class GroundRosterDigest {

    private static final int ROLLS = 400;
    private static final long SEED = 99L;

    private GroundRosterDigest() {}

    public static String of(GroundRosterProfile profile) {
        StringBuilder digest = new StringBuilder(profile.id())
                .append('|').append(profile.factionIds())
                .append('|').append(profile.heavySupport());
        for (GroundRosterProfile.ForceTier tier : GroundRosterProfile.ForceTier.values()) {
            GroundRosterProfile.Issue issue = profile.issue(tier);
            digest.append('|').append(tier).append(':').append(issue.unitType());
            for (RiskLevel risk : RiskLevel.values()) {
                Random rng = new Random(SEED);
                for (int roll = 0; roll < ROLLS; roll++) {
                    SpecialEquipmentDef special = issue.pickSpecialDef(risk, rng);
                    digest.append(issue.pickPrimaryDef(rng).id).append(',')
                            .append(issue.pickGrade(risk, rng)).append(',')
                            .append(issue.pickArmorDef(risk, rng).id()).append(',')
                            .append(special == null ? "none" : special.id()).append(';');
                }
            }
        }
        return digest.toString();
    }
}
