package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.airsupport.AirStrikeCapability;
import com.dillon.starsectormarines.battle.airsupport.AirStrikeDirector;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Land, muster, assault: one battery fort in its production layout, a garrison inside it, and the attacker's squads
 * spread out along the way in, nearest the fort first. Each loop changes one thing, the commander's doctrine.
 *
 * <ul>
 *   <li>{@code control-trickle}: a mission that chooses {@code AssaultStyle.TRICKLE}. The first squad to see the
 *       fort discloses it, and the structure planner detaches one or two squads at a time to the bunker while the
 *       rest are on the lane. This is the control: what a single squad does against this fort is what a fort is built
 *       for.</li>
 *   <li>{@code plan} and the loops after it: the same world mustering. The squads that can be spared gather at a rally point,
 *       the company's readiness is judged against what the commander believes is in front of it, and it goes in as
 *       one body.</li>
 * </ul>
 *
 * <p>The readings that separate them are not the aspect outcome alone, since a fort may legitimately be too strong
 * for the company on offer. They are how many marines are alive when the first one reaches the fort, how many are in
 * its reach together, how many squads, and what the bunker's holder is at the end. The verdicts are the structure
 * of the phases (a company formed, mustered, was committed, and the phases were published); whether the fort fell is
 * reported, never asserted.
 *
 * <p>Both loops keep the fort's wall nests alive. The garrison is stationary inside the bunker: a patrolling garrison
 * would walk off and answer a different question.
 */
public final class AssaultMusterScene implements BehaviorScene {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 420;
    private static final int TICKS = 9000;
    /** The marines' strength per squad. Twelve squads of ten is a force of a hundred and twenty, four on each lane. */
    private static final int SQUAD_SIZE = 10;
    private static final int SQUADS = 12;
    /** Cells between one squad and the next along the way in, nearest the fort first. */
    private static final int SPACING = 16;
    private static final int FIRST_STANDOFF = 30;
    private static final int GARRISON = 6;
    /** "In reach of the fort" for the readings: well inside the bunker's approaches, outside the walls' nests. */
    private static final int RING = 45;
    private static final String RIFLE = "weapon.smg";
    /** The marines carry the field rifle (22 cells): the smg (14) cannot reach a nest (24), and the nests are what a base of fire is for. */
    private static final String MARINE_RIFLE = "weapon.field-rifle";

    private static final List<String> ALL_LOOPS = List.of("control-trickle", "plan", "plan-with-lance",
            "breach-cannon", "breach-charges", "plan-mortar", "plan-air", "plan-mortar-air", "combined-arms");
    private static final String CHARGE_KIT = "special.satchel-charge";
    private static final String ROCKET_KIT = "special.rocket-launcher";
    private static final String RADIO_KIT = "special.comms-radio";
    /** A nest dead within this many ticks of a tube aiming at it, or a sortie over it, is credited to that. */
    private static final int ATTRIBUTION_WINDOW_TICKS = 90;
    private static final float AIR_ATTRIBUTION_CELLS = 12f;
    /** Bombers the air loops declare: three in stock, replaced every forty seconds, directed only by the plan. */
    private static final AirPoolStock BOMBERS = new AirPoolStock(3, 3, 40f);

    private final List<String> loops;
    private final int frameFrom;
    private final int frameTo;
    private final int frameEvery;

    /** Every loop, frames (when a sink wants them) every 90 ticks across the run. */
    public AssaultMusterScene() {
        this(selectedLoops(), 0, TICKS, 90);
    }

    /** {@code -Dbattle.scene.assaultMuster.loops=a,b} plays only those loops, for a developer chasing one. */
    private static List<String> selectedLoops() {
        String chosen = System.getProperty("battle.scene.assaultMuster.loops");
        return chosen == null || chosen.isBlank() ? ALL_LOOPS : List.of(chosen.split(","));
    }

    /** Only the named loops, recording frames in a window of the run: a recording holds every frame in memory. */
    public AssaultMusterScene(List<String> loops, int frameFrom, int frameTo, int frameEvery) {
        this.loops = List.copyOf(loops);
        this.frameFrom = frameFrom;
        this.frameTo = frameTo;
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "assault-muster"; }

