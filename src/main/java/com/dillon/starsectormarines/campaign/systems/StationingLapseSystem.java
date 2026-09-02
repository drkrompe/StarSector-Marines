package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.AbsentDefenceResolution;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
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
 * <p>A defense armed by a vanilla raid is the exception, and does not lapse at all: that
 * raid was fought through vanilla's own strength ratio whether or not the player came, so
 * it settles from vanilla's result through {@link AbsentDefenceResolution}.
 *
 * <p>Runs after the defense producers and {@link StationingIncidentSystem} so an event
 * armed today is never lapsed on the tick that arms it, and before
 * {@link ContractLifecycleSystem} so a deadline falling on the term's final day resolves
 * as a lapse rather than a successful term.
 */
public final class StationingLapseSystem implements CampaignSystem {

    /** Days to answer an armed Garrison defense before the market is written off. */
    public static final int GARRISON_RESPONSE_DAYS = 7;
    /** Days to answer an armed Cadre incident. Longer — an incident is not an assault. */
    public static final int CADRE_RESPONSE_DAYS = 14;

    /** Stateless reader of live vanilla raid state; shared by every live instance. */
    private static final VanillaRaidStatus LIVE_VANILLA = new VanillaRaidStatus();

    interface RosterSource {
        MarineRoster roster();
    }

    /** What vanilla has done with the raid a pending defence was armed from. */
    interface RaidStatusSource {
        RaidStatus status(String marketId, String attackerFactionId);
    }

    /** Ground strength vanilla counted for the defended market, our modifier included. */
    interface DefenderStrengthSource {
        float defenderStrength(String marketId);
    }

    private final RosterSource rosterSource;
    private final RaidStatusSource raidStatusSource;
    private final DefenderStrengthSource defenderStrengthSource;

    public StationingLapseSystem() {
        this(StationingLapseSystem::liveRoster);
    }

    StationingLapseSystem(RosterSource rosterSource) {
        this(rosterSource, LIVE_VANILLA, LIVE_VANILLA);
    }

    StationingLapseSystem(RosterSource rosterSource, RaidStatusSource raidStatusSource,
                          DefenderStrengthSource defenderStrengthSource) {
        this.rosterSource = rosterSource;
        this.raidStatusSource = raidStatusSource;
        this.defenderStrengthSource = defenderStrengthSource;
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
                if (defense.triggerType == GarrisonDefenseTriggerType.VANILLA_RAID) {
                    settleFromVanilla(state, row, day, defense, roster);
                } else {
                    resolveIfExpired(state, row, day, defense.triggeredDay,
                            GARRISON_RESPONSE_DAYS,
                            () -> StationingLapseResolution.apply(state, defense, roster, day));
                }
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
        disarmIfSettled(state, row);
    }

    /**
     * A resolution that refused (stale identity, failed personnel delivery) leaves the
     * payload in place; the deadline stays armed so the next tick retries.
     */
    private static void disarmIfSettled(CampaignState state, int row) {
        if (state.contractResponseDeadlineTick[row] >= 0
                && GarrisonDefensePayload.from(state, state.contractId[row]) == null
                && StationingIncidentPayload.from(state, state.contractId[row]) == null) {
            state.contractResponseDeadlineTick[row] = -1;
        }
    }

    /**
     * A vanilla-triggered defence is not the player's alone to answer: the stationed
     * detachment fought that raid through vanilla's own strength ratio whether or not the
     * player came, so it settles from vanilla's result rather than lapsing
     * ({@code contracts-nouns.md} law 5).
     *
     * <p>While the raid is still in the air there is nothing to settle and no window to
     * miss, so the response deadline carries the term's own expiry — the only honest date
     * on offer — and stays unset for a term with no expiry. A term that runs out with the
     * raid still coming settles as held: the detachment served the term it was paid for.
     */
    private void settleFromVanilla(CampaignState state, int row, int day,
                                   GarrisonDefensePayload defense, MarineRoster roster) {
        String marketId = state.marketRegistry.get(defense.marketId);
        RaidStatus status = raidStatusSource.status(marketId, defense.attackerFactionKey);
        int expires = state.contractExpiresTick[row];
        if (status == RaidStatus.LIVE && (expires < 0 || day < expires)) {
            state.contractResponseDeadlineTick[row] = expires;
            return;
        }
        if (status == RaidStatus.LANDED) {
            AbsentDefenceResolution.applyLanded(state, defense, roster, day,
                    defenderStrengthSource.defenderStrength(marketId));
        } else {
            AbsentDefenceResolution.applyHeld(state, defense, roster, day);
        }
        disarmIfSettled(state, row);
    }

    /**
     * The window runs from the later of the day the event armed and the day this layer
     * first saw it, so a save carrying an event armed before the deadline layer existed
     * gets a full window instead of failing retroactively. Clamped to the term: an event
     * armed five days before expiry gets five days, not the full window.
     */
    public static int armDeadline(int armedDay, int observedDay, int windowDays, int expiresTick) {
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
