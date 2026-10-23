package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.LoadingProgress;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BattleLoadingOverlayModelTest {

    private record Stuck(int stepsDone, int stepCount, String stage) implements LoadingProgress { }

    @Test
    void theSpinnerWalksOnFrameTimeWhileProgressStandsStill() {
        BattleLoadingOverlayModel model = new BattleLoadingOverlayModel(new Reactor());
        Stuck stuck = new Stuck(3, 10, "Loading sprites");
        Map<String, Object> props = model.props();

        model.update(0.1f, stuck);
        String before = lit(props);
        String style = model.progressStyle();
        model.update(BattleLoadingOverlayModel.SPIN_PERIOD_SECONDS / BattleLoadingOverlayModel.DOTS, stuck);

        assertNotEquals(before, lit(props), "the lit dot moved on");
        assertEquals(style, model.progressStyle(), "the bar did not");
        assertEquals("width: 30%;", style);
        assertEquals("Loading sprites", model.stage());
    }

    @Test
    void theLitDotWalksTheRowOncePerPeriod() {
        float third = BattleLoadingOverlayModel.SPIN_PERIOD_SECONDS / 3f;
        assertEquals(0, BattleLoadingOverlayModel.litDot(0.01f));
        assertEquals(1, BattleLoadingOverlayModel.litDot(third + 0.01f));
        assertEquals(2, BattleLoadingOverlayModel.litDot(2 * third + 0.01f));
        assertEquals(0, BattleLoadingOverlayModel.litDot(BattleLoadingOverlayModel.SPIN_PERIOD_SECONDS + 0.01f));
    }

    /** Which dot props currently read as lit. */
    private static String lit(Map<String, Object> props) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < BattleLoadingOverlayModel.DOTS; i++) {
            Signal<?> signal = (Signal<?>) props.get("dot" + i + "Classes");
            out.append(String.valueOf(signal.get()).contains("spin-lit") ? i : "");
        }
        return out.toString();
    }
}
