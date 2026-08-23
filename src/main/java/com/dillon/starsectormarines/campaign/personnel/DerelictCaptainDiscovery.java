package com.dillon.starsectormarines.campaign.personnel;

import com.dillon.starsectormarines.marine.CaptainCandidate;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Trait;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

/** Pure policy bridge from one vanilla salvageable wreck to a frozen captain candidate. */
public final class DerelictCaptainDiscovery {

    private static final String SOURCE_PREFIX = "derelict:";
    private static final String SEED_NAMESPACE = "starsector_marines:derelict_captain:";
    private static final int ELIGIBILITY_MASK = 7;

    private static final String[] NAMES = {
            "Mara Venn", "Ilya Sorn", "Tamsin Rook", "Corin Vale",
            "Nadia Kest", "Juno Reeve", "Seth Arden", "Lena Orlov"
    };
    private static final String[] PORTRAITS = {
            "graphics/portraits/portrait_mercenary01.png",
            "graphics/portraits/portrait_mercenary02.png",
            "graphics/portraits/portrait_mercenary03.png",
            "graphics/portraits/portrait_mercenary04.png",
            "graphics/portraits/portrait_mercenary05.png",
            "graphics/portraits/portrait_mercenary06.png",
            "graphics/portraits/portrait_mercenary07.png",
            "graphics/portraits/portrait_mercenary08.png"
    };
    private static final Trait[] STARTING_TRAITS = {
            null, Trait.FIELD_MEDIC, Trait.NATURAL_LEADER, Trait.SALVAGE_EXPERT
    };

    private DerelictCaptainDiscovery() {}

    public static CaptainCandidate publish(
            MarineRoster roster, SectorEntityToken target, float currentDay) {
        if (roster == null || !isOrdinarySalvageableWreck(target)) return null;
        String sourceKey = sourceKey(target.getId());
        if (!isEligibleSource(sourceKey)) return null;

        Random random = new Random(seedFor(sourceKey));
        int profile = random.nextInt(NAMES.length);
        int rankRoll = random.nextInt(100);
        // A pod survivor is usually a peer of the company's own officer, and
        // occasionally a real find. The old three-rung roll topped out at the
        // starter's rank; the officer ladder has no rung below that any more,
        // so the spread moves upward instead of downward.
        Rank rank = rankRoll < 90 ? Rank.LIEUTENANT : Rank.CAPTAIN;
        Trait trait = STARTING_TRAITS[random.nextInt(STARTING_TRAITS.length)];
        return roster.discoverCaptainCandidate(
                sourceKey, NAMES[profile], PORTRAITS[profile],
                rank, trait, currentDay);
    }

    static boolean isOrdinarySalvageableWreck(SectorEntityToken target) {
        return target != null
                && target.getId() != null
                && !target.getId().trim().isEmpty()
                && target.getCustomPlugin() instanceof DerelictShipEntityPlugin
                && target.hasTag(Tags.SALVAGEABLE)
                && !target.hasTag(Tags.MISSION_ITEM)
                && !target.hasTag(Tags.MISSION_LOCATION)
                && !target.hasTag(Tags.NOT_RANDOM_MISSION_TARGET);
    }

    static boolean isEligibleSource(String sourceKey) {
        if (sourceKey == null || !sourceKey.startsWith(SOURCE_PREFIX)
                || sourceKey.length() == SOURCE_PREFIX.length()) return false;
        return (seedFor(sourceKey) & ELIGIBILITY_MASK) == 0L;
    }

    static String sourceKey(String entityId) {
        if (entityId == null) return null;
        String normalized = entityId.trim();
        return normalized.isEmpty() ? null : SOURCE_PREFIX + normalized;
    }

    private static long seedFor(String sourceKey) {
        UUID id = UUID.nameUUIDFromBytes(
                (SEED_NAMESPACE + sourceKey).getBytes(StandardCharsets.UTF_8));
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }
}
