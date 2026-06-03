package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.EquipmentDropService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.unit.DeathDispatcher;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Stateless mailbox-consumer for the damage pipeline. One entry point —
 * {@link #resolve} — runs the whole post-impact sequence:
 *
 * <ol>
 *   <li>Cover lookup + cover-reduction curve</li>
 *   <li>HP write + death detection</li>
 *   <li>Death cascade — death-pose roll, death-sink emit ({@code deathsThisFrame}),
 *       equipment drop, squad-leader promotion if the dead unit led one,
 *       {@link DeathDispatcher#publish death-event publish} for the migrated
 *       post-death handlers, then dense-registry release</li>
 *   <li>Morale drain — per-mech HP threshold drain for {@link MechLoadoutComponent}
 *       chassis, per-squad hit/death drain (cooldown-gated) for infantry, no-op
 *       for solo units</li>
 * </ol>
 *
 * <p>Counterpart to {@link DamageService}, which owns the inline/queue gate
 * and the SoA queue. The resolver is the body that fires either inline
 * (serial path) or out of the flush drain (parallel-dispatch path). Same
 * applier method ref both ways — semantics are identical across paths.
 *
 * <p>Method ref shape: {@link DamageService.DamageApplier} — four positional
 * args (target, damage, vsTurretMult, moraleImpact). No event class; the
 * SoA queue stores those four values in parallel arrays.
 *
 * <p>Dependencies are constructor-injected. No state — safe to share across
 * the lifetime of a {@code BattleSimulation}.
 */
public final class DamageResolver {

    /**
     * Damage reduction by cover tier — index = cover level. Matches the
     * historical sim constants. Lives here because the only readers are
     * {@link #resolve(DamageEvent)} (and any caller routed through it,
     * including external-strafing damage rerouted from the flyby overlay).
     */
    public static final float[] COVER_DAMAGE_REDUCTION = { 0f, 0.15f, 0.30f, 0.45f };

    private final NavigationGrid grid;
    private final Int2ObjectMap<Squad> squads;
    private final UnitRosterService roster;
    private final EquipmentDropService equipmentDrops;
    private final Consumer<Entity> deathSink;
    private final DeathDispatcher deathDispatcher;
    private final Random rng;
    private final ComponentStore<MechLoadoutComponent> mechLoadouts;

    public DamageResolver(NavigationService navigation,
                          UnitRosterService roster,
                          EquipmentDropService equipmentDrops,
                          Consumer<Entity> deathSink,
                          DeathDispatcher deathDispatcher,
                          Random rng,
                          ComponentStore<MechLoadoutComponent> mechLoadouts) {
        this.grid = navigation.getGrid();
        this.squads = roster.getSquadsMap();
        this.roster = roster;
        this.equipmentDrops = equipmentDrops;
        this.deathSink = deathSink;
        this.deathDispatcher = deathDispatcher;
        this.rng = rng;
        this.mechLoadouts = mechLoadouts;
    }

    /**
     * Resolves a damage event. Idempotent w.r.t. already-dead targets — a
     * target killed by a prior queued entry is skipped entirely when a stacked
     * entry resolves against it later in the same flush, so it doesn't re-emit
     * equipment / death-FX / leader-promo.
     *
     * <p>The early-out is also a fail-loud guard: a lethal entry runs
     * {@link UnitRosterService#releaseFromRegistry} in the cascade below, which
     * nulls the unit's registry pointer. Stacked damage against the same target
     * in one parallel UPDATE_UNITS flush means a later entry sees a released
     * unit, whose Group-C cell accessors ({@link Entity#getCellX}/{@link
     * Entity#getCellY}, read just below for the cover lookup) NPE. Death and
     * registry-release are atomic inside this method, so {@code !wasAlive}
     * means the target is already dead — and the damage is moot anyway.
     */
    public void resolve(Entity target, float damage, float vsTurretMult, float moraleImpact) {
        UnitRegistry registry = roster.getRegistry();
        boolean wasAlive = registry.isAliveById(target.entityId);
        if (!wasAlive) return;
        int tcx = registry.cellXById(target.entityId);
        int tcy = registry.cellYById(target.entityId);
        int targetCover = grid.getCoverAt(tcx, tcy);
        float dr = COVER_DAMAGE_REDUCTION[Math.min(targetCover, COVER_DAMAGE_REDUCTION.length - 1)];
        // vsTurretMult is misnamed history — it's the "vs hardened" multiplier.
        // Honor it for every class TacticalScoring.isHardened recognizes so the
        // AI's projectedRocketDamageOnTarget projection matches the actual HP
        // hit (drone hubs, heavy mechs both took 1× before despite the AI
        // assuming 3.5×, which suppressed the second/third volley rocket the
        // squad gate actually needed). One contract, one classifier.
        float effectiveMult = TacticalScoring.isHardened(target) ? vsTurretMult : 1f;
        float newHp = registry.hpById(target.entityId) - damage * effectiveMult * (1f - dr);
        registry.setHpById(target.entityId, newHp);
        boolean died = newHp <= 0f;   // wasAlive is guaranteed by the early return above
        if (died) {
            target.deathPoseIdx = rng.nextInt(4);
            deathSink.accept(target);
            equipmentDrops.emitIfApplicable(target);
            // Squad leader promotion — if the dead unit was leading a
            // squad, hand the badge to the closest still-alive member.
            // Preserves direction of travel: the new leader stands roughly
            // where the old one fell, so followers don't get yanked
            // sideways when the leader dies mid-maneuver. NO_SQUAD units
            // (turrets, civilians, etc.) skip — no leader to promote.
            if (target.squadId != Entity.NO_SQUAD) {
                Squad ls = squads.get(target.squadId);
                if (ls != null && ls.leaderId == target.entityId) {
                    Entity promoted = pickPromotionCandidate(ls, target);
                    ls.leaderId = (promoted != null) ? promoted.entityId : 0L;
                }
            }
            // Publish the death to the mailbox BEFORE the registry release,
            // so a handler that wants the live entity still sees it. Buffered:
            // handlers don't run here — DeathDispatcher.drain() fans them out
            // at the demolition phase. Every post-death reaction (turret + hub
            // demolition, drone crash, mech wreck, dead-body/render) now reacts
            // off this event, not a list scan. See DeathDispatcher +
            // retire-legacy-units-list. Snapshot the death cell into the event
            // here, while the target is still registered — handlers run at the
            // drain (post-release) where the Group-C cell accessors fail loud.
            deathDispatcher.publish(new DeathEvent(target, tcx, tcy));
            // Drop the dense-registry entry. The legacy units list still retains
            // the dead unit (no cleanup path) until it's deleted outright, but
            // nothing reads a released unit through it anymore — this release is
            // effectively the death bookkeeping. See UnitRegistry class doc.
            roster.releaseFromRegistry(target.entityId);
        }
        // Morale drain — branches on unit type. Gated on moraleImpact > 0
        // so external-source damage (air strafing, scripted scenario damage)
        // can route through the resolver via moraleImpact=0 and skip morale
        // entirely. Preserves the historical applyExternalDamage semantic
        // where strafing didn't rattle squads or trip mech HP thresholds.
        //
        // Mech-class targets use per-chassis morale: HP threshold crossings
        // drain {@link MechLoadoutComponent#morale} (squad-level aggregation
        // happens in {@link SquadMoraleSystem#tick}). The squad's
        // {@link Squad#morale} field is unused for mech squads.
        //
        // Infantry squad members feed the legacy squad-level drain (hit
        // event + cap scaling + death bonus). Solo units (turrets, civilians)
        // skip both — their behaviors don't consult MORALE_BROKEN.
        if (wasAlive && moraleImpact > 0f) {
            if (mechLoadouts.has(target.entityId)) {
                // Only a SURVIVING mech accrues HP-threshold morale drain. A mech
                // killed by this very hit was already released from the registry
                // (above) — its HEALTH component still reads until the death drain
                // transmutes it to a corpse, but a dead mech's threshold morale is
                // moot, so keep the guard.
                if (!died) applyMechHpThresholdDrain(target);
            } else if (target.squadId != Entity.NO_SQUAD) {
                applySquadMoraleDrain(target, moraleImpact, died);
            }
        }
    }

    /**
     * Promotes the closest still-alive squadmate to leader. Returns null if
     * the squad has no other survivors — caller will see a leaderless squad
     * on the next tick and the cohesion / GOAP layers handle that gracefully.
     *
     * <p>Bulk consumer: iterates the registry's dense array directly and
     * reads positions via {@code cellXById/cellYById}. The dead leader
     * is still present in the dense view at this point (we run BEFORE
     * {@code releaseFromRegistry} in the same resolve() call) — filtered by
     * the {@code u == deadLeader} identity check. Other dead units cannot
     * appear: prior deaths this frame would have released themselves in
     * their own resolve() calls.
     */
    private Entity pickPromotionCandidate(Squad squad, Entity deadLeader) {
        Entity best = null;
        float bestDistSq = Float.MAX_VALUE;
        UnitRegistry registry = roster.getRegistry();
        int lx = registry.cellXById(deadLeader.entityId);
        int ly = registry.cellYById(deadLeader.entityId);
        Entity[] dense = registry.denseArray();
        int liveCount = registry.liveCount();
        for (int i = 0; i < liveCount; i++) {
            Entity u = dense[i];
            if (u == deadLeader || u.squadId != squad.id) continue;
            int dx = registry.cellXById(u.entityId) - lx;
            int dy = registry.cellYById(u.entityId) - ly;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }

    /**
     * Squad-level morale drain (infantry path). Death drain stacks on top of
     * hit drain and bypasses the cooldown — a kill is a discrete event the
     * model should always reflect. The hit-component only fires when the
     * cooldown is clear; otherwise a burst of hits in one tick would each
     * stack their per-hit drain and break a full squad in a single frame.
     * Recovery gate (timeSinceUnderFire reset) fires on every hit, even when
     * the drain cooldown blocked the drop — otherwise a sustained burst would
     * only reset the timer on the first bullet, letting recovery resume
     * mid-volley.
     */
    private void applySquadMoraleDrain(Entity target, float moraleImpact, boolean died) {
        Squad sq = squads.get(target.squadId);
        if (sq == null) return;
        float cap = (sq.originalSize > 0 && sq.aliveMembers > 0)
                ? (float) sq.aliveMembers / sq.originalSize
                : 1f;
        float drop = 0f;
        if (sq.moraleDrainCooldown <= 0f) {
            float hit = (cap > 0f)
                    ? SquadMoraleSystem.MORALE_DROP_ON_HIT / cap
                    : SquadMoraleSystem.MORALE_DROP_ON_HIT;
            drop += hit * moraleImpact;
            sq.moraleDrainCooldown = SquadMoraleSystem.MORALE_DRAIN_COOLDOWN;
        }
        if (died) drop += SquadMoraleSystem.MORALE_DROP_ON_DEATH;
        if (drop > 0f) sq.morale = Math.max(0f, sq.morale - drop);
        sq.timeSinceUnderFire = 0f;
    }

    /**
     * Mech-side morale drain on damage. Counts how many entries in
     * {@link SquadMoraleSystem#MECH_HP_DRAIN_THRESHOLDS} the chassis HP just
     * crossed and drops {@link MechLoadoutComponent#morale} by
     * {@link SquadMoraleSystem#MECH_MORALE_DROP_PER_THRESHOLD} per crossing.
     * Always resets {@link MechLoadoutComponent#timeSinceUnderFire} so recovery
     * pauses through sustained fire — even a hit that didn't cross a fresh
     * threshold counts as "still under fire."
     *
     * <p>Monotonic via {@link MechLoadoutComponent#hpThresholdsCrossed} — a healed
     * mech (none today, but defensive) wouldn't refund drains. The drain is
     * keyed to "how far through this fight have you been damaged," not to
     * instantaneous HP.
     */
    private void applyMechHpThresholdDrain(Entity target) {
        MechLoadoutComponent m = mechLoadouts.get(target.entityId);
        m.timeSinceUnderFire = 0f;
        UnitRegistry registry = roster.getRegistry();
        float maxHp = registry.maxHpById(target.entityId);
        if (maxHp <= 0f) return;
        float frac = Math.max(0f, registry.hpById(target.entityId)) / maxHp;
        int newCount = 0;
        for (float t : SquadMoraleSystem.MECH_HP_DRAIN_THRESHOLDS) {
            if (frac <= t) newCount++;
        }
        int crossings = newCount - m.hpThresholdsCrossed;
        if (crossings <= 0) return;
        m.hpThresholdsCrossed = newCount;
        m.morale = Math.max(0f, m.morale - crossings * SquadMoraleSystem.MECH_MORALE_DROP_PER_THRESHOLD);
    }
}
