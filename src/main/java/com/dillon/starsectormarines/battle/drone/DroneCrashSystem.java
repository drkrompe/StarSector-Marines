package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.combat.fx.SmokingWreck;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;

import java.util.List;

/**
 * Per-tick crash sequence for {@link Drone}s that just lost HP. Three
 * phases per drone:
 * <ol>
 *   <li><b>Just-died</b> (alive=false, !crashStarted): mark
 *       {@code crashStarted}, latch {@code crashTimer = CRASH_DURATION_SEC},
 *       puff smoke from the body position.</li>
 *   <li><b>Falling</b> (crashStarted, crashTimer &gt; 0): tick the timer
 *       down, spin the body facing for visual chaos. The renderer reads
 *       this state and draws the drone with a fade-out overlay.</li>
 *   <li><b>Impact</b> (crashTimer &lt;= 0, !crashed): spawn a
 *       {@link SmokingWreck} at the body's floor cell; mark
 *       {@code crashed} so the unit drops off the renderer.</li>
 * </ol>
 *
 * <p>Runs after {@link HubDemolitionSystem} in the sim's tick chain so a
 * hub-cascade kill enters the crash sequence the same tick it dies.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class DroneCrashSystem {

    private final NavigationService navigation;
    private final EffectsService effects;

    public DroneCrashSystem(NavigationService navigation, EffectsService effects) {
        this.navigation = navigation;
        this.effects = effects;
    }

    /**
     * Advances every dead {@link Drone}'s crash state by {@code dt} sim
     * seconds. Live drones, settled wrecks, and non-drones are skipped.
     */
    public void tick(List<Unit> units, float dt) {
        NavigationGrid grid = navigation.getGrid();
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (!(u instanceof Drone d)) continue;
            if (d.crashed) continue;
            if (d.isAlive()) continue;
            if (!d.crashStarted) {
                d.crashStarted = true;
                d.crashTimer = Drone.CRASH_DURATION_SEC;
                effects.spawnSmokePlume(d.body.x, d.body.y);
            }
            d.crashTimer -= dt;
            d.body.facingDegrees += Drone.CRASH_SPIN_DEG_PER_SEC * dt;
            if (d.crashTimer <= 0f) {
                int wx = Math.max(0, Math.min(grid.getWidth() - 1, (int) Math.floor(d.body.x)));
                int wy = Math.max(0, Math.min(grid.getHeight() - 1, (int) Math.floor(d.body.y)));
                effects.spawnSmokingWreck(wx, wy);
                d.crashed = true;
            }
        }
    }
}
