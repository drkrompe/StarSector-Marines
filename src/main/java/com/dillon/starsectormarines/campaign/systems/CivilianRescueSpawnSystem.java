package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;

/**
 * Freezes the terms of a civilian-rescue distress call from the colony's size.
 *
 * <p>A rescue is always a Rav Bloom's distress call: {@code RavBloomSystem} raises it when bores
 * open in a colony ({@code colony-breach-nouns.md}, Campaign: blooms). The epoch lottery that once
 * picked a random market every 45 days is retired; a rescue with no bloom behind it would be a
 * Colony Breach with nothing coming out of the ground.
 */
public final class CivilianRescueSpawnSystem {

    static final int CHOICE_DAYS = 3;

    private CivilianRescueSpawnSystem() {}

    /** A call with the debug spawner's short window. */
    public static long prepareEvent(CampaignState state, long triggerKey,
                                    int marketSlot, int marketSize, int day) {
        return prepareEvent(state, triggerKey, marketSlot, marketSize, day, CHOICE_DAYS);
    }

    /** A call open for {@code choiceDays} days, its stores and cohort scaled by the colony's size. */
    public static long prepareEvent(CampaignState state, long triggerKey,
                                    int marketSlot, int marketSize, int day,
                                    int choiceDays) {
        long tier = (long) marketSize - 2L;
        if (tier < 1L || tier > 4_634L || choiceDays < 0
                || day < 0 || day > Integer.MAX_VALUE - choiceDays) {
            return -1L;
        }
        return CivilianRescueEvent.prepare(state, triggerKey, marketSlot,
                day, day + choiceDays, (int) (25L * tier),
                (int) (15L * tier), (int) (100L * tier * tier));
    }
}
