package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.RoleService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.List;
import java.util.function.Consumer;

/**
 * Stateless per-tick sweep over the active equipment drops owned by
 * {@link EquipmentDropService}: marines pick up kits, unclaimed drops recruit a
 * retriever, and consumed drops are purged.
 *
 * <p>A <b>System</b> (processor) — it owns no state; the drop list lives on the
 * Service, which this drives each tick. Named {@code *System}, not
 * {@code *Service}, under the Service(data-owner)/System(processor) convention —
 * see {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
 *
 * <p>Constructor-injected: {@link UnitRosterService} for the dense-registry
 * iteration the pickup + assignment passes do (live marines only), a
 * {@link Consumer Consumer&lt;Entity&gt;} path-clearer (the sim's
 * {@code clearPath} method-ref) so a freshly-assigned retriever drops its stale
 * path and re-pathfinds toward the kit on its next behavior tick, and the
 * {@link EquipmentDropService} that owns the drop list.
 */
public final class EquipmentDropSystem {

    private final UnitRosterService rosterService;
    private final Consumer<Entity> pathClearer;
    private final EquipmentDropService drops;

    public EquipmentDropSystem(UnitRosterService rosterService, Consumer<Entity> pathClearer,
                               EquipmentDropService drops) {
        this.rosterService = rosterService;
        this.pathClearer = pathClearer;
        this.drops = drops;
    }

    /**
     * Per-tick sweep over active equipment drops:
     * <ol>
     *   <li>Any alive marine on a drop cell consumes it and is promoted to
     *       {@link UnitRole#PLANTER} with the drop's objective. Their old role
     *       is wiped — including any other kit they were currently chasing.</li>
     *   <li>Unconsumed drops without an assigned retriever recruit the nearest
     *       alive {@link UnitRole#COMBATANT} marine, promoting them to
     *       {@link UnitRole#KIT_RETRIEVER}. Existing planters and other
     *       retrievers are skipped so they keep their current task.</li>
     *   <li>Consumed drops fall off the list (via {@link EquipmentDropService#removeConsumed()}).</li>
     * </ol>
     */
    public void tick() {
        List<EquipmentDrop> active = drops.getEquipmentDrops();
        if (active.isEmpty()) return;
        // Live-only over the dense registry — every pass here gates on alive
        // marines and mutates only role/target fields (no death), so a plain
        // dense walk is safe; the corpse never appears.
        World world = rosterService.world();
        RoleService role = rosterService.role();

        // Pickup pass — any marine standing on a drop cell takes the kit.
        for (EquipmentDrop drop : active) {
            if (drop.consumed) continue;
            if (drop.objective.isComplete()) { drop.consumed = true; continue; }
            for (int i = 0, n = rosterService.liveCount(); i < n; i++) {
                Entity u = rosterService.get(i);
                if (u.faction != Faction.MARINE) continue;
                if (world.cellX(u.entityId) != drop.cellX || world.cellY(u.entityId) != drop.cellY) continue;
                role.setRole(u.entityId, UnitRole.PLANTER);
                u.assignedObjective = drop.objective;
                u.equipmentDropTarget = null;
                drop.consumed = true;
                break;
            }
        }

        // Assignment pass — make sure each unconsumed drop has a retriever.
        for (EquipmentDrop drop : active) {
            if (drop.consumed) continue;
            if (hasLivingRetriever(drop)) continue;
            Entity nearest = nearestAvailableMarine(drop.cellX, drop.cellY);
            if (nearest != null) {
                role.setRole(nearest.entityId, UnitRole.KIT_RETRIEVER);
                nearest.equipmentDropTarget = drop;
                // Wipe any stale path so the retriever re-pathfinds to the drop
                // next tick instead of continuing toward their old target.
                pathClearer.accept(nearest);
            }
        }

        drops.removeConsumed();
    }

    private boolean hasLivingRetriever(EquipmentDrop drop) {
        RoleService role = rosterService.role();
        for (int i = 0, n = rosterService.liveCount(); i < n; i++) {
            Entity u = rosterService.get(i);
            if (role.role(u.entityId) == UnitRole.KIT_RETRIEVER && u.equipmentDropTarget == drop) return true;
        }
        return false;
    }

    /**
     * Nearest alive marine that isn't actively occupied with an incomplete
     * objective. Skip-by-state instead of skip-by-role so stale role labels
     * (e.g., a PLANTER whose site already blew but didn't tick through their
     * own update yet) don't strand drops with no retriever. Anyone idle —
     * combatant, finished planter, retriever whose kit got picked up — is
     * eligible. Returns null only when every alive marine is genuinely busy.
     */
    private Entity nearestAvailableMarine(int cx, int cy) {
        World world = rosterService.world();
        RoleService role = rosterService.role();
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = rosterService.liveCount(); i < n; i++) {
            Entity u = rosterService.get(i);
            if (u.faction != Faction.MARINE) continue;
            if (role.role(u.entityId) == UnitRole.PLANTER
                    && u.assignedObjective != null
                    && !u.assignedObjective.isComplete()) continue;
            if (role.role(u.entityId) == UnitRole.KIT_RETRIEVER
                    && u.equipmentDropTarget != null
                    && !u.equipmentDropTarget.consumed) continue;
            float d = TacticalScoring.cellDistance(world.cellX(u.entityId), world.cellY(u.entityId), cx, cy);
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }
}
