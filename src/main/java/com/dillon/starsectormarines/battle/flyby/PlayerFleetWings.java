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
 * <p>Fighter cover is an <em>opt-in</em> detachment commitment (see
 * {@code command-powers-nouns.md}): {@link #committableCarriers()} enumerates
 * the player's carriers and {@link #rosterFrom} builds the roster from the
 * subset the player commits.
 * {@link #fromPlayerFleet()} (whole fleet) is kept as the default / display
 * fallback.
 *
 * <p>Each bay is renewable, the way a carrier's fighters work in space combat
 * ({@link FighterWing#bay}): one airframe on station for the whole battle,
 * rearmed after each sortie it comes home from and replaced after each one it
 * loses. First launches are staggered across the committed bays by a per-bay
 * offset. The clocks are one draft pair for every wing; scaling them by the
 * wing (vanilla's own {@code refit} column runs 3 s for a Wasp to 18 s for a
 * Dagger) or by carrier is a later balance pass.
 */
public final class PlayerFleetWings {

    /**
     * Sim-seconds a bay's airframe rearms after a sortie it came home from.
     *
     * <p>Draft. Short, because coming home is the airframe having spent its
     * ammunition, not its life: the bay should be back over the battle while
     * the fight it left is still the same fight, and a bay's cover reads as a
     * steady rotation with gaps rather than a constant presence.
     */
    public static final float REFIT_SEC = 20f;
    /**
     * Sim-seconds a bay takes to replace an airframe that was shot down.
     *
     * <p>Draft. Long, so a loss costs the player something they feel for the
     * rest of that phase of the battle — four refits' worth — without ever
     * ending the bay's part in it, which is what makes the cover renewable
     * rather than spent.
     */
    public static final float REPLACEMENT_SEC = 90f;
    /** First-arrival time for the first player bay. */
    private static final float BASE_FIRST_ARRIVAL    = 8f;
    /** Per-bay additional delay so the player's wings don't all spawn at once. */
    private static final float PER_BAY_STAGGER       = 4f;

    private PlayerFleetWings() {}

    /**
     * A committable carrier — one fleet member with at least one fighter bay that
     * maps to a known {@link FighterProfile}. Every one the fleet has flies: its
     * mapped bays all join the marine-side fighter cover, and the briefing shows
     * them as aircraft, not as a choice.
     */
    public static final class CarrierBay {
        /** Ship name for the toggle row label. */
        public final String shipName;
        /** Hull combat-sprite path for the pre-battle thumbnail; null if unavailable. */
        public final String spriteName;
        /** Mapped fighter bays on this ship (unmapped wings already filtered out). */
        public final List<FighterProfile> profiles;

        public CarrierBay(String shipName, String spriteName, List<FighterProfile> profiles) {
            this.shipName = shipName;
            this.spriteName = spriteName;
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
            if (!profiles.isEmpty()) {
                String name = member.getShipName() != null ? member.getShipName() : "Carrier";
                String spriteName = member.getHullSpec() != null ? member.getHullSpec().getSpriteName() : null;
                out.add(new CarrierBay(name, spriteName, profiles));
            }
        }
        return out;
    }

    /**
     * Builds the marine-side roster from the committed carriers, one renewable
     * bay per mapped fighter bay, staggering first launches across them. Returns {@link FlybyRoster#EMPTY} for a
     * null/empty commitment.
     */
    public static FlybyRoster rosterFrom(List<CarrierBay> committed) {
        if (committed == null || committed.isEmpty()) return FlybyRoster.EMPTY;
        List<FighterWing> wings = new ArrayList<>();
        float arrival = BASE_FIRST_ARRIVAL;
        for (CarrierBay carrier : committed) {
            for (FighterProfile profile : carrier.profiles) {
                wings.add(FighterWing.bay(profile, Faction.MARINE,
                        arrival, REFIT_SEC, REPLACEMENT_SEC));
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
     * Maps the six supported vanilla fighter and bomber wings to their battle
     * profiles. Unmapped wings (Tridents, Piranhas, modded wings, etc.) return
     * {@code null} and the caller skips them.
     */
    static FighterProfile profileFromWingId(String wingId) {
        if (wingId == null) return null;
        switch (wingId) {
            case "broadsword_wing": return FighterProfile.BROADSWORD;
            case "talon_wing":      return FighterProfile.TALON;
            case "wasp_wing":       return FighterProfile.WASP;
            case "thunder_wing":    return FighterProfile.THUNDER;
            case "longbow_wing":    return FighterProfile.LONGBOW;
            case "dagger_wing":     return FighterProfile.DAGGER;
            default:                return null;
        }
    }
}
