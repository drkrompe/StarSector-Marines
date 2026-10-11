package com.dillon.starsectormarines.battle.squad;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Groups deploying campaign personnel into battle squads by
 * <em>(campaign squad, tactical landing area)</em> rather than by shuttle sortie.
 *
 * <p>A twelve-marine squad rarely fits one lift, so without this a squad that
 * needs three passes lands as three unrelated battle squads, each with its own
 * morale baseline, alert state and plan. Keying on the campaign squad instead
 * means later lifts <em>join</em> the unit that is already on the ground.
 *
 * <p>Berth coordinates never define this key. Transports share one area by
 * default; a mission must explicitly assign separate areas to split a squad.
 * The tactical landing area is part of the key on purpose. One battle squad spanning
 * two zones would have leader-pull cohesion dragging members across the map
 * between landings, so a squad that lands in two places becomes two squads and
 * both are suffixed — {@code (A)} and {@code (B)} — so the player can see the
 * split rather than reading it as attrition. The suffix is applied
 * retroactively to the first landing when the second one happens, because at
 * the time the first lands there is nothing to distinguish it from.
 *
 * <p>Battle-lifetime state, like everything else here; battles never persist.
 */
public final class CampaignSquadIndex {

    private final IntFunction<Squad> squadLookup;

    /** {@code (campaign squad, tactical area)} → the battle squad already on the ground there. */
    private final Map<LandingKey, Integer> byLanding = new HashMap<>();

    /** Campaign squad → its first battle squad, kept only to retro-suffix it on a split. */
    private final Map<String, Integer> firstLanding = new HashMap<>();

    /** Campaign squad → how many distinct zones it has landed at. */
    private final Map<String, Integer> landingCount = new HashMap<>();

    public CampaignSquadIndex(IntFunction<Squad> squadLookup) {
        this.squadLookup = squadLookup;
    }

    /** The squad already at this tactical area, or {@link Squad#NO_SQUAD}. */
    public int landed(String campaignSquadId, int landingAreaId) {
        Integer existing = byLanding.get(new LandingKey(campaignSquadId, landingAreaId));
        return existing != null ? existing : Squad.NO_SQUAD;
    }

    /** Records an explicitly named area and freezes the campaign identity. */
    public void register(CampaignSquadTag tag, int landingAreaId, int battleSquadId) {
        byLanding.put(new LandingKey(tag.squadId, landingAreaId), battleSquadId);
        int landing = landingCount.merge(tag.squadId, 1, Integer::sum);
        if (landing == 1) {
            firstLanding.put(tag.squadId, battleSquadId);
        } else if (landing == 2) {
            label(firstLanding.get(tag.squadId), tag.label + " (A)");
        }
        Squad squad = squadLookup.apply(battleSquadId);
        if (squad != null) {
            squad.expectedSize = tag.strength;
            squad.campaignSquadId = tag.squadId;
            squad.commandGroupIdentity = tag.commandGroupIdentity;
            squad.campaignLabel = landing > 1
                    ? tag.label + " (" + (char) ('A' + landing - 1) + ")"
                    : tag.label;
        }
    }

    private void label(Integer battleSquadId, String label) {
        if (battleSquadId == null) return;
        Squad squad = squadLookup.apply(battleSquadId);
        if (squad != null) squad.campaignLabel = label;
    }

    private record LandingKey(String campaignSquadId, int landingAreaId) {
        private LandingKey {
            if (landingAreaId < 0) {
                throw new IllegalArgumentException(
                        "Campaign landing area must be nonnegative; berth coordinates are not squad destinations");
            }
        }
    }
}
