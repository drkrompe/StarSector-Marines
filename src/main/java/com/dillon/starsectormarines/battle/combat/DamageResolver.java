package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechBodyPlan;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.infantry.EquipmentDropService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.squad.FireTeamMorale;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.HitLog;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.DeathDispatcher;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.Random;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

/**
 * Stateless mailbox-consumer for the damage pipeline. One entry point —
 * {@link #resolve} — runs the whole post-impact sequence:
 *
 * <ol>
 *   <li>Body-part damage multiplier, or for a partless payload the cover lookup +
 *       cover-reduction curve</li>
 *   <li>Mitigation arc resolution against the incoming bearing</li>
 *   <li>Armor/structure write + death detection</li>
 *   <li>Part tally, and a limb wound when a legs or arms hit took structure</li>
 *   <li>Telemetry attribution — applied damage to attacker and target,
 *       plus a kill credit when the hit was fatal</li>
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
 * <p>Method ref shape: {@link DamageService.DamageApplier} — six positional
 * args (target, attacker, damage, penetration, moraleImpact, part). No event
 * class; the SoA queue stores those six values in parallel arrays. The
 * attacker id is carried for telemetry attribution only and never changes
 * what the hit does.
 *
 * <p>Dependencies are constructor-injected. The one mutable field is a
 * caller-owned durability result scratch; damage application is serialized by
 * {@link DamageService}, so the shared calculation adds no per-hit allocation.
 */
public final class DamageResolver {

    /**
     * Damage reduction by cover tier — index = cover level. Matches the
     * historical sim constants. Lives here because the only readers are
     * {@link #resolve(DamageEvent)} (and any caller routed through it,
     * including external-strafing damage rerouted from the flyby overlay).
     */
    /**
     * Penetration that armour does not stop at all: the whole hit goes to structure and the armour pool is
     * untouched. Fire on a soft body ({@code FireHarm}) is the one source; only the roster-unit route below
     * honours it, and a mech or hull is never handed it.
     */
    public static final float THROUGH_ARMOR = Float.POSITIVE_INFINITY;

    public static final float[] COVER_DAMAGE_REDUCTION = { 0f, 0.15f, 0.30f, 0.45f };

    private final NavigationGrid grid;
    private final DoodadService doodads;
    private final Int2ObjectMap<Squad> squads;
    private final UnitRosterService roster;
    private final EquipmentDropService equipmentDrops;
    private final LongConsumer deathSink;
    private final DeathDispatcher deathDispatcher;
    private final IntSupplier simTick;
    private final Random rng;
    private final DurabilityModel.Resolution durability = new DurabilityModel.Resolution();
    private final MechSections.Hit sectionHit = new MechSections.Hit();

    public DamageResolver(NavigationService navigation,
                          DoodadService doodads,
                          UnitRosterService roster,
                          EquipmentDropService equipmentDrops,
                          LongConsumer deathSink,
                          DeathDispatcher deathDispatcher,
                          IntSupplier simTick,
                          Random rng) {
        this.grid = navigation.getGrid();
        this.doodads = doodads;
        this.squads = roster.getSquadsMap();
        this.roster = roster;
        this.equipmentDrops = equipmentDrops;
        this.deathSink = deathSink;
        this.deathDispatcher = deathDispatcher;
        this.simTick = simTick;
        this.rng = rng;
    }

    /**
     * Cover the target actually had against <em>this</em> hit.
     *
     * <p>Cover is stored per facing because it is a property of a boundary: a
     * marine with a wall to the east is covered from the east and naked from
     * the south. When the hit has a locatable source, that is exactly the
     * question this seam can ask, and asking it is what stops a feature on one
     * boundary from protecting its cell from every direction — the difference
     * between a barricade and a free durability bonus.
     *
     * <p>An unattributed hit — a detonation with no attacker, a source already
     * released from the roster — has no bearing at all, so there is no facing
     * to read. It falls back to the direction-blind scalar, which is the same
     * "cover in general" figure the whole path used before there was anything
     * to be directional about.
     */
    private int coverAgainst(World world, long targetId, long attackerId,
                             int targetCellX, int targetCellY) {
        if (attackerId == targetId || !roster.isAliveById(attackerId)) {
            return grid.getCoverAt(targetCellX, targetCellY);
        }
        return grid.getCoverAgainst(targetCellX, targetCellY,
                world.cellX(attackerId), world.cellY(attackerId));
    }

