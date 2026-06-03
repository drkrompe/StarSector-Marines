package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * S0 probe — <b>requirement 2: the mod owns combat completion.</b>
 *
 * <p>Installed into the probe battle by {@link S0BattleCreationPlugin}. On the
 * first frame it calls {@link CombatEngineAPI#setDoNotEndCombat(boolean)
 * setDoNotEndCombat(true)} so vanilla never auto-terminates the engagement — not
 * when a side is wiped, not when the player retreats. From then on <em>we</em>
 * decide: pressing {@code F10} ends combat via {@link CombatEngineAPI#endCombat(float,
 * FleetSide)} with a winner of our choosing.
 *
 * <p>To make the suppression observable, it logs once when a side is eliminated —
 * the point at which vanilla would normally have ended the battle — and keeps the
 * fight running anyway. That log line is the probe's verdict signal for
 * requirement 2.
 *
 * <p>Throwaway dev scaffolding; gated upstream by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public class S0CompletionPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(S0CompletionPlugin.class);

    /** Manual end-combat key. F10 is unbound in vanilla combat, so no clash. */
    private static final int END_KEY = Keyboard.KEY_F10;

    private CombatEngineAPI engine;
    private boolean suppressed;
    private boolean loggedWouldEnd;
    private boolean ending;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) return;

        if (!suppressed) {
            engine.setDoNotEndCombat(true);
            suppressed = true;
            LOG.info("S0 probe: vanilla auto-end suppressed (setDoNotEndCombat=true). "
                    + "Mod owns completion — press F10 to end the battle.");
        }

        if (!loggedWouldEnd && oneSideEliminated()) {
            loggedWouldEnd = true;
            LOG.info("S0 probe: a side is eliminated — vanilla would normally END the battle here. "
                    + "Still running because we own completion. Press F10 to end on our terms.");
        }

        if (!ending) {
            for (InputEventAPI e : events) {
                if (e.isConsumed()) continue;
                if (e.isKeyDownEvent() && e.getEventValue() == END_KEY) {
                    e.consume();
                    ending = true;
                    engine.setDoNotEndCombat(false);
                    engine.endCombat(0.1f, FleetSide.PLAYER);
                    LOG.info("S0 probe: F10 pressed — ending combat (winner=PLAYER) on our terms.");
                    break;
                }
            }
        }
    }

    /** True once either owner has no live, non-hulk ship left in the engine. */
    private boolean oneSideEliminated() {
        boolean playerAlive = false;
        boolean enemyAlive = false;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isHulk() || !ship.isAlive() || ship.isFighter()) continue;
            if (ship.getOriginalOwner() == 0) {
                playerAlive = true;
            } else if (ship.getOriginalOwner() == 1) {
                enemyAlive = true;
            }
        }
        return !(playerAlive && enemyAlive);
    }
}
