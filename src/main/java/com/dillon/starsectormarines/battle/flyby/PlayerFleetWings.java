package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates the player's current fleet loadout into a marine-side
 * {@link FlybyRoster}. Each fitted fighter bay across the fleet becomes one
 * {@link FighterWing}; wing IDs that map to one of our {@link FighterProfile}s
 * contribute, unknown IDs are skipped. Built-in bays (Drover drones, etc.)
 * count via {@code getFittedWings()} the same as refit-installed ones.
 *
 * <p>Called at briefing time (so the briefing UI sees the player's contribution)
 * and at battle start (so the sim uses the same numbers). Both calls re-query
 * — the player may swap their fleet between visits, and we want the briefing
 * to reflect the current state, not a snapshot.
 *
 * <p>Sortie + arrival params are intentionally simple right now: every bay
 * contributes two sorties on a 12s interval, staggered across the fleet by
 * a per-bay offset. Tweaking these against fleet size / carrier class is a
 * later balance pass.
 */
public final class PlayerFleetWings {

    /** Sorties per fitted bay across the battle. Two passes per bay feels like a fighter rearming once. */
    private static final int  SORTIE_COUNT_PER_BAY   = 2;
    /** Sim-seconds between successive sorties from the same bay. */
    private static final float SORTIE_INTERVAL_SEC   = 12f;
    /** First-arrival time for the first player bay. */
    private static final float BASE_FIRST_ARRIVAL    = 8f;
    /** Per-bay additional delay so the player's wings don't all spawn at once. */
    private static final float PER_BAY_STAGGER       = 4f;

    private PlayerFleetWings() {}

    /**
     * Returns the marine-side roster derived from the player's currently fitted
     * fighter bays, or {@link FlybyRoster#EMPTY} when the fleet has no fighter
     * support (or the sector / fleet isn't available yet — safe outside campaign).
     */
    public static FlybyRoster fromPlayerFleet() {
        if (Global.getSector() == null) return FlybyRoster.EMPTY;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return FlybyRoster.EMPTY;

        List<FighterWing> wings = new ArrayList<>();
        float arrival = BASE_FIRST_ARRIVAL;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            ShipVariantAPI variant = member.getVariant();
            if (variant == null) continue;
            List<String> fitted = variant.getFittedWings();
            if (fitted == null) continue;
            for (String wingId : fitted) {
                FighterProfile profile = profileFromWingId(wingId);
                if (profile == null) continue; // unmapped wing — skip silently
                wings.add(new FighterWing(profile, Faction.MARINE,
                        SORTIE_COUNT_PER_BAY, arrival, SORTIE_INTERVAL_SEC));
                arrival += PER_BAY_STAGGER;
            }
        }
        return wings.isEmpty() ? FlybyRoster.EMPTY : new FlybyRoster(wings);
    }

    /**
     * Maps a vanilla wing ID to a {@link FighterProfile}. Coverage is intentionally
     * narrow — we only have art + tuning for these four — but expanding later is
     * a one-line addition per new profile. Unmapped wings (Daggers, Tridents,
     * Piranhas, etc.) return {@code null} and the caller silently skips them.
     */
    static FighterProfile profileFromWingId(String wingId) {
        if (wingId == null) return null;
        switch (wingId) {
            case "broadsword_wing": return FighterProfile.BROADSWORD;
            case "talon_wing":      return FighterProfile.TALON;
            case "wasp_wing":       return FighterProfile.WASP;
            case "thunder_wing":    return FighterProfile.THUNDER;
            default:                return null;
        }
    }
}
