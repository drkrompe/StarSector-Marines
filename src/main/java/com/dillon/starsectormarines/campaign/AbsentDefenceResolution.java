package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AbsentDefenceGrade.Outcome;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CasualtyFate;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Settles a Garrison defence armed by a vanilla raid that the player never answered.
 *
 * <p>The exception in {@code contracts-nouns.md} law 5: the stationed detachment fought
 * that raid through vanilla's own strength ratio whether or not the player came, so the
 * settlement is read off what vanilla did rather than written off as a lapse. Held, held
 * with losses, or overrun — graded by {@link AbsentDefenceGrade} on the raid
 * effectiveness computed with the detachment counted in the market's ground defences.
 *
 * <p>Exactly-once by construction: every path settles through
 * {@link GarrisonDefenseResolution#apply}, which consumes the pending payload and
 * refuses a second call with the same event key.
 *
 * <p>Every settlement also writes the company's {@link DefenceReport} into the Chronicle,
 * so the player learns what their detachment did while they were elsewhere.
 */
public final class AbsentDefenceResolution {

    /**
     * A Garrison defence is a {@code RiskLevel.HIGH} mission when it is fought, so its
     * wounded recover on the same clock whether or not the player commanded it.
     */
    private static final float WIA_DAYS = 18f;

    /** Reputation the employer takes off for a garrison that fought the raid and lost. */
    private static final int OVERRUN_HOUSE_DELTA = -1;

    private static final String OVERRUN_NOTE =
            "Recalled after the stationed garrison was overrun in their absence.";

    /**
     * Raid effectiveness credited to a raid whose ground strength was never estimated.
     * The middle of vanilla's uncertain band: something landed, and nothing on the
     * intel says how hard.
     */
    private static final float UNESTIMATED_EFFECTIVENESS = 0.5f;

    private AbsentDefenceResolution() {}

    /**
     * The raid landed on the defended market. Grades it, writes the casualties onto the
     * roster, and settles the contract — active with a thinner detachment, or failed
     * with the assignment released when the garrison was overrun.
     *
     * @param defenderStrength what vanilla counted for the market's ground defences,
     *                         the stationed contribution included
     * @return the graded outcome, or null when the payload no longer settles
     */
    public static Outcome applyLanded(CampaignState state, GarrisonDefensePayload payload,
                                      MarineRoster roster, int day,
                                      float defenderStrength) {
        return applyLanded(state, payload, roster, day, defenderStrength,
                DefenceReport.Place.UNKNOWN);
    }

    /**
     * {@link #applyLanded(CampaignState, GarrisonDefensePayload, MarineRoster, int, float)}
     * with the place the defence report names.
     */
    public static Outcome applyLanded(CampaignState state, GarrisonDefensePayload payload,
                                      MarineRoster roster, int day,
                                      float defenderStrength, DefenceReport.Place place) {
        if (state == null || payload == null) return null;
        boolean unestimated = payload.attackerStrength <= 0f;
        float effectiveness = unestimated
                ? UNESTIMATED_EFFECTIVENESS
                : AbsentDefenceGrade.raidEffectiveness(
                        payload.attackerStrength, defenderStrength);
        Outcome outcome = unestimated ? Outcome.HELD_WITH_LOSSES
                : AbsentDefenceGrade.grade(effectiveness);
        int casualties = AbsentDefenceGrade.casualties(
                payload.activeSeats, outcome, effectiveness);
        return settle(state, payload, roster, day, outcome, casualties, true, place)
                ? outcome : null;
    }

    /**
     * Nothing landed: the raid went home, or the term ran out with it still in the air
     * having never reached the ground. The detachment held its post at no cost.
     *
     * @return {@link Outcome#HELD}, or null when the payload no longer settles
     */
    public static Outcome applyHeld(CampaignState state, GarrisonDefensePayload payload,
                                    MarineRoster roster, int day) {
        return applyHeld(state, payload, roster, day, DefenceReport.Place.UNKNOWN);
    }

    /**
     * {@link #applyHeld(CampaignState, GarrisonDefensePayload, MarineRoster, int)} with
     * the place the defence report names.
     */
    public static Outcome applyHeld(CampaignState state, GarrisonDefensePayload payload,
                                    MarineRoster roster, int day, DefenceReport.Place place) {
        if (state == null || payload == null) return null;
        return settle(state, payload, roster, day, Outcome.HELD, 0, false, place)
                ? Outcome.HELD : null;
    }

    private static boolean settle(CampaignState state, GarrisonDefensePayload payload,
                                  MarineRoster roster, int day, Outcome outcome,
                                  int casualties, boolean landed, DefenceReport.Place place) {
        // Casualties are written before the settlement, so the settlement's own
        // exactly-once guard is too late to protect them: a replayed payload would strike
        // a second set of marines and then be refused. Ask first.
        GarrisonDefensePayload pending =
                GarrisonDefensePayload.from(state, payload.contractId, roster);
        if (pending == null || pending.eventKey != payload.eventKey) return false;
        boolean victory = outcome != Outcome.OVERRUN;
        MarineCaptain captain = StationingLapseResolution.assignedCaptain(
                state, payload.contractId, roster);
        // Read before anything moves: an overrun releases the assignment, and the report
        // names the detachment that stood, not the one left afterwards.
        int contractRow = state.contractIndex(payload.contractId);
        float stationed = contractRow >= 0
                ? StationedStrength.valueFor(state, contractRow, roster) : 0f;
        List<String> squadNames = stationedSquadNames(roster, payload.contractId);
        int seatsStood = roster != null && !payload.fireteamIds.isEmpty()
                ? deployableMarines(roster, payload.contractId).size() : payload.activeSeats;
        // Written before the settlement so the named path recomputes survivors off a
        // roster that already carries the losses.
        Map<MarineSoldier, MarineSoldierStatus> struck =
                applyCasualties(payload, roster, casualties, day, victory);
        GarrisonDefenseResolution.Result result = GarrisonDefenseResolution.apply(
                state, payload.contractId, payload.eventKey, casualties, false, victory,
                roster, StationingLapseResolution.deployed(payload.fireteamIds, roster));
        if (result == null) return false;
        if (result == GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED) {
            StationingLapseResolution.restoreStrandedCaptain(captain, day, OVERRUN_NOTE);
            // Not lapsedForContract: the garrison stood and was beaten. Writing off a
            // fought defence as a walked-away contract would cost the employer standing
            // the player never actually spent.
            ContractReputation.failedForContract(
                    state, payload.contractId, OVERRUN_HOUSE_DELTA, day);
        }
        report(state, payload, day, place, landed, outcome, captain, squadNames, struck,
                casualties, seatsStood, stationed);
        return true;
    }

    /** Appends the company's account of this settlement; idempotent per event key. */
    private static void report(CampaignState state, GarrisonDefensePayload payload, int day,
                               DefenceReport.Place place, boolean landed, Outcome outcome,
                               MarineCaptain captain, List<String> squadNames,
                               Map<MarineSoldier, MarineSoldierStatus> struck,
                               int casualties, int seatsStood, float stationed) {
        List<String> killed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int wounded = 0;
        for (Map.Entry<MarineSoldier, MarineSoldierStatus> entry : struck.entrySet()) {
            switch (entry.getValue()) {
                case KIA -> killed.add(entry.getKey().name());
                case MIA -> missing.add(entry.getKey().name());
                case WIA -> wounded++;
                default -> { }
            }
        }
        int unnamedLost = struck.isEmpty() ? casualties : 0;
        int seatsLiving = Math.max(0,
                seatsStood - killed.size() - missing.size() - unnamedLost);
        int happened = Math.max(0, Math.min(payload.triggeredDay, day));
        DefenceReport.Facts facts = new DefenceReport.Facts(payload.eventKey, happened,
                place, landed, outcome, captain != null ? captain.name() : null,
                squadNames, killed, missing, wounded, unnamedLost, seatsStood, seatsLiving,
                stationed, payload.attackerStrength);
        state.addChronicleGarrisonDefence(payload.eventKey, payload.marketId,
                payload.attackerFactionId, outcome, seatsStood, seatsLiving,
                DefenceReport.compose(facts, Strings::get), happened, Math.max(happened, day));
    }

    private static List<String> stationedSquadNames(MarineRoster roster, long contractId) {
        List<String> names = new ArrayList<>();
        if (roster == null) return names;
        for (MarineSquad squad : roster.squadsStationedOn(contractId)) {
            if (squad.name() != null && !squad.name().isBlank()) names.add(squad.name());
        }
        return names;
    }

    /**
     * Takes {@code casualties} deployable marines out of the named detachment, seeded
     * from the event so the same raid always claims the same people. The manifest hands
     * the whole deployable detachment to the roster: everyone stationed stood the raid,
     * so everyone's service record carries it, and only the chosen few change status.
     *
     * <p>A count-only legacy assignment has no marines to name; its losses are the
     * {@code marinesLost} the settlement subtracts from the committed count instead.
     *
     * @return each struck marine and the fate rolled for them, for the defence report
     */
    private static Map<MarineSoldier, MarineSoldierStatus> applyCasualties(
            GarrisonDefensePayload payload, MarineRoster roster, int casualties, int day,
            boolean victory) {
        Map<MarineSoldier, MarineSoldierStatus> fates = new LinkedHashMap<>();
        if (roster == null || payload.fireteamIds.isEmpty()) return fates;
        List<MarineSoldier> deployable = deployableMarines(roster, payload.contractId);
        if (deployable.isEmpty()) return fates;
        List<MarineSoldier> struck = new ArrayList<>(deployable);
        Collections.shuffle(struck, new Random(payload.eventKey));
        struck = struck.subList(0, Math.min(casualties, struck.size()));

        Map<String, MarineSoldierStatus> manifest = new LinkedHashMap<>();
        for (MarineSoldier soldier : deployable) {
            manifest.put(soldier.id(), MarineSoldierStatus.ACTIVE);
        }
        for (MarineSoldier soldier : struck) {
            MarineSoldierStatus fate = CasualtyFate.roll(
                    CasualtyFate.seed(payload.eventKey, soldier.id()), victory);
            manifest.put(soldier.id(), fate);
            fates.put(soldier, fate);
        }
        // No battle ran, so there is no telemetry to fold into a career record; the
        // deployment itself is all this defence honestly knows about each marine.
        roster.applySoldierOutcome(manifest, day, WIA_DAYS, Collections.emptyMap(), victory);
        return fates;
    }

    /**
     * Deployable strength only. A marine already wounded from an earlier incident is not
     * on the line and cannot be counted a second time.
     */
    private static List<MarineSoldier> deployableMarines(MarineRoster roster, long contractId) {
        List<MarineSoldier> deployable = new ArrayList<>();
        for (MarineSquad squad : roster.squadsStationedOn(contractId)) {
            for (MarineSoldier soldier : roster.squadMembers(squad)) {
                if (soldier.status() == MarineSoldierStatus.ACTIVE) deployable.add(soldier);
            }
        }
        return deployable;
    }
}
