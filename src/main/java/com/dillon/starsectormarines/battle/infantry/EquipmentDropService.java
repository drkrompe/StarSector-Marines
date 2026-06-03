package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the active equipment-drop list and the per-tick retrieve / pickup
 * sweep. {@link #emitIfApplicable(Entity)} is called by the sim's death
 * cascade ({@code DamageResolver.resolve} for all damage paths — combat fire,
 * AoE splash, and external strafing all route through it); {@link #tick()}
 * runs once per tick to drive pickups, retriever assignments, and cleanup.
 *
 * <p>Constructor-injected dependencies: {@link UnitRosterService} for the
 * dense-registry iteration the pickup + assignment passes do (live marines
 * only), and a
 * {@link Consumer Consumer&lt;Entity&gt;} path-clearer (the sim's
 * {@code clearPath} method-ref) so a freshly-assigned retriever drops its
 * stale path and re-pathfinds toward the kit on its next behavior tick.
 *
 * <p>Sibling slice to {@link UnitRosterService},
 * {@link com.dillon.starsectormarines.battle.combat.DamageService},
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService}, et al.
 */
public final class EquipmentDropService {

    private final UnitRosterService rosterService;
    private final Consumer<Entity> pathClearer;

    private final List<EquipmentDrop> equipmentDrops = new ArrayList<>();

    public EquipmentDropService(UnitRosterService rosterService, Consumer<Entity> pathClearer) {
        this.rosterService = rosterService;
        this.pathClearer = pathClearer;
    }

    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDrops; }

    /**
     * Emits an {@link EquipmentDrop} at the dying unit's cell if they were
     * carrying a kit — i.e., a PLANTER (or a KIT_RETRIEVER whose pointer maps
     * to an objective). Skips drops that would point at a completed objective.
     * The drop is placed at the unit's current cell; mission code should
     * ensure the cell is walkable for normal combat, so retrieval is reachable.
     */
    public void emitIfApplicable(Entity dead) {
        Objective carried = null;
        if (dead.role == UnitRole.PLANTER) {
            carried = dead.assignedObjective;
        } else if (dead.role == UnitRole.KIT_RETRIEVER && dead.equipmentDropTarget != null
                && !dead.equipmentDropTarget.consumed) {
            // Retriever was carrying nothing in-hand, but their target kit
            // is still on the ground. We don't emit a new drop — the existing
            // one remains in the world for someone else to grab.
            return;
        }
        if (carried == null || carried.isComplete()) return;
        // Called from DamageResolver.resolve's died branch before release, so the
        // dead unit is still registered — resolve its cell by index once.
        UnitRegistry registry = rosterService.getRegistry();
        int idx = registry.requireLiveIndex(dead.entityId);
        equipmentDrops.add(new EquipmentDrop(registry.getCellX(idx), registry.getCellY(idx), carried));
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
     *   <li>Consumed drops fall off the list.</li>
     * </ol>
     */
    public void tick() {
        if (equipmentDrops.isEmpty()) return;
        // Live-only over the dense registry — every pass here gates on alive
        // marines and mutates only role/target fields (no death), so a plain
        // dense walk is safe; the corpse never appears.
        UnitRegistry registry = rosterService.getRegistry();

        // Pickup pass — any marine standing on a drop cell takes the kit.
        for (EquipmentDrop drop : equipmentDrops) {
            if (drop.consumed) continue;
            if (drop.objective.isComplete()) { drop.consumed = true; continue; }
            for (int i = 0, n = registry.liveCount(); i < n; i++) {
                Entity u = registry.get(i);
                if (u.faction != Faction.MARINE) continue;
                if (registry.getCellX(i) != drop.cellX || registry.getCellY(i) != drop.cellY) continue;
                u.role = UnitRole.PLANTER;
                u.assignedObjective = drop.objective;
                u.equipmentDropTarget = null;
                drop.consumed = true;
                break;
            }
        }

        // Assignment pass — make sure each unconsumed drop has a retriever.
        for (EquipmentDrop drop : equipmentDrops) {
            if (drop.consumed) continue;
            if (hasLivingRetriever(drop, registry)) continue;
            Entity nearest = nearestAvailableMarine(drop.cellX, drop.cellY, registry);
            if (nearest != null) {
                nearest.role = UnitRole.KIT_RETRIEVER;
                nearest.equipmentDropTarget = drop;
                // Wipe any stale path so the retriever re-pathfinds to the drop
                // next tick instead of continuing toward their old target.
                pathClearer.accept(nearest);
            }
        }

        // Cleanup.
        for (int i = equipmentDrops.size() - 1; i >= 0; i--) {
            if (equipmentDrops.get(i).consumed) equipmentDrops.remove(i);
        }
    }

    private boolean hasLivingRetriever(EquipmentDrop drop, UnitRegistry registry) {
        for (int i = 0, n = registry.liveCount(); i < n; i++) {
            Entity u = registry.get(i);
            if (u.role == UnitRole.KIT_RETRIEVER && u.equipmentDropTarget == drop) return true;
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
    private Entity nearestAvailableMarine(int cx, int cy, UnitRegistry registry) {
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = registry.liveCount(); i < n; i++) {
            Entity u = registry.get(i);
            if (u.faction != Faction.MARINE) continue;
            if (u.role == UnitRole.PLANTER
                    && u.assignedObjective != null
                    && !u.assignedObjective.isComplete()) continue;
            if (u.role == UnitRole.KIT_RETRIEVER
                    && u.equipmentDropTarget != null
                    && !u.equipmentDropTarget.consumed) continue;
            float d = TacticalScoring.cellDistance(registry.getCellX(i), registry.getCellY(i), cx, cy);
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }
}
