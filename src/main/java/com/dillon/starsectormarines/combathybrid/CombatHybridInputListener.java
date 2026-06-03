package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Dev hotkeys for the combat-bridge probes, on the campaign map:
 * <ul>
 *   <li><b>Ctrl+Shift+B</b> → {@link S0BattleProbe#launch()} (S0: player-piloted
 *       battle from a chosen fleet subset).</li>
 *   <li><b>Ctrl+Shift+K</b> → {@link S0BattleProbe#launchSimCoupled()} (the bridge:
 *       spectator canvas hosting a live sim — vanilla damage drains the sim, sim death
 *       despawns the proxy).</li>
 * </ul>
 *
 * <p>A {@link CampaignInputListener} (rather than an intel button) so the trigger
 * only fires on the campaign map — exactly where {@code startBattle} expects to be
 * called — with no risk of launching combat from inside an open UI panel. The
 * Ctrl+Shift modifier keeps it clear of the bare campaign bindings.
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
            if (!e.isKeyDownEvent() || !e.isCtrlDown() || !e.isShiftDown()) continue;

            if (e.getEventValue() == Keyboard.KEY_B) {
                e.consume();
                S0BattleProbe.launch();
                break;
            }
            if (e.getEventValue() == Keyboard.KEY_K) {
                e.consume();
                S0BattleProbe.launchSimCoupled();
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
