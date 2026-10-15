package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Compile-only stand-in; Nexerelin supplies the real class. See {@code nex-stubs/build.gradle}. */
public class GroundBattleIntel {

    public GroundBattleIntel(MarketAPI market, FactionAPI attacker, FactionAPI defender) {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public void init() {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public void start() {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public void setPlayerInitiated(boolean playerInitiated) {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public void setPlayerIsAttacker(Boolean bool) {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public void endBattle(BattleOutcome outcome) {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public static GroundBattleIntel getOngoing(MarketAPI market) {
        throw new UnsupportedOperationException("Nexerelin stub");
    }

    public enum BattleOutcome {
        ATTACKER_VICTORY, DEFENDER_VICTORY, PEACE, DESTROYED, CANCELLED, OTHER
    }
}
