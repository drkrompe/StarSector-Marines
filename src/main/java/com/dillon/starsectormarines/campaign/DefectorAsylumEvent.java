package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Pure lifecycle policy for the two-stage defector-asylum event. */
public final class DefectorAsylumEvent {

    public static final int CUSTODY_DAYS = 10;
    public static final int FOLLOWUP_CHOICE_DAYS = 3;

    public enum Result {
        COMMITTED,
        REFUSED,
        FOLLOWUP_READY,
        PROTECTED,
        BETRAYED,
        INSUFFICIENT_RESOURCES,
        PAYMENT_UNAVAILABLE,
        NOT_READY,
        ALREADY_TERMINAL,
        INVALID
    }

    interface AsylumStore {
        boolean consume(int supplies, int fuel);
    }

    interface CreditStore {
        boolean grant(int credits);
    }

    private DefectorAsylumEvent() {}

    public static long prepare(CampaignState state, long triggerKey,
                               long sourceChainId, long actorHouseId,
                               long targetHouseId, int marketId,
                               int createdTick, int deadlineTick,
                               int suppliesRequired, int fuelRequired,
                               int creditsOffered) {
        if (!validPreparation(state, triggerKey, sourceChainId, actorHouseId,
                targetHouseId, marketId, createdTick, deadlineTick,
                suppliesRequired, fuelRequired, creditsOffered)) {
            return -1L;
        }
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.DEFECTOR_ASYLUM
                    && state.eventSourceChainId[row] == sourceChainId) {
                return state.eventId[row];
            }
        }

        long id = state.appendCampaignEvent(CampaignEventType.DEFECTOR_ASYLUM,
                triggerKey, marketId, createdTick, deadlineTick,
                suppliesRequired, fuelRequired, 0);
        int row = state.eventIndex(id);
        state.eventSourceChainId[row] = sourceChainId;
        state.eventActorHouseId[row] = actorHouseId;
        state.eventTargetHouseId[row] = targetHouseId;
        state.eventCreditsOffered[row] = creditsOffered;
        return id;
    }

    public static Result refuse(CampaignState state, long eventId, int day) {
        int row = defectorRow(state, eventId);
        if (row < 0 || day < 0) return Result.INVALID;
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_CHOICE
                || !withinInitialWindow(state, row, day)) {
            return Result.NOT_READY;
        }
        state.eventDecisionTick[row] = day;
        state.eventState[row] = CampaignEventState.REFUSED.toByte();
        return Result.REFUSED;
    }

    public static Result commit(CampaignState state, long eventId, int day) {
        return commit(state, eventId, day, new AsylumStore() {
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
                         AsylumStore store) {
        int row = defectorRow(state, eventId);
        if (row < 0 || day < 0 || store == null
                || day > Integer.MAX_VALUE - CUSTODY_DAYS - FOLLOWUP_CHOICE_DAYS) {
            return Result.INVALID;
        }
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_CHOICE
                || !withinInitialWindow(state, row, day)) {
            return Result.NOT_READY;
        }
        if (!store.consume(state.eventSuppliesRequired[row],
                state.eventFuelRequired[row])) {
            return Result.INSUFFICIENT_RESOURCES;
        }

        state.eventDecisionTick[row] = day;
        state.eventFollowupTick[row] = day + CUSTODY_DAYS;
        state.eventFollowupDeadlineTick[row] =
                state.eventFollowupTick[row] + FOLLOWUP_CHOICE_DAYS;
        state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        return Result.COMMITTED;
    }

    public static Result advanceToFollowup(CampaignState state, long eventId, int day) {
        int row = defectorRow(state, eventId);
        if (row < 0 || day < 0) return Result.INVALID;
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState == CampaignEventState.PENDING_FOLLOWUP) {
            return Result.FOLLOWUP_READY;
        }
        if (eventState != CampaignEventState.COMMITTED
                || day < state.eventFollowupTick[row]) {
            return Result.NOT_READY;
        }
        state.eventState[row] = CampaignEventState.PENDING_FOLLOWUP.toByte();
        return Result.FOLLOWUP_READY;
    }

    public static Result protect(CampaignState state, long eventId, int day) {
        int row = defectorRow(state, eventId);
        if (row < 0 || day < 0) return Result.INVALID;
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_FOLLOWUP
                || day < state.eventFollowupTick[row]) {
            return Result.NOT_READY;
        }
        resolve(state, row, DefectorAsylumOutcome.PROTECTED, day);
        return Result.PROTECTED;
    }

    public static Result betray(CampaignState state, long eventId, int day) {
        return betray(state, eventId, day, new CreditStore() {
            @Override
            public boolean grant(int credits) {
                CargoAPI cargo = playerCargo();
                if (cargo == null) return false;
                cargo.getCredits().add(credits);
                return true;
            }
        });
    }

    static Result betray(CampaignState state, long eventId, int day,
                         CreditStore store) {
        int row = defectorRow(state, eventId);
        if (row < 0 || day < 0 || store == null) return Result.INVALID;
        CampaignEventState eventState = eventState(state, row);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.PENDING_FOLLOWUP
                || day < state.eventFollowupTick[row]
                || day > state.eventFollowupDeadlineTick[row]) {
            return Result.NOT_READY;
        }
        if (!store.grant(state.eventCreditsOffered[row])) {
            return Result.PAYMENT_UNAVAILABLE;
        }
        resolve(state, row, DefectorAsylumOutcome.BETRAYED, day);
        return Result.BETRAYED;
    }

    private static void resolve(CampaignState state, int row,
                                DefectorAsylumOutcome outcome, int day) {
        state.eventDefectorOutcome[row] = outcome.toByte();
        state.eventResolvedTick[row] = day;
        state.eventState[row] = CampaignEventState.RESOLVED.toByte();
    }

    private static boolean validPreparation(CampaignState state, long triggerKey,
                                            long sourceChainId,
                                            long actorHouseId,
                                            long targetHouseId, int marketId,
                                            int createdTick, int deadlineTick,
                                            int suppliesRequired,
                                            int fuelRequired,
                                            int creditsOffered) {
        if (state == null || triggerKey < 0L || sourceChainId < 0L
                || actorHouseId < 0L || targetHouseId < 0L
                || actorHouseId == targetHouseId || marketId < 0
                || createdTick < 0 || deadlineTick < createdTick
                || suppliesRequired <= 0 || fuelRequired <= 0
                || creditsOffered <= 0) {
            return false;
        }
        int chainRow = state.chainIndex(sourceChainId);
        return chainRow >= 0
                && state.chainActorHouseId[chainRow] == actorHouseId
                && state.chainTarget[chainRow] == targetHouseId
                && state.chainMarketId[chainRow] == marketId
                && state.houseIndex(actorHouseId) >= 0
                && state.houseIndex(targetHouseId) >= 0
                && state.marketRegistry.get(marketId) != null;
    }

    private static int defectorRow(CampaignState state, long eventId) {
        if (state == null) return -1;
        int row = state.eventIndex(eventId);
        return row >= 0 && CampaignEventType.fromByte(state.eventType[row])
                == CampaignEventType.DEFECTOR_ASYLUM ? row : -1;
    }

    private static CampaignEventState eventState(CampaignState state, int row) {
        return CampaignEventState.fromByte(state.eventState[row]);
    }

    private static boolean withinInitialWindow(CampaignState state, int row, int day) {
        return day >= state.eventCreatedTick[row]
                && day <= state.eventDeadlineTick[row];
    }

    private static CargoAPI playerCargo() {
        CampaignFleetAPI fleet = Global.getSector() != null
                ? Global.getSector().getPlayerFleet() : null;
        return fleet != null ? fleet.getCargo() : null;
    }
}
