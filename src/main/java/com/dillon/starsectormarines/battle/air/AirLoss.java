package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * One aircraft the battle destroyed, remembered after the entity is reaped.
 *
 * <p><b>The entity cannot answer this question later.</b> A shot-down craft is
 * GONE at the end of the tick that killed it and every component goes with it,
 * so anything the campaign wants to know about the loss has to be copied out at
 * the moment it happens. That is the whole reason this exists as a ledger entry
 * rather than as a query.
 *
 * <p>Written by {@code AirSystem} and read once, at resolution. Nothing in the
 * sim reads it back: a loss changes nothing about the battle it happened in,
 * which is why it can be an append-only list rather than state anybody has to
 * keep in step.
 *
 * @param frame what it was flying as — a {@link FittedBoat} for one of the
 *     company's own, a bare {@link ShuttleType} or a fighter profile otherwise
 * @param faction whose it was
 * @param cause what killed it, as the log says it
 * @param passengerSoldierIds the campaign ids of the marines still aboard, in
 *     seat order; empty when the loadout named none, which is every craft
 *     carrying people the campaign does not track
 * @param passengersAboard how many were still aboard, named or not — an
 *     unnamed passenger still died
 */
public record AirLoss(Airframe frame, Faction faction, String cause,
                      List<String> passengerSoldierIds, int passengersAboard) {

    public AirLoss {
        passengerSoldierIds = passengerSoldierIds == null
                ? List.of() : List.copyOf(passengerSoldierIds);
    }
}
