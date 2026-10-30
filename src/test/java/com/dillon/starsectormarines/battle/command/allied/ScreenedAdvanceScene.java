package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
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
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A screened advance across open ground to a rally point ({@code allied-command.md}, Screened advance):
 * the two elements move as one formation. Militia and allied armour start at the west edge; the rally point
 * is near the east edge; a handful of Runners wait two thirds of the way along to give the screen a contact.
 *
 * <p>Four loops. {@code infantry-screen}: three militia teams screen two tanks, an escort APC and a
 * Hound, the Runners ahead. {@code armour-screen}: two tanks and an APC screen the militia.
 * {@code flank-contact}: the infantry screen again with a dug-in picket off the north flank instead of the
 * Runners, so the fighting advance has to turn to meet it. {@code control}: the same force as {@code infantry-screen},
 * every element sent straight to the rally point, which is what an allied station order on its own does.
 *
 * <p>Verdicts assert the formation's mechanics, read off each element's body (its leading two thirds, as the
 * commander reads them, so a hull pulling out to withdraw is not the formation): it stepped off; while
 * advancing, the main was never further along the route than the screen's lead; once formed, the screen's
 * and the main's bodies never stood further apart than the formation's offset and a tolerance, engaged or
 * not; engaged, its progress along the route halted, it turned to face the threat (from the front the screen
 * already stands between; from the flank the formation faces round and an element wheels onto it), and its
 * elements fired; it picked the advance back up once the contact cleared (on the flank, by clearing rather
 * than at the cap); both elements reached the rally point within a few seconds of each other. In the control
 * every one of these is a reading. The time to the rally and the losses are readings.
 */
public final class ScreenedAdvanceScene implements BehaviorScene {

    static final int WIDTH = 170;
    static final int HEIGHT = 56;
    static final int START_X = 14, RALLY_X = 150, AXIS_Y = 28;
    /** Cells the main may stand ahead of the screen's lead before it counts as overtaking: a hull's half length. */
    static final float OVERTAKE_TOLERANCE = 2f;
    /** Cells past the formation's offset the screen may stand in front of the main: a squad's own spread. */
    static final float FORMATION_TOLERANCE = 6f;
    /** Ticks after step-off the formation is given to form before the gap is read. */
    static final int FORM_TICKS = 300;
    /** Cells from its line an element's centre must stand to be at the rally point. */
    static final float RALLY_REACH = 8f;
    /** Seconds apart the two elements may reach the rally point and still have arrived together. */
    static final float TOGETHER_SECONDS = 10f;
    static final int RUNNERS = 6;
    static final int RUNNERS_X = 108;
    /**
     * The flank loop's enemy: a picket of riflemen that rises from cover off the north edge of the route,
     * abreast of the formation, once its anchor is {@link #PICKET_AT} cells along, and holds where it is. A
     * picket in the open would be seen, and shot, from far ahead, which is a frontal contact; Runners charge
     * into melee too fast for anyone to turn to them. A picket firing from the flank is the contact a
     * formation should wheel to meet.
     */
    static final float PICKET_AT = 60f;
    static final int PICKET_Y = 6, PICKET = 4;
    /** Cells toward the threat the screen's body must stand from the main's for the formation to have turned to face it. */
    static final float FACED = 3f;
    /**
     * Cells an element's body must have moved toward a flank threat while engaged: what a squad or a hull shifts
     * in the nine seconds or so a picket lasts against the formation's guns.
     */
    static final float WHEELED = 2f;
    static final int TICKS = 12_000;
    /** Ticks the scene plays on once both have rallied, so the review shows them standing. */
    static final int AFTER_RALLY = 150;

    /** The advance being played, for the snapshot suite's annotations; set and cleared around each loop. */
    static volatile ScreenedAdvance current;

    @Override public String id() { return "screened-advance"; }

    @Override public String label() {
        return "Militia and allied armour move to a rally point in formation, one screening the other, against a direct-to-rally control";
    }

