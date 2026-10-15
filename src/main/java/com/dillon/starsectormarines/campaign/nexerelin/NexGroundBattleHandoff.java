package com.dillon.starsectormarines.campaign.nexerelin;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel.BattleOutcome;

/**
 * The only class that names a Nexerelin type. It builds a ground battle the way Nexerelin's own Invade does and ends
 * it at once with the Marine Operations result, so Nexerelin applies the transfer, unrest, garrison memory, intel and
 * listeners exactly as if its own battle had ended that way ({@code nex-invasion-conquest.md}, slice 0).
 *
 * <p>Reach it only after {@link Nexerelin#isEnabled()}, and call it inside a {@code Throwable} guard: a renamed
 * Nexerelin signature surfaces here as a {@link LinkageError}.
 */
final class NexGroundBattleHandoff {

    private NexGroundBattleHandoff() {}

    /** Whether Nexerelin already has a ground battle running at the market, which this must never stack on. */
    static boolean battleOngoing(MarketAPI market) {
        return GroundBattleIntel.getOngoing(market) != null;
    }

    static void end(MarketAPI market, boolean attackerVictory) {
        FactionAPI attacker = PlayerFactionStore.getPlayerFaction();
        GroundBattleIntel intel = new GroundBattleIntel(market, attacker, market.getFaction());
        intel.setPlayerIsAttacker(true);
        // Player-initiated for init(), so the player commands the attack as in Nexerelin's own prepIntel ...
        intel.setPlayerInitiated(true);
        intel.init();
        // ... but not for start(), which would otherwise take the fleet's cargo marines into Nexerelin units.
        // start() is still required: it creates the response script endBattle() ends, and the defenders a
        // defender victory is measured against.
        intel.setPlayerInitiated(false);
        intel.start();
        intel.setPlayerInitiated(true);
        intel.endBattle(attackerVictory ? BattleOutcome.ATTACKER_VICTORY : BattleOutcome.DEFENDER_VICTORY);
    }
}
