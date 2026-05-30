package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.power.CommandPowerService.PendingActivation;

/**
 * Stateless tick consumer for {@link CommandPowerService} — the
 * Services-own-state / Systems-are-stateless shape. Each sim tick it: drains the
 * UI's queued activations (committing the ones the pool can afford and that are
 * off cooldown), regenerates command points, and ages cooldowns + in-flight
 * transient effects down.
 *
 * <p>The view layer ({@code ops.BattleScreen}) owns the {@code TARGETING}
 * state and projects {@link CommandPowerService.ActivePing}s into the fog; this
 * system never touches the UI or the vision pass. It only ever drives the
 * {@code COMMITTED -> COOLDOWN} half of the lifecycle.
 */
public final class CommandPowerSystem {

    private final CommandPowerService service;

    public CommandPowerSystem(CommandPowerService service) {
        this.service = service;
    }

    public void tick(float dt) {
        // Commit queued activations first so a power fired this tick gets its
        // full cooldown before the age-down below trims dt off it.
        for (PendingActivation req : service.drainPending()) {
            CommandPower power = service.getPower(req.powerId);
            if (!service.canActivate(power)) continue; // not affordable / on cooldown — drop
            service.commit(power);
            power.resolve(req.cellX, req.cellY, service);
        }
        service.regenCommandPoints(dt);
        service.tickCooldowns(dt);
        service.tickActivePings(dt);
    }
}
