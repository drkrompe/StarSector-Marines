package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingLapseResolution;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;

import java.util.EnumSet;

/**
 * Gives every pending Garrison defense and Cadre incident a response deadline, and
 * resolves it as never-answered once that deadline passes.
 *
 * <p>Without this, ignoring an armed event is strictly profitable: stationing rows are
 * accepted with zero phases, so {@link ContractLifecycleSystem} would complete the term
 * regardless of a live payload while {@link ContractRetainerSystem} keeps paying through
 * {@code IN_PROGRESS}.
 *
 * <p>Runs after the defense producers and {@link StationingIncidentSystem} so an event
 * armed today is never lapsed on the tick that arms it, and before
 * {@link ContractLifecycleSystem} so a deadline falling on the term's final day resolves
 * as a lapse rather than a successful term.
 */
public final class StationingLapseSystem implements CampaignSystem {

    /** Days to answer an armed Garrison defense before the market is written off. */
    static final int GARRISON_RESPONSE_DAYS = 7;
    /** Days to answer an armed Cadre incident. Longer — an incident is not an assault. */
    static final int CADRE_RESPONSE_DAYS = 14;

    interface RosterSource {
        MarineRoster roster();
    }

    private final RosterSource rosterSource;

    public StationingLapseSystem() {
        this(StationingLapseSystem::liveRoster);
    }

    StationingLapseSystem(RosterSource rosterSource) {
        this.rosterSource = rosterSource;
    }

    @Override
    public String name() {
        return "StationingLapse";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        MarineRoster roster = rosterSource.roster();
        for (int row = 0; row < state.contractCount; row++) {
            if (!ContractType.fromByte(state.contractType[row]).isStationing()) continue;
            long contractId = state.contractId[row];

            GarrisonDefensePayload defense =
                    GarrisonDefensePayload.from(state, contractId, roster);
            if (defense != null) {
                resolveIfExpired(state, row, day, defense.triggeredDay,
                        GARRISON_RESPONSE_DAYS,
                        () -> StationingLapseResolution.apply(state, defense, roster, day));
                continue;
            }

            StationingIncidentPayload incident =
                    StationingIncidentPayload.from(state, contractId, roster);
            if (incident != null) {
                resolveIfExpired(state, row, day, incident.dueDay, CADRE_RESPONSE_DAYS,
                        () -> StationingLapseResolution.apply(state, incident, roster, day));
                continue;
            }

            // Nothing outstanding — disarm so the next event gets a fresh window.
            state.contractResponseDeadlineTick[row] = -1;
        }
    }

    private void resolveIfExpired(CampaignState state, int row, int day, int armedDay,
                                  int windowDays, Runnable resolve) {
        int deadline = state.contractResponseDeadlineTick[row];
        if (deadline < 0) {
            deadline = armDeadline(armedDay, day, windowDays,
                    state.contractExpiresTick[row]);
            state.contractResponseDeadlineTick[row] = deadline;
        }
        if (day < deadline) return;
        resolve.run();
        // A resolution that refused (stale identity, failed personnel delivery) leaves
        // the payload in place; the deadline stays armed so the next tick retries.
        if (state.contractResponseDeadlineTick[row] >= 0
                && GarrisonDefensePayload.from(state, state.contractId[row]) == null
                && StationingIncidentPayload.from(state, state.contractId[row]) == null) {
            state.contractResponseDeadlineTick[row] = -1;
        }
    }

    /**
     * The window runs from the later of the day the event armed and the day this layer
     * first saw it, so a save carrying an event armed before the deadline layer existed
     * gets a full window instead of failing retroactively. Clamped to the term: an event
     * armed five days before expiry gets five days, not the full window.
     */
    static int armDeadline(int armedDay, int observedDay, int windowDays, int expiresTick) {
        long anchor = Math.max(armedDay, observedDay);
        long deadline = anchor + windowDays;
        if (expiresTick >= 0) deadline = Math.min(deadline, expiresTick);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(observedDay, deadline));
    }

    private static MarineRoster liveRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }
}
