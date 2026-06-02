package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Dev hotkey for the S0 probe: <b>Ctrl+Shift+B</b> on the campaign map launches a
 * vanilla combat instance via {@link S0BattleProbe#launch()}.
 *
 * <p>A {@link CampaignInputListener} (rather than an intel button) so the trigger
 * only fires on the campaign map — exactly where {@code startBattle} expects to be
 * called — with no risk of launching combat from inside an open UI panel. The
 * Ctrl+Shift modifier keeps it clear of the bare {@code B} campaign binding.
 *
 * <p>Registered transiently each game load by {@code StarsectorMarinesModPlugin},
 * gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public class CombatHybridInputListener implements CampaignInputListener {

    @Override
    public int getListenerInputPriority() {
        return 1;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyDownEvent()
                    && e.getEventValue() == Keyboard.KEY_B
                    && e.isCtrlDown()
                    && e.isShiftDown()) {
                e.consume();
                S0BattleProbe.launch();
                break;
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }
}
