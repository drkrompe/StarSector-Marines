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
 * <p>Fighter cover is now an <em>opt-in</em> commitment (roadmap command-powers
 * S2 Slice 3): {@link #committableCarriers()} enumerates the player's carriers
 * and {@link #rosterFrom} builds the roster from the subset the player commits.
 * {@link #fromPlayerFleet()} (whole fleet) is kept as the default / display
 * fallback.
 *
 * <p>Sortie + arrival params are intentionally simple right now: every bay
 * contributes two sorties on a 12s interval, staggered across the committed
 * carriers by a per-bay offset. Tweaking these against fleet size / carrier
 * class is a later balance pass.
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
     * A committable carrier — one fleet member with at least one fighter bay that
     * maps to a known {@link FighterProfile}. The pre-battle UI renders one
     * opt-in toggle per entry; committing it contributes all its mapped bays to
     * the marine-side fighter cover.
     */
    public static final class CarrierBay {
        /** Ship name for the toggle row label. */
        public final String shipName;
        /** Mapped fighter bays on this ship (unmapped wings already filtered out). */
        public final List<FighterProfile> profiles;

        CarrierBay(String shipName, List<FighterProfile> profiles) {
            this.shipName = shipName;
            this.profiles = profiles;
        }

        /** Number of mapped bays — drives the row label. */
        public int bayCount() { return profiles.size(); }
    }

    /**
     * The player's carriers that can contribute fighter cover (≥1 mapped bay), in
     * fleet order. Empty outside campaign or when no fitted bay maps to a profile.
     * Snapshotted by the pre-battle screens so toggle indices stay stable across
     * a layout.
     */
    public static List<CarrierBay> committableCarriers() {
        if (Global.getSector() == null) return new ArrayList<>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return new ArrayList<>();

        List<CarrierBay> out = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            ShipVariantAPI variant = member.getVariant();
            if (variant == null) continue;
            List<String> fitted = variant.getFittedWings();
            if (fitted == null) continue;
            List<FighterProfile> profiles = new ArrayList<>();
            for (String wingId : fitted) {
                FighterProfile profile = profileFromWingId(wingId);
                if (profile != null) profiles.add(profile); // unmapped wing — skip silently
            }
            if (!profiles.isEmpty()) out.add(new CarrierBay(member.getShipName(), profiles));
        }
        return out;
    }

    /**
     * Builds the marine-side roster from the committed carriers, staggering
     * arrivals across all committed bays. Returns {@link FlybyRoster#EMPTY} for a
     * null/empty commitment.
     */
    public static FlybyRoster rosterFrom(List<CarrierBay> committed) {
        if (committed == null || committed.isEmpty()) return FlybyRoster.EMPTY;
        List<FighterWing> wings = new ArrayList<>();
        float arrival = BASE_FIRST_ARRIVAL;
        for (CarrierBay carrier : committed) {
            for (FighterProfile profile : carrier.profiles) {
                wings.add(new FighterWing(profile, Faction.MARINE,
                        SORTIE_COUNT_PER_BAY, arrival, SORTIE_INTERVAL_SEC));
                arrival += PER_BAY_STAGGER;
            }
        }
        return wings.isEmpty() ? FlybyRoster.EMPTY : new FlybyRoster(wings);
    }

    /**
     * Whole-fleet roster — every fitted bay contributes. The default / display
     * fallback; the opt-in path is {@link #rosterFrom(List)} over a committed
     * subset of {@link #committableCarriers()}.
     */
    public static FlybyRoster fromPlayerFleet() {
        return rosterFrom(committableCarriers());
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
