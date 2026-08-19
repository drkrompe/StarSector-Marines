package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Choice and outcome authority for a blind Silent Colony expedition. */
public final class SilentColonyEvent {

    public enum Result {
        COMMITTED,
        REFUSED,
        RESOLVED,
        INSUFFICIENT_RESOURCES,
        NOT_READY,
        ALREADY_TERMINAL,
        INVALID
    }

    interface ExpeditionStore {
        boolean consume(int supplies, int fuel);
    }

    private SilentColonyEvent() {}

    public static long prepare(CampaignState state, long triggerKey,
                               int marketId, int createdTick,
                               int deadlineTick, int suppliesRequired,
                               int fuelRequired, int survivorsAtRisk,
                               long threatSeed) {
        if (!validPreparation(state, triggerKey, marketId, createdTick,
                deadlineTick, suppliesRequired, fuelRequired,
                survivorsAtRisk, threatSeed)) {
            return -1L;
        }
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.SILENT_COLONY
                    && state.eventTriggerKey[row] == triggerKey) {
                return state.eventId[row];
            }
        }

        long id = state.appendCampaignEvent(CampaignEventType.SILENT_COLONY,
                triggerKey, marketId, createdTick, deadlineTick,
                suppliesRequired, fuelRequired, survivorsAtRisk);
        int row = state.eventIndex(id);
        state.eventColonyThreatSeed[row] = threatSeed;
        return id;
    }

    public static Result refuse(CampaignState state, long eventId, int day) {
        int row = colonyRow(state, eventId);
        if (row < 0 || day < 0) return Result.INVALID;
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_CHOICE
                || !withinChoiceWindow(state, row, day)) {
            return Result.NOT_READY;
        }
        state.eventDecisionTick[row] = day;
        state.eventState[row] = CampaignEventState.REFUSED.toByte();
        return Result.REFUSED;
    }

    public static Result commit(CampaignState state, long eventId, int day) {
        return commit(state, eventId, day, new ExpeditionStore() {
            @Override
            public boolean consume(int supplies, int fuel) {
                CargoAPI cargo = playerCargo();
                if (cargo == null || cargo.getSupplies() < supplies
                        || cargo.getFuel() < fuel) {
                    return false;
                }
                cargo.removeSupplies(supplies);
                cargo.removeFuel(fuel);
                return true;
            }
        });
    }

    static Result commit(CampaignState state, long eventId, int day,
                         ExpeditionStore store) {
        int row = colonyRow(state, eventId);
        if (row < 0 || day < 0 || store == null) return Result.INVALID;
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_CHOICE
                || !withinChoiceWindow(state, row, day)) {
            return Result.NOT_READY;
        }
        if (!store.consume(state.eventSuppliesRequired[row],
                state.eventFuelRequired[row])) {
            return Result.INSUFFICIENT_RESOURCES;
        }
        state.eventDecisionTick[row] = day;
        state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        return Result.COMMITTED;
    }

    public static Result resolve(CampaignState state, long eventId,
                                 int survivorsRescued,
                                 boolean archiveRecovered, int day) {
        int row = colonyRow(state, eventId);
        if (row < 0 || survivorsRescued < 0 || day < 0) {
            return Result.INVALID;
        }
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.COMMITTED
                || day < state.eventDecisionTick[row]) {
            return Result.NOT_READY;
        }

        state.eventCiviliansRescued[row] = Math.min(survivorsRescued,
                state.eventCiviliansAtRisk[row]);
        state.eventColonyArchiveOutcome[row] = (archiveRecovered
                ? AbandonedColonyArchiveOutcome.RECOVERED
                : AbandonedColonyArchiveOutcome.LOST).toByte();
        state.eventResolvedTick[row] = day;
        state.eventState[row] = CampaignEventState.RESOLVED.toByte();
        return Result.RESOLVED;
    }

    private static boolean validPreparation(CampaignState state,
                                            long triggerKey, int marketId,
                                            int createdTick, int deadlineTick,
                                            int suppliesRequired,
                                            int fuelRequired,
                                            int survivorsAtRisk,
                                            long threatSeed) {
        return state != null && triggerKey >= 0L && marketId >= 0
                && state.marketRegistry.get(marketId) != null
                && createdTick >= 0 && deadlineTick >= createdTick
                && suppliesRequired > 0 && fuelRequired > 0
                && survivorsAtRisk > 0 && threatSeed >= 0L;
    }

    private static int colonyRow(CampaignState state, long eventId) {
        if (state == null) return -1;
        int row = state.eventIndex(eventId);
        return row >= 0 && CampaignEventType.fromByte(state.eventType[row])
                == CampaignEventType.SILENT_COLONY ? row : -1;
    }

    private static CampaignEventState eventState(CampaignState state, int row) {
        return CampaignEventState.fromByte(state.eventState[row]);
    }

    private static boolean withinChoiceWindow(CampaignState state, int row,
                                              int day) {
        return day >= state.eventCreatedTick[row]
                && day <= state.eventDeadlineTick[row];
    }

    private static CargoAPI playerCargo() {
        CampaignFleetAPI fleet = Global.getSector() != null
                ? Global.getSector().getPlayerFleet() : null;
        return fleet != null ? fleet.getCargo() : null;
    }
}
