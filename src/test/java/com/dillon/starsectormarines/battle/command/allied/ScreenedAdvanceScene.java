package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A screened advance across open ground to a rally point ({@code allied-command.md}, Screened advance).
 * Militia and allied armour start at the west edge; the rally point is near the east edge; a handful of
 * Runners wait two thirds of the way along to give the screen a contact.
 *
 * <p>Three loops. {@code infantry-screen}: three militia teams screen two tanks, an escort APC and a
 * Hound. {@code armour-screen}: two tanks and an APC screen the militia. {@code control}: the same force
 * as {@code infantry-screen}, every element sent straight to the rally point, which is what an allied
 * station order on its own does.
 *
 * <p>Verdicts assert the advance's mechanics: it stepped off; the main was never further along the route
 * than the screen's lead; the bounds were cleared in order, every one; on contact the screen halted short
 * of its bound; both elements stood at the rally point at the end. In the control every one of these is a
 * reading. How far the armour got ahead, the time to the rally, and the losses are readings.
 */
public final class ScreenedAdvanceScene implements BehaviorScene {

    static final int WIDTH = 170;
    static final int HEIGHT = 56;
    static final int START_X = 14, RALLY_X = 150, AXIS_Y = 28;
    static final float BOUND = 34f;
    /** Cells the main may stand ahead of the screen's lead before it counts as overtaking: a hull's half length. */
    static final float OVERTAKE_TOLERANCE = 2f;
    /** Cells from its line an element's centre must stand to be at the rally point. */
    static final float RALLY_REACH = 10f;
    static final int RUNNERS = 6;
    static final int RUNNERS_X = 108;
    static final int TICKS = 12_000;
    /** Ticks the scene plays on once both have rallied, so the review shows them standing. */
    static final int AFTER_RALLY = 150;

    /** The advance being played, for the snapshot suite's annotations; set and cleared around each loop. */
    static volatile ScreenedAdvance current;

    @Override public String id() { return "screened-advance"; }

    @Override public String label() {
        return "Militia and allied armour bound to a rally point, one screening the other, against a direct-to-rally control";
    }

