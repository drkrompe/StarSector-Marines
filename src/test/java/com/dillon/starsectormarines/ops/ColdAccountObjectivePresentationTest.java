package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention.Phase;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdAccountObjectivePresentationTest {
    @Test void hudDisclosesOnlyTheCurrentStageAndRetiresWithdrawalAfterRecovery() {
        var model = new BattleHudOverlayModel(new Reactor(), ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateIntervention(Phase.EVIDENCE, "Secure the dispatch office", 100, 100);
        assertEquals("A COLD ACCOUNT", value(model, "objectiveTitle"));
        assertEquals("DISPATCH OFFICE / RECOVER RECORDS", value(model, "objectiveTally"));
        model.updateIntervention(Phase.WITHDRAW, "Clear the bombardment zone", 100, 100);
        assertEquals("HEGEMONY / WITHDRAW", value(model, "objectiveTitle"));
        assertEquals("CLEAR FIRING AREA > SHUTTLE PICKUP", value(model, "objectiveTally"));
        model.updateIntervention(Phase.PATROL, "Resolute resuming patrol", 20, 100);
        assertEquals("COMPANY RECOVERED / STRONGHOLD SILENCED", value(model, "objectiveTally"));
    }

    private Object value(BattleHudOverlayModel model, String key) {
        return ((Signal<?>) model.props().get(key)).get();
    }
}
