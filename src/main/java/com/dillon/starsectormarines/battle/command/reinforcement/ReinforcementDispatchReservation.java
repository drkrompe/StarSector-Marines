package com.dillon.starsectormarines.battle.command.reinforcement;

/** Provisional trigger-owned state released only when delivery rejects terminally. */
@FunctionalInterface
interface ReinforcementDispatchReservation {
    void release();
}