    @Override public int frameDelayMillis() { return 80; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("infantry-screen", "armour-screen", "flank-contact", "control")) reports.add(run(loop, frames));
        return reports;
    }

    static SceneWorld build() {
        return SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(20261023L).build();
    }

    /** The flank picket, four riflemen in a row abreast of {@code x}, armed as a militia team is and immobile. */
    private static void picket(BattleSimulation sim, int x) {
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        MarineLoadout[] kit = InfantryLoadoutRolls.defenderSquad(PICKET, UnitType.MILITIA, RiskLevel.MEDIUM, new Random(7L));
        for (int i = 0; i < PICKET; i++) {
            EntitySpec spec = new EntitySpec("Picket " + (i + 1), Faction.DEFENDER, UnitType.MILITIA, x - 3 + i * 2, PICKET_Y)
                    .squad(squad).moveSpeed(0f);
            kit[i].seedInto(spec);
            sim.spawn(spec);
        }
    }

    private SceneReport run(String loop, FrameSink frames) {
        boolean flank = loop.equals("flank-contact");
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
            armour.hull(allied.deploy(sim, VehicleStockFit.CONVOY_ESCORT, armourX - 2, AXIS_Y, 0f));
            if (!armourLeads) armour.mech(hound(sim, armourX - 6, AXIS_Y + 14));
            boolean picketUp = false;
            for (int i = 0; !flank && i < RUNNERS; i++) {
                sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                        RUNNERS_X + i % 3, AXIS_Y - 3 + (i / 3) * 6).role(UnitRole.SWARM_PRESSURE));
            }
            sim.getNavigationMesh().rebuild();

            AdvanceRoute route = AdvanceRoute.straight(START_X, AXIS_Y, RALLY_X, AXIS_Y);
            List<String> beats = new ArrayList<>();
            ScreenedAdvance advance = ScreenedAdvance.builder(loop, "screened-advance-scene", allied, route)
                    .screen(armourLeads ? armour : infantry)
                    .main(armourLeads ? infantry : armour)
                    .startWhen(ScreenedAdvance.Trigger.atSeconds(2f))
                    .beats(beats::add)
                    .build();
            boolean control = loop.equals("control");
            List<Integer> screenSquads = new ArrayList<>(advance.screen().squads);
            List<Long> screenHulls = new ArrayList<>(advance.screen().hulls), mainHulls = new ArrayList<>(advance.main().hulls);
            int screenAtStart = ScreenedAdvance.positions(sim, advance.screen()).size();
            int mainAtStart = ScreenedAdvance.positions(sim, advance.main()).size();
            current = advance;

            float worstLead = Float.NEGATIVE_INFINITY, worstGap = Float.NEGATIVE_INFINITY;
            int worstLeadTick = -1, worstGapTick = -1, steppedOff = -1, ralliedTick = -1;
            int screenArrived = -1, mainArrived = -1;
            float contactScreenAt = Float.NaN, contactMainAt = Float.NaN, contactWorst = Float.NEGATIVE_INFINITY;
            float worstApart = Float.NEGATIVE_INFINITY, bestFaced = Float.NEGATIVE_INFINITY, flankFacing = 0f;
            int roundsEngaged = 0, contactEnded = -1, lastContactEnd = -1;
            float reformLead = Float.NEGATIVE_INFINITY;
            float anchorAtContactEnd = Float.NaN;
            float[] screenAtContact = null, mainAtContact = null;
            float screenWheeled = 0f, mainWheeled = 0f;
            Map<Long, Integer> rounds = new HashMap<>();
            ScreenedAdvance.Phase lastPhase = ScreenedAdvance.Phase.WAITING;
            float screenLine = route.length(), mainLine = route.length() - ScreenedAdvance.DEFAULT_SCREEN_AHEAD;
            int ticks = 0;
            for (int tick = 0; tick < TICKS; tick++) {
                if (control && tick == 60) sendStraight(sim, allied, infantry, armour);
                if (flank && !picketUp && advance.anchor() >= PICKET_AT) {
                    picketUp = true;
                    picket(sim, START_X + Math.round(advance.anchor()));
                }
                if (!control) advance.advance(sim);
                allied.advance(sim);
                sim.advance(BattleSimulation.TICK_DT);
                ticks = tick + 1;
                boolean started = control ? tick >= 60 : advance.phase() != ScreenedAdvance.Phase.WAITING;
                if (started && steppedOff < 0) steppedOff = tick;
                if (started && tick % 10 == 0) {
                    float screenLead = advance.lead(sim, advance.screen()), mainLead = advance.lead(sim, advance.main());
                    // Engaged, the elements are free to shift and wheel, and they are given the formation's forming time
                    // to re-form after it; the screen leads along the route while advancing.
                    boolean advancing = advance.phase() != ScreenedAdvance.Phase.CONTACT;
                    boolean reforming = lastContactEnd >= 0 && tick - lastContactEnd < FORM_TICKS;
                    if (screenLead > Float.NEGATIVE_INFINITY && mainLead > Float.NEGATIVE_INFINITY) {
                        if (advancing && !reforming && mainLead - screenLead > worstLead) {
                            worstLead = mainLead - screenLead;
                            worstLeadTick = tick;
                        }
                        if (advancing && reforming) reformLead = Math.max(reformLead, mainLead - screenLead);
                    }
                    float[] sb = advance.body(sim, advance.screen()), mb = advance.body(sim, advance.main());
                    float screenCentre = sb == null ? 0f : sb[2], mainCentre = mb == null ? 0f : mb[2];
                    if (tick - steppedOff >= FORM_TICKS && sb != null && mb != null && screenCentre - mainCentre > worstGap) {
                        worstGap = screenCentre - mainCentre;
                        worstGapTick = tick;
                    }
                    float[] s2 = sb, m2 = mb;
                    if (tick - steppedOff >= FORM_TICKS && s2 != null && m2 != null)
                        worstApart = Math.max(worstApart, (float) Math.hypot(s2[0] - m2[0], s2[1] - m2[1]));
                    boolean engaged = advance.phase() == ScreenedAdvance.Phase.CONTACT;
                    roundsEngaged += fired(sim, advance, rounds, engaged);
                    float[] facing = advance.facing();
                    if (engaged && facing != null && s2 != null && m2 != null) {
                        // The wheel: how far the screen stands from the main toward the threat, and how far each
                        // element's body has moved toward it since the contact began.
                        if (screenAtContact == null) {
                            screenAtContact = new float[]{s2[0], s2[1]};
                            mainAtContact = new float[]{m2[0], m2[1]};
                        }
                        bestFaced = Math.max(bestFaced, (s2[0] - m2[0]) * facing[0] + (s2[1] - m2[1]) * facing[1]);
                        screenWheeled = Math.max(screenWheeled, (s2[0] - screenAtContact[0]) * facing[0] + (s2[1] - screenAtContact[1]) * facing[1]);
                        mainWheeled = Math.max(mainWheeled, (m2[0] - mainAtContact[0]) * facing[0] + (m2[1] - mainAtContact[1]) * facing[1]);
                        flankFacing = Math.max(flankFacing, -facing[1]);
                    }
                    if (lastPhase == ScreenedAdvance.Phase.CONTACT && !engaged) {
                        lastContactEnd = tick;
                        if (contactEnded < 0) {
                            contactEnded = tick;
                            anchorAtContactEnd = advance.anchor();
                        }
                    }
                    lastPhase = advance.phase();
                    if (screenArrived < 0 && Math.abs(screenCentre - screenLine) <= RALLY_REACH) screenArrived = tick;
                    if (mainArrived < 0 && Math.abs(mainCentre - mainLine) <= RALLY_REACH) mainArrived = tick;
                    if (advance.phase() == ScreenedAdvance.Phase.CONTACT) {
                        if (Float.isNaN(contactScreenAt)) {
                            contactScreenAt = screenCentre;
                            contactMainAt = mainCentre;
                        }
                        contactWorst = Math.max(contactWorst, Math.max(screenCentre - contactScreenAt, mainCentre - contactMainAt));
                    }
                }
                if (tick % 30 == 0) frames.frame(loop, sim, tick, caption(loop, advance, tick));
                boolean done = control ? screenArrived >= 0 && mainArrived >= 0 : advance.phase() == ScreenedAdvance.Phase.RALLIED;
                if (ralliedTick < 0 && done) ralliedTick = tick;
                if (ralliedTick >= 0 && tick - ralliedTick >= AFTER_RALLY) break;
            }
            current = null;

            float[] screenBody = advance.body(sim, advance.screen()), mainBody = advance.body(sim, advance.main());
            float screenCentre = screenBody == null ? 0f : screenBody[2], mainCentre = mainBody == null ? 0f : mainBody[2];
            boolean screenThere = Math.abs(screenCentre - screenLine) <= RALLY_REACH;
            boolean mainThere = advance.main().size() == 0 || Math.abs(mainCentre - mainLine) <= RALLY_REACH;
            float apart = screenArrived < 0 || mainArrived < 0 ? Float.POSITIVE_INFINITY
                    : Math.abs(screenArrived - mainArrived) * BattleSimulation.TICK_DT;
            boolean contacted = beats.stream().anyMatch(b -> b.contains(".contact_"));
            boolean cleared = beats.stream().anyMatch(b -> b.contains(".cleared_"));
            boolean pushed = beats.stream().anyMatch(b -> b.contains(".pushed_"));
            float offset = advance.screenAhead();
            boolean resumed = contactEnded >= 0 && advance.anchor() > anchorAtContactEnd;

            List<Verdict> verdicts = new ArrayList<>();
            Map<String, Number> metrics = new LinkedHashMap<>();
            String leadDetail = "main's lead at most " + fmt(worstLead) + " cells past the screen's lead (tick " + worstLeadTick + ")"
                    + (Float.isFinite(reformLead) ? "; re-forming after a contact, at most " + fmt(reformLead) + " (a reading)" : "");
            String gapDetail = "screen's centre at most " + fmt(worstGap) + " cells in front of the main's along the route (tick "
                    + worstGapTick + ") and at most " + fmt(worstApart) + " cells from it, engaged or not; the formation's offset is "
                    + fmt(offset) + " and the tolerance " + fmt(FORMATION_TOLERANCE);
            String togetherDetail = "screen at the rally on tick " + screenArrived + ", main on tick " + mainArrived
                    + (Float.isInfinite(apart) ? "" : ", " + fmt(apart) + " s apart");
            if (control) {
                verdicts.add(Verdict.of("control-plays", steppedOff >= 0,
                        "elements sent straight to the rally point on tick 60; " + leadDetail + "; " + gapDetail + "; " + togetherDetail));
            } else {
                verdicts.add(Verdict.of("steps-off", steppedOff >= 0, "stepped off on tick " + steppedOff + ": " + advance.log()));
                verdicts.add(Verdict.of("screen-leads", worstLead <= OVERTAKE_TOLERANCE, leadDetail));
                verdicts.add(Verdict.of("formation-holds", worstGap <= offset + FORMATION_TOLERANCE
                        && worstApart <= offset + FORMATION_TOLERANCE, gapDetail));
                verdicts.add(Verdict.of("halts-while-engaged", !contacted || contactWorst <= ScreenedAdvance.ARRIVE_REACH,
                        contacted ? "neither element's centre went on along the route more than " + fmt(contactWorst) + " cells while engaged"
                                : "no contact (a reading, not a failure)"));
                boolean wheeled = flank ? flankFacing > 0.7f && Math.max(screenWheeled, mainWheeled) >= WHEELED : bestFaced >= FACED;
                verdicts.add(Verdict.of("faces-the-threat", !contacted || wheeled,
                        contacted ? "engaged, the screen stood up to " + fmt(bestFaced) + " cells from the main toward the threat; "
                                + "toward it the screen moved " + fmt(screenWheeled) + " cells and the main " + fmt(mainWheeled)
                                + (flank ? "; the formation faced up to " + fmt(flankFacing) + " of the way round to the north flank" : "")
                                : "no contact (a reading, not a failure)"));
                verdicts.add(Verdict.of("fights-when-engaged", !contacted || roundsEngaged > 0,
                        contacted ? roundsEngaged + " rounds fired by the formation while engaged" : "no contact (a reading, not a failure)"));
                verdicts.add(Verdict.of("resumes-after-clear", !contacted || resumed && (!flank || cleared),
                        contacted ? "contact ended on tick " + contactEnded + (cleared ? " by clearing" : pushed ? " at the cap" : "")
                                + "; anchor " + fmt(anchorAtContactEnd) + " then, " + fmt(advance.anchor()) + " at the end"
                                : "no contact (a reading, not a failure)"));
                if (flank) verdicts.add(Verdict.of("flank-contact-happened", contacted, "the flank picket " + (contacted ? "" : "never ") + "engaged the formation"));
                verdicts.add(Verdict.of("arrive-together", ralliedTick >= 0 && screenThere && mainThere && apart <= TOGETHER_SECONDS,
                        "rallied " + (ralliedTick < 0 ? "never" : "on tick " + ralliedTick) + "; " + togetherDetail
                                + "; screen centre " + fmt(screenCentre) + ", main centre " + fmt(mainCentre) + " of " + fmt(route.length()) + " along"));
            }
            metrics.put("rallyTick", ralliedTick);
            metrics.put("mainAheadOfScreenMax", finite(worstLead));
            metrics.put("mainAheadOfScreenReforming", finite(reformLead));
            metrics.put("screenAheadOfMainMax", finite(worstGap));
            metrics.put("arrivalSecondsApart", finite(apart));
            metrics.put("elementsApartMax", finite(worstApart));
            metrics.put("screenTowardThreatMax", finite(bestFaced));
            metrics.put("screenWheeled", screenWheeled);
            metrics.put("mainWheeled", mainWheeled);
            metrics.put("roundsWhileEngaged", roundsEngaged);
            metrics.put("contactEndedTick", contactEnded);
            metrics.put("contacts", (int) beats.stream().filter(b -> b.contains(".contact_")).count());
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
        float behind = route.length() - ScreenedAdvance.DEFAULT_SCREEN_AHEAD;
        for (long hull : armour.hulls) {
            int[] at = route.slot(behind, i++, n, ScreenedAdvance.DEFAULT_SPACING);
            allied.station(sim, hull, at[0], at[1]);
        }
        for (long mech : armour.mechs) {
            int[] at = route.slot(behind, i++, n, ScreenedAdvance.DEFAULT_SPACING);
            allied.stationMech(sim, mech, at[0], at[1]);
        }
    }

    /** Rounds the formation's recorded combatants have fired since the last call, counted only while {@code engaged}. */
    private static int fired(BattleSimulation sim, ScreenedAdvance advance, Map<Long, Integer> seen, boolean engaged) {
        List<Long> ids = new ArrayList<>();
        for (ScreenedAdvance.Element element : List.of(advance.screen(), advance.main())) {
            ids.addAll(element.hulls);
            ids.addAll(element.mechs);
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                Squad squad = sim.squadOf(unit);
                if (squad != null && element.squads.contains(squad.id)) ids.add(unit);
            }
        }
        int total = 0;
        for (long id : ids) {
            // A hull's guns are not a combatant's row; the infantry's and the walkers' rounds are what is read.
            if (!sim.telemetry().isRecorded(id)) continue;
            int now = sim.telemetry().roundsFired(id);
            Integer before = seen.put(id, now);
            if (engaged && before != null && now > before) total += now - before;
        }
        return total;
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
        return loop + " / in formation / " + advance.phase().name().toLowerCase().replace('_', ' ') + " / " + tick;
    }

    /** A reading that never happened (no contact, never arrived) is reported as -1. */
    private static float finite(float v) {
        return Float.isFinite(v) ? v : -1f;
    }

    private static String fmt(float v) {
        return Float.isInfinite(v) || Float.isNaN(v) ? "-" : String.format("%.1f", v);
    }
}
