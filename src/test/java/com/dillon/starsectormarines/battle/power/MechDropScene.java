package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ConquestTrackLayout;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.OrbitalLift;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A commander calls a mech lance down on its drop zone: does it land claimed, and does that commander plan it?
 *
 * <p>The question is about ownership and delivery, so the world is as small as it can be: open ground, a marine
 * company at the beachhead, the far lone body each scene keeps so neither side is ever absent, and the production
 * {@link ConquestCommand} as the marine commander. The call is the scripted caller's: it is made through the same
 * {@link MechDropService#call} surface a commander's commit stage uses, with the commander's own mission claim, on a
 * fixed tick, so the loop does not depend on a muster that picks the moment.
 *
 * <ul>
 *   <li>{@code called}: one declared lance. The carrier should come down from orbit onto the drop zone, unload the
 *       whole lance, and the lance should be claimed by the commander and then carry an assignment it made.</li>
 *   <li>{@code control-no-allowance}: the same world, the same call, and no allowance declared. Nothing should fly
 *       and no mech should appear: the instrument's proof that the lance in the first loop came from the allowance.</li>
 * </ul>
 */
public final class MechDropScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;
    private static final int TICKS = 3000;
    /** The drop zone, in cells: the middle of where the company stands. */
    private static final int ZONE_X = 30;
    private static final int ZONE_Y = 50;
    private static final int CALL_TICK = 90;
    /** A lance is down within this many cells of the zone's cell it was aimed at. */
    private static final int ZONE_REACH = 14;
    private static final String RIFLE = "weapon.smg";
    private static final List<MechVariant> LANCE =
            List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.HOUND, MechVariant.SIROCCO);
    private static final String ISSUER = new ConquestCommand(
            new ConquestTrackLayout(TraversalAxis.WEST_TO_EAST, WIDTH, HEIGHT)).strategyId();

    @Override public String id() { return "mech-drop"; }

    @Override public String label() {
        return "Commander-called mech drop: a marine commander spends its mission allowance to land a lance on the "
                + "drop zone, claimed for itself and planned by it";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("called", "control-no-allowance")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim, MechDropService service, MechDropService.Outcome lateCall) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean allowed = loop.equals("called");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 195, 95, spec -> spec.moveSpeed(0f));
        builder.squad("company").faction(Faction.MARINE).size(6).at(ZONE_X, ZONE_Y).primary(RIFLE).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261013L);

        // The control declares nothing: the lance in the first loop must come from the allowance and nowhere else.
        MechDropService service = new MechDropService(allowed
                ? MechDropDeclaration.NONE.with(Faction.MARINE,
                        new MechDropAllowance(1, LANCE).calledAtTick(CALL_TICK))
                : MechDropDeclaration.NONE);
        sim.setMechDrops(service);

        ConquestTrackLayout tracks = new ConquestTrackLayout(TraversalAxis.WEST_TO_EAST, WIDTH, HEIGHT);
        ConquestCommand commander = new ConquestCommand(tracks);
        sim.setAutonomousCommander(Faction.MARINE, commander, ConquestCommandDisclosure.INSTANCE);
        service.installScriptedCaller(Faction.MARINE,
                SquadCommandClaim.mission(commander.strategyId(), "commander-called mech lance"),
                () -> new int[]{ZONE_X, ZONE_Y});

        Trace trace = new Trace();
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 15, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                        loop, tick, trace.caption()))
                .run(TICKS);
        // The control is refused when it asks, which is what the allowance is for.
        MechDropService.Outcome lateCall = allowed ? null : sim.callMechDrop(Faction.MARINE,
                SquadCommandClaim.mission(commander.strategyId(), "commander-called mech lance"), ZONE_X, ZONE_Y);
        return new Run(trace, sim, service, lateCall);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        MechDropService service = run.service();
        List<Verdict> verdicts = new ArrayList<>();
        if (loop.equals("called")) {
            boolean committed = service.drops().size() == 1 && service.drops().get(0).tick() >= CALL_TICK;
            verdicts.add(Verdict.of("call-committed-once", committed,
                    service.drops().size() + " call(s), first at tick "
                            + (service.drops().isEmpty() ? -1 : service.drops().get(0).tick())
                            + "; lances remaining " + service.lancesRemaining(Faction.MARINE)));
            boolean orbital = !OrbitalLift.enabled() || Math.abs(t.entryDistance - OrbitalLift.DESCENT_CELLS) < 0.5f;
            verdicts.add(Verdict.of("descends-from-orbit", t.entryDistance > 0f && orbital,
                    String.format(Locale.ROOT, "carrier entered %.1f cells from its landing cell (descent %.0f)",
                            t.entryDistance, OrbitalLift.DESCENT_CELLS)));
            verdicts.add(Verdict.of("lands-at-the-drop-zone", t.firstMechDistance >= 0f && t.firstMechDistance <= ZONE_REACH,
                    String.format(Locale.ROOT, "first mech down at tick %d, %.1f cells from the zone", t.firstMechTick,
                            t.firstMechDistance)));
            verdicts.add(Verdict.of("whole-lance-unloaded", t.mechsAtMost == LANCE.size() && t.oneSquad,
                    t.mechsAtMost + " mechs on the ground, one squad: " + t.oneSquad));
            verdicts.add(Verdict.of("claimed-for-the-commander", t.claimedByCommander,
                    "directive issuer " + t.firstIssuer + " at tick " + t.claimTick));
            verdicts.add(Verdict.of("planned-by-the-commander", t.assignedTick >= 0,
                    "assignment from " + t.assignmentIssuer + " ("
                            + t.assignmentReason + ") first at tick " + t.assignedTick));
            verdicts.add(Verdict.of("carrier-carries-the-commanders-claim", ISSUER.equals(t.carrierClaimIssuer),
                    "carrier claim issuer " + t.carrierClaimIssuer + "; no player support claim is installed"));
        } else {
            verdicts.add(Verdict.of("nothing-flies", service.drops().isEmpty() && !t.carrierSeen,
                    service.drops().size() + " calls, carrier seen " + t.carrierSeen));
            verdicts.add(Verdict.of("no-mech-appears", t.mechsAtMost == 0, t.mechsAtMost + " mechs"));
            verdicts.add(Verdict.of("a-call-is-refused", run.lateCall() == MechDropService.Outcome.NO_ALLOWANCE,
                    "asked after the run: " + run.lateCall()));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("callTick", service.drops().isEmpty() ? -1 : service.drops().get(0).tick());
        metrics.put("firstMechTick", t.firstMechTick);
        metrics.put("claimTick", t.claimTick);
        metrics.put("assignedTick", t.assignedTick);
        metrics.put("mechs", t.mechsAtMost);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        boolean carrierSeen;
        float entryDistance = -1f;
        int firstMechTick = -1;
        float firstMechDistance = -1f;
        int mechsAtMost;
        boolean oneSquad = true;
        int mechSquadId = Squad.NO_SQUAD;
        boolean claimedByCommander;
        int claimTick = -1;
        String firstIssuer = "none";
        int assignedTick = -1;
        String assignmentIssuer = "none";
        String assignmentReason = "none";
        String carrierClaimIssuer = "none";

        String caption() {
            if (assignedTick >= 0) return "lance planned by " + assignmentIssuer;
            if (firstMechTick >= 0) return "lance down, " + mechsAtMost + " mechs";
            return carrierSeen ? "carrier inbound" : "no lance";
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            long[] air = sim.getAirEntityIds();
            if (air.length > 0 && !carrierSeen) {
                carrierSeen = true;
                ShuttleMission mission = sim.world().mission(air[0]);
                entryDistance = (float) Math.hypot(mission.entryX - (mission.lzX), mission.entryY - (mission.lzY));
                SquadCommandClaim claim = mission.commandClaim;
                if (claim != null) carrierClaimIssuer = claim.issuer();
            }
            int mechs = 0;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE
                        || sim.identity().type(unit) != UnitType.HEAVY_MECH) continue;
                mechs++;
                int squadId = sim.squad().squadId(unit);
                if (mechSquadId == Squad.NO_SQUAD) mechSquadId = squadId;
                else if (squadId != mechSquadId) oneSquad = false;
                if (firstMechTick < 0) {
                    firstMechTick = tick;
                    firstMechDistance = (float) Math.hypot(sim.world().x(unit) - ZONE_X, sim.world().y(unit) - ZONE_Y);
                }
            }
            mechsAtMost = Math.max(mechsAtMost, mechs);
            if (mechSquadId == Squad.NO_SQUAD) return;
            CommandDirective directive = sim.getSquadCommandDirective(mechSquadId);
            if (directive == null) return;
            if (claimTick < 0 && directive.ownsSquad()) {
                claimTick = tick;
                firstIssuer = directive.issuer();
                claimedByCommander = ISSUER.equals(directive.issuer());
            }
            Squad squad = sim.getSquad(mechSquadId);
            if (assignedTick < 0 && directive.ownsAssignment() && squad != null && squad.assignedObjective != null
                    && ISSUER.equals(directive.issuer())) {
                assignedTick = tick;
                assignmentIssuer = directive.issuer();
                assignmentReason = directive.reason();
            }
        }
    }
}
