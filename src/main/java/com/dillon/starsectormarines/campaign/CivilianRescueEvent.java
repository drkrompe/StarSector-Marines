package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Choice and outcome policy for the civilian-rescue black-swan event. */
public final class CivilianRescueEvent {

    public enum Result {
        COMMITTED,
        REFUSED,
        RESOLVED,
        INSUFFICIENT_RESOURCES,
        NOT_READY,
        ALREADY_TERMINAL,
        INVALID
    }

    interface ReliefStore {
        boolean commit(int supplies, int fuel);
    }

    private CivilianRescueEvent() {}

    public static long prepare(CampaignState state, long triggerKey,
                               int marketId, int createdTick, int deadlineTick,
                               int suppliesRequired, int fuelRequired,
                               int civiliansAtRisk) {
        if (!validPreparation(state, triggerKey, marketId, createdTick,
                deadlineTick, suppliesRequired, fuelRequired, civiliansAtRisk)) {
            return -1L;
        }
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.CIVILIAN_RESCUE
                    && state.eventTriggerKey[row] == triggerKey) {
                return state.eventId[row];
            }
        }
        return state.appendCampaignEvent(CampaignEventType.CIVILIAN_RESCUE,
                triggerKey, marketId, createdTick, deadlineTick,
                suppliesRequired, fuelRequired, civiliansAtRisk);
    }

    public static Result refuse(CampaignState state, long eventId, int day) {
        int row = rescueRow(state, eventId);
        if (row < 0 || day < 0) return Result.INVALID;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_CHOICE
                || !withinChoiceWindow(state, row, day)) {
            return Result.NOT_READY;
        }
        state.eventState[row] = CampaignEventState.REFUSED.toByte();
        state.eventDecisionTick[row] = day;
        return Result.REFUSED;
    }

    public static Result commit(CampaignState state, long eventId, int day) {
        return commit(state, eventId, day, new ReliefStore() {
            @Override
            public boolean commit(int supplies, int fuel) {
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
                         ReliefStore store) {
        int row = rescueRow(state, eventId);
        if (row < 0 || day < 0 || store == null) return Result.INVALID;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_CHOICE
                || !withinChoiceWindow(state, row, day)) {
            return Result.NOT_READY;
        }
        if (!store.commit(state.eventSuppliesRequired[row],
                state.eventFuelRequired[row])) {
            return Result.INSUFFICIENT_RESOURCES;
        }
        state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        state.eventDecisionTick[row] = day;
        return Result.COMMITTED;
    }

    public static Result resolve(CampaignState state, long eventId,
                                 int civiliansRescued, int day) {
        int row = rescueRow(state, eventId);
        if (row < 0 || civiliansRescued < 0 || day < 0) return Result.INVALID;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.COMMITTED
                || day < state.eventDecisionTick[row]) {
            return Result.NOT_READY;
        }
        state.eventCiviliansRescued[row] = Math.min(
                civiliansRescued, state.eventCiviliansAtRisk[row]);
        state.eventResolvedTick[row] = day;
        state.eventState[row] = CampaignEventState.RESOLVED.toByte();
        return Result.RESOLVED;
    }

    public static boolean hasOpenRescue(CampaignState state) {
        if (state == null) return false;
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.CIVILIAN_RESCUE) {
                continue;
            }
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE
                    || eventState == CampaignEventState.COMMITTED) {
                return true;
            }
        }
        return false;
    }

    private static boolean validPreparation(CampaignState state, long triggerKey,
                                            int marketId, int createdTick,
                                            int deadlineTick,
                                            int suppliesRequired,
                                            int fuelRequired,
                                            int civiliansAtRisk) {
        return state != null && triggerKey >= 0L && marketId >= 0
                && state.marketRegistry.get(marketId) != null
                && createdTick >= 0 && deadlineTick >= createdTick
                && suppliesRequired >= 0 && fuelRequired >= 0
                && (suppliesRequired > 0 || fuelRequired > 0)
                && civiliansAtRisk > 0;
    }

    private static int rescueRow(CampaignState state, long eventId) {
        if (state == null) return -1;
        int row = state.eventIndex(eventId);
        return row >= 0 && CampaignEventType.fromByte(state.eventType[row])
                == CampaignEventType.CIVILIAN_RESCUE ? row : -1;
    }

    private static boolean withinChoiceWindow(CampaignState state, int row, int day) {
        return day >= state.eventCreatedTick[row]
                && day <= state.eventDeadlineTick[row];
    }

    private static CargoAPI playerCargo() {
        CampaignFleetAPI fleet = Global.getSector() != null
                ? Global.getSector().getPlayerFleet() : null;
        return fleet != null ? fleet.getCargo() : null;
    }
}
