package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.OrderTrace;
import com.dillon.starsectormarines.battle.scene.OrderTrace.Sample;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.scene.Verdicts;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One squad ordered to take a room, and one enemy standing well off the way
 * there that nobody can shoot at: <b>does the squad get on with its order?</b>
 *
 * <p>The noun doc has always said it should. Prosecution — the initiative an
 * advancing HOLD publishes when a non-approaching direct contact is one only
 * part of the squad can engage — is explicitly bounded: "if no legal position
 * exists inside the maneuver leash, the member continues its assigned advance."
 * The code did the opposite. Prosecution anchors the firing-position search on
 * the squad's own centroid with a twelve-cell leash;
 * {@code TacticalScoring.findFiringPositionWithin} returns null when nothing
 * inside that leash has both the reach and the line of fire; and null was read
 * as {@code UNREACHABLE}, whose answer — correctly, for the case it was written
 * for — is to clear the path and fight from where you stand.
 *
 * <h2>What the live battle showed</h2>
 * <p>Two squad dumps of one battle, taken on 2026-09-01. Squad 168: twelve
 * marines under {@code CLEAR_ZONE}, executing {@code EnterZone}, standing on
 * their landing pad for a hundred and eighty consecutive frames with no member
 * moving, no member holding a path, and every member settled. The picture
 * underneath was HOLD / ADVANCING / PROSECUTE against one defender thirty cells
 * away, eight believed contacts, a force ratio of 0.18 in the marines' favour,
 * and <em>zero</em> engageable members. Squad 167 was in the same state against
 * a contact forty cells off its axis. Nothing was going to end it either:
 * {@code TacticalScoring.contactHoldIsFresh} is true whenever any contact is
 * being observed at all, so the picture that produced the freeze was renewed by
 * the freeze.
 *
 * <h2>The map is an order the squad cannot carry out standing still</h2>
 * <p>Open ground, {@value #WIDTH} by {@value #HEIGHT}, with one walled room in
 * the east holding one occupant — which is what makes the squad's
 * {@code CLEAR_ZONE} order relevant, since a zone with no live enemy in it is
 * one {@code ClearAssignedZoneGoal} yields rather than plans for. The squad
 * starts sixty-odd cells west of it, so the plan is
 * {@code EnterZone} then {@code ClearZone} and the whole recording is spent in
 * the first of those, which is the step that halts on contact.
 *
 * <p>The contact is {@value #OFF_AXIS_CELLS} cells north of the squad's start
 * and barely any distance east of it, which does three things at once. It is
 * inside {@code CONTACT_PICTURE_RADIUS} and inside marine vision, so it is
 * seen — a believed contact that is not being observed this tick is not
 * <em>direct</em>, and prosecution is only published against a direct one. It
 * is off the advance axis, so it classifies as a flank rather than a front
 * contact, which is what carries the doctrine to HOLD instead of ADVANCE. And
 * it is further off than the leash plus the squad's weapon reach, so the
 * firing-position search has nothing to return.
 *
 * <p><b>The squad carries one named weapon rather than a rolled kit, and that
 * is load-bearing.</b> The default roll hands out carbines and one pulse rifle,
 * whose reaches differ by ten cells, so at this distance five marines would
 * have no firing position and the sixth would — and the recording would be five
 * men standing still beside one man walking off north, which is neither
 * behaviour this scene set out to compare. Every seat carries the carbine, so
 * "out of reach" is a fact about the squad.
 *
 * <p>The contact is <b>stationary</b> because an approaching one publishes
 * RECEIVE, not PROSECUTE, and armed because an unarmed unsquadded body is
 * scenery. The far defender in the sealed pocket is the usual one: present so
 * the defender side is never absent, walled in so it is never a reason to go
 * anywhere.
 *
 * <h2>The three loops</h2>
 * <p>{@code fall-through} is the subject. {@code frozen-control} is the same
 * world with {@code battle.squad.prosecutionFallThrough} off, and it is part of
 * the instrument rather than a courtesy: a scene that only recorded the fixed
 * behaviour could not say whether its geometry reproduces the defect at all,
 * and "the squad walked east" is not evidence until the identical world with
 * the fall-through off is shown not to.
 *
 * <p>{@code in-reach} moves the same contact to {@value #IN_REACH_CELLS} cells,
 * inside leash plus reach, so a firing position does exist. It is the guard on
 * the other side: the fix must not have deleted prosecution, and a squad that
 * walked past a contact it could perfectly well have taken positions against
 * would satisfy every verdict above and none of the doctrine.
 *
 * <h2>What it records</h2>
 * <p>The control makes <b>0.0 cells</b> of eastward progress in nine hundred
 * ticks, with 898 of its 899 contact ticks reading PROSECUTE — the live dump
 * reproduced to the decimal. The subject makes 46.0 cells under the identical
 * picture, 398 of 399 ticks PROSECUTE, and not one plan-less tick after the
 * first. The third loop puts 25 HP into its contact and covers 6.3 cells to the
 * subject's 15.6 over the first three hundred ticks, so what changed is the
 * bound on prosecution rather than prosecution itself.
 *
 * <h2>Two ways this world was wrong before it was right</h2>
 * <p>Both were silent, and both would have produced a green scene measuring
 * something else. A hole left in the room's wall as ordinary floor is a
 * <em>gap</em> and not a portal: the detector merged the room into the field,
 * the order named the zone the squad was already standing in, the tactical axis
 * pointed at the middle of the map, and the contact came out FRONT instead of
 * flank — so the doctrine was ADVANCE and there was no prosecution anywhere in
 * the recording. {@code SceneBuilder.doorway} is the marked-cell version, and
 * {@link #build} refuses a room that did not come out as a zone of its own.
 *
 * <p>The other is that the room's occupant becomes the picture's primary as the
 * squad closes on it, which is entirely correct and which halved the measured
 * prosecution share by counting a squad's proper ADVANCE against the enemy in
 * front of it as a failure to prosecute the enemy beside it. Every reading here
 * is gated on the primary being the body the scene is named for.
 */
public final class ProsecutionHoldScene implements BehaviorScene {

    static final int WIDTH = 88;
    static final int HEIGHT = 56;

    /** Thirty seconds — long enough to cross most of the map at marine pace. */
    private static final int TICKS = 900;
    private static final int LAST_TICK = TICKS - 1;
    private static final int FRAME_EVERY_TICKS = 15;

    /**
     * The window {@code in-reach} is compared over. Long enough for the squad
     * to have covered real ground if it were merely walking, short enough that
     * a prosecution which ends in a dead contact has not yet had time to turn
     * back into an ordinary advance and hide itself.
     */
    private static final int EARLY_WINDOW = 300;

    private static final int SQUAD_SIZE = 6;
    private static final int SQUAD_X = 10;
    private static final int SQUAD_Y = 9;

    /**
     * The carbine, issued to every seat. Fourteen cells of reach against the
     * twelve-cell prosecution leash puts the no-position boundary at
     * twenty-six cells from the squad centre, comfortably inside the
     * thirty-six-cell vision and contact-picture radii — which is the whole
     * window this scene needs and the pulse rifle's twenty-four-cell reach
     * does not leave.
     */
    private static final String CARBINE = "weapon.smg";

    /** The room: walls, one door, and the occupant that makes clearing it a job. */
    private static final int ROOM_WEST_X = 66;
    private static final int ROOM_EAST_X = 80;
    private static final int ROOM_NORTH_Y = 2;
    private static final int ROOM_SOUTH_Y = 16;
    private static final int ROOM_DOOR_Y = 9;
    private static final int ROOM_OCCUPANT_X = 76;
    private static final int ROOM_OCCUPANT_Y = 9;

    /** Where the room's zone is read from — an interior cell, never the threshold. */
    private static final int ROOM_INSIDE_X = 73;
    private static final int ROOM_INSIDE_Y = 9;

    /** The sealed pocket that keeps the defender side present. */
    private static final int POCKET_WEST_X = 83;
    private static final int POCKET_EAST_X = 87;
    private static final int POCKET_NORTH_Y = 48;
    private static final int POCKET_SOUTH_Y = 52;

    /** Out of reach: past the leash plus the carbine, inside vision. */
    private static final int OFF_AXIS_CELLS = 32;

    /** In reach: inside the leash plus the carbine, outside everybody's reach. */
    private static final int IN_REACH_CELLS = 20;

    /** Ground the subject must make east before the recording ends. */
    private static final float ADVANCE_BAR_CELLS = 40f;

    /** Ground the frozen control is allowed before it stops being frozen. */
    private static final float FREEZE_BAR_CELLS = 5f;

    /**
     * The share of the ticks where the off-axis contact is the picture's
     * primary that must read HOLD / ADVANCING / PROSECUTE. Not all of them:
     * the first sighting has no motion sample yet and reads RECEIVE, and the
     * last ticks before a contact leaves the radius are the squad releasing
     * it, which is the doctrine working rather than the scene missing.
     * Measured, both out-of-reach loops sit one tick short of every tick.
     */
    private static final float PROSECUTE_SHARE = 0.8f;

    private static final String SUBJECT_LOOP = "fall-through";
    private static final String CONTROL_LOOP = "frozen-control";
    private static final String IN_REACH_LOOP = "in-reach";

    private static final String MARINES = "marines";
    private static final String CONTACT = "contact";
    private static final String OCCUPANT = "occupant";
    private static final String FAR = "far";

    @Override public String id() { return "prosecution-hold"; }

    @Override
    public String label() {
        return "Prosecution hold: does a squad prosecuting a contact it cannot"
                + " reach get on with the order it was given?";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        boolean previous = AbstractZoneAction.isProsecutionFallThroughEnabled();
        try {
            AbstractZoneAction.setProsecutionFallThroughForEvidence(true);
            Loop fallThrough = playOne(frames, SUBJECT_LOOP, OFF_AXIS_CELLS);
            Loop inReach = playOne(frames, IN_REACH_LOOP, IN_REACH_CELLS);
            AbstractZoneAction.setProsecutionFallThroughForEvidence(false);
            Loop frozen = playOne(frames, CONTROL_LOOP, OFF_AXIS_CELLS);
            return List.of(report(SUBJECT_LOOP, fallThrough, null),
                    report(CONTROL_LOOP, frozen, null),
                    report(IN_REACH_LOOP, inReach, fallThrough));
        } finally {
            AbstractZoneAction.setProsecutionFallThroughForEvidence(previous);
        }
    }

    /**
     * The world every loop shares, bar where the contact stands.
     *
     * <p>The order is written after the build rather than through the builder's
     * {@code assigned} hook, because the zone it names does not exist until the
     * sim has detected it. Read off the grid, never guessed: a
     * {@code CLEAR_ZONE} naming a zone id that does not exist is an order the
     * goal declines, and a scene whose squad silently has no mission records a
     * squad standing still for a reason that has nothing to do with contact.
     */
    private static SceneWorld build(int contactOffset) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                .wall(ROOM_WEST_X, ROOM_NORTH_Y, ROOM_EAST_X, ROOM_NORTH_Y)
                .wall(ROOM_WEST_X, ROOM_SOUTH_Y, ROOM_EAST_X, ROOM_SOUTH_Y)
                .wall(ROOM_EAST_X, ROOM_NORTH_Y, ROOM_EAST_X, ROOM_SOUTH_Y)
                .wall(POCKET_WEST_X, POCKET_NORTH_Y, POCKET_EAST_X, POCKET_NORTH_Y)
                .wall(POCKET_WEST_X, POCKET_SOUTH_Y, POCKET_EAST_X, POCKET_SOUTH_Y)
                .wall(POCKET_WEST_X, POCKET_NORTH_Y, POCKET_WEST_X, POCKET_SOUTH_Y)
                .wall(POCKET_EAST_X, POCKET_NORTH_Y, POCKET_EAST_X, POCKET_SOUTH_Y);
        // The room's west face carries the only way in. Walled solid and then
        // holed with a marked doorway, because an ordinary floor cell left in
        // a wall is a gap rather than a portal: the detector merges the room
        // into the field, the order names the zone the squad is already in,
        // and the axis points at the middle of the whole map. Which is exactly
        // what the first version of this scene recorded.
        builder.wall(ROOM_WEST_X, ROOM_NORTH_Y, ROOM_WEST_X, ROOM_SOUTH_Y)
                .doorway(ROOM_WEST_X, ROOM_DOOR_Y);

        builder.squad(MARINES)
                .faction(Faction.MARINE)
                .type(UnitType.MARINE)
                .size(SQUAD_SIZE)
                .primary(CARBINE)
                .at(SQUAD_X, SQUAD_Y)
                .done();
        // Stationary: an approaching contact publishes RECEIVE rather than
        // PROSECUTE, and this scene is about the other one. Armed and squadded
        // so it is a contact at all.
        builder.squad(CONTACT)
                .faction(Faction.DEFENDER)
                .type(UnitType.MARINE_RED)
                .size(1)
                .primary(CARBINE)
                .at(SQUAD_X + 2, SQUAD_Y + contactOffset)
                .stationary()
                .done();
        // Inside the room, so the assigned zone is not already clear.
        builder.unit(OCCUPANT, Faction.DEFENDER, UnitType.MARINE_RED,
                ROOM_OCCUPANT_X, ROOM_OCCUPANT_Y, spec -> spec.moveSpeed(0f));
        builder.unit(FAR, Faction.DEFENDER, UnitType.MARINE,
                POCKET_WEST_X + 2, POCKET_NORTH_Y + 2, spec -> spec.moveSpeed(0f));

        SceneWorld world = builder.build();
        int targetZone = world.zoneAt(ROOM_INSIDE_X, ROOM_INSIDE_Y);
        if (targetZone < 0 || targetZone == world.zoneAt(SQUAD_X, SQUAD_Y)) {
            throw new IllegalStateException("The room did not come out as a zone of its"
                    + " own (room " + targetZone + ", field "
                    + world.zoneAt(SQUAD_X, SQUAD_Y) + "), so the order would name"
                    + " ground the squad is already standing on");
        }
        int squadId = world.squadId(MARINES);
        Squad squad = world.squad(MARINES);
        if (squad != null) {
            squad.assignedObjective = ObjectiveAssignment.clearZone(squadId, targetZone);
        }
        return world;
    }

    private Loop playOne(FrameSink frames, String loopId, int contactOffset) {
        SceneWorld world = build(contactOffset);
        int squadId = world.squadId(MARINES);
        int targetZone = world.zoneAt(ROOM_INSIDE_X, ROOM_INSIDE_Y);
        OrderTrace trace = new OrderTrace().track(MARINES, squadId);
        PictureTrace picture = new PictureTrace(world, targetZone);

        SceneRun.of(world)
                .observe(trace, picture)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "prosecution / " + loopId + "  t=" + tick + "  "
                                + picture.captionAt(tick))
                .run(TICKS);
        return new Loop(trace, picture, squadId);
    }

    /**
     * Judges one loop. {@code comparedWith} is the subject loop, supplied only
     * to {@code in-reach}, whose whole question is a comparison: prosecution is
     * visible as ground <em>not</em> covered, and that means nothing without a
     * loop that covered it.
     */
    private SceneReport report(String loopId, Loop loop, Loop comparedWith) {
        PictureTrace picture = loop.picture();
        List<Verdict> verdicts = new ArrayList<>();
        if (comparedWith == null) {
            // Both out-of-reach loops assert the doctrine that produced them.
            // The in-reach loop deliberately does not: a squad that reaches its
            // firing positions has engageable members and publishes RECEIVE
            // from then on, which is the doctrine working rather than a fault.
            verdicts.add(initiativeIsProsecute(loop));
        }
        if (SUBJECT_LOOP.equals(loopId)) {
            verdicts.add(Verdict.of("advances-under-prosecution",
                    loop.forwardCells() >= ADVANCE_BAR_CELLS,
                    String.format(Locale.ROOT,
                            "the centroid made %.1f cell(s) east towards the room at x=%d"
                                    + " (wanted at least %.0f)",
                            loop.forwardCells(), ROOM_INSIDE_X, ADVANCE_BAR_CELLS)));
            verdicts.add(Verdicts.planlessAtMost("never-planless", loop.trace(),
                    loop.squadId(), 1, LAST_TICK, 1));
        }
        if (CONTROL_LOOP.equals(loopId)) {
            verdicts.add(Verdict.of("control-freezes",
                    loop.forwardCells() <= FREEZE_BAR_CELLS,
                    String.format(Locale.ROOT,
                            "the centroid made %.1f cell(s) east with the fall-through off"
                                    + " (wanted at most %.0f, or the geometry is not"
                                    + " reproducing the freeze this scene exists to measure)",
                            loop.forwardCells(), FREEZE_BAR_CELLS)));
        }
        if (comparedWith != null) {
            verdicts.add(Verdict.of("still-prosecutes-in-reach",
                    picture.hostileDamage > 0f
                            && loop.forwardCells(EARLY_WINDOW)
                                    < comparedWith.forwardCells(EARLY_WINDOW),
                    String.format(Locale.ROOT,
                            "%.1f HP of hostile damage and %.1f cell(s) east in the first"
                                    + " %d ticks, against the subject loop's %.1f"
                                    + " (wanted damage, and less ground — a squad that"
                                    + " walks past a contact it can take positions"
                                    + " against has lost prosecution, not fixed it)",
                            picture.hostileDamage, loop.forwardCells(EARLY_WINDOW),
                            EARLY_WINDOW, comparedWith.forwardCells(EARLY_WINDOW))));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("forwardCells", loop.forwardCells());
        metrics.put("forwardCellsFirst" + EARLY_WINDOW, loop.forwardCells(EARLY_WINDOW));
        metrics.put("planlessTicks", loop.trace().planlessTicks(loop.squadId()));
        metrics.put("contactPrimaryTicks", picture.contactPrimaryTicks);
        metrics.put("prosecuteTicks", picture.prosecuteTicks);
        metrics.put("receiveTicks", picture.receiveTicks);
        metrics.put("hostileDamageDealt", picture.hostileDamage);
        metrics.put("membersInTargetZone", picture.membersInZone);
        metrics.put("marinesAlive", picture.marinesAlive);
        metrics.put("cellsTravelled", loop.trace().cellsTravelled(loop.squadId()));
        return new SceneReport(id(), loopId, TICKS, verdicts, metrics);
    }

    /**
     * The picture the freeze was produced by, asserted rather than assumed.
     *
     * <p>Without this the scene could pass while measuring an entirely
     * different doctrine — a squad planted by RECEIVE also stands still, and a
     * squad that never saw the contact at all walks east for reasons of its
     * own. The share is over the ticks this contact was the picture's primary
     * rather than over the run, because in the subject loop the squad walks out
     * of the contact's radius partway through and is quite right to stop then.
     */
    private static Verdict initiativeIsProsecute(Loop loop) {
        PictureTrace picture = loop.picture();
        if (picture.contactPrimaryTicks == 0) {
            return Verdict.fail("initiative-is-prosecute",
                    "the off-axis contact was never the squad's primary, so nothing"
                            + " here is about prosecuting it");
        }
        float share = picture.prosecuteTicks / (float) picture.contactPrimaryTicks;
        return Verdict.of("initiative-is-prosecute", share >= PROSECUTE_SHARE,
                String.format(Locale.ROOT,
                        "HOLD/ADVANCING/PROSECUTE on %d of the %d tick(s) the off-axis"
                                + " contact was the picture's primary (%.0f%%, wanted at"
                                + " least %.0f%%); the pictures read %s",
                        picture.prosecuteTicks, picture.contactPrimaryTicks,
                        share * 100f, PROSECUTE_SHARE * 100f, picture.pictureTally()));
    }

    /** One played loop and the two instruments it was read from. */
    private record Loop(OrderTrace trace, PictureTrace picture, int squadId) {

        /** Ground the centroid made east across the whole recording. */
        float forwardCells() {
            return forwardCells(LAST_TICK);
        }

        /**
         * Ground the centroid made east by {@code toTick}. East rather than
         * total distance travelled, because a squad prosecuting a contact
         * covers plenty of ground going nowhere — the reading has to be
         * progress along the order's own axis or a pinned squad's shuffling
         * reads as an advance.
         */
        float forwardCells(int toTick) {
            Sample first = trace.at(squadId, 0);
            Sample last = trace.at(squadId, Math.min(toTick, LAST_TICK));
            if (first == null || last == null) return 0f;
            return last.centroidX() - first.centroidX();
        }
    }

    /**
     * The squad's contact picture, its shooting, and where it ended up — the
     * readings {@link OrderTrace} does not carry because they are about
     * doctrine and damage rather than about the order stack.
     *
     * <p>Damage is sampled per tick and carried forward per marine rather than
     * summed at the end: a marine who dies stops being resolvable and his
     * telemetry goes with him, and in a scene about a squad under fire that is
     * most of the reading. It is the squad's whole hostile output rather than
     * damage attributed to one victim — with the room's occupant sixty cells
     * away and out of carbine reach for the entire recording, the only thing
     * this squad can shoot is the contact, and the out-of-reach loops confirm
     * it by dealing none at all.
     */
    private static final class PictureTrace implements TickObserver {

        private final SceneWorld world;
        private final long[] marines;
        private final float[] damage;
        private final int targetZone;
        /**
         * The off-axis contact this scene is named for. Every reading is gated
         * on the picture's primary being <em>this</em> body, because the room's
         * own occupant becomes a believed contact as the squad closes on it,
         * and a squad correctly reading ADVANCE against the enemy in front of
         * it would otherwise be counted as having stopped prosecuting the
         * enemy beside it.
         */
        private final long contact;

        private int contactPrimaryTicks;
        private int prosecuteTicks;
        private int receiveTicks;
        private float hostileDamage;
        private int membersInZone;
        private int marinesAlive;
        private final Map<Integer, String> captions = new LinkedHashMap<>();
        /**
         * Every doctrine / posture / initiative triple seen while the off-axis
         * contact was the primary, with its tick count and the inputs that
         * chose it. A scene whose picture is not the one it is named for is
         * measuring something else entirely, and the useful FAIL line is the
         * one that says which picture it got and which input produced it.
         */
        private final Map<String, Integer> tally = new LinkedHashMap<>();

        private PictureTrace(SceneWorld world, int targetZone) {
            this.world = world;
            this.marines = world.members(MARINES);
            this.damage = new float[marines.length];
            this.targetZone = targetZone;
            this.contact = world.members(CONTACT)[0];
            this.marinesAlive = marines.length;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            Squad squad = world.squad(MARINES);
            SquadContactPicture picture = squad == null ? null : squad.contactPicture;
            boolean observed = picture != null && picture.directContactCount() > 0
                    && picture.primaryContactId() == contact;
            if (observed) {
                contactPrimaryTicks++;
                if (picture.doctrine() == Doctrine.HOLD
                        && picture.posture() == Posture.ADVANCING) {
                    if (picture.contactInitiative() == ContactInitiative.PROSECUTE) {
                        prosecuteTicks++;
                    } else if (picture.contactInitiative() == ContactInitiative.RECEIVE) {
                        receiveTicks++;
                    }
                }
                // Everything selectContactInitiative and selectDoctrine read,
                // because a scene that got the wrong picture wants to be told
                // which input it got wrong rather than only that it missed.
                tally.merge(picture.doctrine() + "/" + picture.posture()
                        + "/" + picture.contactInitiative()
                        + " [" + picture.forceBalance() + " " + picture.dominantSector()
                        + " " + picture.primaryMotion()
                        + " engageable " + picture.primaryEngageableMembers()
                        + "/" + picture.liveMembers() + "]", 1, Integer::sum);
            }
            captions.put(tick, picture == null ? "—"
                    : picture.doctrine() + "/" + picture.contactInitiative());

            CombatTelemetryService telemetry = sim.telemetry();
            int alive = 0;
            int inZone = 0;
            float dealt = 0f;
            for (int i = 0; i < marines.length; i++) {
                long marine = marines[i];
                if (sim.resolveUnit(marine) != 0L) {
                    alive++;
                    if (telemetry.isRecorded(marine)) damage[i] = telemetry.damageDealt(marine);
                    if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(marine),
                            sim.world().cellY(marine)) == targetZone) inZone++;
                }
                dealt += damage[i];
            }
            marinesAlive = alive;
            membersInZone = inZone;
            hostileDamage = dealt;
        }

        String captionAt(int tick) {
            return captions.getOrDefault(tick, "—");
        }

        /** The doctrine tally as {@code HOLD/ADVANCING/PROSECUTE×411}. */
        String pictureTally() {
            if (tally.isEmpty()) return "(no observed contact)";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : tally.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append('×').append(e.getValue());
            }
            return sb.toString();
        }
    }
}
