package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AttackMoveGoal;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.OrderTrace;
import com.dillon.starsectormarines.battle.scene.OrderTrace.Sample;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.scene.Verdicts;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.debug.SquadOrderRecorder;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * One squad, one standing mission, and one ground click: <b>does a squad take a
 * direct order at once, hold it, and hand back to its mission when done?</b>
 *
 * <p>Those are four separate promises and the order path keeps all four or none
 * of them. A click reaches the squad through a mailbox, a validation pass, a
 * destination resolver, a temporary assignment that stands <em>over</em> the
 * commander's without replacing it, an execution invalidation, and a release on
 * arrival. Every unit test around that seam pins one link — the service queues,
 * the system activates, {@code assignmentForExecution} prefers the player's, the
 * release clears it — and none of them can show the thing the path is actually
 * for: that a click is answered on the next tick, is still being carried out
 * three hundred ticks later, and gives the mission back rather than stranding
 * the squad on the ground the player pointed at.
 *
 * <h2>The map is a corridor the mission runs the length of</h2>
 * <p>Open ground, 96 by 32. The squad starts west with a commander's
 * {@code ATTACK_MOVE} to the far east edge — a long advance it cannot finish
 * inside the recording, which is the point: the mission is unmistakably still
 * outstanding when the player's order arrives and unmistakably still outstanding
 * when it is handed back. The player's click goes to (40, 6), well off the
 * mission's own axis, so <b>the two orders disagree about direction</b> and a
 * squad merely continuing east cannot be mistaken for one obeying.
 *
 * <p><b>The far defender exists so the battle keeps ticking.</b> A side that is
 * absent ends the simulation immediately and a scene that ends on tick one
 * records nothing. It is immobile and a whole map away, past marine vision, so
 * it is a presence rather than an attractor.
 *
 * <h2>The control is the same world with nobody clicking</h2>
 * <p>"The squad went to (40, 6) and then east again" says nothing until you know
 * what it does when no order arrives. The {@code control} loop changes exactly
 * one thing — no click — so whatever the subject loop does differently it does
 * because of the order, and nothing else in the scene differs.
 *
 * <h2>The third loop contests the order</h2>
 * <p>{@code AttackMoveGoal} is documented to keep its MISSION relevance through
 * contact, deliberately, because the engagement goals carry no memory of a
 * destination and a squad that drops to them fights, wins, and stands there. A
 * player order is an attack move, so that promise is the player's promise too:
 * an order given into a fight is still an order. The {@code contact} loop puts
 * an armed two-marine picket at (44, 10), a few cells past the destination, so
 * the crossing is contested and the claim is measured rather than trusted. The
 * <em>goal</em> may legitimately change while that fight is on — what may not
 * change is whose order the squad is carrying out, so that loop asserts only the
 * player prefix.
 *
 * <h2>What to read</h2>
 * <p>The tick numbers are the finding. {@code accepted} is bounded to a two-tick
 * window rather than "eventually", because the whole difference between a
 * responsive order and a sluggish one lives in those ticks and only an upper
 * bound can fail. The click lands in the mailbox during tick 60's player phase,
 * {@code SquadMoveOrderSystem.tick} drains it inside that same tick's advance,
 * and the observer of tick 61 is the first that can see it — so 61 is the
 * expected answer and 62 the slack.
 *
 * <p><b>Plan-less ticks are the other reading.</b> Both ends of the order
 * invalidate the squad's execution on purpose: taking the order clears the
 * plan, and releasing it clears the plan again. That is correct — a stale plan
 * pointed at the wrong ground is worse than none — but it is only correct if
 * the replan pass immediately behind it fills the hole in the same tick.
 * A plan-less tick anywhere after the first replan means the order path opened
 * a gap the ladder did not close, which is the {@code YieldFreezeScene} defect
 * arriving by a different road.
 *
 * <h2>What it records today: the order is taken and never given back</h2>
 * <p>The first two promises hold and the last two do not. The click is accepted
 * on tick 61 — the first tick that can see it — and the squad is planning under
 * it on that same tick with no plan-less gap anywhere in the run. It then walks
 * off its mission axis to the ground it was pointed at, arriving around tick
 * 640. <b>And there it stays.</b> The player's order still stands at tick 899,
 * with the squad's centroid parked 2.80 cells from (40.5, 6.5); played out to
 * three thousand ticks it is the identical centroid, to two decimal places, from
 * tick 640 onwards. The commander's {@code ATTACK_MOVE} to the east edge is
 * never resumed, in a battle that would otherwise still have it to do.
 *
 * <p><b>Two radii disagree, and the gap between them is where the squad is
 * stranded.</b> {@code SquadMoveOrderSystem.arrived} releases the order when the
 * squad centroid is within {@link AttackMove#ARRIVAL_RADIUS} — 2 cells — of the
 * destination. {@code AttackMove} itself stops advancing once every member is
 * within {@code SQUAD_ARRIVAL_RADIUS} — 5 cells — and each arrived member then
 * holds its ground rather than closing further, deliberately, so that one
 * marine's arrival cannot complete a step the whole squad shares. A body of six
 * people that has stopped at a five-cell footprint check settles with its
 * centroid a little under three cells out, which satisfies the action and misses
 * the release by 0.8 of a cell. Nothing ever moves it again: the order does not
 * expire, and while it stands it outranks the mission it is masking.
 *
 * <p>The {@code contact} loop parks at 2.59 cells and the same thing happens, so
 * this is not about the fight. The {@code control} loop passes everything it is
 * asked, which is what says the freeze belongs to the order rather than to the
 * map: with nobody clicking, the same squad on the same ground holds its mission
 * goal for all 899 ticks and covers 45.1 cells.
 *
 * <p>Whichever way that is closed — releasing on the squad's own arrival test
 * rather than on a centroid, widening the release to
 * {@code SQUAD_ARRIVAL_RADIUS}, or having the order expire when its action
 * reports success — <b>the bar here does not move</b>. "Handed the order back
 * near where it was sent" is the promise; a scene that relaxes it until the
 * current behaviour passes has stopped measuring the thing it was built for.
 */
public final class PlayerOrderScene implements BehaviorScene {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 32;

    /**
     * Thirty seconds of battle. Long enough to cross, hand back, and make
     * ground on the mission again — and, as it turns out, long enough to watch
     * the squad settle at its destination around tick 640 and sit there for
     * another 260 ticks with the order still on it.
     */
    private static final int TICKS = 900;
    private static final int LAST_TICK = TICKS - 1;
    private static final int FRAME_EVERY_TICKS = 15;

    private static final int SQUAD_SIZE = 6;
    private static final int SQUAD_X = 10;
    private static final int SQUAD_Y = 16;

    /** The commander's standing order: the far east edge, unreachable inside the recording. */
    private static final int MISSION_X = 84;
    private static final int MISSION_Y = 16;

    /** The click, and where it lands — off the mission's axis so the two orders disagree. */
    private static final int ORDER_TICK = 60;
    private static final int ORDER_X = 40;
    private static final int ORDER_Y = 6;

    /**
     * The window the order may be accepted in. Tick 61 is the first observation
     * that can see it, so this is "next tick" with one tick of slack rather
     * than an open-ended "eventually" — a bound that cannot fail measures
     * nothing.
     */
    private static final int ACCEPT_EARLIEST = 61;
    private static final int ACCEPT_LATEST = 62;

    /**
     * How near the destination the squad must be when the order lets go.
     * {@code SquadMoveOrderSystem} releases on a centroid within
     * {@link AttackMove#ARRIVAL_RADIUS}, but that test runs inside the advance
     * and the trace reads the tick after it, by which time the squad has taken
     * another step. Twice the radius is the slack for that one step.
     */
    private static final float ARRIVAL_BAR_CELLS = 2f * AttackMove.ARRIVAL_RADIUS;

    /** Ground the squad must make back towards its mission after the handback. */
    private static final int RESUME_CELLS = 10;

    /** The picket that contests the third loop's crossing. */
    private static final int PICKET_SIZE = 2;
    private static final int PICKET_X = 44;
    private static final int PICKET_Y = 10;

    private static final String MARINES = "marines";
    private static final String PICKET = "picket";

    /** The goal name {@link AttackMoveGoal} reports; both orders are attack moves. */
    private static final String ATTACK_MOVE = AttackMoveGoal.INSTANCE.name();

    @Override public String id() { return "player-order"; }

    @Override
    public String label() {
        return "Player order: does a squad take a direct order at once, hold it,"
                + " and hand back to its mission when done?";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(
                ordered(frames, "handback", false),
                control(frames),
                ordered(frames, "contact", true));
    }

    /**
     * Builds the world both kinds of loop share.
     *
     * <p>The squad's mission is written through the builder's {@code assigned}
     * hook and nothing here ever touches {@code playerTacticalOrder}: the scene
     * exists to exercise the real order path, and a scene that sets the field
     * directly has tested the goal and skipped everything it meant to be about.
     */
    private static SceneWorld build(boolean picket) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false);
        builder.squad(MARINES)
                .faction(Faction.MARINE)
                .type(UnitType.MARINE)
                .size(SQUAD_SIZE)
                .at(SQUAD_X, SQUAD_Y)
                .assigned(id -> ObjectiveAssignment.attackMove(id, MISSION_X, MISSION_Y))
                .done();
        if (picket) {
            // Armed and squadded, because target acquisition runs off the squad
            // and an unarmed loose body would be scenery: the crossing has to be
            // contested for the loop to be about contact at all.
            builder.squad(PICKET)
                    .faction(Faction.DEFENDER)
                    .type(UnitType.MARINE_RED)
                    .size(PICKET_SIZE)
                    .at(PICKET_X, PICKET_Y)
                    .stationary()
                    .done();
        }
        // A whole map away and immobile: present so the defender side is never
        // absent, out of vision so it is never a reason to go anywhere.
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, 3,
                spec -> spec.moveSpeed(0f));
        return builder.build();
    }

    /** The subject loops: a click at tick 60, with or without a picket in the way. */
    private SceneReport ordered(FrameSink frames, String loopId, boolean picket) {
        SceneWorld world = build(picket);
        int squadId = world.squadId(MARINES);
        OrderTrace trace = new OrderTrace().track(MARINES, squadId);
        ContactTicks contact = new ContactTicks(world);
        ScriptedPlayer player = ScriptedPlayer.on(world)
                .moveSquad(ORDER_TICK, MARINES, ORDER_X, ORDER_Y);

        SceneRun.of(world)
                .player(player)
                .observe(trace, contact)
                .frames(frames, loopId, FRAME_EVERY_TICKS, tick -> caption(trace, squadId, tick))
                .run(TICKS);

        OptionalInt accepted = trace.firstTick(squadId, Sample::playerOrdered);
        int acceptTick = accepted.orElse(-1);
        OptionalInt handedBack = acceptTick < 0 ? OptionalInt.empty()
                : trace.firstTick(squadId, s -> s.tick() > acceptTick && !s.playerOrdered());
        int handbackTick = handedBack.orElse(-1);
        String missionLabel = SquadOrderRecorder.assignmentLabel(
                ObjectiveAssignment.attackMove(squadId, MISSION_X, MISSION_Y));

        List<Verdict> verdicts = new ArrayList<>();
        if (picket) {
            verdicts.add(Verdict.of("contact-happened", contact.ticks > 0,
                    "the squad was in direct contact on " + contact.ticks + " tick(s)"
                            + " (wanted more than 0)"));
        }
        // Acceptance and the plan behind it are pinned once, on the uncontested
        // loop. Asking the same two questions again with a firefight in the
        // middle of them would not be a second reading of the order path; it
        // would be the same reading with more ways to fail for reasons the loop
        // is not about.
        if (!picket) {
            verdicts.add(Verdicts.firstTickWithin("accepted", trace, squadId,
                    Sample::playerOrdered, ACCEPT_EARLIEST, ACCEPT_LATEST,
                    "the squad held the player's order"));
            verdicts.add(acceptTick < 0
                    ? unmeasured("planned-on-issue", "the order was never accepted")
                    : Verdicts.latency("planned-on-issue", trace, squadId, acceptTick,
                            s -> ATTACK_MOVE.equals(s.goal()) && !s.planless(), 1,
                            "planned under the player's order"));
        }
        // Contact may legitimately move the squad off AttackMove for a while;
        // what it may not do is move the squad off the player's order.
        String heldName = picket ? "held-through-contact" : "held-until-arrival";
        // With no handback the window runs to the end of the recording. That is
        // a strictly longer window than accept-to-handback rather than a
        // relaxed one, so the answer still means what the name says — and a
        // loop whose order never releases has plenty to say about whether it
        // was held, which "not measurable" would have thrown away.
        int heldTo = handbackTick < 0 ? LAST_TICK : handbackTick - 1;
        verdicts.add(acceptTick < 0
                ? unmeasured(heldName, "the order was never accepted")
                : Verdicts.held(heldName, trace, squadId, acceptTick, heldTo,
                        picket ? Sample::playerOrdered
                               : s -> s.playerOrdered() && ATTACK_MOVE.equals(s.goal()),
                        picket ? "the player's order"
                               : "the player's order under " + ATTACK_MOVE));
        verdicts.add(handedBack(trace, squadId, handbackTick, picket));
        verdicts.add(handbackTick < 0
                ? unmeasured("replanned-on-handback", "the order was never handed back")
                : Verdicts.latency("replanned-on-handback", trace, squadId, handbackTick,
                        s -> !s.playerOrdered() && !s.planless()
                                && missionLabel.equals(s.executing()), 1,
                        "took the mission back"));
        if (!picket) {
            verdicts.add(resumedMission(trace, squadId, handbackTick));
        }
        // Tick 0 is excluded: it precedes the first replan, so every squad in
        // every scene is plan-less there and counting it would make the bar
        // unmeetable for a reason that has nothing to do with orders.
        verdicts.add(Verdicts.planlessAtMost("never-planless", trace, squadId,
                1, LAST_TICK, 0));
        if (!picket) {
            verdicts.add(Verdicts.alive("intact", trace, squadId, LAST_TICK, SQUAD_SIZE));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("acceptTick", acceptTick);
        metrics.put("handbackTick", handbackTick);
        metrics.put("planlessTicks", trace.planlessTicks(squadId));
        metrics.put("cellsTravelled", trace.cellsTravelled(squadId));
        metrics.put("aliveAtEnd", aliveAtEnd(trace, squadId));
        if (picket) {
            metrics.put("contactTicks", contact.ticks);
            metrics.put("picketAliveAtEnd", aliveMembers(world, PICKET));
        }
        return new SceneReport(id(), loopId, TICKS, verdicts, metrics);
    }

    /** The control: the same world, the same mission, and nobody clicking. */
    private SceneReport control(FrameSink frames) {
        SceneWorld world = build(false);
        int squadId = world.squadId(MARINES);
        OrderTrace trace = new OrderTrace().track(MARINES, squadId);

        SceneRun.of(world)
                .observe(trace)
                .frames(frames, "control", FRAME_EVERY_TICKS,
                        tick -> caption(trace, squadId, tick))
                .run(TICKS);

        OptionalInt ordered = trace.firstTick(squadId, Sample::playerOrdered);
        List<Verdict> verdicts = List.of(
                Verdict.of("never-player-ordered", ordered.isEmpty(),
                        ordered.isEmpty()
                                ? "no player order stood at any tick"
                                : "a player order stood from tick " + ordered.getAsInt()
                                        + " with nobody clicking"),
                Verdicts.held("mission-held", trace, squadId, 1, LAST_TICK,
                        s -> ATTACK_MOVE.equals(s.goal()), "the mission's " + ATTACK_MOVE + " goal"),
                Verdicts.planlessAtMost("never-planless", trace, squadId, 1, LAST_TICK, 0),
                Verdicts.alive("intact", trace, squadId, LAST_TICK, SQUAD_SIZE));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("planlessTicks", trace.planlessTicks(squadId));
        metrics.put("cellsTravelled", trace.cellsTravelled(squadId));
        metrics.put("aliveAtEnd", aliveAtEnd(trace, squadId));
        return new SceneReport(id(), "control", TICKS, verdicts, metrics);
    }

    /**
     * The order let go, and it let go where it was sent. Existence alone is not
     * enough: an order released for the wrong reason — the squad withdrawing,
     * the squad ceasing to be a valid player squad — also clears the field, and
     * would read as a clean handback from a squad still nowhere near the ground
     * it was pointed at.
     */
    private static Verdict handedBack(OrderTrace trace, int squadId, int handbackTick,
                                      boolean distanceOptional) {
        if (handbackTick < 0) {
            // Where it stopped is the diagnosis, so it goes on the FAIL line:
            // an order that never releases and a squad that never arrives look
            // the same until somebody prints the distance.
            Sample last = trace.at(squadId, LAST_TICK);
            String where = last == null ? "" : String.format(Locale.ROOT,
                    " with the centroid %.2f cell(s) from (%.1f, %.1f), outside the"
                            + " %.1f-cell release radius",
                    Math.hypot(last.centroidX() - (ORDER_X + 0.5f),
                            last.centroidY() - (ORDER_Y + 0.5f)),
                    ORDER_X + 0.5f, ORDER_Y + 0.5f, AttackMove.ARRIVAL_RADIUS);
            return Verdict.fail("handed-back",
                    "the player's order still stood at tick " + LAST_TICK + where);
        }
        if (distanceOptional) {
            return Verdict.pass("handed-back", "handed back at tick " + handbackTick);
        }
        Sample sample = trace.at(squadId, handbackTick);
        double away = Math.hypot(sample.centroidX() - (ORDER_X + 0.5f),
                sample.centroidY() - (ORDER_Y + 0.5f));
        return Verdict.of("handed-back", away <= ARRIVAL_BAR_CELLS,
                String.format(Locale.ROOT,
                        "handed back at tick %d with the centroid %.1f cell(s) from (%.1f, %.1f)"
                                + " (wanted within %.1f)",
                        handbackTick, away, ORDER_X + 0.5f, ORDER_Y + 0.5f, ARRIVAL_BAR_CELLS));
    }

    /**
     * The squad got on with the mission afterwards rather than standing on the
     * ground the player sent it to — the failure a released order looks exactly
     * like from a picture.
     */
    private static Verdict resumedMission(OrderTrace trace, int squadId, int handbackTick) {
        if (handbackTick < 0) {
            return unmeasured("resumed-mission", "the order was never handed back");
        }
        Sample from = trace.at(squadId, handbackTick);
        Sample end = trace.at(squadId, LAST_TICK);
        if (from == null || end == null) {
            return unmeasured("resumed-mission", "the recording has no sample to compare");
        }
        float gained = end.centroidX() - from.centroidX();
        return Verdict.of("resumed-mission", gained >= RESUME_CELLS,
                String.format(Locale.ROOT,
                        "centroid moved %.1f cell(s) east between tick %d and tick %d"
                                + " (wanted at least %d towards the mission at x=%d)",
                        gained, handbackTick, LAST_TICK, RESUME_CELLS, MISSION_X));
    }

    /**
     * A verdict that could not be read because an earlier one failed. Recorded
     * as a failure rather than dropped: a loop that quietly returns five
     * verdicts instead of eight has changed what it is measuring without
     * saying so.
     */
    private static Verdict unmeasured(String name, String why) {
        return Verdict.fail(name, "not measurable: " + why);
    }

    private static int aliveAtEnd(OrderTrace trace, int squadId) {
        Sample sample = trace.at(squadId, LAST_TICK);
        return sample == null ? 0 : sample.alive();
    }

    private static int aliveMembers(SceneWorld world, String key) {
        int alive = 0;
        for (long member : world.members(key)) {
            if (world.sim().resolveUnit(member) != 0L) alive++;
        }
        return alive;
    }

    private static String caption(OrderTrace trace, int squadId, int tick) {
        Sample sample = trace.at(squadId, tick);
        return "player order  t=" + tick + "  "
                + (sample == null || sample.executing().isEmpty() ? "—" : sample.executing());
    }

    /**
     * Ticks on which the squad was in direct contact, so the third loop can say
     * its crossing was actually contested rather than assume a picket in the
     * way was noticed. Read from the squad's own alert-pass flag, which is what
     * the replan reads too.
     */
    private static final class ContactTicks implements TickObserver {

        private final SceneWorld world;
        private int ticks;

        private ContactTicks(SceneWorld world) {
            this.world = world;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            Squad squad = world.squad(MARINES);
            if (squad != null && squad.hasDirectContactThisTick()) ticks++;
        }
    }
}
