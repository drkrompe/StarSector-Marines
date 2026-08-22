package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.StationingLapseSystem;
import com.dillon.starsectormarines.marine.MarineRoster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure projection of "what is the player being asked to decide right now" over the
 * already-persisted contract tables.
 *
 * <p>It stores nothing. Every notice is derived on demand from
 * {@link GarrisonDefensePayload} / {@link StationingIncidentPayload}, so the domain
 * stays the single source of truth and a resolution applied anywhere — battle,
 * withdrawal, lapse — removes the notice with no bookkeeping here.
 *
 * <p>No {@code Global} access and no side effects, so the whole enumeration and
 * acknowledgement rule set is headless-testable.
 */
public final class PlayerEventInbox {

    /**
     * How many days before the deadline a deferred notice fires its second and final
     * reminder. Also the width of the window in which a freshly armed notice skips
     * straight to the reminder stage, so an event that arms late never pops twice in
     * two days.
     */
    public static final int REMINDER_LEAD_DAYS = 2;

    /** Presented once on arrival. */
    static final byte STAGE_ARRIVAL = 1;
    /** Presented again as the deadline closes. Terminal — a notice never pops a third time. */
    static final byte STAGE_REMINDER = 2;

    private PlayerEventInbox() {}

    /**
     * Every live player decision, soonest deadline first, contract id as tiebreak.
     * Includes notices the player has already been shown; filter with
     * {@link #nextToPresent} for the push surface.
     */
    public static List<PlayerEventNotice> pending(CampaignState state, MarineRoster roster,
                                                  int day) {
        if (state == null) return Collections.emptyList();
        List<PlayerEventNotice> notices = new ArrayList<>();
        for (int row = 0; row < state.contractCount; row++) {
            if (!ContractType.fromByte(state.contractType[row]).isStationing()) continue;
            PlayerEventNotice notice = noticeFor(state, row, roster, day);
            if (notice != null) notices.add(notice);
        }
        notices.sort(Comparator.comparingInt((PlayerEventNotice n) -> n.deadlineDay)
                .thenComparingLong(n -> n.contractId));
        return notices;
    }

    /**
     * The first notice the player has not yet seen at its current urgency, or null when
     * the inbox is empty or fully acknowledged. Ordering matches {@link #pending}, so
     * the most urgent unacknowledged decision is always offered first.
     */
    public static PlayerEventNotice nextToPresent(CampaignState state, MarineRoster roster,
                                                  int day) {
        for (PlayerEventNotice notice : pending(state, roster, day)) {
            if (stageOf(state, notice) < requiredStage(notice, day)) return notice;
        }
        return null;
    }

    /**
     * Records that {@code notice} was actually shown at {@code day}. Call only after the
     * UI confirms it displayed — acknowledging an attempt would silently swallow the
     * event when the dialog was refused.
     *
     * @return true if the acknowledgement moved the row forward
     */
    public static boolean acknowledge(CampaignState state, PlayerEventNotice notice, int day) {
        if (state == null || notice == null) return false;
        int row = state.contractIndex(notice.contractId);
        if (row < 0) return false;
        byte stage = requiredStage(notice, day);
        if (stageOf(state, notice) >= stage) return false;
        state.contractNoticeAckKey[row] = notice.sourceKey;
        state.contractNoticeAckStage[row] = stage;
        return true;
    }

    /** True once this exact notice has been shown at least once. */
    public static boolean isAcknowledged(CampaignState state, PlayerEventNotice notice) {
        return stageOf(state, notice) >= STAGE_ARRIVAL;
    }

    /**
     * How far this notice's presentation should have got by {@code day}. A notice inside
     * the reminder window jumps straight to the terminal stage rather than queueing an
     * arrival pop it no longer has time to act on.
     */
    static byte requiredStage(PlayerEventNotice notice, int day) {
        return day >= notice.deadlineDay - REMINDER_LEAD_DAYS ? STAGE_REMINDER : STAGE_ARRIVAL;
    }

    /**
     * The recorded stage, but only for the notice the ack actually describes — a new
     * event arming on the same contract carries a different {@code sourceKey} and so
     * reads as unseen.
     */
    static byte stageOf(CampaignState state, PlayerEventNotice notice) {
        if (state == null || notice == null) return 0;
        int row = state.contractIndex(notice.contractId);
        if (row < 0 || state.contractNoticeAckKey[row] != notice.sourceKey) return 0;
        return state.contractNoticeAckStage[row];
    }

    private static PlayerEventNotice noticeFor(CampaignState state, int row,
                                               MarineRoster roster, int day) {
        long contractId = state.contractId[row];

        GarrisonDefensePayload defense =
                GarrisonDefensePayload.from(state, contractId, roster);
        if (defense != null) {
            return new PlayerEventNotice(PlayerEventNotice.Kind.GARRISON_DEFENSE,
                    contractId, defense.eventKey, defense.marketId, defense.triggeredDay,
                    deadline(state, row, defense.triggeredDay,
                            StationingLapseSystem.GARRISON_RESPONSE_DAYS, day),
                    defense.captainId, defense.committedMarines, defense.activeSeats,
                    "garrisonDefensePending", defenseLabelKey(defense.triggerType));
        }

        StationingIncidentPayload incident =
                StationingIncidentPayload.from(state, contractId, roster);
        if (incident != null) {
            return new PlayerEventNotice(PlayerEventNotice.Kind.CADRE_INCIDENT,
                    contractId, incident.dueDay, incident.marketId, incident.dueDay,
                    deadline(state, row, incident.dueDay,
                            StationingLapseSystem.CADRE_RESPONSE_DAYS, day),
                    incident.captainId, incident.committedMarines, incident.activeSeats,
                    "stationingIncidentPending", incidentLabelKey(incident.type));
        }
        return null;
    }

    /**
     * Prefers the deadline {@code StationingLapseSystem} already armed. Projects the same
     * window when it has not run yet for this event — the inbox is read on frames, the
     * lapse system only on day boundaries, so the popup would otherwise show a countdown
     * to a deadline that does not exist yet.
     */
    private static int deadline(CampaignState state, int row, int armedDay,
                                int windowDays, int day) {
        int armed = state.contractResponseDeadlineTick[row];
        if (armed >= 0) return armed;
        return StationingLapseSystem.armDeadline(armedDay, day, windowDays,
                state.contractExpiresTick[row]);
    }

    private static String defenseLabelKey(GarrisonDefenseTriggerType type) {
        switch (type) {
            case RIVAL_STRIKE: return "garrisonDefenseRivalStrike";
            case VANILLA_RAID: return "garrisonDefenseVanillaRaid";
            case INTERNAL_FLIP: return "garrisonDefenseInternalFlip";
            case NONE:
            default: return "garrisonDefenseUnknown";
        }
    }

    private static String incidentLabelKey(StationingIncidentType type) {
        switch (type) {
            case FACTORY_ACCIDENT: return "stationingIncidentFactoryAccident";
            case LIVE_FIRE_RAID: return "stationingIncidentLiveFireRaid";
            case DEFECTOR_LEAD: return "stationingIncidentDefectorLead";
            case NONE:
            default: return "stationingIncidentUnknown";
        }
    }
}