    @Override public String label() {
        return "Assault muster: squads that reach a fort one at a time are cut down by it; the same squads gathered "
                + "into a company go in together";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : loops) {
            boolean restoreStanding = ConquestCommand.standingFireMissionsEnabled();
            // The loops that judge the plan's own fire missions leave the squads' standing ones out, which would clear the works first.
            ConquestCommand.setStandingFireMissionsEnabled(!(loop.equals("plan-mortar") || loop.equals("plan-mortar-air") || loop.equals("combined-arms")));
            try {
                reports.add(report(loop, playOne(loop, window(frames))));
            } finally {
                ConquestCommand.setStandingFireMissionsEnabled(restoreStanding);
            }
        }
        return reports;
    }

    /** Passes on only the frames inside the recording window. */
    private FrameSink window(FrameSink sink) {
        if (sink == FrameSink.NONE) return sink;
        return new FrameSink() {
            @Override public void frame(String loopId, BattleSimulation sim, int tick, String caption) {
                if (tick >= frameFrom && tick <= frameTo) sink.frame(loopId, sim, tick, caption);
            }

            @Override public void advanced(String loopId, BattleSimulation sim, int tick) {
                sink.advanced(loopId, sim, tick);
            }
        };
    }

    private record Run(Trace trace, BattleSimulation sim, Fort fort) {}

    private record FortGround(NavigationGrid grid, CellTopology topology,
                              List<com.dillon.starsectormarines.battle.turret.DefensePost> posts,
                              CompoundLayout.Placement at) {
        int[] cell(int lx, int ly) {
            return at.cell(lx, ly);
        }
    }

    private static FortGround stamp() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 1)));
        FortStage stage = new FortStage();
        stage.run(ctx);
        return new FortGround(grid, topology, List.copyOf(ctx.defensePosts), stage.placed().get(0).placement());
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        FortGround terrain = stamp();
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .missionCompletion(false);
        FortPlan.Gate gate = FortPlan.of(FortKind.BATTERY_FORT).gates().get(0);
        List<String> keys = new ArrayList<>();
        boolean lance = loop.equals("plan-with-lance");
        // The lance loop has a short force (five squads of six), so the company is short of what the fort is believed
        // to take and the commander asks for a lance.
        int squads = SQUADS;
        int squadSize = SQUAD_SIZE;
        for (int i = 0; i < squads; i++) {
            int standoff = i == 0 ? FIRST_STANDOFF : FIRST_STANDOFF + 40 + SPACING * i;
            int[] at = clamp(terrain.cell(gate.mouthX() + gate.outX() * standoff, gate.mouthY() + gate.outY() * standoff));
            String key = "marines-" + i;
            keys.add(key);
            var spec = builder.squad(key).faction(Faction.MARINE).size(squadSize).at(at[0], at[1]).primary(MARINE_RIFLE);
            // The capability loops differ only in the kit the same company carries.
            // Rockets answer the nests; the charges are carried by other squads, so a close breach is the plan the company can staff.
            if (loop.equals("breach-charges") && i % 3 == 1) spec.special(CHARGE_KIT, 4);
            if (loop.equals("breach-charges") && i % 3 == 2) spec.special(ROCKET_KIT, 4, 3);
            issueFireSupport(loop, i, spec);
            spec.done();
        }
        if (loop.equals("breach-cannon")) {
            int[] at = clamp(terrain.cell(gate.mouthX() + gate.outX() * (FIRST_STANDOFF + 20),
                    gate.mouthY() + gate.outY() * (FIRST_STANDOFF + 20)));
            builder.squad("cannon-mech").faction(Faction.MARINE).size(1).at(at[0], at[1])
                    .mech(MechVariant.SIROCCO, MechRole.LR_SUPPORT).done();
            keys.add("cannon-mech");
        }
        int[] core = BatteryFort.coreCentre(FortAspect.FIRE_CONTROL);
        int[] garrisonAt = terrain.cell(core[0], core[1] + 2);
        builder.squad("garrison").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(GARRISON)
                .at(garrisonAt[0], garrisonAt[1]).primary(RIFLE).stationary().done();
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, HEIGHT - 5);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 145, 5);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        if (lance) {
            sim.setMechDrops(new MechDropService(MechDropDeclaration.NONE.with(Faction.MARINE,
                    new MechDropAllowance(1, List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.HOUND,
                            MechVariant.SIROCCO)))));
        }
        if (hasAir(loop)) {
            sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE,
                    AirPoolAllowance.of(BOMBERS, null, /*aiDirected*/ false))));
        }
        Fort fort = sim.forts().forts().get(0);
        ConquestCommand command = new ConquestCommand(new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT))
                .withAssaultStyle(loop.equals("control-trickle") ? AssaultStyle.TRICKLE : AssaultStyle.MUSTER);
        sim.setAutonomousCommander(Faction.MARINE, command, ConquestCommandDisclosure.INSTANCE);
        Trace trace = new Trace(fort, keys, world);
        trace.command = command;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                        loop, tick, trace.caption()))
                .run(TICKS);
        return new Run(trace, sim, fort);
    }

    /**
     * The fire-support loops differ from the plan loop only in the kit the same twelve squads carry: field mortars (two tubes to
     * a section), forward-observer radios (one to a squad) with a bomber pool declared for the side, or both; the combined-arms loop
     * adds the charges and the rockets that cut the wall and answer the nests as well.
     */
    private static void issueFireSupport(String loop, int i, SceneBuilder.SquadSpec spec) {
        switch (loop) {
            case "plan-mortar" -> {
                if (i % 3 == 1) spec.special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 2);
            }
            case "plan-air" -> {
                spec.special(RADIO_KIT, 1);
            }
            case "plan-mortar-air" -> {
                if (i % 4 == 1) spec.special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 2);
                if (i % 4 == 2) spec.special(RADIO_KIT, 1);
            }
            case "combined-arms" -> {
                switch (i % 4) {
                    case 0 -> spec.special(RADIO_KIT, 1);
                    case 1 -> spec.special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 2);
                    case 2 -> spec.special(CHARGE_KIT, 4);
                    default -> spec.special(CHARGE_KIT, 4);
                }
            }
            default -> { }
        }
    }

    private static boolean hasAir(String loop) {
        return loop.equals("plan-air") || loop.equals("plan-mortar-air") || loop.equals("combined-arms");
    }

    private static int[] clamp(int[] cell) {
        return new int[] {Math.max(4, Math.min(WIDTH - 5, cell[0])), Math.max(4, Math.min(HEIGHT - 5, cell[1]))};
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        boolean muster = !loop.equals("control-trickle");
        List<Verdict> verdicts = new ArrayList<>();
        if (muster) {
            verdicts.add(Verdict.of("a-company-mustered-first", t.firstMusterTick >= 0 && t.firstAssaultTick > t.firstMusterTick,
                    "muster published at tick " + t.firstMusterTick + ", assault at tick " + t.firstAssaultTick));
            verdicts.add(Verdict.of("the-foothold-was-held", t.footholdTicks > 0,
                    t.footholdTicks + " ticks with a squad published holding the drop zone"));
            verdicts.add(Verdict.of("it-went-in-as-one-body",
                    t.peakSquadsInReach >= Math.min(3, t.companySquads),
                    "peak " + t.peakSquadsInReach + " squads in reach together, company of " + t.companySquads
                            + " squads committed"));
            verdicts.add(Verdict.of("no-lone-squad-was-detached-to-the-fort", t.soloStructureOrders == 0,
                    t.soloStructureOrders + " DEFENCE_STRUCTURE_* directives"));
        }
        if (loop.startsWith("plan")) {
            verdicts.add(Verdict.of("the-plan-positioned-a-base-of-fire", t.stepTicks.containsKey("POSITION") && t.stepTicks.containsKey("SUPPRESS"),
                    "plan steps first seen at " + t.stepTicks));
            verdicts.add(Verdict.of("the-plan-held-the-assault-until-the-nests-were-dealt-with",
                    !t.stepTicks.containsKey("ENTRY") || t.stepTicks.get("ENTRY") > t.stepTicks.get("SUPPRESS"),
                    "SUPPRESS at " + t.stepTicks.get("SUPPRESS") + ", ENTRY at " + t.stepTicks.get("ENTRY")));
        }
        if (loop.startsWith("breach")) {
            verdicts.add(Verdict.of("the-company-took-the-breach-it-could-staff",
                    t.planVariant.startsWith("breach"), "plan variant " + t.planVariant + ", shortfall: " + t.planShortfall));
            verdicts.add(Verdict.of("the-breachers-fired-at-the-wall", t.roundsAtWalls > 0,
                    t.roundsAtWalls + " rounds, charges and cuts spent on the ordered wall"));
            verdicts.add(Verdict.of("the-wall-came-down", t.wallFirstOpenTick >= 0,
                    "wall cells " + t.wallStart + " at start, " + t.wallStanding + " standing at end; first opened at tick "
                            + t.wallFirstOpenTick + ", cut through at " + t.wallCutTick));
        }
        // The combined-arms loop reports the tubes as readings only: its mission nest may be dead before the section arrives.
        if (loop.equals("plan-mortar") || loop.equals("plan-mortar-air")) {
            verdicts.add(Verdict.of("the-tubes-were-set-down", t.tubeRounds.size() > 0,
                    t.tubeRounds.size() + " tubes were served, " + t.mortarRounds() + " rounds fired in all"));
            verdicts.add(Verdict.of("the-tubes-fired-on-the-assigned-mission", t.mortarMissionRounds() > 0,
                    t.mortarMissionRounds() + " of " + t.mortarRounds() + " rounds fired at a body on the mission"));
            verdicts.add(Verdict.of("the-tubes-stood-out-of-the-nests-reach", t.closestTubeToNest >= 24f,
                    "closest served tube to a live nest: " + Math.round(t.closestTubeToNest) + " cells (a nest reaches 24)"));
        }
        if (loop.equals("plan-mortar") || loop.equals("plan-mortar-air") || loop.equals("combined-arms")) {
            // Two tubes in a company of five do not staff a base of fire alone; the reading is that they counted for something.
            verdicts.add(Verdict.of("the-tubes-counted-toward-the-base-of-fire", !t.planShortfall.contains("base_of_fire needs ")
                            || !t.planShortfall.matches("(?s).*base_of_fire needs [^;]*: (\\d+) of \\1 short.*"),
                    "shortfall at suppression: " + (t.planShortfall.isEmpty() ? "none" : t.planShortfall)));
        }
        if (hasAir(loop)) {
            int asked = t.command == null ? 0 : t.command.strikeOutcomes().values().stream().mapToInt(Integer::intValue).sum();
            int launched = t.poolEvents(AirPoolService.EventType.LAUNCHED);
            verdicts.add(Verdict.of("the-air-was-asked-or-the-ceiling-held",
                    asked > 0 || t.nestRiskMin > AirStrikeDirector.RISK_CEILING,
                    asked + " strikes asked for " + (t.command == null ? "" : t.command.strikeOutcomes()) + ", " + launched
                            + " launched; believed risk over the nests at its lowest " + Math.round(t.nestRiskMin * 100) / 100f
                            + " against a ceiling of " + AirStrikeDirector.RISK_CEILING));
        }
        if (loop.equals("plan-with-lance")) {
            verdicts.add(Verdict.of("a-short-company-calls-a-lance", run.sim().mechDrops().drops().size() == 1
                    && run.sim().mechDrops().drops().get(0).issuer().equals("conquest-attacker"),
                    run.sim().mechDrops().drops().size() + " call(s) in the ledger"));
            verdicts.add(Verdict.of("the-landed-lance-joins-the-company", t.lanceInCompanyTick >= 0,
                    "a machine squad first in a company at tick " + t.lanceInCompanyTick));
        }
        if (!muster) {
            verdicts.add(Verdict.of("no-company-forms", t.firstMusterTick < 0 && t.firstAssaultTick < 0,
                    "company stages published: muster " + t.firstMusterTick + ", assault " + t.firstAssaultTick));
            verdicts.add(Verdict.of("squads-are-detached-one-or-two-at-a-time", t.soloStructureOrders > 0,
                    t.soloStructureOrders + " DEFENCE_STRUCTURE_* directives"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fortDisclosedTick", t.disclosedTick);
        metrics.put("firstContactTick", t.firstContactTick);
        metrics.put("marinesAliveAtContact", t.aliveAtContact);
        metrics.put("squadsAliveAtContact", t.squadsAliveAtContact);
        metrics.put("peakMarinesInReach", t.peakMarinesInReach);
        metrics.put("peakSquadsInReach", t.peakSquadsInReach);
        metrics.put("marinesInReachAt150TicksAfterContact", t.inReachAfterContact);
        metrics.put("firstMusterTick", t.firstMusterTick);
        metrics.put("firstAssaultTick", t.firstAssaultTick);
        metrics.put("companySquads", t.companySquads);
        metrics.put("soloStructureOrders", t.soloStructureOrders);
        metrics.put("marinesLost", t.startMarines - t.aliveNow);
        metrics.put("marinesAliveAtEnd", t.aliveNow);
        metrics.put("garrisonAliveAtEnd", t.garrisonAlive);
        metrics.put("bunkerFlipTick", t.flipTick);
        metrics.put("bunkerHeldByMarinesAtEnd", run.fort().battery().owner() == Faction.MARINE ? 1 : 0);
        metrics.put("footholdTicks", t.footholdTicks);
        metrics.put("nestsKilled", t.nestsKilled);
        metrics.put("nestsTotal", run.fort().nestIds().size());
        metrics.put("nestWearEquivalentNests", Math.round(t.nestWear * 10) / 10f);
        metrics.put("closestMarineToANest", Math.round(t.closestToNest));
        metrics.put("firstNestKilledTick", t.firstNestKilledTick);
        metrics.put("gateReachedTick", t.gateReachedTick);
        for (Map.Entry<String, Integer> step : t.stepTicks.entrySet()) metrics.put("step" + step.getKey() + "Tick", step.getValue());
        if (run.fort().frame() != null) {
            int index = 0;
            for (FortAssault.Way way : FortAssault.Works.of(run.fort().frame()).ways) {
                metrics.put("way" + index++ + "_" + way.variant().name().toLowerCase(Locale.ROOT) + "_nestsCovering",
                        way.covering().size());
                if (way.variant() == Variant.BREACH) {
                    metrics.put("breachMouthX", way.mouthX());
                    metrics.put("breachMouthY", way.mouthY());
                }
            }
        }
        metrics.put("wallCellsAtStart", t.wallStart);
        metrics.put("wallCellsStandingAtEnd", t.wallStanding);
        metrics.put("wallFirstOpenedTick", t.wallFirstOpenTick);
        metrics.put("wallCutThroughTick", t.wallCutTick);
        metrics.put("shellsAndChargesAtWalls", t.roundsAtWalls);
        metrics.put("lanceInCompanyTick", t.lanceInCompanyTick);
        metrics.put("nestsKilledByMortar", t.killedByMortar);
        metrics.put("nestsKilledByAir", t.killedByAir);
        metrics.put("nestsKilledByGround", t.killedByGround);
        metrics.put("tubesServed", t.tubeRounds.size());
        metrics.put("mortarRounds", t.mortarRounds());
        metrics.put("mortarRoundsOnMission", t.mortarMissionRounds());
        metrics.put("mortarSectionsOpened", run.sim().fieldMortars().sections().opened());
        metrics.put("mortarSectionsExpired", run.sim().fieldMortars().sections().expired());
        metrics.put("mortarSectionsAbandoned", run.sim().fieldMortars().sections().abandoned());
        metrics.put("mortarSectionsUnderFire", run.sim().fieldMortars().sections().underFire());
        metrics.put("mortarSectionSecondsHeld", Math.round(run.sim().fieldMortars().sections().secondsHeld()));
        metrics.put("mortarSectionRounds", run.sim().fieldMortars().sections().roundsFired());
        metrics.put("mortarSectionMissionRounds", run.sim().fieldMortars().sections().missionRoundsFired());
        metrics.put("tubeSetDowns", run.sim().fieldMortars().setUps());
        metrics.put("closestServedTubeToALiveNest", t.closestTubeToNest == Float.MAX_VALUE ? -1 : Math.round(t.closestTubeToNest));
        metrics.put("strikesAsked", t.command == null ? 0 : t.command.strikeOutcomes().values().stream().mapToInt(Integer::intValue).sum());
        for (AirPoolService.Outcome outcome : AirPoolService.Outcome.values()) {
            metrics.put("strikes" + outcome.name(), t.command == null ? 0 : t.command.strikeOutcomes().getOrDefault(outcome, 0));
        }
        metrics.put("airframesLaunched", t.poolEvents(AirPoolService.EventType.LAUNCHED));
        metrics.put("airframesLost", t.poolEvents(AirPoolService.EventType.LOST));
        metrics.put("airframesDrivenOff", t.poolEvents(AirPoolService.EventType.ABORTED));
        metrics.put("callsDropped", t.poolEvents(AirPoolService.EventType.CALL_DROPPED));
        metrics.put("nestRiskMaxBelieved", Math.round(t.nestRiskMax * 100) / 100f);
        metrics.put("nestRiskMinBelieved", t.nestRiskMin == Float.MAX_VALUE ? -1 : Math.round(t.nestRiskMin * 100) / 100f);
        metrics.put("lanceCalls", run.sim().mechDrops().drops().size());
        metrics.put("fortX", (int) run.fort().battery().x());
        metrics.put("fortY", (int) run.fort().battery().y());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final Fort fort;
        private final List<String> keys;
        private final SceneWorld world;
        private final long[] garrison;
        int startMarines;
        int aliveNow;
        int garrisonAlive;
        int disclosedTick = -1;
        int firstContactTick = -1;
        int aliveAtContact = -1;
        int squadsAliveAtContact = -1;
        int peakMarinesInReach;
        int peakSquadsInReach;
        int inReachAfterContact = -1;
        int firstMusterTick = -1;
        int firstAssaultTick = -1;
        int companySquads;
        int soloStructureOrders;
        int flipTick = -1;
        int footholdTicks;
        int lanceInCompanyTick = -1;
        int nestsKilled;
        float nestWear;
        float closestToNest = Float.MAX_VALUE;
        int firstNestKilledTick = -1;
        int gateReachedTick = -1;
        final Map<String, Integer> stepTicks = new LinkedHashMap<>();
        private String caption = "";
        /** Wall cells of the breach way (mouth inward) that stood at the start, and how many still do. */
        int wallStart = -1;
        int wallStanding = -1;
        int wallFirstOpenTick = -1;
        int wallCutTick = -1;
        int roundsAtWalls;
        String planVariant = "";
        String planShortfall = "";
        String breachSquadKit = "";
        ConquestCommand command;
        /** Nest id to the last tick a served tube had it as its mission target, and a pool sortie was over it. */
        final Map<Long, Integer> lastMortarAim = new HashMap<>();
        final Map<Long, Integer> lastAirNear = new HashMap<>();
        final Set<Long> nestsSeenDead = new HashSet<>();
        int killedByMortar;
        int killedByAir;
        int killedByGround;
        /** Rounds fired and fired on a mission, by gunner. */
        final Map<Long, int[]> tubeRounds = new HashMap<>();
        float closestTubeToNest = Float.MAX_VALUE;
        float nestRiskMax;
        float nestRiskMin = Float.MAX_VALUE;
        private final Map<AirPoolService.EventType, Integer> poolTally = new EnumMap<>(AirPoolService.EventType.class);
        private int poolSeen;

        int poolEvents(AirPoolService.EventType type) {
            return poolTally.getOrDefault(type, 0);
        }

        int mortarRounds() {
            int n = 0;
            for (int[] r : tubeRounds.values()) n += r[0];
            return n;
        }

        int mortarMissionRounds() {
            int n = 0;
            for (int[] r : tubeRounds.values()) n += r[1];
            return n;
        }

        Trace(Fort fort, List<String> keys, SceneWorld world) {
            this.fort = fort;
            this.keys = keys;
            this.world = world;
            this.garrison = world.members("garrison");
            for (String key : keys) startMarines += world.members(key).length;
        }

        String caption() {
            return caption;
        }

        /** The fire support readings: which tubes are up and on what, where the bombers are, who killed which nest. */
        private void observeFireSupport(BattleSimulation sim, int tick) {
            World w = sim.world();
            for (FieldMortarService.Tube tube : sim.fieldMortars().tubes()) {
                // Only the tubes set down once the plan has been committed are the plan's: an earlier tube was a squad's own.
                boolean planned = tubeRounds.containsKey(tube.carrierId) || (firstAssaultTick >= 0 && tick >= firstAssaultTick);
                if (!planned) continue;
                tubeRounds.put(tube.carrierId, new int[] {tube.roundsFired(), tube.missionRoundsFired()});
                if (tube.missionTarget() != 0L) lastMortarAim.put(tube.missionTarget(), tick);
                if (tube.phase() != FieldMortarService.Phase.SERVING) continue;
                for (long nest : fort.nestIds()) {
                    if (!w.isAlive(nest)) continue;
                    closestTubeToNest = Math.min(closestTubeToNest, (float) Math.hypot(tube.cellX() + 0.5f - w.x(nest), tube.cellY() + 0.5f - w.y(nest)));
                }
            }
            for (long craft : sim.getAirEntityIds()) {
                ShuttleMission mission = w.mission(craft);
                if (mission == null || !mission.poolSortie) continue;
                for (long nest : fort.nestIds()) {
                    if (Math.hypot(w.x(craft) - w.x(nest), w.y(craft) - w.y(nest)) <= AIR_ATTRIBUTION_CELLS) lastAirNear.put(nest, tick);
                }
            }
            for (long nest : fort.nestIds()) {
                if (w.isAlive(nest) || !nestsSeenDead.add(nest)) continue;
                if (tick - lastMortarAim.getOrDefault(nest, -100000) <= ATTRIBUTION_WINDOW_TICKS) killedByMortar++;
                else if (tick - lastAirNear.getOrDefault(nest, -100000) <= ATTRIBUTION_WINDOW_TICKS) killedByAir++;
                else killedByGround++;
            }
            List<AirPoolService.Event> events = sim.airPool().events();
            while (poolSeen < events.size()) poolTally.merge(events.get(poolSeen++).type(), 1, Integer::sum);
            if (tick % 150 == 0 && sim.airPool().hasPool(Faction.MARINE)) {
                for (long nest : fort.nestIds()) {
                    if (!w.isAlive(nest)) continue;
                    float risk = AirStrikeCapability.aaRisk(sim, Faction.MARINE, w.x(nest), w.y(nest));
                    nestRiskMax = Math.max(nestRiskMax, risk);
                    if (risk > 0f) nestRiskMin = Math.min(nestRiskMin, risk);
                }
            }
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            observeFireSupport(sim, tick);
            long core = fort.place(FortAspect.FIRE_CONTROL).coreId();
            float bx = sim.world().x(core);
            float by = sim.world().y(core);
            int alive = 0;
            int marinesInReach = 0;
            int squadsInReach = 0;
            int squadsAlive = 0;
            for (String key : keys) {
                int living = 0;
                int near = 0;
                for (long id : world.members(key)) {
                    if (!sim.world().isAlive(id)) continue;
                    living++;
                    if (Math.hypot(sim.world().x(id) - bx, sim.world().y(id) - by) <= RING) near++;
                }
                alive += living;
                if (living > 0) squadsAlive++;
                marinesInReach += near;
                if (near > 0) squadsInReach++;
            }
            aliveNow = alive;
            int g = 0;
            for (long id : garrison) if (sim.world().isAlive(id)) g++;
            garrisonAlive = g;
            if (firstContactTick < 0 && marinesInReach > 0) {
                firstContactTick = tick;
                aliveAtContact = alive;
                squadsAliveAtContact = squadsAlive;
            }
            if (firstContactTick >= 0 && tick == firstContactTick + 150) inReachAfterContact = marinesInReach;
            peakMarinesInReach = Math.max(peakMarinesInReach, marinesInReach);
            peakSquadsInReach = Math.max(peakSquadsInReach, squadsInReach);
            if (flipTick < 0 && fort.battery().owner() == Faction.MARINE) flipTick = tick;
            if (disclosedTick < 0 && !sim.defenceStructures().isEmpty() && sim.structureSighted(
                    sim.defenceStructures().get(0).seenCellX(), sim.defenceStructures().get(0).seenCellY())) {
                disclosedTick = tick;
            }
            int killed = 0;
            for (long nest : fort.nestIds()) if (!sim.world().isAlive(nest)) killed++;
            nestsKilled = killed;
            float worn = 0f;
            float closest = Float.MAX_VALUE;
            for (long nest : fort.nestIds()) {
                if (!sim.world().isAlive(nest)) { worn += 1f; continue; }
                float full = sim.world().maxHp(nest) + (sim.world().hasArmor(nest) ? sim.world().maxArmor(nest) : 0f);
                float left = sim.world().hp(nest) + (sim.world().hasArmor(nest) ? sim.world().armor(nest) : 0f);
                if (full > 0) worn += 1f - left / full;
                for (String key : keys) for (long id : world.members(key)) {
                    if (sim.world().isAlive(id)) closest = Math.min(closest, (float) Math.hypot(sim.world().x(id) - sim.world().x(nest), sim.world().y(id) - sim.world().y(nest)));
                }
            }
            nestWear = worn;
            closestToNest = Math.min(closestToNest, closest);
            if (firstNestKilledTick < 0 && killed > 0) firstNestKilledTick = tick;
            if (gateReachedTick < 0 && fort.frame() != null) {
                for (FortAssault.Way way : FortAssault.Works.of(fort.frame()).ways) {
                    if (way.variant() == Variant.BREACH) continue;
                    for (String key : keys) {
                        for (long id : world.members(key)) {
                            if (sim.world().isAlive(id) && Math.hypot(sim.world().x(id) - way.entryX(), sim.world().y(id) - way.entryY()) <= 6) {
                                gateReachedTick = tick;
                            }
                        }
                    }
                }
            }
            if (fort.frame() != null) {
                for (FortAssault.Way way : FortAssault.Works.of(fort.frame()).ways) {
                    if (way.variant() != Variant.BREACH) continue;
                    int standing = 0;
                    for (int k = 1; k <= 8; k++) {
                        int x = way.mouthX() - way.outX() * k;
                        int y = way.mouthY() - way.outY() * k;
                        if (x == way.entryX() && y == way.entryY()) break;
                        if (sim.getGrid().inBounds(x, y) && !sim.getGrid().isWalkable(x, y)) standing++;
                    }
                    if (wallStart < 0) wallStart = standing;
                    wallStanding = standing;
                    if (wallFirstOpenTick < 0 && standing < wallStart) wallFirstOpenTick = tick;
                    if (wallCutTick < 0 && standing == 0) wallCutTick = tick;
                }
            }
            roundsAtWalls = sim.wallBreach().roundsAtWalls();
            var snapshot = sim.getCommanderSnapshot(Faction.MARINE);
            if (snapshot != null && snapshot.detail() instanceof ConquestFrontSnapshot front) {
                boolean foothold = false;
                for (ConquestFrontSnapshot.CompanyState company : front.companies()) {
                    if (company.stage().equals("FOOTHOLD")) foothold = true;
                    if (lanceInCompanyTick < 0 && company.objectiveId() >= 0) {
                        for (int squadId : company.squadIds()) {
                            long lead = sim.squadMemberAt(squadId, 0);
                            if (lead != 0L && sim.world().hasMechLoadout(lead)) lanceInCompanyTick = tick;
                        }
                    }
                    if (company.objectiveId() >= 0 && !company.step().isEmpty()) stepTicks.putIfAbsent(company.step(), tick);
                    if (company.step().equals("SUPPRESS") && planVariant.isEmpty()) { planVariant = company.variant(); planShortfall = String.join("; ", company.shortfall()); }
                    if (company.stage().equals("MUSTER") && firstMusterTick < 0) firstMusterTick = tick;
                    if (company.stage().equals("ASSAULT")) {
                        if (firstAssaultTick < 0) firstAssaultTick = tick;
                        companySquads = Math.max(companySquads, company.squadIds().size());
                    }
                }
                if (foothold) footholdTicks++;
                caption = front.companies().isEmpty() ? "no company"
                        : front.companies().stream().filter(c -> c.objectiveId() >= 0).findFirst()
                        .map(c -> c.stage() + " " + c.arrivedSquads() + "/" + c.squadIds().size() + " squads, "
                                + c.strength() + "/" + c.required()).orElse("foothold only");
                caption += " / marines " + alive + " / reach " + marinesInReach;
                TreeSet<Integer> seen = new TreeSet<>();
                for (ConquestFrontSnapshot.SquadDirective directive : front.directives()) {
                    if (directive.reason().name().startsWith("DEFENCE_STRUCTURE_")) seen.add(directive.squadId());
                }
                soloStructureOrders += seen.size() > 0 ? 1 : 0;
            }
        }
    }
}