    @Override public int frameDelayMillis() { return 80; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("infantry-screen", "armour-screen", "control")) reports.add(run(loop, frames));
        return reports;
    }

    static SceneWorld build() {
        return SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(20261023L).build();
    }

    private SceneReport run(String loop, FrameSink frames) {
        SceneWorld scene = build();
        try (BattleSimulation sim = scene.sim()) {
            AlliedCommand allied = new AlliedCommand("screened-advance-scene");
            Random rng = new Random(23L);
            ScreenedAdvance.Element infantry = new ScreenedAdvance.Element();
            ScreenedAdvance.Element armour = new ScreenedAdvance.Element();
            boolean armourLeads = loop.equals("armour-screen");
            // The screen stands nearer the start line than the main, as a screen forms in front.
            int infantryX = armourLeads ? START_X - 6 : START_X, armourX = armourLeads ? START_X : START_X - 8;
            for (int t = 0; t < 3; t++) {
                int y = AXIS_Y - 10 + t * 10;
                List<int[]> cells = new ArrayList<>();
                for (int m = 0; m < ColonyMilitia.POST_SIZE; m++) cells.add(new int[]{infantryX - m % 2, y + m / 2 - 1});
                infantry.squad(ColonyMilitia.seatTeam(sim, cells, "Militia " + (t + 1), RiskLevel.MEDIUM, rng));
            }
            armour.hull(allied.deploy(sim, VehicleType.BREAKTHROUGH_TANK, armourX, AXIS_Y - 7, 0f));
            armour.hull(allied.deploy(sim, VehicleType.BREAKTHROUGH_TANK, armourX, AXIS_Y + 7, 0f));
            armour.hull(allied.deploy(sim, VehicleType.ESCORT_APC, armourX - 2, AXIS_Y, 0f));
            if (!armourLeads) armour.mech(hound(sim, armourX - 6, AXIS_Y + 14));
            for (int i = 0; i < RUNNERS; i++) {
                sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                        RUNNERS_X + i % 3, AXIS_Y - 3 + (i / 3) * 6).role(UnitRole.SWARM_PRESSURE));
            }
            sim.getNavigationMesh().rebuild();

            AdvanceRoute route = AdvanceRoute.straight(START_X, AXIS_Y, RALLY_X, AXIS_Y);
            List<String> beats = new ArrayList<>();
            ScreenedAdvance advance = ScreenedAdvance.builder(loop, "screened-advance-scene", allied, route)
                    .screen(armourLeads ? armour : infantry)
                    .main(armourLeads ? infantry : armour)
                    .boundEvery(BOUND)
                    .startWhen(ScreenedAdvance.Trigger.atSeconds(2f))
                    .beats(beats::add)
                    .build();
            boolean control = loop.equals("control");
            List<Integer> screenSquads = new ArrayList<>(advance.screen().squads);
            List<Long> screenHulls = new ArrayList<>(advance.screen().hulls), mainHulls = new ArrayList<>(advance.main().hulls);
            int screenAtStart = ScreenedAdvance.positions(sim, advance.screen()).size();
            int mainAtStart = ScreenedAdvance.positions(sim, advance.main()).size();
            current = advance;

            float worstLead = Float.NEGATIVE_INFINITY;
            int worstLeadTick = -1, steppedOff = -1, ralliedTick = -1;
            float contactHaltedAt = Float.NaN, contactWorst = Float.NEGATIVE_INFINITY;
            int ticks = 0;
            for (int tick = 0; tick < TICKS; tick++) {
                if (control && tick == 60) sendStraight(sim, allied, infantry, armour);
                if (!control) advance.advance(sim);
                allied.advance(sim);
                sim.advance(BattleSimulation.TICK_DT);
                ticks = tick + 1;
                boolean started = control ? tick >= 60 : advance.phase() != ScreenedAdvance.Phase.WAITING;
                if (started && steppedOff < 0) steppedOff = tick;
                if (started && tick % 10 == 0) {
                    float screenLead = advance.lead(sim, advance.screen()), mainLead = advance.lead(sim, advance.main());
                    if (screenLead > Float.NEGATIVE_INFINITY && mainLead > Float.NEGATIVE_INFINITY
                            && mainLead - screenLead > worstLead) {
                        worstLead = mainLead - screenLead;
                        worstLeadTick = tick;
                    }
                    if (advance.phase() == ScreenedAdvance.Phase.CONTACT) {
                        if (Float.isNaN(contactHaltedAt)) contactHaltedAt = advance.centre(sim, advance.screen());
                        contactWorst = Math.max(contactWorst, advance.centre(sim, advance.screen()) - contactHaltedAt);
                    }
                }
                if (tick % 30 == 0) frames.frame(loop, sim, tick, caption(loop, advance, tick));
                if (ralliedTick < 0 && (control ? bothAt(sim, advance, route.length()) : advance.phase() == ScreenedAdvance.Phase.RALLIED)) {
                    ralliedTick = tick;
                }
                if (ralliedTick >= 0 && tick - ralliedTick >= AFTER_RALLY) break;
            }
            current = null;

            float screenCentre = advance.centre(sim, advance.screen()), mainCentre = advance.centre(sim, advance.main());
            boolean screenThere = Math.abs(screenCentre - route.length()) <= RALLY_REACH;
            boolean mainThere = advance.main().size() == 0
                    || Math.abs(mainCentre - (route.length() - ScreenedAdvance.DEFAULT_STANDOFF)) <= RALLY_REACH;
            List<String> boundBeats = new ArrayList<>();
            for (String b : beats) if (b.contains(".bound_")) boundBeats.add(b);
            boolean inOrder = true;
            for (int i = 0; i < boundBeats.size(); i++) inOrder &= boundBeats.get(i).endsWith(".bound_" + (i + 1));
            boolean contacted = beats.stream().anyMatch(b -> b.contains(".contact_"));

            List<Verdict> verdicts = new ArrayList<>();
            Map<String, Number> metrics = new LinkedHashMap<>();
            String leadDetail = "main's lead at most " + fmt(worstLead) + " cells past the screen's lead (tick " + worstLeadTick + ")";
            if (control) {
                verdicts.add(Verdict.of("control-plays", steppedOff >= 0, "elements sent straight to the rally point on tick 60; " + leadDetail));
            } else {
                verdicts.add(Verdict.of("steps-off", steppedOff >= 0, "stepped off on tick " + steppedOff));
                verdicts.add(Verdict.of("screen-leads", worstLead <= OVERTAKE_TOLERANCE, leadDetail));
                verdicts.add(Verdict.of("bounds-in-order", inOrder && (ralliedTick < 0 || boundBeats.size() == advance.boundCount()),
                        boundBeats.size() + " of " + advance.boundCount() + " bounds cleared, " + (inOrder ? "in order" : "out of order")
                                + ": " + advance.log()));
                verdicts.add(Verdict.of("contact-halts-screen", !contacted || contactWorst <= ScreenedAdvance.ARRIVE_REACH,
                        contacted ? "screen centre moved at most " + fmt(contactWorst) + " cells on while halted on contact"
                                : "no contact (a reading, not a failure)"));
                verdicts.add(Verdict.of("both-rally", ralliedTick >= 0 && screenThere && mainThere,
                        "rallied " + (ralliedTick < 0 ? "never" : "on tick " + ralliedTick) + "; screen centre " + fmt(screenCentre)
                                + ", main centre " + fmt(mainCentre) + " of " + fmt(route.length()) + " along"));
            }
            metrics.put("rallyTick", ralliedTick);
            metrics.put("mainAheadOfScreenMax", worstLead);
            metrics.put("contacts", (int) beats.stream().filter(b -> b.contains(".contact_")).count());
            metrics.put("boundsCleared", boundBeats.size());
            metrics.put("screenLeft", ScreenedAdvance.positions(sim, advance.screen()).size());
            metrics.put("screenAtStart", screenAtStart);
            metrics.put("mainLeft", ScreenedAdvance.positions(sim, advance.main()).size());
            metrics.put("mainAtStart", mainAtStart);
            metrics.put("screenCentreAlong", screenCentre);
            metrics.put("mainCentreAlong", mainCentre);
            metrics.put("screenSquads", screenSquads.size());
            metrics.put("screenHulls", screenHulls.size());
            metrics.put("mainHulls", mainHulls.size());
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        } finally {
            current = null;
        }
    }

    /** What an allied commander with only station orders does: everything to the rally point at once. */
    private static void sendStraight(BattleSimulation sim, AlliedCommand allied, ScreenedAdvance.Element infantry,
                                     ScreenedAdvance.Element armour) {
        AdvanceRoute route = AdvanceRoute.straight(START_X, AXIS_Y, RALLY_X, AXIS_Y);
        int i = 0, n = infantry.size();
        for (int squad : infantry.squads) {
            int[] at = route.slot(route.length(), i++, n, ScreenedAdvance.DEFAULT_SPACING);
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, at[0], at[1]), CommandAuthority.GARRISON,
                    "screened-advance-scene", "straight to the rally point");
        }
        i = 0;
        n = armour.size();
        float behind = route.length() - ScreenedAdvance.DEFAULT_STANDOFF;
        for (long hull : armour.hulls) {
            int[] at = route.slot(behind, i++, n, ScreenedAdvance.DEFAULT_SPACING);
            allied.station(sim, hull, at[0], at[1]);
        }
        for (long mech : armour.mechs) {
            int[] at = route.slot(behind, i++, n, ScreenedAdvance.DEFAULT_SPACING);
            allied.stationMech(sim, mech, at[0], at[1]);
        }
    }

    private static boolean bothAt(BattleSimulation sim, ScreenedAdvance advance, float length) {
        return Math.abs(advance.centre(sim, advance.screen()) - length) <= RALLY_REACH
                && Math.abs(advance.centre(sim, advance.main()) - (length - ScreenedAdvance.DEFAULT_STANDOFF)) <= RALLY_REACH;
    }

    private static long hound(BattleSimulation sim, int x, int y) {
        int squad = sim.mintSquad(Faction.ALLY, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("Hound", Faction.ALLY, UnitType.HEAVY_MECH, x, y)
                .atPosition(x + .5f, y + .5f).mechVariant(MechVariant.HOUND).squad(squad));
        sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.HOUND, MechVariant.HOUND.defaultRole, "independent"));
        Squad s = sim.getSquad(squad);
        s.leaderId = mech;
        s.originalSize = 1;
        sim.claimSquadCommand(squad, CommandAuthority.GARRISON, "screened-advance-scene", "the main's walker");
        return mech;
    }

    private static String caption(String loop, ScreenedAdvance advance, int tick) {
        if (loop.equals("control")) return "control: straight to the rally point / " + tick;
        int shown = Math.min(advance.bound() + 1, advance.boundCount());
        return loop + " / bound " + shown + " of " + advance.boundCount() + " / " + advance.phase().name().toLowerCase().replace('_', ' ')
                + " / " + tick;
    }

    private static String fmt(float v) {
        return Float.isInfinite(v) ? "-" : String.format("%.1f", v);
    }
}
