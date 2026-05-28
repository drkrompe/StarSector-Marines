package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapons.MechLoadoutState;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.equipment.EquipmentDropService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.List;
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
 *       equipment drop, squad-leader promotion if the dead unit led one</li>
 *   <li>Morale drain — per-mech HP threshold drain for {@link MechLoadoutState}
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
    private final List<Unit> units;
    private final Int2ObjectMap<Squad> squads;
    private final UnitRosterService roster;
    private final EquipmentDropService equipmentDrops;
    private final Consumer<Unit> deathSink;
    private final Random rng;

    public DamageResolver(NavigationService navigation,
                          UnitRosterService roster,
                          EquipmentDropService equipmentDrops,
                          Consumer<Unit> deathSink,
                          Random rng) {
        this.grid = navigation.getGrid();
        this.units = roster.getUnits();
        this.squads = roster.getSquadsMap();
        this.roster = roster;
        this.equipmentDrops = equipmentDrops;
        this.deathSink = deathSink;
        this.rng = rng;
    }

    /**
     * Resolves a damage event. Idempotent w.r.t. already-dead targets — the
     * HP write still runs (drives the corpse into the negatives, no behavior
     * change) but the death cascade is gated on the wasAlive→isAlive
     * transition, so a target killed by a prior queued entry doesn't re-emit
     * equipment / death-FX / leader-promo when a stacked entry resolves
     * against it later in the same flush.
     */
    public void resolve(Unit target, float damage, float vsTurretMult, float moraleImpact) {
        boolean wasAlive = target.isAlive();
        int targetCover = grid.getCoverAt(target.getCellX(), target.getCellY());
        float dr = COVER_DAMAGE_REDUCTION[Math.min(targetCover, COVER_DAMAGE_REDUCTION.length - 1)];
        // vsTurretMult is misnamed history — it's the "vs hardened" multiplier.
        // Honor it for every class TacticalScoring.isHardened recognizes so the
        // AI's projectedRocketDamageOnTarget projection matches the actual HP
        // hit (drone hubs, heavy mechs both took 1× before despite the AI
        // assuming 3.5×, which suppressed the second/third volley rocket the
        // squad gate actually needed). One contract, one classifier.
        float effectiveMult = TacticalScoring.isHardened(target) ? vsTurretMult : 1f;
        target.setHp(target.getHp() - damage * effectiveMult * (1f - dr));
        boolean died = wasAlive && !target.isAlive();
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
            if (target.squadId != Unit.NO_SQUAD) {
                Squad ls = squads.get(target.squadId);
                if (ls != null && ls.leader == target) {
                    ls.leader = pickPromotionCandidate(ls, target);
                }
            }
            // Drop the dense-registry entry. The legacy units list keeps
            // the dead unit so post-death consumers (turret demolition,
            // drone crash, etc.) still see it; those migrate in a later
            // phase, at which point this release becomes the sole death
            // bookkeeping. See UnitRegistry class doc.
            roster.releaseFromRegistry(target.entityId);
        }
        // Morale drain — branches on unit type. Gated on moraleImpact > 0
        // so external-source damage (air strafing, scripted scenario damage)
        // can route through the resolver via moraleImpact=0 and skip morale
        // entirely. Preserves the historical applyExternalDamage semantic
        // where strafing didn't rattle squads or trip mech HP thresholds.
        //
        // Mech-class targets use per-chassis morale: HP threshold crossings
        // drain {@link MechLoadoutState#morale} (squad-level aggregation
        // happens in {@link SquadMoraleSystem#tick}). The squad's
        // {@link Squad#morale} field is unused for mech squads.
        //
        // Infantry squad members feed the legacy squad-level drain (hit
        // event + cap scaling + death bonus). Solo units (turrets, civilians)
        // skip both — their behaviors don't consult MORALE_BROKEN.
        if (wasAlive && moraleImpact > 0f) {
            if (target.mech != null) {
                applyMechHpThresholdDrain(target);
            } else if (target.squadId != Unit.NO_SQUAD) {
                applySquadMoraleDrain(target, moraleImpact, died);
            }
        }
    }

    /**
     * Promotes the closest still-alive squadmate to leader. Returns null if
     * the squad has no other survivors — caller will see a leaderless squad
     * on the next tick and the cohesion / GOAP layers handle that gracefully.
     *
     * <p>Bulk SoA consumer: iterates the registry's dense array directly and
     * reads positions from {@code cellXArray()/cellYArray()}. The dead leader
     * is still present in the dense view at this point (we run BEFORE
     * {@code releaseFromRegistry} in the same resolve() call) — filtered by
     * the {@code u == deadLeader} identity check. Other dead units cannot
     * appear: prior deaths this frame would have released themselves in
     * their own resolve() calls.
     */
    private Unit pickPromotionCandidate(Squad squad, Unit deadLeader) {
        Unit best = null;
        float bestDistSq = Float.MAX_VALUE;
        int lx = deadLeader.getCellX();
        int ly = deadLeader.getCellY();
        UnitRegistry registry = roster.getRegistry();
        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();
        for (int i = 0; i < liveCount; i++) {
            Unit u = dense[i];
            if (u == deadLeader || u.squadId != squad.id) continue;
            int dx = cellX[i] - lx;
            int dy = cellY[i] - ly;
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
    private void applySquadMoraleDrain(Unit target, float moraleImpact, boolean died) {
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
     * crossed and drops {@link MechLoadoutState#morale} by
     * {@link SquadMoraleSystem#MECH_MORALE_DROP_PER_THRESHOLD} per crossing.
     * Always resets {@link MechLoadoutState#timeSinceUnderFire} so recovery
     * pauses through sustained fire — even a hit that didn't cross a fresh
     * threshold counts as "still under fire."
     *
     * <p>Monotonic via {@link MechLoadoutState#hpThresholdsCrossed} — a healed
     * mech (none today, but defensive) wouldn't refund drains. The drain is
     * keyed to "how far through this fight have you been damaged," not to
     * instantaneous HP.
     */
    private void applyMechHpThresholdDrain(Unit target) {
        MechLoadoutState m = target.mech;
        m.timeSinceUnderFire = 0f;
        float maxHp = target.getMaxHp();
        if (maxHp <= 0f) return;
        float frac = Math.max(0f, target.getHp()) / maxHp;
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
