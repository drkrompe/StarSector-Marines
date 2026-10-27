package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.DepotHoldSequence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Counterattack's depot for judging the mission's difficulty: the whole map marked with every placement and the road
 * attacks (who starts where, how many, when each wave enters and the final wave's technical with its route and park),
 * then the story battle played through the look preset with the company, the Hound recovered, at the moments that
 * decide it: the company's first step into dispatch, each wave on the field, the technical parked, and the end. The
 * company is the evidence's (`DepotHoldEvidenceTest`): twelve troops under autonomous command, sent at the technical
 * once it parks and back to dispatch's door once it burns, as a player would. Opt-in; seed {@code -Dsnapshot.depot.seed}
 * (default 8102). 1920x1080.
 */
public final class CounterattackDepotLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Ticks after an event its stills wait, so the bodies it brought are on screen. */
    private static final int SETTLE = 90;
    private static final int MAX_TICKS = 18_000;

    @Override public String id() { return "counterattack-depot"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Counterattack's depot: placements and road attacks marked, then the warehouse fight"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        long seed = Long.getLong("snapshot.depot.seed", 8102L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            var roster = new MarineRoster();
            roster.ensureActiveSoldiers(12);
            var deployment = CampaignMarineDeployment.freeze(roster, 12);
            var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
            try (BattleSimulation sim = BattleSetup.createOpeningOperation(seed, manifest, 1,
                    OpeningOperationKind.COUNTERATTACK,
                    new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD), true)) {
                deployment.applyTo(sim, 1);
                float w = CounterattackDepot.WIDTH, h = CounterattackDepot.HEIGHT;
                float whole = Math.max(w, h * 16f / 9f);
                DepotHoldSequence hold = sim.getDepotHold();
                BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                        w / 2f, h / 2f, whole);
                sim.advance(BattleSimulation.TICK_DT);
                out.add(new SnapshotArtifact("00-plan.png", look.renderMarked(sim, plan(sim, hold))));
                play(sim, hold, look, out, w / 2f, h / 2f, whole);
            }
        }
        return out;
    }

    /** Every placement and the road attacks, read off the battle as built. */
    private static ReviewAnnotations plan(BattleSimulation sim, DepotHoldSequence hold) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        marks.label(hold.x() + .5f, hold.y() + .5f, "Dispatch warehouse: hold 8 s", ReviewStyle.KEEP);
        for (LandingPad pad : sim.getMarineLandingPads())
            marks.box(pad.left(), pad.bottom(), pad.right(), pad.top(), "Company lands: 12, two sorties", ReviewStyle.LANDING);
        marks.label(CounterattackDepot.HOUND_X + .5f, CounterattackDepot.HOUND_Y + .5f, "Hound berth (Wayward)", ReviewStyle.ALLY);
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            if (squad.faction == Faction.DEFENDER)
                marks.label(squad.centroidX, squad.centroidY, "Raiders x" + squad.aliveMembers, ReviewStyle.FRONT);
            else if (squad.faction == Faction.ALLY)
                marks.label(squad.centroidX, squad.centroidY, "Militia x" + squad.aliveMembers, ReviewStyle.ALLY);
        }
        List<Integer> sizes = hold.forceSizes();
        // Cell rows count up the screen: row 3 is the road mouth at the bottom (south), the last row the top (north).
        int south = 3, north = CounterattackDepot.HEIGHT - 4;
        marks.arrow(78.5f, south, 78.5f, south + 12, "Wave 1 south: " + sizes.get(0)
                + " on first entry; wave 2: " + sizes.get(2) + " + technical at +40 s", ReviewStyle.APPROACH);
        marks.arrow(78.5f, north, 78.5f, north - 12, "Wave 1 north: " + sizes.get(1)
                + " on first entry; wave 2: " + sizes.get(3) + " at +40 s", ReviewStyle.APPROACH);
        int[] path = hold.technicalPath(sim);
        List<ReviewAnnotation.Point> route = new ArrayList<>();
        for (int i = 0; i + 1 < path.length; i += 2) route.add(new ReviewAnnotation.Point(path[i] + .5f, path[i + 1] + .5f));
        if (route.size() >= 2) marks.polyline(route, "Technical's route", ReviewStyle.DUG_IN);
        int[] at = hold.technicalRoute();
        if (at != null) marks.label(at[2] + .5f, at[3] + .5f, "Technical parks (heavy MG)", ReviewStyle.DUG_IN);
        return marks.build();
    }

    /** Plays the story battle as the evidence does and keeps the frames that decide it. */
    private static void play(BattleSimulation sim, DepotHoldSequence hold, BattleReviewFrameRenderer look,
                             List<SnapshotArtifact> out, float cx, float cy, float whole) throws Exception {
        int ordered = -1, sentAtTruck = -1;
        boolean handedBack = false, truckDown = false;
        int entered = -1, wave1 = -1, wave2 = -1, parked = -1;
        for (int tick = 0; tick < MAX_TICKS && !sim.isComplete(); tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            int now = sim.getSimTickIndex();
            var hound = sim.getWaylineHound();
            if (ordered < 0) for (Squad squad : sim.getSquads()) {
                if (squad.faction != Faction.MARINE || squad.campaignSquadId == null || squad.originalSize != 12) continue;
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id,
                                CounterattackDepot.HOUND_X - 2, CounterattackDepot.HOUND_Y - 1),
                        CommandAuthority.PLAYER_INTERVENTION, "depot-look", "Secure the marked berth");
                ordered = squad.id;
                break;
            }
            if (!handedBack && ordered >= 0 && hound.mechId() != 0) {
                sim.handoffSquadCommand(ordered, "depot-look", CommandAuthority.MISSION_COMMAND,
                        OpeningOperationCommand.issuer(Faction.MARINE), null, "Join the warehouse assault");
                handedBack = true;
            }
            var progress = hold.progress();
            if (entered < 0 && progress.alerted()) entered = now;
            if (wave1 < 0 && progress.waves() >= 1) wave1 = now;
            if (wave2 < 0 && progress.waves() >= 2) wave2 = now;
            long truck = hold.technicalId();
            if (truck != 0L) {
                int[] route = hold.technicalRoute();
                var body = sim.convoy().body(truck);
                if (parked < 0 && Math.hypot(body.x - route[2] - .5f, body.y - route[3] - .5f) <= 1.5f) parked = now;
                if (parked >= 0 && !truckDown && (now - parked) % 600 == 0) order(sim, (int) body.x, (int) body.y, "Take out the technical");
                if (!truckDown && !sim.convoy().isPresent(truck)) {
                    truckDown = true;
                    float[] door = hold.door(sim);
                    order(sim, (int) door[0] - 2, (int) door[1], "Back to dispatch's door");
                }
            }
            if (now == entered) stills(out, look, sim, hold, "01-company-enters-dispatch", cx, cy, whole);
            if (wave1 >= 0 && now == wave1 + SETTLE) stills(out, look, sim, hold, "02-wave-one", cx, cy, whole);
            if (wave2 >= 0 && now == wave2 + SETTLE) stills(out, look, sim, hold, "03-wave-two", cx, cy, whole);
            if (parked >= 0 && now == parked) stills(out, look, sim, hold, "04-technical-parked", cx, cy, whole);
            if (sentAtTruck < 0 && parked >= 0 && now == parked + 300) {
                sentAtTruck = now;
                stills(out, look, sim, hold, "05-ten-seconds-later", cx, cy, whole);
            }
        }
        stills(out, look, sim, hold, "06-end-" + (sim.isComplete() ? String.valueOf(sim.getWinner()).toLowerCase() : "unresolved"),
                cx, cy, whole);
    }

    private static void order(BattleSimulation sim, int x, int y, String reason) {
        for (Squad squad : sim.getSquads())
            if (squad.faction == Faction.MARINE && squad.aliveMembers > 0)
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id, x, y),
                        CommandAuthority.PLAYER_INTERVENTION, "depot-look", reason);
    }

    /** The whole map and dispatch at play zoom. */
    private static void stills(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                               DepotHoldSequence hold, String name, float cx, float cy, float whole) throws Exception {
        look.refocus(cx, cy, whole);
        out.add(new SnapshotArtifact(name + "-whole.png", look.render(sim, "", ReviewAnnotations.NONE)));
        look.refocus(hold.x() - 10f, hold.y() + .5f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
        out.add(new SnapshotArtifact(name + "-dispatch.png", look.render(sim, "", ReviewAnnotations.NONE)));
    }
}