    /**
     * Damage cut from the ground the target stands in — a trench — against <em>this</em> hit.
     *
     * <p>A trench has no facing: it covers the man in it from every direction, and more the
     * further off the shooter is ({@link BallisticResolver#occupantCoverScale}). An unattributed
     * hit has no distance to read; a detonation with no attacker is the shelling a trench was dug
     * against, so it meets the full cut.
     */
    private float occupantCoverAgainst(World world, long targetId, long attackerId,
                                       int targetCellX, int targetCellY) {
        int level = doodads.occupantCoverAt(targetCellX, targetCellY, 0f);
        if (level <= 0) return 0f;
        float cut = COVER_DAMAGE_REDUCTION[Math.min(level, COVER_DAMAGE_REDUCTION.length - 1)];
        if (attackerId == targetId || !roster.isAliveById(attackerId)) return cut;
        float dx = world.x(attackerId) - world.x(targetId);
        float dy = world.y(attackerId) - world.y(targetId);
        return cut * BallisticResolver.occupantCoverScale((float) Math.sqrt(dx * dx + dy * dy));
    }

    /**
     * Resolves a damage event. Idempotent w.r.t. already-dead targets — a
     * target killed by a prior queued entry is skipped entirely when a stacked
     * entry resolves against it later in the same flush, so it doesn't re-emit
     * equipment / death-FX / leader-promo.
     *
     * <p>The early-out is also a fail-loud guard: a lethal entry runs
     * {@link UnitRosterService#releaseFromRegistry} in the cascade below, which
     * removes the unit from the live roster. Stacked damage against the same target
     * in one parallel UPDATE_UNITS flush means a later entry sees a released
     * unit, whose by-id cell reads ({@code world.cellX(id)}/{@code
     * world.cellY(id)}, read just below for the cover lookup) would
     * otherwise run against a corpse. Death and registry-release are atomic
     * inside this method, so {@code !wasAlive} means the target is already dead
     * — and the damage is moot anyway.
     */
    public void resolve(long targetId, long attackerId, float damage, float penetration, float moraleImpact,
                        BodyPart part) {
        World world = roster.world();
        boolean wasAlive = roster.isAliveById(targetId);
        if (!wasAlive) return;
        int tcx = world.cellX(targetId);
        int tcy = world.cellY(targetId);
        float tx = world.x(targetId);
        float ty = world.y(targetId);
        // A round that landed on a body part has already beaten the cover: the cover had its
        // say when it decided which parts it hid. Only a payload with no part — an area burst,
        // a strafe, a hull — still has its damage cut by the cover it hit.
        float dr = 0f;
        if (part == null || !part.hasCoverRows()) {
            int targetCover = coverAgainst(world, targetId, attackerId, tcx, tcy);
            dr = Math.max(
                    COVER_DAMAGE_REDUCTION[Math.min(targetCover, COVER_DAMAGE_REDUCTION.length - 1)],
                    occupantCoverAgainst(world, targetId, attackerId, tcx, tcy));
        }
        float hpBefore = world.hp(targetId);
        boolean hasArmor = world.hasArmor(targetId);
        float armorBefore = hasArmor ? world.armor(targetId) : 0f;
        float armorRating = hasArmor ? world.armorRating(targetId) : 0f;
        // What the part is worth: a head hit more, a limb less. Before mitigation and armour,
        // which stay whole-body layers, so penetration still meets the same plate.
        float partMultiplier = part != null ? part.damageMultiplier : 1f;
        float postCoverDamage = damage * partMultiplier * (1f - dr) * world.damageTakenMult(targetId);
        // Stamped before mitigation and armour have their say: "a hit landed on
        // me" is true whatever eventually absorbed it, and a screen that ate the
        // whole round is still evidence somebody is shooting.
        if (postCoverDamage > 0f) {
            roster.combat().recordDamageTaken(targetId, simTick.getAsInt());
        }
        // Mitigation resolves here — after cover, before armor — because the arc
        // is measured against the target's facing at the moment the hit lands,
        // and this is the one seam that knows both. An unattributed hit has no
        // locatable source, so it has no bearing and is never mitigated.
        float mitigationSoak = 0f;
        MitigationService screens = roster.mitigations();
        if (screens.isActive(targetId) && roster.isAliveById(attackerId)) {
            mitigationSoak = screens.soakAgainst(targetId, tx, ty,
                    world.x(attackerId), world.y(attackerId));
        }
        MechLoadoutComponent mech = world.hasMechLoadout(targetId)
                ? world.mechLoadout(targetId) : null;
        float armorDamage;
        float structureDamage;
        float newHp;
        String hitArc = null;
        String hitPart = part != null ? String.valueOf(part) : null;
        if (mech != null) {
            // A mech is hit on a section: its own armor and structure, spill into the torso, and
            // only the torso kills. The chassis columns keep the sections' totals, so every
            // whole-chassis reader (health bar, morale thresholds, AI) sees the same body.
            MechSections sections = mech.sectionsFor(hpBefore, world.maxHp(targetId),
                    armorBefore, hasArmor ? world.maxArmor(targetId) : 0f);
            MechSection section = sectionStruck(world, targetId, attackerId, mech, sections, part);
            ArmorArcs.Arc torsoArc = torsoArc(world, targetId, attackerId, mech);
            ArmorArcs.Profile arcs = MechSections.torsoArcs();
            hitArc = torsoArc.name();
            hitPart = String.valueOf(section);
            sections.resolve(section, postCoverDamage, penetration, mitigationSoak, armorRating,
                    armorRating * arcs.rating(torsoArc), arcs.plate(torsoArc), torsoArc == ArmorArcs.Arc.REAR,
                    durability, sectionHit);
            if (sectionHit.mitigatedDamage() > 0f) screens.absorb(targetId, sectionHit.mitigatedDamage());
            armorDamage = sectionHit.armorDamage();
            structureDamage = sectionHit.structureDamage();
            if (hasArmor) {
                // The rear plate is held on top of the chassis's armor, so the column's maximum follows it.
                world.setMaxArmor(targetId, sections.totalMaxArmor());
                world.setArmor(targetId, sections.totalArmor());
            }
            newHp = sections.torsoDestroyed() ? 0f : sections.totalStructure();
            if (sectionHit.sectionDestroyed() && !sections.torsoDestroyed()) {
                applySectionLoss(targetId, mech, section);
            }
        } else {
            boolean through = penetration == THROUGH_ARMOR;
            DurabilityModel.resolveInto(postCoverDamage, through ? 0f : penetration, mitigationSoak,
                    through ? 0f : armorBefore, armorRating, hpBefore, durability);
            // The pool is spent by exactly what the model said it absorbed, which is
            // the step that lets massed fire beat a screen rather than wait it out.
            // It may break here; that is the interesting way for a screen to end.
            if (durability.mitigatedDamage() > 0f) {
                screens.absorb(targetId, durability.mitigatedDamage());
            }
            if (hasArmor && durability.armorDamage() > 0f) {
                world.setArmor(targetId, Math.max(0f, armorBefore - durability.armorDamage()));
            }
            armorDamage = durability.armorDamage();
            structureDamage = durability.structureDamage();
            newHp = hpBefore - structureDamage;
        }
        world.setHp(targetId, newHp);
        boolean died = newHp <= 0f;   // wasAlive is guaranteed by the early return above
        // Telemetry runs here, at the one point in the sim that knows both what
        // the hit actually cost after cover and armor resolution and
        // whether it was fatal. Credited HP is clamped to what was
        // left, so overkill from a rocket doesn't read as output the shooter
        // produced. Both writes are safe on a target that is about to be
        // released: TELEMETRY is not in the corpse-remove mask.
        float applied = armorDamage + structureDamage;
        CombatTelemetryService telemetry = roster.telemetry();
        telemetry.recordDamageTaken(targetId, applied);
        if (part != null && mech == null) recordPartConsequence(targetId, part, died, structureDamage, telemetry);
        // Its own quantity, never folded into armor: a screen that showed up as
        // smaller resolved damage would be a statistical rumour rather than a
        // capability the player can read. What the pool absorbed is attributable
        // exactly as the refused fraction used to be.
        float mitigated = mech != null ? sectionHit.mitigatedDamage() : durability.mitigatedDamage();
        telemetry.recordDamageMitigated(targetId, mitigated);
        HitLog hitLog = telemetry.hitLog();
        if (hitLog.recording()) {
            hitLog.record(new HitLog.Hit(hitLog.tick(), targetId, HitLog.label(roster, targetId),
                    attackerId, HitLog.label(roster, attackerId), damage, postCoverDamage, penetration,
                    armorRating, hitArc, hitPart, mitigated, armorDamage, structureDamage,
                    newHp, hasArmor ? world.armor(targetId) : 0f, died));
        }
        // Gated on isRecorded, not on the NO_ATTACKER sentinel alone: a convoy
        // vehicle's turret fires with the vehicle entity as the attacker, and a
        // vehicle carries GROUND_IDENTITY rather than IDENTITY, so the faction
        // read below is fail-loud on one. Nothing is lost by skipping it — this
        // block writes telemetry and nothing else, and an entity with no
        // TELEMETRY row has nowhere to be credited.
        if (attackerId != targetId && roster.identity().faction(targetId) == Faction.DEFENDER) {
            telemetry.recordToLedger(attackerId, applied, died, roster.identity().type(targetId).isTurret());
        }
        if (telemetry.isRecorded(attackerId) && attackerId != targetId) {
            boolean friendly = roster.identity().faction(attackerId)
                    .friendlyTo(roster.identity().faction(targetId));
            telemetry.recordDamageDealt(attackerId, applied, friendly);
            if (died && !friendly) telemetry.recordKill(attackerId);
        }
        if (died) {
            deathSink.accept(targetId);
            equipmentDrops.emitIfApplicable(targetId);
            // Squad leader promotion — if the dead unit was leading a
            // squad, hand the badge to the closest still-alive member.
            // Preserves direction of travel: the new leader stands roughly
            // where the old one fell, so followers don't get yanked
            // sideways when the leader dies mid-maneuver. NO_SQUAD units
            // (turrets, civilians, etc.) skip — no leader to promote.
            if (roster.squad().hasSquad(targetId)) {
                Squad ls = squads.get(roster.squad().squadId(targetId));
                if (ls != null && ls.leaderId == targetId) {
                    ls.leaderId = pickPromotionCandidate(ls, targetId);
                }
            }
            // Publish the death to the mailbox BEFORE the registry release,
            // so a handler that wants the live entity still sees it. Buffered:
            // handlers don't run here — DeathDispatcher.drain() fans them out
            // at the demolition phase. Every post-death reaction (turret + hub
            // demolition, drone crash, mech wreck, dead-body/render) reacts off
            // this event, not a list scan. Snapshot the true death position into
            // the event while the target still has its live capabilities; handlers
            // run later at the fixed drain phase. See DeathDispatcher and
            // ecs-nouns.md.
            // Roll the corpse prone-pose here (the normal-death path is the one that
            // leaves a ground body); the drone-cascade path publishes -1 (no corpse).
            // Publish the id directly — the target is still registered here (release
            // is below), and handlers read the id-keyed corpse columns + the event
            // snapshot, never a live handle.
            deathDispatcher.publish(new DeathEvent(targetId, tx, ty, rng.nextInt(4)));
            // Remove the id from the dense live roster. The entity stays in the
            // world so buffered death handlers can transmute it to its corpse
            // lifecycle and consume the event snapshot.
            roster.releaseFromRegistry(targetId);
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
        // Infantry feeds the drain to the hit marine's own fire team (hit
        // event + cap scaling + death bonus) — cohesion breaks at team
        // granularity, so fire concentrated on one team must not spread its
        // drain across composed siblings. Solo units (turrets, civilians)
        // skip both — their behaviors don't consult MORALE_BROKEN.
        if (wasAlive && moraleImpact > 0f) {
            if (world.hasMechLoadout(targetId)) {
                // Only a SURVIVING mech accrues HP-threshold morale drain. A mech
                // killed by this very hit was already released from the registry
                // (above) — its HEALTH component still reads until the death drain
                // transmutes it to a corpse, but a dead mech's threshold morale is
                // moot, so keep the guard.
                if (!died) applyMechHpThresholdDrain(targetId);
            } else if (roster.squad().hasSquad(targetId)) {
                applyFireTeamMoraleDrain(targetId, moraleImpact, died);
            }
        }
    }

    /**
     * Tallies the part a payload landed on and, for a limb hit that took structure from a man who
     * lived through it, leaves the wound ({@code body-part-cover.md}, Part consequences). A hit
     * the armour absorbed whole never reached the man, so it wounds nothing.
     */
    private void recordPartConsequence(long targetId, BodyPart part, boolean died, float structureDamage,
                                       CombatTelemetryService telemetry) {
        boolean known = roster.identity().has(targetId);
        Faction struck = known ? roster.identity().faction(targetId) : null;
        if (struck != null) telemetry.recordPartHit(struck, part);
        if (died || structureDamage <= 0f) return;
        switch (part.consequence) {
            case LIMB_WOUND -> {
                roster.wounds().wound(targetId, part);
                if (struck != null) telemetry.recordWound(struck, part);
            }
            // A mech's sections answer at zero, not per hit: see applySectionLoss.
            case NONE, CRIPPLE, MOUNT_LOSS, SENSOR_LOSS -> { }
        }
    }

    /**
     * The section a hit on a mech lands on. A modelled round brings the band of the height it
     * arrived at; a payload with none (a blast, a strafe) is struck at a uniform height. A
     * torso-height hit takes the arms mount with the chassis's lateral share; a shoulder-height
     * hit takes the shoulder on the side it came from, read against the torso's facing.
     */
    private MechSection sectionStruck(World world, long targetId, long attackerId,
                                      MechLoadoutComponent mech, MechSections sections, BodyPart part) {
        MechBodyPlan plan = sections.plan();
        BodyPart band = plan.owns(part) ? part : plan.bodyPlan().atHeight(rng.nextFloat());
        boolean fromLeft;
        if (attackerId != targetId && roster.isAliveById(attackerId)) {
            fromLeft = MechSections.fromLeft(mech.torsoFacingDegrees,
                    world.x(attackerId) - world.x(targetId), world.y(attackerId) - world.y(targetId));
        } else {
            fromLeft = rng.nextBoolean();
        }
        return sections.sectionFor(band, fromLeft, rng.nextFloat());
    }

    /**
     * The arc of the torso a hit on a mech arrives on, read from its shooter's position against the upper torso's
     * facing, the frame its arm hits are read in. A hit with no shooter to read meets the front, which under
     * {@link MechSections#TORSO_ARCS} is the chassis rating as it stands.
     */
    private ArmorArcs.Arc torsoArc(World world, long targetId, long attackerId, MechLoadoutComponent mech) {
        if (attackerId == targetId || !roster.isAliveById(attackerId)) return ArmorArcs.Arc.FRONT;
        return ArmorArcs.classify(mech.torsoFacingDegrees,
                world.x(attackerId) - world.x(targetId), world.y(attackerId) - world.y(targetId));
    }

    /**
     * What a section at zero costs a mech that still stands ({@code mechs-nouns.md}):
     * a mount loses its weapon, the head its sensors. Crippled legs need nothing written here:
     * the sections say so, and movement reads it through {@code MechSectionModifier}.
     */
    private void applySectionLoss(long targetId, MechLoadoutComponent mech, MechSection section) {
        if (section.mount() != null) {
            mech.destroyMount(section.mount());
            // The engagement range was the deployed mounts' longest reach. Doctrine and target
            // acquisition read it, so it has to follow the surviving mounts or a mech that lost
            // its long-range rack would keep perching and acquiring at that rack's band.
            roster.world().setAttackRange(targetId, mech.maxWeaponRange());
            // The appearance's mount selectors were authored at spawn; re-author them so the
            // lost weapon's layer drops off the drawn chassis.
            roster.world().refreshMechMountAppearance(targetId, mech);
        } else if (section == MechSection.HEAD) {
            VisionService vision = roster.vision();
            vision.setVisionRange(targetId, vision.visionRange(targetId) * MechSections.SENSOR_LOSS_VISION);
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
     * the {@code u == deadLeaderId} id check. Other dead units cannot
     * appear: prior deaths this frame would have released themselves in
     * their own resolve() calls.
     *
     * @return the new leader's entity id, or {@code 0L} if the squad has no
     *         other survivors.
     */
    private long pickPromotionCandidate(Squad squad, long deadLeaderId) {
        long best = 0L;
        float bestDistSq = Float.MAX_VALUE;
        World world = roster.world();
        int lx = world.cellX(deadLeaderId);
        int ly = world.cellY(deadLeaderId);
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long u = dense[i];
            if (u == deadLeaderId || !roster.squad().hasSquad(u) || roster.squad().squadId(u) != squad.id) continue;
            int dx = world.cellX(u) - lx;
            int dy = world.cellY(u) - ly;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }

    /**
     * Morale drain charged to the hit marine's own fire team (infantry path).
     * The team — not the squad — is the unit that breaks, so a burst walked
     * across one team leaves its siblings composed and still executing the
     * squad's plan.
     *
     * <p>Death drain stacks on top of hit drain and bypasses the cooldown —
     * a kill is a discrete event the model should always reflect. The
     * hit-component only fires when the team's cooldown is clear; otherwise a
     * burst of hits in one tick would each stack their per-hit drain and
     * break an intact team in a single frame. The recovery gate
     * ({@link FireTeamMorale#timeSinceUnderFire} reset) fires on every hit,
     * even when the drain cooldown blocked the drop — otherwise a sustained
     * burst would only reset the timer on the first bullet, letting recovery
     * resume mid-volley.
     *
     * <p>Reads the team's cap off the census
     * {@link SquadMoraleSystem#tick} took this tick. A team spawned since
     * that census has {@code originalSize == 0} and so reads cap 1.0 — the
     * intact default, correct for a team that has not been shot at yet.
     */
    private void applyFireTeamMoraleDrain(long targetId, float moraleImpact, boolean died) {
        Squad sq = squads.get(roster.squad().squadId(targetId));
        if (sq == null) return;
        FireTeamMorale team = sq.fireTeamMorale(roster.squad().fireTeamIndex(targetId));
        float cap = team.cap();
        float drop = 0f;
        if (team.drainCooldown <= 0f) {
            drop += SquadMoraleSystem.hitDrain(cap, moraleImpact, roster.combat().moraleDrainMultiplier(targetId));
            team.drainCooldown = SquadMoraleSystem.MORALE_DRAIN_COOLDOWN;
        }
        if (died) drop += SquadMoraleSystem.MORALE_DROP_ON_DEATH;
        if (drop > 0f) team.morale = Math.max(0f, team.morale - drop);
        team.timeSinceUnderFire = 0f;
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
    private void applyMechHpThresholdDrain(long targetId) {
        World world = roster.world();
        MechLoadoutComponent m = world.mechLoadout(targetId);
        m.timeSinceUnderFire = 0f;
        float maxHp = world.maxHp(targetId);
        if (maxHp <= 0f) return;
        float frac = Math.max(0f, world.hp(targetId)) / maxHp;
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
