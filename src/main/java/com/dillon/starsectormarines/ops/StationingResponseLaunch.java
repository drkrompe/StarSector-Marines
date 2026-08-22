package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingLapseResolution;
import com.dillon.starsectormarines.campaign.StationingNoForceResolution;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;

/**
 * The single route from a pending stationing response to the briefing that fights it.
 *
 * <p>Two callers reach it: the local {@code Manage → Respond} button on
 * {@link StationingScreen}, and the self-triggered event popup, which seeds a freshly
 * opened Marine Ops dialog straight onto the same briefing. Keeping one implementation
 * is what makes "answer it from the popup" and "answer it at the market" provably the
 * same operation with the same detachment, rather than two battle paths that drift.
 */
public final class StationingResponseLaunch {

    public enum Result {
        /** Context is routed at the briefing; the caller has nothing left to do. */
        BRIEFING,
        /**
         * The detachment could not field anyone, so the shipped no-force policy already
         * settled the assignment. No battle will run.
         */
        NO_FORCE_RESOLVED,
        /** Nothing pending, no planet to fight over, or the mission could not be built. */
        UNAVAILABLE
    }

    private StationingResponseLaunch() {}

    /**
     * Resolves whichever response is pending on {@code contractId} and routes {@code ctx}
     * onto it. Used by the popup, which knows a contract id but not which kind of event
     * armed on it.
     */
    public static Result respondTo(MarineOpsContext ctx, long contractId) {
        CampaignState state = state();
        if (state == null) return Result.UNAVAILABLE;
        MarineRoster roster = roster();
        GarrisonDefensePayload defense =
                GarrisonDefensePayload.from(state, contractId, roster);
        if (defense != null) return respond(ctx, defense);
        StationingIncidentPayload incident =
                StationingIncidentPayload.from(state, contractId, roster);
        if (incident != null) return respond(ctx, incident);
        return Result.UNAVAILABLE;
    }

    /**
     * Abandons whatever response is pending on {@code contractId} right now, producing
     * the same end state as letting the G31 deadline run out. Choosing the bad outcome
     * deliberately costs exactly what drifting into it costs — the popup offers it so
     * the player can close the book rather than carry a decision they have already made.
     *
     * @return true if an assignment was actually written off
     */
    public static boolean writeOff(long contractId) {
        CampaignState state = state();
        if (state == null) return false;
        MarineRoster roster = roster();
        int day = currentDay();
        GarrisonDefensePayload defense =
                GarrisonDefensePayload.from(state, contractId, roster);
        if (defense != null) {
            return StationingLapseResolution.apply(state, defense, roster, day) != null;
        }
        StationingIncidentPayload incident =
                StationingIncidentPayload.from(state, contractId, roster);
        if (incident != null) {
            return StationingLapseResolution.apply(state, incident, roster, day) != null;
        }
        return false;
    }

    public static Result respond(MarineOpsContext ctx, GarrisonDefensePayload payload) {
        if (ctx == null || payload == null || ctx.planet == null) return Result.UNAVAILABLE;
        if (noForce(payload.activeSeats, payload.fireteamIds.isEmpty())) {
            return StationingNoForceResolution.apply(state(), payload, roster(), currentDay())
                    != null ? Result.NO_FORCE_RESOLVED : Result.UNAVAILABLE;
        }
        Mission mission = GarrisonDefenseMissionFactory.create(
                payload, ctx.planet.getName(), factionId(ctx));
        return route(ctx, mission, payload.captainId, payload.fireteamIds);
    }

    public static Result respond(MarineOpsContext ctx, StationingIncidentPayload payload) {
        if (ctx == null || payload == null || ctx.planet == null) return Result.UNAVAILABLE;
        if (noForce(payload.activeSeats, payload.fireteamIds.isEmpty())) {
            return StationingNoForceResolution.apply(state(), payload, roster(), currentDay())
                    != null ? Result.NO_FORCE_RESOLVED : Result.UNAVAILABLE;
        }
        Mission mission = StationingIncidentMissionFactory.create(
                payload, ctx.planet.getName(), factionId(ctx));
        return route(ctx, mission, payload.captainId, payload.fireteamIds);
    }

    /**
     * A named detachment with nobody left standing is a no-force case. A legacy
     * count-only assignment has no fireteams at all and still fights.
     */
    private static boolean noForce(int activeSeats, boolean fireteamsEmpty) {
        return activeSeats == 0 && !fireteamsEmpty;
    }

    private static Result route(MarineOpsContext ctx, Mission mission, String captainId,
                                Iterable<String> fireteamIds) {
        if (mission == null) return Result.UNAVAILABLE;
        ctx.setSelectedCaptainId(captainId);
        ctx.setSelectedMission(mission);
        ctx.replaceMarineSquadSelection(fireteamIds);
        ctx.goTo(ScreenId.BRIEFING);
        return Result.BRIEFING;
    }

    private static String factionId(MarineOpsContext ctx) {
        return ctx.market != null && ctx.market.getFaction() != null
                ? ctx.market.getFaction().getId() : null;
    }

    private static int currentDay() {
        return Global.getSector() != null ? CampaignClock.day() : 0;
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static MarineRoster roster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }
}
