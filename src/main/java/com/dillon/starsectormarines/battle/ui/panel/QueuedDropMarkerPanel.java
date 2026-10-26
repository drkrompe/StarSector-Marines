package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.LiftQueue;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

/**
 * Marks the landing ground of every machine drop still waiting for a ferry craft: those on
 * the lift queue, and those placed while the picker held the battle paused. A drop a craft
 * has taken is in the air and drawn as the craft.
 */
public final class QueuedDropMarkerPanel implements HudPanel {

    private static final float MARKER_ALPHA = 0.75f;

    private final BattleUiContext ctx;

    public QueuedDropMarkerPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void update(float dt) {}

    @Override
    public void render(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (sim == null || camera == null) return;
        float alpha = alphaMult * MARKER_ALPHA;
        LiftQueue queue = sim.liftQueue();
        if (queue != null) {
            for (LiftQueue.MachineDrop drop : queue.queued()) {
                paint(camera, drop.load(), drop.lzX(), drop.lzY(), alpha);
            }
        }
        CommandPowerService service = sim.getCommandPowerService();
        for (CommandPowerService.PendingDrop drop : service.pendingDrops()) {
            if (service.getPower(drop.powerId()) instanceof ArmorDropPower power
                    && drop.manifestIndex() < power.manifest().size()) {
                paint(camera, power.manifest().get(drop.manifestIndex()).load(),
                        drop.cellX() + 0.5f, drop.cellY() + 0.5f, alpha);
            }
        }
    }

    private static void paint(BattleCamera camera, ArmorLoad load, float x, float y, float alpha) {
        String name = new BayManifest.Entry(0, load).typeName();
        CommandPowerTargetingPanel.paintTargetingMarker(camera,
                BattlefieldMarkerPresentation.queuedDrop(name),
                camera.cellToScreenX(x), camera.cellToScreenY(y), alpha);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {}

    @Override
    public boolean isVisible() {
        BattleSimulation sim = ctx.getSim();
        if (sim == null) return false;
        LiftQueue queue = sim.liftQueue();
        return queue != null && !queue.queued().isEmpty()
                || !sim.getCommandPowerService().pendingDrops().isEmpty();
    }
}
