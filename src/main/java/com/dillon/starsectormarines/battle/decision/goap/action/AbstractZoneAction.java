package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.ApproachBound;
import com.dillon.starsectormarines.battle.infantry.SmokeTactics;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared base for the squad-push actions
 * ({@link EnterZone}, {@link ClearZone}, {@link HoldZone}, {@link AttackMove},
 * {@link AmbientAdvance}).
 * They take no part in the backward-chaining planner (empty
 * preconditions/effects, flat cost) and share one body of advance behaviour:
 * the contact-scored commit in {@link #advanceIntoZone}, the fire-team bound
 * in {@link #executeBounding}, and the open-ground echelon in
 * {@link #holdsForQuietEchelon}.
 *
 * <p><b>Advancing under contact is not a zone concept, and the destination is
 * a parameter rather than a field.</b> Three of the family push toward a zone
 * and answer {@link #memberInZone} to know they have arrived; {@link AttackMove}
 * pushes toward a bare cell and often begins and ends inside one enormous
 * outdoor zone. Bounding lived inside {@code EnterZone} while it was the only
 * action that did it, which meant the order most obviously about fighting your
 * way somewhere was the one that walked there in a single file.
 *
 * <p>Two of the family push toward a bare cell rather than a zone, and they
 * differ in what authorizes the push: {@link AttackMove} is an order and fails
 * when its {@code ObjectiveAssignment} is withdrawn, while
 * {@link AmbientAdvance} is what a squad does when it holds no workable order
 * at all and so validates none.
 *
 * <p>The zone family adds one rule of its own: <b>a member isn't performing a
 * zone action until it is actually inside the target zone.</b>
 * That rule is the zone-entry precondition shared across those three —
 * {@link #memberInZone}. A member standing outside {@code targetZoneId} must
 * first consolidate <em>into</em> it via {@link #advanceIntoZone} rather than
 * engaging from an adjacent room: without this, a member with a firing
 * solution across a portal sits at the threshold trading fire forever, and a
 * compound capture stays permanently contested while the squad fights the
 * room from the doorway instead of pushing in.
 *
 * <p>The one tactical knob between members of the family is whether the
 * advance may <em>commit</em> to contact. {@link EnterZone} is the approach
 * step: it scores local force, retreat posture, and distance from the advance
 * axis every tick, then either presses with shots of opportunity or fights
 * inside a bounded off-axis leash. {@link ClearZone}/{@link HoldZone} are
 * commitment steps: the squad is taking the room, so they push through contact
 * while firing suppressively instead of freezing at the threshold. That's the
 * {@code haltOnContact} flag, not duplicated movement code.
 */
abstract class AbstractZoneAction implements Action {

    private static final boolean ISOLATED_ADVANCE_THREAT = Boolean.parseBoolean(
            System.getProperty("battle.squad.isolatedAdvanceThreat", "true"));

    /**
     * Minimum sim-seconds since the last squad replan before a contact-halt is
     * allowed to force another. Without this throttle a pinned squad would
     * replan every tick — the planner would re-pick the same approach action
     * (no morale break, no other relevant goal), the marine would halt again,
     * replan would fire again, ad infinitum. 1.0s gives a clean
     * one-replan-per-contact-event under the 2.0s base period. Only consulted
     * on the {@code haltOnContact} path.
     */
    protected static final float CONTACT_HALT_REPLAN_THROTTLE = 1.0f;
    /** Raw threat weight that flips a pressing squad into committed contact. */
    static final float ADVANCE_COMMIT_THRESHOLD = 0.55f;
    /** Lower threshold that releases a committed squad back onto the objective route. */
    static final float ADVANCE_RELEASE_THRESHOLD = 0.30f;
    /** Minimum useful off-axis firing-position radius once the squad commits. */
    static final float ADVANCE_LEASH_MIN = 4f;
    /**
     * Range at which a contact one member is looking at stops the whole
     * advance, whatever the route score made of it.
     *
     * <p>Much tighter than the range at which that contact is worth a screen,
     * and the two must not be confused. Ten cells is a room: near enough that
     * the bearing is known and something should go between the squad and it,
     * and still far enough that pressing on with moving fire is a legitimate
     * choice — a squad that outnumbers one weak contact four to one is
     * <em>meant</em> to press, and the route score says so deliberately.
     * Overriding that turns every passing straggler into a halt.
     *
     * <p>At knife range it stops being a judgement. Walking on past somebody
     * this close is walking a file through their fire, and no odds computation
     * makes that right.
     */
    static final float ONSET_COMMIT_CELLS = 4f;

    /**
     * Whether a knife-range onset stops the advance. <b>Off, because it was
     * measured and it costs ground.</b>
     *
     * <p>Against a control on the same tree, the reinforced-south fixture gave
     * up a capture and a held compound and killed eighteen fewer defenders,
     * and full-strength-west ran fourteen percent longer. The mechanism is not
     * mysterious: squads that stop for whatever is closest arrive later, and in
     * a fixture that ends on a clock, later means fewer objectives taken.
     *
     * <p>Kept rather than deleted because the reasoning that produced it still
     * looks right and the measurement is about Conquest's particular shape — a
     * timed advance across open ground, where the cost of stopping is paid
     * immediately and the benefit is diffuse. It wants a different trigger than
     * range alone (what the contact <em>is</em>, whether it can be walked past
     * safely, whether anybody is already answering it) before it earns being on
     * by default. {@code -Dbattle.squad.contactDrill=true} turns it on for the
     * next attempt to find that trigger, and everything it needs is built.
     *
     * <p>The publication it reads is unaffected and stays on: a squad still
     * knows what one of its members is looking at, screens still answer it, and
     * cooperating squads still group on it. Only the halt is withheld.
     */
    public static final String CONTACT_DRILL_PROPERTY = "battle.squad.contactDrill";

    private static final boolean CONTACT_DRILL_ENABLED = Boolean.parseBoolean(
            System.getProperty(CONTACT_DRILL_PROPERTY, "false"));

    /**
     * Turns off zone-contained objective firing positions, restoring the
     * behaviour to the attack-move-only form it shipped in. On by default;
     * {@code -Dbattle.squad.objectiveFiringInZone=false} is the control run.
     *
     * <p>It exists because the alternative control is a commit-to-commit
     * comparison, and this repository has already charged a squad behaviour
     * with a seven-thousand-tick swing that turned out to be another session's
     * reinforcement work arriving on a merge. A switch measures one change.
     */
    public static final String OBJECTIVE_FIRING_IN_ZONE_PROPERTY =
            "battle.squad.objectiveFiringInZone";

    private static final boolean OBJECTIVE_FIRING_IN_ZONE = Boolean.parseBoolean(
            System.getProperty(OBJECTIVE_FIRING_IN_ZONE_PROPERTY, "true"));

    /**
     * Whether a prosecuting member that can find <em>no</em> firing cell inside
     * its maneuver leash carries on with its assigned advance. On by default,
     * because the noun doc has always said so: "if no legal position exists
     * inside the maneuver leash, the member continues its assigned advance."
     * {@code -Dbattle.squad.prosecutionFallThrough=false} restores the freeze.
     *
     * <p>The freeze was a live defect rather than a theory. Two squad dumps of
     * one battle recorded twelve marines standing on their landing pad for a
     * hundred and eighty consecutive frames — no member moving, no member
     * holding a path, every member settled — under HOLD/ADVANCING/PROSECUTE
     * against one stationary contact thirty cells away that not one of them
     * could engage. Prosecution anchors the search on the squad centroid with
     * a twelve-cell leash, the contact was further off than leash plus weapon
     * reach, the search returned nothing, and "nothing" was read as "no path",
     * whose answer is to plant. It repeated every tick for as long as the
     * contact stayed visible, which under {@code contactHoldIsFresh} is as
     * long as anybody can see it at all.
     *
     * <p>A switch rather than a bare change for the reason the one above it
     * exists: the alternative control is a commit-to-commit comparison, and
     * this repository has already charged a squad behaviour with a swing that
     * belonged to another session's merge.
     */
    public static final String PROSECUTION_FALL_THROUGH_PROPERTY =
            "battle.squad.prosecutionFallThrough";

    /**
     * Read once at class load and settable for evidence. Volatile because a
     * scene's control loop flips it between loops on one thread while the
     * member dispatch reads it from several.
     */
    private static volatile boolean prosecutionFallThrough = Boolean.parseBoolean(
            System.getProperty(PROSECUTION_FALL_THROUGH_PROPERTY, "true"));

    /** Whether a prosecuting member with nowhere to shoot from walks on. */
    public static boolean isProsecutionFallThroughEnabled() {
        return prosecutionFallThrough;
    }

    /**
     * Evidence seam: turns the fall-through on or off for the rest of this JVM.
     *
     * <p>Exists so one scene can play its subject and its frozen control in a
     * single run — loops play sequentially on one thread, and a control that
     * needed a second JVM would be a second command nobody runs. Restore the
     * previous value in a {@code finally}; production never calls this, and
     * {@link #PROSECUTION_FALL_THROUGH_PROPERTY} is what a Conquest control run
     * switches.
     */
    public static void setProsecutionFallThroughForEvidence(boolean value) {
        prosecutionFallThrough = value;
    }

    /** Maximum off-axis firing-position radius at full threat weight. */
    static final float ADVANCE_LEASH_MAX = 12f;

    /**
     * Off-axis firing radius, anchored on the order's own destination cell,
     * that an approaching ({@code haltOnContact}) member may spend closing on
     * a target it can see but cannot yet reach. Matches
     * {@link AttackMove#BASE_OF_FIRE_LEASH} — the same bounded improvement,
     * available here to a member that was never assigned base-of-fire at all,
     * just to bring one order's own destination into range of a target that
     * is off-axis enough to have earned no threat commit.
     *
     * <p><b>Anchored on the destination, and it has to stay that way.</b> A
     * leash anchored on the member's own current position re-anchors every
     * tick: close the gap and the edge of the leash closes with you, which is
     * an unbounded creep toward the enemy — a slow charge that abandons the
     * order and breaks the commander's front — dressed up as a firing-position
     * search. Anchoring on the fixed cell the squad was actually sent to
     * bounds the total excursion to this many cells from that cell, forever,
     * however long the member spends trying. A target far enough off the
     * destination that no cell inside this leash can reach it is left to the
     * ordinary route; that asymmetry (near miss solved, far miss ignored) is
     * the feature, not a gap in it.
     */
    static final float OBJECTIVE_FIRING_LEASH = 8f;


    /** Role-slot prefix for the fire-team partition a bounding advance moves in. */
    static final String FIRE_TEAM = "fireteam:";
    /** Test/fixture aliases for the first two organizational teams. */
    static final String TEAM_A = FIRE_TEAM + "0";
    static final String TEAM_B = FIRE_TEAM + "1";
    /** Cells gained by each maneuvering fire team before the role rotates. */
    static final float BOUNDING_STRIDE = 6f;
    /** Open-ground progress required before the next quiet-advance team releases. */
    static final float ECHELON_RELEASE_DISTANCE = 2f;
    /** Formation authority yields unless this local square radius is fully open. */
    static final int ECHELON_OPEN_CLEARANCE = 2;

    protected final int targetZoneId;

    protected AbstractZoneAction(int targetZoneId) {
        this.targetZoneId = targetZoneId;
    }

    public final int targetZoneId() { return targetZoneId; }

    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /** True iff {@code member}'s logical cell lies inside {@link #targetZoneId}. */
    protected final boolean memberInZone(long member, BattleView sim) {
        return sim.getZoneGraph().zoneIdAt(sim.world().cellX(member), sim.world().cellY(member)) == targetZoneId;
    }

    /**
     * Whether this action may abandon its own destination to go and fight a
     * contact that is merely visible, rather than one astride its route.
     *
     * <p>Separated from the route-threat commit on purpose, because the two
     * are different decisions wearing the same {@code committed} flag.
     * {@link #updateAdvanceThreat} commits a squad to a threat it has to walk
     * through — the destination survives, and the leash is anchored on the
     * route. Contact prosecution commits it to the primary contact wherever
     * that contact happens to be, anchored on the squad's own centroid, and
     * the destination plays no part in it at all. An action whose destination
     * came from outside the AI needs to refuse the second while keeping the
     * first.
     *
     * <p>Defaults to true: an autonomously assigned advance chooses its own
     * fights, which is the whole point of the contact picture.
     */
    protected boolean prosecutesContactOffRoute(Squad squad) {
        return true;
    }

    /**
     * Advance a member that is standing <em>outside</em> the target zone toward
     * {@code (destX, destY)} — an interior cell — taking opportunistic shots at
     * a visible in-range enemy while it moves. Caller invokes this only when
     * {@link #memberInZone} is false and then returns {@link ActionStatus#RUNNING}.
     *
     * <p>Target handling mirrors the in-zone engage paths: a stale-but-alive
     * target that's no longer worth pursuing is dropped and re-picked via
     * {@link TacticalScoring#findBestTarget}, so a member that fixated on the
     * approach doesn't walk past fresh shooters.
     *
     * @param haltOnContact when true (approach semantics), the squad's
     *        threat-scored advance leash may stop the member to prosecute a
     *        route contact; when false (commitment semantics), the member keeps
     *        pushing into the zone while firing.
     */
    protected final void advanceIntoZone(long member, Squad squad, BattleControl sim,
                                         int destX, int destY, boolean haltOnContact) {
        boolean committed = false;
        boolean routeCommitted = false;
        boolean doctrineHold = false;
        boolean prosecuteContact = false;
        float engageLeash = 0f;
        long advanceThreat = 0L;
        int threatAnchorX = -1;
        int threatAnchorY = -1;
        if (haltOnContact) {
            updateAdvanceThreat(squad, sim, destX, destY);
            committed = squad.advanceEngageCommitted;
            // Kept apart from committed, which three different decisions write.
            // Only this one means "the enemy is astride the route", and it is
            // the one whose refusals plant rather than walk on.
            routeCommitted = committed;
            Doctrine doctrine = squad.contactPicture.doctrine();
            boolean advancingPicture = squad.contactPicture.posture() == Posture.ADVANCING;
            if (advancingPicture && doctrine == Doctrine.DISENGAGE) {
                BreakContact.INSTANCE.execute(member, squad, sim);
                return;
            }
            doctrineHold = TacticalScoring.shouldHardHoldAdvance(squad,
                    squad.contactPicture, sim.getSimTickIndex());
            prosecuteContact = prosecutesContactOffRoute(squad)
                    && advancingPicture && doctrine == Doctrine.HOLD
                    && squad.contactPicture.contactInitiative()
                    == ContactInitiative.PROSECUTE
                    && sim.resolveUnit(squad.contactPicture.primaryContactId()) != 0L;
            committed |= doctrineHold;
            committed |= prosecuteContact;
            engageLeash = squad.advanceEngageLeash;
            advanceThreat = squad.advanceThreatId;
            threatAnchorX = squad.advanceThreatAnchorX;
            threatAnchorY = squad.advanceThreatAnchorY;
            if (prosecuteContact) {
                advanceThreat = squad.contactPicture.primaryContactId();
                threatAnchorX = Math.round(squad.centroidX - 0.5f);
                threatAnchorY = Math.round(squad.centroidY - 0.5f);
                engageLeash = ADVANCE_LEASH_MAX;
            }
        }

        long target = sim.targetOf(member);
        if (committed && sim.resolveUnit(advanceThreat) != 0L) {
            target = advanceThreat;
            sim.world().setTargetId(member, target);
        } else if (target == 0L || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }

        boolean inContact = false;
        boolean clearShotOnTarget = false;
        if (target != 0L) {
            float d = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            clearShotOnTarget = sim.getTacticalScoring().hasClearShot(member, target);
            inContact = d <= sim.world().attackRange(member) && clearShotOnTarget;
        }

        long opportune = 0L;
        if (inContact) {
            sim.combat().setFireIntent(member, target,
                    committed ? FireStance.STANCED : FireStance.MOVING,
                    committed);
        } else {
            // Opportunistic return fire while advancing. The pursuit target
            // is out of range/LoS (or absent) — across the open approach
            // that left members marching past enemies they could hit,
            // eating shots without returning any. Fire on the nearest enemy
            // actually in range and LoS, MOVING stance, without halting or
            // touching the pursuit target: the squad still commits to the
            // zone push, the trigger just stops it being a sitting duck.
            // FiringSystem's beginBurst tracks the intent target, so the
            // follow-up burst tracks the enemy we shot, not the pursuit target.
            opportune = sim.getTacticalScoring().closestEnemyInAttackRange(
                    member, sim.combat().reflexTargetId(member),
                    TacticalScoring.OPPORTUNITY_RETARGET_DISTANCE_MARGIN);
            if (opportune != 0L) {
                sim.combat().setFireIntent(member, opportune, FireStance.MOVING, false);
            }
        }

        if (committed && inContact) {
            int[] path = sim.world().path(member);
            // A committed contact suppresses the long objective route, but a
            // successful shot may have authored RepositionToCover's short,
            // strictly-better-cover move. Its per-member cooldown is the
            // ownership marker: advance that staggered micro-move while it is
            // live; otherwise plant on the firing line as before.
            boolean activeCoverReposition = sim.world().repositionCooldown(member) > 0f
                    && sim.world().pathIdx(member) < Paths.cellCount(path);
            if (activeCoverReposition) {
                sim.advanceMovement(member);
            } else if (!Paths.isEmpty(path)) {
                sim.clearPath(member);
            }
            if (squad.timeSinceReplan >= CONTACT_HALT_REPLAN_THROTTLE) {
                squad.timeSinceReplan = Planner.REPLAN_PERIOD;
            }
            return;
        }

        if (committed && target != 0L && threatAnchorX >= 0 && threatAnchorY >= 0) {
            var selection = sim.getTacticalScoring().selectSquadFiringPositionWithin(
                    member, target, squad, sim.getSimTickIndex(), threatAnchorX,
                    threatAnchorY, engageLeash, TacticalScoring.ANY_ZONE);
            if (selection.deferred()) {
                // A budget refusal says nothing about whether this commitment
                // has a firing position. Pause locomotion without discarding
                // the path or resuming the objective on an invented negative.
                // Opportunity fire was already authored above. A validated
                // incumbent is returned as a position, not as deferred.
                return;
            }
            int[] firingPos = selection.cell();
            // The three refusals are different facts and get different
            // answers. No path at all means the commitment cannot be
            // prosecuted by walking, so the member holds and fights from
            // where it stands — which is what a committed member does on its
            // firing line anyway. Marching it to the objective instead sends
            // a squad that has decided to fight straight past the enemy, and
            // the matrix charged twelve extra squads for that. A path that is
            // merely not worth the walk leaves a member that can still move,
            // so it carries on toward the objective.
            //
            // No cell at all is the third, and which answer it gets depends on
            // which commitment asked. Under the route-threat commit the enemy
            // is on the ground the squad has to cross, and planting is what
            // the noun doc prescribes whether or not a better angle exists.
            // Under prosecution the destination played no part in choosing the
            // fight, the leash is drawn round the squad's own centre, and the
            // rule has always been that a member with no legal position inside
            // it continues its assigned advance — so it does, keeping the
            // target for opportunity fire on the way.
            boolean prosecutionOnly = prosecuteContact && !routeCommitted && !doctrineHold;
            switch (advanceToReachableFiringPosition(member, sim, firingPos)) {
                case MOVED -> { return; }
                case NO_POSITION -> {
                    if (!prosecutionFallThrough || !prosecutionOnly) {
                        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
                        return;
                    }
                    // fall through to the objective
                }
                case UNREACHABLE -> {
                    if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
                    return;
                }
                case NOT_WORTH_THE_WALK -> { /* fall through to the objective */ }
            }
        }

        // A flank/rear or adverse-odds picture can order a contact line even
        // when no believed enemy lies close enough to the literal route
        // segment to provide a firing-position anchor. Plant instead of
        // silently resuming the objective path; opportunity fire above still
        // answers any target that becomes legal.
        if (doctrineHold) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return;
        }

        // A marine may improve its firing position within the footprint of
        // the order it was given, and no further.
        //
        // This is the approach-only counterpart to the committed firing-line
        // branch above: committed is false here, which is exactly the squad
        // 148 case that motivated it — ten marines marching an objective hop
        // while the sole observed contact sat 28-33 cells out, off the
        // advance axis, and correctly failed to commit the squad (see
        // ADVANCE_THREAT_LOOKAHEAD) while nine of its ten rifles also
        // correctly had a clear shot on something they could not reach.
        // Without this, "not committed" meant "do nothing but hope the
        // opportunistic shot above found a closer target" — which it usually
        // doesn't, since the whole reason this member is here is that the
        // only thing it can see is the one thing it cannot hit. So: not
        // fighting a route contact worth committing to, not already firing on
        // anything (opportune == 0L), but looking straight at something out
        // of range with nothing in the way. Close enough of that gap to
        // shoot, anchored within OBJECTIVE_FIRING_LEASH cells of the
        // destination this member was actually sent to — see that constant
        // for why the destination and not the member is the anchor.
        //
        // The footprint is the leash AND the room, when the order names one.
        // This was restricted to an order with no target zone — the attack
        // move, which is what it was built for — because the search knows
        // nothing about rooms and would happily improve a member's angle by
        // walking it into the room next door. That restriction turned out to
        // exclude most of the population it was written for: instrumented
        // over a Conquest matrix, 466,940 member-ticks reached this gate and
        // 271,732 of them (58%) were refused for naming a zone, of which
        // 39,342 had a legal firing cell inside their own leash. Containment
        // is the answer rather than exclusion, and it belongs in the search,
        // where every candidate can be tested — see the requiredZoneId
        // overload of findFiringPositionWithin. targetZoneId carries both
        // cases already: negative for an order with no room, and then the
        // search is unconstrained exactly as before.
        if (OBJECTIVE_FIRING_IN_ZONE
                && haltOnContact && !committed && !inContact
                && target != 0L && clearShotOnTarget && opportune == 0L) {
            int[] firingPos = sim.getTacticalScoring().findSquadFiringPositionWithin(
                    member, target, squad, sim.getSimTickIndex(), destX, destY, OBJECTIVE_FIRING_LEASH, targetZoneId);
            // Uncommitted: this member was never told to fight here, so any
            // refusal simply resumes the order it does have. This deliberately
            // includes a deferred shared-pool refresh: an optional improvement
            // must not stall travel toward the actual objective.
            if (advanceToReachableFiringPosition(member, sim, firingPos)
                    == FiringApproach.MOVED) return;
        } else if (haltOnContact && !committed && !inContact && targetZoneId < 0
                && target != 0L && clearShotOnTarget && opportune == 0L) {
            // Control path for -Dbattle.squad.objectiveFiringInZone=false: the
            // behaviour exactly as it shipped before containment, so a matrix
            // run can be compared against one on the same tree rather than
            // against an older commit carrying every other difference with it.
            int[] firingPos = sim.getTacticalScoring().findSquadFiringPositionWithin(
                    member, target, squad, sim.getSimTickIndex(), destX, destY, OBJECTIVE_FIRING_LEASH);
            // As above, a deferred optional improvement may resume the order.
            if (advanceToReachableFiringPosition(member, sim, firingPos)
                    == FiringApproach.MOVED) return;
        }

        if (sim.movement().mayRepath(member)) {
            // Every member ordered into this zone walks to the same interior
            // cell, so this is the dense same-destination case a shared
            // reverse field exists for: one field per zone serves the whole
            // push instead of one A* per member per repath. The firing-position
            // route above stays on A* — that destination is picked per member.
            int memberX = sim.world().cellX(member);
            int memberY = sim.world().cellY(member);
            // This long route is also the only one that consults the squad's
            // memory of its own dead. A follow-up walking the ground the last
            // squad was destroyed on is the objective route choosing badly, and
            // is worth a detour; a firing position five cells away is not, and
            // biasing that would move somebody off cover for a reason having
            // nothing to do with the shot in front of them.
            RouteCostField losses = sim.getRouteCostField(squad.faction);
            SquadPlan plan = squad.currentPlan;
            SquadPlan.Step step = plan == null ? null : plan.currentStep();
            // Another member can advance the shared plan during execution.
            // Only a matching prepared step may serve this action's route.
            Object routeToken = step != null && step.action == this ? step : null;
            sim.setPath(member,
                    SharedGoalPolicy.usesSquadRouteCorridors(sim.liveUnitCount())
                            ? sim.findSquadPathToGoal(squad.id, squad.routingEpoch,
                                    routeToken, memberX, memberY, destX, destY, losses)
                            : SharedGoalPolicy.usesSharedGoalFields(sim.liveUnitCount())
                            ? sim.findSharedPathToGoal(memberX, memberY,
                                    destX, destY, losses)
                            : GridPathfinder.findPathWithCost(sim.getGrid(),
                                    memberX, memberY, destX, destY,
                                    GridPathfinder.USE_CARDINAL_NAVIGATION,
                                    sim.getOccupancyMap(), losses));
        }
        sim.advanceMovement(member);
    }

    /**
     * Move toward {@code firingPos}, refusing a position the member cannot
     * actually walk to, and report whether a move was authored. A caller that
     * gets {@code false} must fall through to whatever it would have done with
     * no firing position at all.
     *
     * <p><b>The picker is blind to reachability and always has been.</b>
     * {@code TacticalScoring.findFiringPositionWithin} scores walkability,
     * leash distance, range and line of fire; nothing in it asks whether a
     * path exists. {@code TacticalScoring.findReachableFiringPosition} is the
     * variant that does, and it says so in its own documentation. So a cell
     * with a clear shot from the far side of a sealed wall is a perfectly
     * ordinary answer, and the caller that took it set an empty path, moved
     * nobody, and returned — every tick, for as long as it stayed committed.
     *
     * <p>Worse than standing still: {@code setPath} stamps the repath throttle
     * only on a non-empty assignment, so an empty one never throttles and the
     * next tick runs the same search again. An A* toward an unreachable cell
     * exhausts the whole reachable component before failing, so the freeze
     * bought a full-component search per member per tick.
     *
     * <p>The pathfind here is the one the caller was making anyway, so
     * refusing costs nothing it was not already paying.
     */
    protected enum FiringApproach {
        /** A move was authored toward the position. */
        MOVED,
        /**
         * The search found no cell at all: nowhere inside the leash has both
         * the reach and the line of fire. Distinct from {@link #UNREACHABLE},
         * which is a real cell the member cannot walk to, and the two were
         * folded together for a while — with the result that a squad
         * prosecuting a contact further off than leash plus weapon reach
         * planted itself for as long as it could see the contact, every tick,
         * having decided to fight something it had no way of reaching.
         */
        NO_POSITION,
        /**
         * No path exists. There is nothing to walk toward, so the commitment
         * cannot be prosecuted by moving and the member should hold where it
         * is rather than resume the objective.
         */
        UNREACHABLE,
        /**
         * A path exists but costs far more than the straight line it stands
         * in for. The member can move perfectly well — it just should not
         * spend the march on this — so carrying on toward the objective is
         * the better use of the same legs.
         */
        NOT_WORTH_THE_WALK
    }

    protected static FiringApproach advanceToReachableFiringPosition(long member,
                                                                     BattleControl sim,
                                                                     int[] firingPos) {
        if (firingPos == null) return FiringApproach.NO_POSITION;
        if (!sim.movement().mayRepath(member)) {
            // Throttled: the path in hand was checked when it was set, so
            // walking it on is right. Nothing in hand means nothing to walk.
            if (Paths.isEmpty(sim.world().path(member))) return FiringApproach.UNREACHABLE;
            sim.advanceMovement(member);
            return FiringApproach.MOVED;
        }
        int memberX = sim.world().cellX(member);
        int memberY = sim.world().cellY(member);
        int[] path = GridPathfinder.findPath(sim.getGrid(), memberX, memberY,
                firingPos[0], firingPos[1], sim.getOccupancyMap());
        if (Paths.isEmpty(path)) {
            sim.getTacticalScoring().forgetFiringPosition(member);
            return FiringApproach.UNREACHABLE;
        }
        if (!worthWalkingTo(memberX, memberY, firingPos, path)) {
            // A geometrically usable cell is not an accepted execution choice
            // when its approach fails the caller's travel bound.
            sim.getTacticalScoring().forgetFiringPosition(member);
            return FiringApproach.NOT_WORTH_THE_WALK;
        }
        sim.setPath(member, path);
        sim.advanceMovement(member);
        return FiringApproach.MOVED;
    }

    /**
     * Whether {@code path} is a walk worth taking to reach {@code firingPos} —
     * it exists at all, and it is not a detour out of proportion to the
     * straight line it is standing in for. Package-visible so the boundary
     * stays testable without standing up an action.
     */
    static boolean worthWalkingTo(int fromX, int fromY, int[] firingPos, int[] path) {
        return ApproachBound.worthWalkingTo(fromX, fromY,
                firingPos[0], firingPos[1], path, true);
    }

    /**
     * Once-per-tick route decision, isolated from unrelated bounding/plan locks.
     * Contenders still await this tick's complete decision: this changes neither
     * cadence nor hysteresis and does not duplicate scoring or use stale results.
     * The tally reads immutable beliefs and a spatial snapshot, not state owned
     * by squad.lock. Contact-onset publication has the same dedicated ownership.
     */
    protected static void updateAdvanceThreat(Squad squad, BattleControl sim, int destX, int destY) {
        updateAdvanceThreat(squad, sim, destX, destY, ISOLATED_ADVANCE_THREAT);
    }

    /** Same production path with explicit monitor selection for focused controls. */
    static void updateAdvanceThreat(Squad squad, BattleControl sim, int destX, int destY,
                                    boolean isolated) {
        int tick = sim.getSimTickIndex();
        if (squad.advanceThreatTick == tick) return;
        synchronized (isolated ? squad.advanceThreatLock : squad.lock) {
            if (squad.advanceThreatTick == tick) return;
            computeAdvanceThreat(squad, sim, destX, destY, tick);
            // Volatile publication-last: the lock-free fast path must never
            // observe this tick while any of its shared decision is unfinished.
            squad.advanceThreatTick = tick;
        }
    }

    private static void computeAdvanceThreat(Squad squad, BattleControl sim,
                                            int destX, int destY, int tick) {
        TacticalScoring.AdvanceThreat threat = sim.getTacticalScoring()
                .assessAdvanceThreat(squad, destX, destY, tick);
        squad.advanceEngageWeight = threat.weight();
        squad.advanceEngageCommitted = shouldCommitAdvance(
                squad.advanceEngageCommitted, threat.weight());
        squad.advanceEngageLeash = squad.advanceEngageCommitted
                ? Math.max(ADVANCE_LEASH_MIN, ADVANCE_LEASH_MAX * threat.weight())
                : 0f;
        squad.advanceThreatId = threat.primaryThreatId();
        squad.advanceThreatFoes = threat.foes();
        squad.advanceThreatFriends = threat.friends();
        squad.advanceThreatAnchorX = threat.axisAnchorX();
        squad.advanceThreatAnchorY = threat.axisAnchorY();
        squad.advanceThreatRetreating = threat.primaryRetreating();
        applyContactOnset(squad, sim, tick);
    }

    /**
     * Folds a member's contact onset into the squad's advance decision.
     *
     * <p><b>An advance does not walk past somebody one of its people is looking
     * at.</b> The route score is an aggregate over believed contacts and is
     * deliberately unhurried, which is right for deciding whether a defended
     * line is worth committing to and wrong for the marine who has just come
     * round a corner into a rifle. A single hostile inside ten cells may never
     * move that score at all, and the squad walks through it in file — which is
     * exactly the behaviour the attack move was built to stop, arriving one
     * scale further down than it was fixed.
     *
     * <p>So an onset inside {@link #ONSET_COMMIT_CELLS} commits the squad
     * outright rather than adding weight to be weighed, and supplies the anchor
     * with it, because a contact that close is the thing to take firing
     * positions against whatever the aggregate liked better.
     *
     * <p><b>The range is the release, and it has to be.</b> Forcing the commit
     * flag every tick while a contact stands there defeats the hysteresis
     * underneath it — the squad can never let go, so an enemy who breaks off
     * and withdraws still pins it, which is a latch with no exit rather than a
     * contact drill. Because the force applies only within knife range, an
     * enemy who dies, breaks line of sight, or simply backs away stops being an
     * onset and the ordinary release resumes from the score.
     *
     * <p>A singling-out at range is deliberately <em>not</em> enough to commit.
     * Stopping an advance because somebody far off has taken an interest is how
     * a squad is pinned by one rifle, and the answer to it is a screen and to
     * keep moving — which the equipment layer already gives.
     */
    private static void applyContactOnset(Squad squad, BattleControl sim, int tick) {
        if (!CONTACT_DRILL_ENABLED || !onsetForcesCommit(squad)) return;
        long contact = sim.resolveUnit(squad.onsetContactId);
        if (contact == 0L) return;
        squad.advanceEngageCommitted = true;
        squad.advanceEngageLeash = Math.max(squad.advanceEngageLeash,
                ADVANCE_LEASH_MIN);
        squad.advanceThreatId = contact;
        squad.advanceThreatAnchorX = sim.world().cellX(contact);
        squad.advanceThreatAnchorY = sim.world().cellY(contact);
    }

    /**
     * Whether this squad's published onset is the kind that stops an advance,
     * independent of whether the drill is switched on. Separated so the range
     * distinction stays testable while the behaviour it gates is off: what
     * could silently drift is the boundary, not the switch.
     */
    static boolean onsetForcesCommit(Squad squad) {
        return squad.onsetAtCloseQuarters
                && squad.onsetTick >= 0
                && squad.onsetDistance <= ONSET_COMMIT_CELLS;
    }

    static boolean shouldCommitAdvance(boolean wasCommitted, float weight) {
        return wasCommitted ? weight >= ADVANCE_RELEASE_THRESHOLD
                : weight >= ADVANCE_COMMIT_THRESHOLD;
    }

    /**
     * Representative interior cell of {@code zone} — the middle entry in its
     * flat cell-index array. {@code cellIndices} is in detection order (roughly
     * scan-line), so the middle usually lands deep in the zone rather than at a
     * portal edge. Returns {@code null} for a missing or empty zone.
     */
    protected static int[] interiorCell(NavigationZone zone, NavigationGrid grid) {
        if (zone == null) return null;
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return null;
        int pick = cells[cells.length / 2];
        return new int[]{ pick % grid.getWidth(), pick / grid.getWidth() };
    }

    /** {@link #interiorCell} resolved from a zone id against the live graph/grid. */
    protected static int[] interiorCellOf(int zoneId, BattleView sim) {
        return interiorCell(sim.getZoneGraph().zoneById(zoneId), sim.getGrid());
    }
    protected final boolean holdsForQuietEchelon(long member, Squad squad,
                                                BattleControl sim,
                                                int destX, int destY) {
        if (squad.isMechSquad() || squad.contactPicture.hasContacts()) return false;
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step == null || step.action != this) return false;
        List<List<Long>> teams = liveTeams(step, sim);
        int teamIndex = teamIndexContaining(teams, member);
        if (teamIndex <= 0 || teams.size() < 2) return false;
        List<Long> currentTeam = teams.get(teamIndex);
        for (long teammate : currentTeam) {
            if (sim.movement().has(teammate) && !sim.movement().settled(teammate)) {
                return false; // once released, the whole team completes its movement
            }
            if (!locallyOpenForEchelon(teammate, sim)) return false;
        }

        float axisX = destX + 0.5f - squad.centroidX;
        float axisY = destY + 0.5f - squad.centroidY;
        float axisLength = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (axisLength <= ECHELON_RELEASE_DISTANCE) return false;
        axisX /= axisLength;
        axisY /= axisLength;
        float predecessor = teamProjection(teams.get(teamIndex - 1), axisX, axisY, sim);
        float current = teamProjection(currentTeam, axisX, axisY, sim);
        return predecessor - current < ECHELON_RELEASE_DISTANCE;
    }

    private static float teamProjection(List<Long> team, float axisX,
                                        float axisY, BattleView sim) {
        float projection = 0f;
        for (long member : team) {
            projection += sim.world().x(member) * axisX
                    + sim.world().y(member) * axisY;
        }
        return projection / team.size();
    }

    private static boolean locallyOpenForEchelon(long member, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int cx = sim.world().cellX(member);
        int cy = sim.world().cellY(member);
        if (grid.isDoorway(cx, cy)) return false;
        for (int y = cy - ECHELON_OPEN_CLEARANCE;
             y <= cy + ECHELON_OPEN_CLEARANCE; y++) {
            for (int x = cx - ECHELON_OPEN_CLEARANCE;
                 x <= cx + ECHELON_OPEN_CLEARANCE; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)
                        || grid.isDoorway(x, y)) return false;
            }
        }
        return true;
    }
    protected final boolean executeBounding(long member, Squad squad,
                                           BattleControl sim,
                                           int destX, int destY) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step == null || step.action != this) return false;
        String memberTeam = step.slotOf(member);
        if (memberTeam == null || !memberTeam.startsWith(FIRE_TEAM)) return false;

        // Bound only inside the threat's beaten zone. Commitment reaches much
        // further than fire does — the advance-threat score looks tens of cells
        // down the route — so a squad can be committed to a contact that cannot
        // touch it, and bounding that stretch buys nothing for the price of
        // moving half the squad at a time. The gate is a correctness argument
        // rather than a measured win: it changed neither canonical fixture by a
        // tick, which says the bounds these battles actually run are already
        // inside the beaten zone.
        if (!sim.getTacticalScoring().threatReaches(squad.advanceThreatId,
                squad.centroidX, squad.centroidY, BOUNDING_STRIDE)) {
            clearBounding(squad);
            return false;
        }

        if (SmokeTactics.holdForAdvanceSmoke(squad, squad.advanceThreatId,
                destX, destY, sim)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return true;
        }

        List<List<Long>> teams = liveTeams(step, sim);
        if (teams.size() < 2) {
            clearBounding(squad);
            return false;
        }

        BoundingState state;
        synchronized (squad.lock) {
            long threat = squad.advanceThreatId;
            if (squad.boundingActive && !matchesCurrentAdvance(squad, threat, destX, destY)) {
                squad.clearBoundingOverwatch();
            }

            if (squad.boundingActive && allBoundersArrived(squad, sim)) {
                if (!beginPhase(squad, sim, teams, squad.boundingPhase + 1, threat, destX, destY)) {
                    squad.clearBoundingOverwatch();
                    squad.boundingAttemptTick = sim.getSimTickIndex();
                    return false;
                }
            }

            if (!squad.boundingActive) {
                if (squad.boundingAttemptTick == sim.getSimTickIndex()) return false;
                if (!beginPhase(squad, sim, teams, 0, threat, destX, destY)) return false;
            }
            state = new BoundingState(squad.boundingPhase, squad.boundingThreatId,
                    squad.boundingMemberIds, squad.boundingTargetXs, squad.boundingTargetYs);
        }

        int memberTeamIndex = teamIndexContaining(teams, member);
        int maneuverTeamIndex = Math.floorMod(state.phase + 1, teams.size());
        if (memberTeamIndex != maneuverTeamIndex) {
            holdOverwatch(member, state.threat, sim);
            return true;
        }
        return moveBounder(member, state, sim);
    }

    private boolean beginPhase(Squad squad, BattleControl sim,
                               List<List<Long>> teams, int phase, long threat,
                               int destX, int destY) {
        squad.boundingAttemptTick = sim.getSimTickIndex();
        int maneuverTeam = Math.floorMod(phase + 1, teams.size());
        List<Long> bounders = teams.get(maneuverTeam);
        List<Long> suppressors = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            if (i != maneuverTeam) suppressors.addAll(teams.get(i));
        }
        int[] stride = nextStrideCell(squad, phase > 0, destX, destY);
        if (stride == null) return false;
        boolean smokeScreensStride = sim.resolveUnit(threat) != 0L
                && sim.getGrid().hasTransientOpacityOnLine(sim.world().cellX(threat),
                sim.world().cellY(threat), stride[0], stride[1]);
        if (!smokeScreensStride && !hasFiringMember(suppressors, threat, sim)) return false;
        List<TacticalScoring.BoundingPosition> positions = sim.getTacticalScoring()
                .findBoundingPositions(bounders, threat, stride[0], stride[1], destX, destY);
        if (positions.size() != bounders.size()) return false;

        long[] memberIds = new long[positions.size()];
        int[] xs = new int[positions.size()];
        int[] ys = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            TacticalScoring.BoundingPosition position = positions.get(i);
            memberIds[i] = position.memberId();
            xs[i] = position.x();
            ys[i] = position.y();
        }

        squad.boundingActive = true;
        squad.boundingPhase = phase;
        squad.boundingTargetZoneId = targetZoneId;
        squad.boundingDestX = destX;
        squad.boundingDestY = destY;
        squad.boundingThreatId = threat;
        squad.boundingStrideX = stride[0];
        squad.boundingStrideY = stride[1];
        squad.boundingMemberIds = memberIds;
        squad.boundingTargetXs = xs;
        squad.boundingTargetYs = ys;
        return true;
    }

    private int[] nextStrideCell(Squad squad, boolean fromPreviousStride,
                                 int destX, int destY) {
        float startX = fromPreviousStride ? squad.boundingStrideX + 0.5f : squad.centroidX;
        float startY = fromPreviousStride ? squad.boundingStrideY + 0.5f : squad.centroidY;
        float dx = destX + 0.5f - startX;
        float dy = destY + 0.5f - startY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance < TacticalScoring.BOUNDING_MIN_FORWARD_PROGRESS) return null;
        float stride = Math.min(BOUNDING_STRIDE, distance);
        int x = (int) Math.floor(startX + dx / distance * stride);
        int y = (int) Math.floor(startY + dy / distance * stride);
        return new int[]{x, y};
    }

    private static boolean hasFiringMember(List<Long> members, long threat, BattleControl sim) {
        for (long member : members) {
            if (canFireAt(member, threat, sim)) return true;
        }
        return false;
    }

    private static boolean canFireAt(long member, long threat, BattleView sim) {
        if (sim.resolveUnit(member) == 0L || sim.resolveUnit(threat) == 0L) return false;
        float distance = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                sim.world().x(threat), sim.world().y(threat));
        return distance <= sim.world().attackRange(member)
                && sim.getTacticalScoring().hasClearShot(member, threat);
    }

    private static void holdOverwatch(long member, long threat, BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        if (canFireAt(member, threat, sim)) {
            sim.world().setTargetId(member, threat);
            sim.combat().setFireIntent(member, threat, FireStance.STANCED, false);
        }
    }

    private static boolean moveBounder(long member, BoundingState state, BattleControl sim) {
        int index = boundingTargetIndex(state.memberIds, member);
        if (index < 0) return false;
        int x = state.targetXs[index];
        int y = state.targetYs[index];
        if (sim.movement().atCell(member, x, y)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return true;
        }
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    x, y, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return true;
    }

    private static boolean allBoundersArrived(Squad squad, BattleView sim) {
        boolean anyLive = false;
        for (int i = 0; i < squad.boundingMemberIds.length; i++) {
            long member = squad.boundingMemberIds[i];
            if (sim.resolveUnit(member) == 0L) continue;
            anyLive = true;
            if (!sim.movement().atCell(member,
                    squad.boundingTargetXs[i], squad.boundingTargetYs[i])) return false;
        }
        return anyLive;
    }

    private static int boundingTargetIndex(long[] memberIds, long member) {
        for (int i = 0; i < memberIds.length; i++) {
            if (memberIds[i] == member) return i;
        }
        return -1;
    }

    private boolean matchesCurrentAdvance(Squad squad, long threat,
                                          int destX, int destY) {
        return squad.boundingTargetZoneId == targetZoneId
                && squad.boundingDestX == destX
                && squad.boundingDestY == destY
                && squad.boundingThreatId == threat;
    }

    private static List<Long> liveMembers(List<Long> assigned, BattleView sim) {
        if (assigned == null || assigned.isEmpty()) return List.of();
        List<Long> live = new ArrayList<>(assigned.size());
        for (long member : assigned) {
            if (sim.resolveUnit(member) != 0L) live.add(member);
        }
        return live;
    }

    private static List<List<Long>> liveTeams(SquadPlan.Step step, BattleView sim) {
        List<List<Long>> teams = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : step.assignments.entrySet()) {
            if (!entry.getKey().startsWith(FIRE_TEAM)) continue;
            List<Long> live = liveMembers(entry.getValue(), sim);
            if (!live.isEmpty()) teams.add(live);
        }
        return teams;
    }

    private static int teamIndexContaining(List<List<Long>> teams, long member) {
        for (int i = 0; i < teams.size(); i++) {
            for (long candidate : teams.get(i)) if (candidate == member) return i;
        }
        return -1;
    }

    protected static void clearBounding(Squad squad) {
        if (!squad.boundingActive && squad.boundingThreatId == 0L) return;
        squad.clearBoundingOverwatch();
    }

    private record BoundingState(int phase, long threat,
                                 long[] memberIds, int[] targetXs, int[] targetYs) {}
}
