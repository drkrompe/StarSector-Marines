package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.InfantryCohesion;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SquadRejoin;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A squad that stepped off short-handed, and the rest of it landing behind:
 * <b>does a late arrival cross to its squad without picking a fight of its
 * own?</b>
 *
 * <p>A campaign squad assembles across lifts and {@code SquadFormUpSystem}
 * holds it at its landing zone while it does — but that gate times out, and a
 * shuttle shot down on its second run, a squad split across two zones, or a
 * replacement wave all leave the same thing behind: a marine who joins a squad
 * that is already most of a map away. Every unit test around this pins one
 * link — which arrivals are marked, what the reflex does with one tick, which
 * rules skip a rejoining member — and none of them can show the thing the state
 * is actually for: that the crossing is half a minute during which a lone
 * marine is walking past other people's problems.
 *
 * <h2>The map is the length of one crossing</h2>
 * <p>Open ground, 96 by 32. Four marines land at the western landing zone under
 * a commander's {@code ATTACK_MOVE} to (48, 16) and, with their form-up already
 * timed out, step off at once. Four more land at that same landing zone at tick
 * {@value #LIFT_TICK}, by which point the squad is well past
 * {@link InfantryCohesion#COHESION_RADIUS} — which is the whole situation, and
 * the only one this scene is about.
 *
 * <p><b>The second lift is put down through the production rule.</b> The scene
 * spawns the marines and calls {@code SquadRejoin.markIfLateArrival} with the
 * arguments {@code InfantryPayload.tryDeploy} passes it, so what decides
 * whether these four are late is the shipped rule rather than the scene's
 * opinion. What is elided is the shuttle: flying one would add a whole aircraft
 * state machine to a recording about six people walking.
 *
 * <p><b>The far defender exists so the battle keeps ticking.</b> A side that is
 * absent ends the simulation immediately. It is immobile and a whole map away,
 * past marine vision, so it is a presence rather than an attractor.
 *
 * <h2>The picket arrives with the crossing, not before it</h2>
 * <p>The contested loops put two armed defenders at
 * ({@value #PICKET_X}, {@value #PICKET_Y}) — beside the route the late arrivals
 * walk, eight cells off it, which is inside the carbine's fourteen so both
 * sides can shoot. <b>They are spawned at the lift rather than at the start,
 * and that is not a convenience</b>: the main body walks the same axis on its
 * way east and would pass within carbine range of that cell, killing the picket
 * before the second lift is even in the air. The loop would then record a
 * crossing that was never contested — the mistake {@code AirfieldSortieScene}
 * made with unarmed ambushers, arriving from the other direction. By the lift
 * the squad is twenty cells further east and cannot reach them.
 *
 * <h2>Four loops, and two of them carry no verdict</h2>
 * <p>{@code rejoin} is the uncontested crossing: were they marked, did they
 * close, did the state retire, and did any of them fire on the way (there is
 * nothing in this loop to shoot at, so the answer must be none).
 * {@code contested} adds the picket. {@code control} and
 * {@code contested-control} are those same two worlds with
 * {@code battle.infantry.rejoin=false}, and they assert nothing about the
 * squad's behaviour — they exist so the subject's numbers can be read against
 * something.
 *
 * <p><b>The uncontested control very nearly ties, and that is a finding rather
 * than a disappointment.</b> Four marines dropped thirty-one cells behind their
 * squad get back to it at tick 836 without the state and 799 with it: the
 * cohesion pull, which has been there all along, does most of this work on an
 * empty map. If that were the whole measurement the state would not be worth
 * shipping.
 *
 * <p><b>What it is worth shipping for is the contested pair.</b> Put two armed
 * defenders eight cells off the crossing and the control <em>stops</em>: 420 of
 * its 1,528 crossing ticks — fourteen seconds — are spent standing still
 * trading fire with a picket it was never sent to fight, and it reaches its
 * squad at tick 906. The subject stands still on <b>none</b> of its 849, puts
 * 114 rounds down range while walking, and is back inside cohesion at tick 755,
 * a hundred and fifty ticks earlier. Neither loop strays more than a couple of
 * cells off the axis, so the fault was never that a late arrival walks
 * <em>toward</em> the enemy; it is that it halts, on the spot, for as long as
 * the enemy lives. That is what {@code kept-crossing} measures and what no
 * lateral distance could have seen.
 */
public final class LateArrivalScene implements BehaviorScene {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 32;

    /** Fifty seconds: long enough to advance, land the rest, and walk it back together. */
    private static final int TICKS = 1500;
    private static final int LAST_TICK = TICKS - 1;
    private static final int FRAME_EVERY_TICKS = 20;

    /** The tick the second lift touches down. The squad is on its objective by then. */
    static final int LIFT_TICK = 500;

    private static final int FIRST_LIFT = 4;
    private static final int SECOND_LIFT = 4;
    private static final int LZ_X = 8;
    private static final int LZ_Y = 16;

    /** The commander's standing order — reachable inside the recording, so the squad settles. */
    private static final int MISSION_X = 48;
    private static final int MISSION_Y = 16;

    /**
     * One named weapon to every seat rather than a rolled kit. The rolled kit
     * hands out weapons whose reach differs by eighteen cells, and every
     * distance in this scene — who can return fire at the picket, who could
     * have reached it — is measured in weapon range.
     */
    private static final String CARBINE = "weapon.smg";

    /**
     * The picket beside the crossing: eight cells off the axis, which is inside
     * the carbine's fourteen so both sides can shoot, and far enough west that
     * the main body — twenty cells further east by the time it is placed —
     * cannot reach it.
     */
    static final int PICKET_SIZE = 2;
    static final int PICKET_X = 20;
    static final int PICKET_Y = 8;

    /**
     * How far off the landing-zone-to-squad axis a rejoining marine may stray
     * before it counts as having gone looking for the picket. A marine walking
     * the cohesion pull holds the row he landed on to within a cell or two; one
     * that went to take a firing position on the picket would have to cross
     * most of the eight cells between.
     */
    private static final float LATERAL_BAR = 4f;

    /** Ground covered in a tick below which a marine counts as standing still. */
    private static final float HALT_EPSILON = 0.01f;

    /**
     * Share of a crossing a rejoining marine may spend stationary. Not zero:
     * separation and collision hold somebody up for a tick here and there.
     */
    private static final float HALT_ALLOWANCE = 0.15f;

    private static final String MARINES = "marines";
    private static final String CAMPAIGN_SQUAD_ID = "cs-late-arrival";

    @Override public String id() { return "late-arrival"; }

    @Override
    public String label() {
        return "Late arrival: does a marine who lands after the squad stepped off"
                + " cross to it without picking a fight of its own?";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(
                crossing(frames, "rejoin", false),
                crossing(frames, "contested", true),
                control(frames, "control", false),
                control(frames, "contested-control", true));
    }

    /**
     * The world both kinds of loop share: a campaign squad whose form-up has
     * already run out, holding a commander's attack move east.
     */
    private static SceneWorld build() {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false);
        builder.squad(MARINES)
                .faction(Faction.MARINE)
                .type(UnitType.MARINE)
                .size(FIRST_LIFT)
                .at(LZ_X, LZ_Y)
                .primary(CARBINE)
                .assigned(id -> ObjectiveAssignment.attackMove(id, MISSION_X, MISSION_Y))
                .done();
        // A whole map away and immobile: present so the defender side is never
        // absent, out of vision so it is never a reason to go anywhere.
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, HEIGHT - 3,
                spec -> spec.moveSpeed(0f));
        SceneWorld world = builder.build();

        Squad squad = world.squad(MARINES);
        squad.campaignSquadId = CAMPAIGN_SQUAD_ID;
        squad.campaignLabel = "Squad 01";
        squad.expectedSize = FIRST_LIFT + SECOND_LIFT;
        // Already waited out its form-up and stepped off short-handed. Written
        // as the state rather than played, because playing it is sixty seconds
        // of marines standing still and the shipped timing is not this scene's
        // to change.
        squad.formUpElapsed = SquadFormUpSystem.FORM_UP_TIMEOUT;
        squad.leaderId = world.members(MARINES)[0];
        return world;
    }

    /** The subject loops: a second lift at {@link #LIFT_TICK}, with or without a picket beside it. */
    private SceneReport crossing(FrameSink frames, String loopId, boolean picket) {
        SceneWorld world = build();
        Squad squad = world.squad(MARINES);
        Crossing crossing = new Crossing(world, squad);

        SceneRun run = SceneRun.of(world)
                .observe(crossing)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> caption(loopId, crossing, tick));
        run.run(LIFT_TICK);
        if (picket) crossing.spawnPicket();
        int marked = crossing.landSecondLift();
        run.run(TICKS - LIFT_TICK);

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("marked-on-landing", marked == SECOND_LIFT,
                marked + " of " + SECOND_LIFT + " marines landed as late arrivals"
                        + " (wanted all of them; the squad was "
                        + String.format(Locale.ROOT, "%.1f", crossing.gapAtLanding)
                        + " cells away and "
                        + InfantryCohesion.COHESION_RADIUS + " is cohesion)"));
        verdicts.add(crossing.closedToCohesion());
        verdicts.add(crossing.stateRetired());
        if (picket) {
            verdicts.add(Verdict.of("contested", crossing.ticksInPicketReach > 0,
                    "a late arrival stood inside the picket's reach with a clear"
                            + " shot on " + crossing.ticksInPicketReach + " tick(s)"
                            + " (wanted more than 0)"));
            verdicts.add(Verdict.of("returned-fire", crossing.shotsWhileRejoining > 0,
                    crossing.shotsWhileRejoining + " round(s) fired while rejoining"
                            + " (wanted more than 0: rejoining is not the same as"
                            + " not defending yourself, and the picket is the only"
                            + " thing on this map within reach)"));
            verdicts.add(noDiversion(crossing));
            verdicts.add(crossing.keptCrossing());
        } else {
            verdicts.add(Verdict.of("no-shots-fired", crossing.shotsWhileRejoining == 0,
                    crossing.shotsWhileRejoining + " round(s) fired while"
                            + " rejoining (wanted 0; nothing in this loop is"
                            + " reachable to shoot at)"));
        }
        return new SceneReport(id(), loopId, TICKS, verdicts, crossing.metrics(picket));
    }

    /**
     * Nobody left the corridor between the landing zone and the squad. The
     * honest form of "did not pursue": a shot is only ever taken at somebody
     * already inside the shooter's range — the firing system will not let a
     * round go otherwise — so counting shots cannot separate return fire from
     * prosecution. Where the marine's feet went can.
     */
    private static Verdict noDiversion(Crossing crossing) {
        return Verdict.of("no-diversion", crossing.maxLateral <= LATERAL_BAR,
                String.format(Locale.ROOT,
                        "the furthest a rejoining marine strayed off the"
                                + " landing-zone-to-squad axis was %.1f cell(s)"
                                + " (bar %.1f; the picket sits %d off it), closing"
                                + " to %.1f cell(s) of the picket",
                        crossing.maxLateral, LATERAL_BAR,
                        Math.abs(PICKET_Y - LZ_Y), crossing.minPicketDistance));
    }

    /**
     * The same crossing with the state switched off, and no verdict on it.
     *
     * <p>The property is set before the world is built, so nobody is ever
     * marked and the slot-assignment and arrival exclusions cannot fire either
     * — this is the tree without the behaviour rather than the behaviour
     * declining. The one verdict it does carry is about itself: that it really
     * did run without the state.
     */
    private SceneReport control(FrameSink frames, String loopId, boolean picket) {
        boolean previously = SquadRejoin.isEnabled();
        SquadRejoin.setEnabledForEvidence(false);
        try {
            SceneWorld world = build();
            Squad squad = world.squad(MARINES);
            Crossing crossing = new Crossing(world, squad);

            SceneRun run = SceneRun.of(world)
                    .observe(crossing)
                    .frames(frames, loopId, FRAME_EVERY_TICKS,
                            tick -> caption(loopId, crossing, tick));
            run.run(LIFT_TICK);
            if (picket) crossing.spawnPicket();
            int marked = crossing.landSecondLift();
            run.run(TICKS - LIFT_TICK);

            List<Verdict> verdicts = List.of(
                    Verdict.of("ran-without-the-state", marked == 0
                                    && squad.rejoiningCount() == 0,
                            marked + " marine(s) marked and " + squad.rejoiningCount()
                                    + " still carrying the state (wanted 0 and 0)"));
            return new SceneReport(id(), loopId, TICKS, verdicts,
                    crossing.metrics(picket));
        } finally {
            SquadRejoin.setEnabledForEvidence(previously);
        }
    }

    private static String caption(String loopId, Crossing crossing, int tick) {
        return "late arrival [" + loopId + "]  t=" + tick + "  "
                + crossing.captionSuffix();
    }

    /**
     * The instrument: where the late arrivals are relative to their squad, what
     * they shot at on the way, and how far off the crossing they went.
     *
     * <p>It owns the second lift as well, because the landing has to happen
     * mid-run and the readings that follow are all keyed on which marines came
     * off it.
     */
    private static final class Crossing implements TickObserver {

        private final BattleSimulation sim;
        private final Squad squad;

        private long[] lateArrivals = new long[0];
        private long[] picketMembers = new long[0];
        private int[] cohesionTick = new int[0];
        private float[] endDistance = new float[0];
        private float[] previousX = new float[0];
        private float[] previousY = new float[0];

        private float gapAtLanding;
        private float maxLateral;
        private float minPicketDistance = Float.MAX_VALUE;
        private int shotsWhileRejoining;
        private int ticksInPicketReach;
        private int crossingTicks;
        private int haltedTicks;
        private int stillFlaggedAtEnd;
        private int aliveAtEnd;
        private float squadX;

        private Crossing(SceneWorld world, Squad squad) {
            this.sim = world.sim();
            this.squad = squad;
        }

        /**
         * Two armed defenders beside the crossing, minted as their own squad.
         * A loose unsquadded body carries no loadout and cannot acquire a
         * target, so it would be scenery and the loop would record a crossing
         * nobody contested.
         */
        void spawnPicket() {
            int picketSquad = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
            picketMembers = new long[PICKET_SIZE];
            for (int i = 0; i < PICKET_SIZE; i++) {
                EntitySpec spec = new EntitySpec("picket-" + i, Faction.DEFENDER,
                        UnitType.MARINE_RED, PICKET_X + i, PICKET_Y);
                new MarineLoadout(UnitRole.COMBATANT, null, CARBINE, null, 0)
                        .seedInto(spec);
                spec.squad(picketSquad);
                spec.moveSpeed = 0f;
                picketMembers[i] = sim.spawn(spec);
            }
            Squad picket = sim.getSquad(picketSquad);
            if (picket != null) {
                picket.originalSize = PICKET_SIZE;
                picket.aliveMembers = PICKET_SIZE;
            }
        }

        /**
         * The rest of the squad, set down at the landing zone through the
         * production marking rule with the arguments the shuttle payload passes
         * it.
         *
         * @return how many of them the rule called late arrivals
         */
        int landSecondLift() {
            gapAtLanding = distanceToSquad(LZ_X + 0.5f, LZ_Y + 0.5f);
            lateArrivals = new long[SECOND_LIFT];
            cohesionTick = new int[SECOND_LIFT];
            endDistance = new float[SECOND_LIFT];
            previousX = new float[SECOND_LIFT];
            previousY = new float[SECOND_LIFT];
            int marked = 0;
            for (int i = 0; i < SECOND_LIFT; i++) {
                cohesionTick[i] = -1;
                int x = LZ_X + i % 2;
                int y = LZ_Y - 1 + i / 2;
                EntitySpec spec = new EntitySpec("late-" + i, Faction.MARINE,
                        UnitType.MARINE, x, y);
                new MarineLoadout(UnitRole.COMBATANT, null, CARBINE, null, 0)
                        .seedInto(spec);
                spec.squad(squad.id);
                // A fire team of their own, the way a lift's worth of marines
                // arrives: the second lift is the squad's second team.
                spec.fireTeam(1);
                // The payload counts a marine as deboarded before it spawns it,
                // and the form-up gate reads that count.
                squad.originalSize++;
                long unit = sim.spawn(spec);
                lateArrivals[i] = unit;
                if (SquadRejoin.markIfLateArrival(squad, unit, x, y, sim.getRoster())) {
                    marked++;
                }
            }
            return marked;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            squadX = anchorX();
            if (lateArrivals.length == 0) return;
            int alive = 0;
            int flagged = 0;
            for (int i = 0; i < lateArrivals.length; i++) {
                long unit = sim.resolveUnit(lateArrivals[i]);
                if (unit == 0L) continue;
                alive++;
                if (squad.isRejoining(unit)) flagged++;
                float x = sim.world().x(unit);
                float y = sim.world().y(unit);
                float gap = distanceToSquad(x, y);
                endDistance[i] = gap;
                if (cohesionTick[i] < 0 && gap <= InfantryCohesion.COHESION_RADIUS) {
                    cohesionTick[i] = tick;
                }
                if (cohesionTick[i] >= 0) continue;
                // Everything below is about the crossing itself, so it is only
                // read while this marine is still making it.
                crossingTicks++;
                if (previousX[i] != 0f
                        && TacticalScoring.cellDistance(x, y, previousX[i], previousY[i])
                                < HALT_EPSILON) {
                    haltedTicks++;
                }
                previousX[i] = x;
                previousY[i] = y;
                maxLateral = Math.max(maxLateral, Math.abs(y - (LZ_Y + 0.5f)));
                readPicket(sim, unit, x, y);
                readShots(sim, unit);
            }
            aliveAtEnd = alive;
            stillFlaggedAtEnd = flagged;
        }

        /** How near the picket this marine came, and whether the picket could reach it. */
        private void readPicket(BattleSimulation sim, long unit, float x, float y) {
            for (long member : picketMembers) {
                long picket = sim.resolveUnit(member);
                if (picket == 0L) continue;
                float px = sim.world().x(picket);
                float py = sim.world().y(picket);
                float gap = TacticalScoring.cellDistance(x, y, px, py);
                minPicketDistance = Math.min(minPicketDistance, gap);
                if (gap <= sim.world().attackRange(picket)
                        && sim.getTacticalScoring().hasClearShot(picket, unit)) {
                    ticksInPicketReach++;
                    return;
                }
            }
        }

        /**
         * Rounds this marine actually put down range while rejoining, off the
         * last advance's shot events.
         *
         * <p>Read from the events rather than from the fire intent, which is
         * consumed inside the advance and reads zero from every observer — a
         * loop instrumented that way reports a firefight it watched as nobody
         * having fired a shot, which is what this one did on its first play.
         */
        private void readShots(BattleSimulation sim, long unit) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != unit) continue;
                shotsWhileRejoining++;
            }
        }

        /** Distance from a point to the squad's cohesion anchor: its live leader, else its centroid. */
        private float distanceToSquad(float x, float y) {
            long leader = sim.resolveUnit(squad.leaderId);
            float ax = leader != 0L ? sim.world().x(leader) : squad.centroidX;
            float ay = leader != 0L ? sim.world().y(leader) : squad.centroidY;
            return TacticalScoring.cellDistance(x, y, ax, ay);
        }

        private float anchorX() {
            long leader = sim.resolveUnit(squad.leaderId);
            return leader != 0L ? sim.world().x(leader) : squad.centroidX;
        }

        Verdict closedToCohesion() {
            int closed = 0;
            int latest = -1;
            for (int i = 0; i < cohesionTick.length; i++) {
                if (cohesionTick[i] < 0) continue;
                closed++;
                latest = Math.max(latest, cohesionTick[i]);
            }
            return Verdict.of("closed-to-cohesion", closed == cohesionTick.length,
                    String.format(Locale.ROOT,
                            "%d of %d late arrivals were back inside %.0f cells of"
                                    + " the squad by tick %d; furthest still out is"
                                    + " %.1f cell(s) at the end",
                            closed, cohesionTick.length,
                            InfantryCohesion.COHESION_RADIUS, latest, furthestAtEnd()));
        }

        /**
         * The crossing was a crossing rather than a halt. The reading the
         * contested loop turns on: a marine who stops to fight the picket does
         * it standing still on the axis, which no lateral measurement can see.
         */
        Verdict keptCrossing() {
            int allowance = Math.round(crossingTicks * HALT_ALLOWANCE);
            return Verdict.of("kept-crossing", haltedTicks <= allowance,
                    String.format(Locale.ROOT,
                            "stood still on %d of %d crossing tick(s), %.0f%%"
                                    + " (allowance %d)",
                            haltedTicks, crossingTicks,
                            crossingTicks == 0 ? 0f
                                    : 100f * haltedTicks / crossingTicks,
                            allowance));
        }

        Verdict stateRetired() {
            return Verdict.of("state-retired",
                    stillFlaggedAtEnd == 0 && squad.rejoiningCount() == 0,
                    stillFlaggedAtEnd + " late arrival(s) still rejoining at tick "
                            + LAST_TICK + " and " + squad.rejoiningCount()
                            + " id(s) left on the squad (wanted 0 and 0)");
        }

        private float furthestAtEnd() {
            float furthest = 0f;
            for (float d : endDistance) furthest = Math.max(furthest, d);
            return furthest;
        }

        Map<String, Number> metrics(boolean picket) {
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("gapAtLanding", round(gapAtLanding));
            metrics.put("lastCohesionTick", lastCohesionTick());
            metrics.put("neverClosed", neverClosed());
            metrics.put("furthestAtEnd", round(furthestAtEnd()));
            metrics.put("maxLateralOffAxis", round(maxLateral));
            metrics.put("crossingTicks", crossingTicks);
            metrics.put("haltedTicksWhileCrossing", haltedTicks);
            metrics.put("roundsFiredWhileRejoining", shotsWhileRejoining);
            metrics.put("lateArrivalsAlive", aliveAtEnd);
            metrics.put("stillRejoining", stillFlaggedAtEnd);
            if (picket) {
                metrics.put("ticksInPicketReach", ticksInPicketReach);
                metrics.put("minPicketDistance", round(minPicketDistance));
                metrics.put("picketAliveAtEnd", picketAlive());
            }
            return metrics;
        }

        private int picketAlive() {
            int alive = 0;
            for (long member : picketMembers) {
                if (sim.resolveUnit(member) != 0L) alive++;
            }
            return alive;
        }

        private int lastCohesionTick() {
            int latest = -1;
            for (int t : cohesionTick) latest = Math.max(latest, t);
            return latest;
        }

        private int neverClosed() {
            int never = 0;
            for (int t : cohesionTick) if (t < 0) never++;
            return never;
        }

        private static float round(float value) {
            if (value == Float.MAX_VALUE) return -1f;
            return Math.round(value * 10f) / 10f;
        }

        String captionSuffix() {
            if (lateArrivals.length == 0) {
                return String.format(Locale.ROOT, "squad at x=%.0f, second lift inbound",
                        squadX);
            }
            return String.format(Locale.ROOT, "%d rejoining, furthest %.0f cells out",
                    stillFlaggedAtEnd, furthestAtEnd());
        }
    }
}
