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
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
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
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
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
import java.util.TreeMap;
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
 * of the phases (a company formed, mustered, was committed, and the phases were published) and the mechanics of
 * each capability; whether the fort fell, whether the wall came down and what it cost are reported, never asserted.
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
            "breach-cannon", "breach-charges", "breach-charges-smoke", "plan-mortar", "plan-air", "plan-mortar-air", "combined-arms");
    private static final String CHARGE_KIT = "special.satchel-charge";
    private static final String ROCKET_KIT = "special.rocket-launcher";
    private static final String RADIO_KIT = "special.comms-radio";
    /** Ticks between the samples of whether the company's own sight lines are screened. */
    private static final int BLIND_SAMPLE_TICKS = 15;
    /** A marine this near a live nest, with no wall between, is in its own line of fire for the sample. */
    private static final float OWN_FIRE_REACH_CELLS = 24f;
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
            reports.add(report(loop, playOne(loop, window(frames))));
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
        // The screen loop is a short force: too few fire squads to put one against every nest, so the nests it cannot engage are
        // the ones it must screen.
        for (int i = 0; i < squads; i++) {
            int standoff = i == 0 ? FIRST_STANDOFF : FIRST_STANDOFF + 40 + SPACING * i;
            int[] at = clamp(terrain.cell(gate.mouthX() + gate.outX() * standoff, gate.mouthY() + gate.outY() * standoff));
            String key = "marines-" + i;
            keys.add(key);
            var spec = builder.squad(key).faction(Faction.MARINE).size(squadSize).at(at[0], at[1]).primary(MARINE_RIFLE);
            // The capability loops differ only in the kit the same company carries.
            // Rockets answer the nests; the charges are carried by other squads, so a close breach is the plan the company can staff.
            if (chargesLoop(loop) && i % 3 == 1) spec.special(CHARGE_KIT, 4);
            if (chargesLoop(loop) && i % 3 == 2) spec.special(ROCKET_KIT, 4, 3);
            if (loop.equals("breach-charges-smoke") && i % 3 == 0) spec.special(SpecialEquipmentRegistry.SMOKE_GRENADE_ID, 2);
            issueFireSupport(loop, i, spec);
            spec.done();
        }
        if (loop.equals("breach-cannon")) {
            int[] at = clamp(terrain.cell(gate.mouthX() + gate.outX() * (FIRST_STANDOFF + 20),
                    gate.mouthY() + gate.outY() * (FIRST_STANDOFF + 20)));
            builder.squad("cannon-mech").faction(Faction.MARINE).size(1).at(at[0], at[1])
                    .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT).done();
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
        sim.random().setSeed(Long.getLong("battle.scene.assaultMuster.seed", 20261016L));
        if (lance) {
            sim.setMechDrops(new MechDropService(MechDropDeclaration.NONE.with(Faction.MARINE,
                    new MechDropAllowance(1, List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.HOUND,
                            MechVariant.WARDEN)))));
        }
        if (hasAir(loop)) {
            sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE,
                    AirPoolAllowance.of(BOMBERS, null, /*aiDirected*/ false))));
        }
        Fort fort = sim.forts().forts().get(0);
        ConquestCommand command = new ConquestCommand(new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT))
                .withAssaultStyle(loop.equals("control-trickle") ? AssaultStyle.TRICKLE : AssaultStyle.MUSTER);
        // The loops that judge the plan's own fire missions leave the squads' standing ones out, which would clear the works first.
        if (loop.equals("plan-mortar") || loop.equals("plan-mortar-air") || loop.equals("combined-arms")) {
            command.withoutStandingFireMissionsForEvidence();
        }
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

    /** The loops whose company carries satchels for the sappers and rockets for the base of fire. */
    private static boolean chargesLoop(String loop) {
        return loop.equals("breach-charges") || loop.equals("breach-charges-smoke");
    }

    private static boolean hasAir(String loop) {
        return loop.equals("plan-air") || loop.equals("plan-mortar-air") || loop.equals("combined-arms");
    }

    /** Pulses of slack allowed on the satchel hold's bound before it must have ended: the plan is read once a pulse. */
    private static final int SATCHEL_PULSE_ALLOWANCE_TICKS = 300;

    /** The first fort's record of what its company did with the sappers it held, or null when no company ran a plan. */
    private static FortAssault.SatchelRecord satchelRecord(Trace t) {
        return t.command == null ? null : t.command.satchelRecords().values().stream().findFirst().orElse(null);
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
                    t.companySquads >= 3,
                    "company of " + t.companySquads + " squads committed together (peak " + t.peakSquadsInReach
                            + " in reach of the fort is a reading)"));
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
            BreachMechanics b = t.breach;
            // Which way in the plan takes (planVariant, breachCoverAtPlan, gateCoverAtPlan) depends on which nests the fight has
            // left standing when the company goes in: a reading, not a verdict.
            verdicts.add(Verdict.of("breach-orders-went-to-squads-that-carry-a-breaching-kit",
                    b.unequippedSquads.isEmpty(),
                    b.orderedSquads.size() + " squads ordered to open the wall (none is vacuous: the sappers can be held back and lost), " + b.unequippedSquads.size()
                            + " of them carrying nothing that can " + b.unequippedNotes));
            // Vacuous when no breacher ever stood at the wall with kit ready: getting there alive is the fight's business.
            verdicts.add(Verdict.of("a-breacher-in-reach-with-kit-ready-spends-it",
                    b.firstReadyInReachTick < 0 || (b.firstSpendTick >= 0
                            && b.firstSpendTick <= b.firstReadyInReachTick + BreachMechanics.SPEND_WINDOW_TICKS),
                    b.firstReadyInReachTick < 0 ? "no breacher stood at the wall with kit ready (nothing to judge)"
                            : "first ready in reach at tick " + b.firstReadyInReachTick + ", first round, charge or cut spent at tick "
                            + b.firstSpendTick));
            boolean closeBreach = t.planVariant.startsWith("breach (close)");
            // A breacher is not left standing: one that is ordered, alive and in no contact keeps closing on the wall. Whether it
            // gets there alive is the fight's business and is reported below as a reading.
            verdicts.add(Verdict.of("no-breacher-stood-idle-short-of-the-wall-in-no-contact",
                    b.maxIdleTicks() <= BreachMechanics.IDLE_LIMIT_TICKS,
                    "longest idle spell " + b.maxIdleTicks() + " ticks; " + (b.tracks.isEmpty() ? "no breacher was ordered" : b.describeTracks())));
            verdicts.add(Verdict.of("a-charge-set-on-the-wall-damaged-it",
                    b.chargesDamagedWall == b.chargesResolved,
                    b.chargesSet + " charges set, " + b.chargesResolved + " detonated, " + b.chargesDamagedWall
                            + " of those lowered or opened their wall cell"));
            // A sapper stands under every gun that sees its approach while it sets the charge, so it is let go only when the plan has
            // judged the approach survivable (every nest on it dead, suppressed, screened or out of sight), and the hold is bounded.
            FortAssault.SatchelRecord s = satchelRecord(t);
            if (closeBreach) {
                verdicts.add(Verdict.of("no-sapper-is-ordered-to-the-wall-before-its-approach-is-survivable",
                        s == null || b.firstOrderTick < 0 || (s.releasedTick >= 0 && b.firstOrderTick >= s.releasedTick && s.openAtRelease == 0),
                        s == null ? "no satchel record" : "first order to open the wall at tick " + b.firstOrderTick + ", approach judged survivable at tick "
                                + s.releasedTick + " with " + s.openAtRelease + " nests open, answers " + s.answersAtRelease));
                verdicts.add(Verdict.of("a-held-company-works-its-approach-or-changes-it",
                        s == null || s.heldSinceTick < 0 || s.releasedTick >= 0 || s.approachChangedTick >= 0
                                || s.lastHeldTick - s.lastProgressTick <= SatchelApproach.PATIENCE_TICKS + SATCHEL_PULSE_ALLOWANCE_TICKS,
                        s == null ? "no satchel record" : "held " + s.heldTicks() + " ticks, " + (s.lastHeldTick - s.lastProgressTick) + " since a further nest was answered (patience " + SatchelApproach.PATIENCE_TICKS
                                + "), released at " + s.releasedTick + ", approach changed at " + s.approachChangedTick + " " + s.approachChange
                                + (s.breachLog.isEmpty() ? "" : "; breach roles " + s.breachLog)));
                verdicts.add(Verdict.of("a-smoke-screen-asked-for-was-thrown-or-ended-on-purpose",
                        s == null || s.asked(SatchelApproach.Enabler.SMOKE) == 0 || !t.cloudsStood.isEmpty()
                                || s.smokeLog.stream().anyMatch(line -> line.contains("withdrawn") || line.contains("retired")),
                        s == null ? "no satchel record" : s.asked(SatchelApproach.Enabler.SMOKE) + " screens ordered, " + t.cloudsStood.size()
                                + " clouds stood (first at tick " + t.firstCloudTick + "); " + s.smokeLog));
                verdicts.add(Verdict.of("a-held-sapper-is-waiting-on-an-action-or-has-none-available",
                        s == null || s.heldSinceTick < 0 || s.releasedTick >= 0 || s.asked.values().stream().mapToInt(Integer::intValue).sum() > 0
                                || s.unservablePulses > 0 || s.pendingPulses > 0,
                        s == null ? "no satchel record" : "asked " + s.asked + ", pulses with an open nest nothing could answer " + s.unservablePulses + ", pulses with one already in motion " + s.pendingPulses + ", last reading " + s.lastReadings));
            }
        }
        // The combined-arms loop reports the tubes as readings only: its mission nest may be dead before the section arrives.
        if (loop.equals("plan-mortar") || loop.equals("plan-mortar-air")) {
            verdicts.add(Verdict.of("the-tubes-were-set-down", t.tubeRounds.size() > 0,
                    t.tubeRounds.size() + " tubes were served, " + t.mortarRounds() + " rounds fired in all"));
        }
        if (loop.equals("plan-mortar") || loop.equals("plan-mortar-air") || loop.equals("combined-arms")) {
            // Two tubes in a company of five do not staff a base of fire alone; the mechanic is that they count for something
            // when the company carries any. Which squads the muster draws is the fight's business, so with none it is vacuous.
            verdicts.add(Verdict.of("the-tubes-counted-toward-the-base-of-fire", !t.companyCarriesTubes
                            || !t.planShortfall.matches("(?s).*base_of_fire needs [^;]*: (\\d+) of \\1 short.*"),
                    (t.companyCarriesTubes ? "company carries tubes; " : "no tubes in the company; ") + "shortfall at suppression: "
                            + (t.planShortfall.isEmpty() ? "none" : t.planShortfall)));
        }
        if (hasAir(loop)) {
            int asked = t.command == null ? 0 : t.command.strikeOutcomes().values().stream().mapToInt(Integer::intValue).sum();
            int launched = t.poolEvents(AirPoolService.EventType.LAUNCHED);
            verdicts.add(Verdict.of("the-air-was-asked-or-the-ceiling-held",
                    !t.companyCarriesRadio || asked > 0 || t.nestRiskMin > AirStrikeDirector.RISK_CEILING,
                    (t.companyCarriesRadio ? "company carries a radio; " : "no radio in the company; ") + asked + " strikes asked for " + (t.command == null ? "" : t.command.strikeOutcomes()) + ", " + launched
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
        metrics.put("breachCoverAtPlan", t.breachCoverAtPlan);
        metrics.put("gateCoverAtPlan", t.gateCoverAtPlan);
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
        metrics.put("breachOrderedSquads", t.breach.orderedSquads.size());
        metrics.put("breachFirstOrderTick", t.breach.firstOrderTick);
        metrics.put("breachNearestApproachCells", t.breach.nearestApproach == Float.MAX_VALUE ? -1 : Math.round(t.breach.nearestApproach));
        metrics.put("breachFirstReadyInReachTick", t.breach.firstReadyInReachTick);
        metrics.put("breachFirstSpendTick", t.breach.firstSpendTick);
        metrics.put("breachSquadsReached", t.breach.tracks.values().stream().filter(BreachMechanics.SquadTrack::reached).count());
        metrics.put("breachIdleTicksMax", t.breach.maxIdleTicks());
        metrics.put("breachSquadsDied", t.breach.tracks.values().stream().filter(s -> s.diedTick >= 0).count());
        metrics.put("wallChargesSet", t.breach.chargesSet);
        metrics.put("wallChargesDetonated", t.breach.chargesResolved);
        FortAssault.SatchelRecord sr = satchelRecord(t);
        metrics.put("satchelHeldSinceTick", sr == null ? -1 : sr.heldSinceTick);
        metrics.put("satchelReleasedTick", sr == null ? -1 : sr.releasedTick);
        metrics.put("satchelHeldTicks", sr == null ? 0 : sr.heldTicks());
        metrics.put("satchelAskedEngage", sr == null ? 0 : sr.asked(SatchelApproach.Enabler.ENGAGE));
        metrics.put("satchelAskedSmoke", sr == null ? 0 : sr.asked(SatchelApproach.Enabler.SMOKE));
        metrics.put("smokeCloudsStood", t.cloudsStood.size());
        metrics.put("smokeGrenadeClouds", t.cloudsStood.size());
        metrics.put("smokeCloudsThinned", t.thinnedClouds.size());
        metrics.put("smokeLongestCloudSeconds", Math.round(t.longestCloudSeconds));
        metrics.put("smokeCloudTicks", t.cloudTicks);
        metrics.put("ownFireOpenSamples", t.ownFireOpenSamples);
        metrics.put("ownFireBlindedSamples", t.ownFireBlindedSamples);
        metrics.put("anyMarineBlindedTicks", t.ownFireBlindedSamples * BLIND_SAMPLE_TICKS);
        metrics.put("baseOfFireOpenSamples", t.baseOfFireOpenSamples);
        metrics.put("baseOfFireBlindedSamples", t.baseOfFireBlindedSamples);
        metrics.put("ownFireBlindedTicks", t.baseOfFireBlindedSamples * BLIND_SAMPLE_TICKS);
        metrics.put("satchelReleasedBlinded", sr == null ? 0 : sr.answersAtRelease.getOrDefault(SatchelApproach.Answer.BLINDED, 0));
        metrics.put("smokeRefusedBlindsBase", sr == null ? 0 : sr.refused.getOrDefault(SatchelApproach.Refusal.BLINDS_BASE_OF_FIRE, 0));
        metrics.put("smokeRefusedStrandsSappers", sr == null ? 0 : sr.refused.getOrDefault(SatchelApproach.Refusal.STRANDS_SAPPERS, 0));
        metrics.put("smokeTooShortPulses", sr == null ? 0 : sr.smokeTooShortPulses);
        metrics.put("satchelAskedStrike", sr == null ? 0 : sr.asked(SatchelApproach.Enabler.STRIKE));
        metrics.put("satchelTicksSinceProgress", sr == null ? 0 : Math.max(0, sr.lastHeldTick - sr.lastProgressTick));
        metrics.put("satchelNestsDiedWhileHeld", sr == null ? 0 : sr.nestsDiedWhileHeld);
        metrics.put("satchelApproachChangedTick", sr == null ? -1 : sr.approachChangedTick);
        metrics.put("satchelUnservablePulses", sr == null ? 0 : sr.unservablePulses);
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

    /**
     * The mechanics of a wall breach, observed rather than judged by outcome: which squads the plan ordered to open the
     * wall and whether they carry anything that can, whether a breacher with his kit ready and the wall in reach had
     * spent it within a few seconds, and whether a charge set on the wall damaged it when its fuse ran out. Whether
     * the wall came down is a reading elsewhere.
     */
    private static final class BreachMechanics {
        /** A breacher in reach with kit ready has this many ticks to have spent a round, charge or cut (ten seconds). */
        static final int SPEND_WINDOW_TICKS = 300;

        final Set<Integer> orderedSquads = new HashSet<>();
        final Set<Integer> unequippedSquads = new HashSet<>();
        final List<String> unequippedNotes = new ArrayList<>();
        int firstReadyInReachTick = -1;
        int firstSpendTick = -1;
        int firstOrderTick = -1;
        /** Closest any member of an ordered squad has stood to the ordered wall cell (a reading). */
        float nearestApproach = Float.MAX_VALUE;
        int chargesSet;
        int chargesResolved;
        int chargesDamagedWall;
        private final Map<Long, int[]> chargeCells = new HashMap<>();
        private final Map<Long, Integer> chargeWallHp = new HashMap<>();

        /** A squad this close to the wall cell (cells) has reached it, in the sense of a breacher standing at the work. */
        static final float REACHED_CELLS = 10f;
        /** A squad that has made less than this much ground (cells) over a sampling window, in no contact, is standing idle. */
        static final float IDLE_MOVE_CELLS = 1f;
        static final int SAMPLE_TICKS = 120;
        /** An ordered breacher in no contact may stand still this long (a replan, a regroup) before it is a stall. */
        static final int IDLE_LIMIT_TICKS = 600;

        /** Every squad that was ever ordered to open the wall, followed to the end of the battle. */
        final Map<Integer, SquadTrack> tracks = new TreeMap<>();
        /** The fort whose nests the sappers are measured against, for the loss log. */
        Fort fort;

        /** One breacher squad's course: when it was ordered, how close it came, whether it stood idle, how it ended. */
        static final class SquadTrack {
            final int orderedTick;
            final float wallX;
            final float wallY;
            int startAlive;
            int alive;
            float best = Float.MAX_VALUE;
            int bestTick = -1;
            int diedTick = -1;
            int releasedTick = -1;
            boolean ordered = true;
            int idleTicks;
            private float sampleX = Float.NaN;
            private float sampleY;

            SquadTrack(int orderedTick, int cx, int cy) {
                this.orderedTick = orderedTick;
                this.wallX = cx + 0.5f;
                this.wallY = cy + 0.5f;
            }

            /** Where the squad last lost a man: the nearest live nest and its distance. */
            String lastLoss = "";

            void observe(BattleSimulation sim, Fort fort, Squad squad, boolean orderedNow, int tick) {
                World world = sim.world();
                int count = sim.squadMemberCount(squad.id);
                double sx = 0;
                double sy = 0;
                int living = 0;
                for (int i = 0; i < count; i++) {
                    long member = sim.squadMemberAt(squad.id, i);
                    if (member == 0L || !world.isAlive(member)) continue;
                    living++;
                    sx += world.renderX(member);
                    sy += world.renderY(member);
                    float d = (float) Math.hypot(wallX - world.renderX(member), wallY - world.renderY(member));
                    if (d < best) {
                        best = d;
                        bestTick = tick;
                    }
                }
                if (startAlive == 0) startAlive = living;
                if (alive > living && living > 0 && fort != null) lastLoss = lossNote(sim, fort, sx / living, sy / living, tick, alive - living);
                if (alive > 0 && living == 0 && diedTick < 0) diedTick = tick;
                alive = living;
                if (ordered && !orderedNow) releasedTick = tick;
                ordered = orderedNow;
                if (living > 0 && tick % SAMPLE_TICKS == 0) {
                    float cx = (float) (sx / living);
                    float cy = (float) (sy / living);
                    float toWall = (float) Math.hypot(wallX - cx, wallY - cy);
                    if (!Float.isNaN(sampleX) && ordered && toWall > REACHED_CELLS && squad.timeSinceContact > 5f
                            && Math.hypot(cx - sampleX, cy - sampleY) < IDLE_MOVE_CELLS) {
                        idleTicks += SAMPLE_TICKS;
                    }
                    sampleX = cx;
                    sampleY = cy;
                }
            }

            private static String lossNote(BattleSimulation sim, Fort fort, double x, double y, int tick, int lost) {
                World w = sim.world();
                int bestIndex = -1;
                double best = Double.MAX_VALUE;
                int index = 0;
                for (long nest : fort.nestIds()) {
                    if (w.isAlive(nest)) {
                        double d = Math.hypot(w.x(nest) - x, w.y(nest) - y);
                        if (d < best) {
                            best = d;
                            bestIndex = index;
                        }
                    }
                    index++;
                }
                return "-" + lost + "@" + tick + " at " + Math.round(x) + "," + Math.round(y) + " nearest live nest #" + bestIndex + " " + Math.round(best) + " cells";
            }

            void vanished(int tick) {
                if (alive > 0 && diedTick < 0) diedTick = tick;
                alive = 0;
            }

            boolean reached() {
                return best <= REACHED_CELLS;
            }

            /** How it ended: at the wall, dead short of it, handed back to the assault, or still walking. */
            String end() {
                if (reached()) return "REACHED";
                if (alive == 0) return "DIED";
                return ordered ? "STILL_ORDERED" : "HANDED_BACK";
            }

            String describe(int id) {
                return "sq" + id + " ordered@" + orderedTick + " " + startAlive + "->" + alive + " closest=" + Math.round(best)
                        + "@" + bestTick + (diedTick >= 0 ? " died@" + diedTick : "") + (releasedTick >= 0 ? " released@" + releasedTick : "")
                        + " idle=" + idleTicks + " " + end() + (lastLoss.isEmpty() ? "" : " lastLoss " + lastLoss);
            }
        }

        private void track(BattleSimulation sim, int tick) {
            Set<Integer> seen = new HashSet<>();
            for (Squad squad : sim.getSquads()) {
                ObjectiveAssignment order = squad.assignmentForExecution();
                boolean orderedNow = order != null && order.breaches();
                SquadTrack track = tracks.get(squad.id);
                if (track == null) {
                    if (!orderedNow) continue;
                    track = new SquadTrack(tick, order.breachCellX(), order.breachCellY());
                    tracks.put(squad.id, track);
                }
                seen.add(squad.id);
                track.observe(sim, fort, squad, orderedNow, tick);
            }
            for (Map.Entry<Integer, SquadTrack> e : tracks.entrySet()) {
                if (!seen.contains(e.getKey())) e.getValue().vanished(tick);
            }
        }

        int maxIdleTicks() {
            int max = 0;
            for (SquadTrack track : tracks.values()) max = Math.max(max, track.idleTicks);
            return max;
        }

        boolean anyReached() {
            for (SquadTrack track : tracks.values()) if (track.reached()) return true;
            return false;
        }

        String describeTracks() {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<Integer, SquadTrack> e : tracks.entrySet()) {
                if (out.length() > 0) out.append("; ");
                out.append(e.getValue().describe(e.getKey()));
            }
            return out.toString();
        }

        void observe(BattleSimulation sim, int tick) {
            track(sim, tick);
            NavigationGrid grid = sim.getGrid();
            World world = sim.world();
            for (Squad squad : sim.getSquads()) {
                ObjectiveAssignment order = squad.assignmentForExecution();
                if (order == null || !order.breaches()) continue;
                int cx = order.breachCellX();
                int cy = order.breachCellY();
                boolean capable = false;
                boolean ready = false;
                int count = sim.squadMemberCount(squad.id);
                for (int i = 0; i < count; i++) {
                    long member = sim.squadMemberAt(squad.id, i);
                    if (member == 0L || !world.isAlive(member)) continue;
                    capable |= canCutWalls(world, member);
                    nearestApproach = Math.min(nearestApproach, (float) Math.hypot(cx + 0.5f - world.renderX(member), cy + 0.5f - world.renderY(member)));
                    ready |= readyAtWall(sim, grid, world, member, cx, cy);
                }
                if (orderedSquads.add(squad.id) && !capable) { unequippedSquads.add(squad.id); unequippedNotes.add("squad " + squad.id + " at tick " + tick + " with " + count + " members"); }
                if (firstOrderTick < 0) firstOrderTick = tick;
                if (ready && !grid.isWalkable(cx, cy) && firstReadyInReachTick < 0) firstReadyInReachTick = tick;
            }
            if (firstSpendTick < 0 && sim.wallBreach().roundsAtWalls() > 0) firstSpendTick = tick;
            Set<Long> present = new HashSet<>();
            for (SatchelChargeService.ChargeView charge : sim.satchelCharges().activeCharges()) {
                if (charge.targetId() != 0L) continue;
                present.add(charge.id());
                int[] cell = chargeCells.computeIfAbsent(charge.id(), k -> {
                    chargesSet++;
                    return new int[] {(int) Math.floor(charge.x()), (int) Math.floor(charge.y())};
                });
                chargeWallHp.put(charge.id(), grid.getWallHp(cell[0], cell[1]));
            }
            for (Map.Entry<Long, int[]> entry : chargeCells.entrySet()) {
                long id = entry.getKey();
                if (present.contains(id) || !chargeWallHp.containsKey(id)) continue;
                int[] cell = entry.getValue();
                chargesResolved++;
                if (grid.isWalkable(cell[0], cell[1]) || grid.getWallHp(cell[0], cell[1]) < chargeWallHp.get(id)) {
                    chargesDamagedWall++;
                }
                chargeWallHp.remove(id);
            }
        }

        /** Whether the member carries something an order to open a wall can spend. */
        private static boolean canCutWalls(World world, long member) {
            if (world.hasMechLoadout(member)) {
                for (MechWeaponMount mount : world.mechLoadout(member).mounts()) {
                    if (mount != null && mount.weaponDef().wallDamage > 0) return true;
                }
                return false;
            }
            if (!world.hasSecondaryWeapon(member)) return false;
            SpecialEquipmentDef kit = world.specialEquipment(member);
            return switch (kit.activation()) {
                case DIRECT_EXPLOSIVE -> kit.weaponDef().wallDamage > 0;
                case UTILITY_SATCHEL -> kit.satchelChargeSpec().wallBreachDamage() > 0;
                default -> false;
            };
        }

        /** Whether this member, as he stands now, has the wall in reach of kit that is ready to be spent on it. */
        private static boolean readyAtWall(BattleSimulation sim, NavigationGrid grid, World world, long member,
                                           int cx, int cy) {
            float fromX = world.renderX(member);
            float fromY = world.renderY(member);
            float tx = cx + 0.5f;
            float ty = cy + 0.5f;
            float distance = (float) Math.hypot(tx - fromX, ty - fromY);
            if (world.hasMechLoadout(member)) {
                for (MechWeaponMount mount : world.mechLoadout(member).mounts()) {
                    if (mount == null) continue;
                    WeaponDef weapon = mount.weaponDef();
                    if (weapon.wallDamage > 0 && distance <= weapon.range && distance >= weapon.minRange) return true;
                }
                return false;
            }
            if (!world.hasSecondaryWeapon(member)
                    || world.secondaryCooldownTimer(member) > 0f || world.secondaryActionTimer(member) > 0f) return false;
            SpecialEquipmentDef kit = world.specialEquipment(member);
            if (kit.activation() == SpecialActivation.DIRECT_EXPLOSIVE) {
                WeaponDef weapon = kit.weaponDef();
                return weapon.wallDamage > 0 && world.secondaryAmmo(member) > 0 && distance <= weapon.range
                        && distance >= weapon.minRange && lineReaches(grid, fromX, fromY, tx, ty, cx, cy);
            }
            if (kit.activation() == SpecialActivation.UTILITY_SATCHEL) {
                SatchelChargeSpec spec = kit.satchelChargeSpec();
                return spec.wallBreachDamage() > 0 && distance <= spec.contactRange() + 4f
                        && !sim.satchelCharges().hasChargeAtCell(cx, cy);
            }
            return false;
        }

        private static boolean lineReaches(NavigationGrid grid, float fromX, float fromY, float toX, float toY,
                                           int cx, int cy) {
            long packed = grid.firstWallOnLine(fromX, fromY, toX, toY);
            int hitX = (int) packed;
            int hitY = (int) (packed >>> 32);
            if (hitX == -1 && hitY == -1) return true;
            return Math.abs(hitX - cx) <= 1 && Math.abs(hitY - cy) <= 1;
        }
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
        /** Nests the plan believed live over the breach and over the least-covered gate when the first company went in, which is when it commits to a way. */
        int coverAtPlanTick = -1;
        int breachCoverAtPlan = -1;
        int gateCoverAtPlan = -1;
        String breachSquadKit = "";
        ConquestCommand command;
        final BreachMechanics breach = new BreachMechanics();
        /** Every cloud of smoke that stood on the field, by id, and the tick the first did. */
        final Set<Long> cloudsStood = new HashSet<>();
        int firstCloudTick = -1;
        final Set<Long> thinnedClouds = new HashSet<>();
        int cloudTicks;
        float longestCloudSeconds;
        /** Marine-samples (every {@link #BLIND_SAMPLE_TICKS} ticks) with a live nest in reach and no wall between, and of those how many had smoke on the line. */
        int ownFireOpenSamples;
        int ownFireBlindedSamples;
        /** As above for the plan's base of fire only, against the nest each fire squad is placed on. */
        int baseOfFireOpenSamples;
        int baseOfFireBlindedSamples;
        /** Whether a squad committed to the company ever carried a field mortar, or a forward observer's radio. */
        boolean companyCarriesTubes;
        boolean companyCarriesRadio;
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
            this.breach.fort = fort;
            for (String key : keys) startMarines += world.members(key).length;
        }

        String caption() {
            return caption;
        }

        /**
         * Counts, for every living marine of the company with a live nest in reach and open ground (no wall) to it, whether smoke
         * stands on that line: the company's own fire blinded by the screen it laid. A reading, not a verdict.
         */
        private void sampleOwnFire(BattleSimulation sim) {
            World w = sim.world();
            for (String key : keys) {
                for (long id : world.members(key)) {
                    if (!w.isAlive(id)) continue;
                    for (long nest : fort.nestIds()) {
                        if (!w.isAlive(nest)) continue;
                        if (Math.hypot(w.x(id) - w.x(nest), w.y(id) - w.y(nest)) > OWN_FIRE_REACH_CELLS) continue;
                        if (sim.getGrid().firstWallOnLine(w.cellX(id), w.cellY(id), w.cellX(nest), w.cellY(nest)) != -1) continue;
                        ownFireOpenSamples++;
                        if (sim.getGrid().smokeDepthOnLine(w.x(id), w.y(id), w.x(nest), w.y(nest)) > 0) ownFireBlindedSamples++;
                        break;
                    }
                }
            }
        }

        /**
         * The base of fire's own sight: for each living member of a squad the plan has placed against a live nest, whether the line to
         * that nest is open ground, and of those how many have smoke across it. This is the company's fire blinded by its own screen, and
         * only that: a sapper walking inside a cloud, or an assault squad waiting behind one, is the screen doing what it was laid for.
         */
        private void sampleBaseOfFire(BattleSimulation sim) {
            World w = sim.world();
            FortAssault.SatchelRecord record = satchelRecord(this);
            if (record == null) return;
            for (Map.Entry<Integer, int[]> placed : record.baseOfFire.entrySet()) {
                int count = sim.squadMemberCount(placed.getKey());
                for (int i = 0; i < count; i++) {
                    long member = sim.squadMemberAt(placed.getKey(), i);
                    if (member == 0L || !w.isAlive(member)) continue;
                    int[] nest = placed.getValue();
                    if (Math.hypot(w.x(member) - nest[0], w.y(member) - nest[1]) > OWN_FIRE_REACH_CELLS) continue;
                    if (sim.getGrid().firstWallOnLine(w.cellX(member), w.cellY(member), nest[0], nest[1]) != -1) continue;
                    baseOfFireOpenSamples++;
                    if (sim.getGrid().smokeDepthOnLine(w.x(member), w.y(member), nest[0] + 0.5f, nest[1] + 0.5f) > 0) baseOfFireBlindedSamples++;
                }
            }
        }

        /** Records what the plan believed of the nests over each way in when the company went in. */
        private void noteCover(int tick) {
            FortAssault.NestBelief belief = command == null ? null : command.nestBelief(fort.id());
            if (belief == null || fort.frame() == null) return;
            coverAtPlanTick = tick;
            int gate = Integer.MAX_VALUE;
            for (FortAssault.Way way : FortAssault.Works.of(fort.frame()).ways) {
                if (way.variant() == Variant.BREACH) breachCoverAtPlan = belief.liveCovering(way);
                else gate = Math.min(gate, belief.liveCovering(way));
            }
            gateCoverAtPlan = gate == Integer.MAX_VALUE ? -1 : gate;
        }

        /** Records whether the committed squads carry the kit the fire-support verdicts are about. */
        private void noteCarriers(BattleSimulation sim, List<Integer> squadIds) {
            World w = sim.world();
            for (int squadId : squadIds) {
                int count = sim.squadMemberCount(squadId);
                for (int i = 0; i < count; i++) {
                    long member = sim.squadMemberAt(squadId, i);
                    if (member == 0L || !w.isAlive(member) || !w.hasSecondaryWeapon(member)) continue;
                    String kit = w.specialEquipment(member).id();
                    if (SpecialEquipmentRegistry.FIELD_MORTAR_ID.equals(kit)) companyCarriesTubes = true;
                    if (RADIO_KIT.equals(kit)) companyCarriesRadio = true;
                }
            }
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
            for (var cloud : sim.smokeFields().activeFields()) {
                if (cloudsStood.add(cloud.id()) && firstCloudTick < 0) firstCloudTick = tick;
                if (cloud.radius() < cloud.fullRadius()) thinnedClouds.add(cloud.id());
                longestCloudSeconds = Math.max(longestCloudSeconds, cloud.totalDuration());
                cloudTicks++;
            }
            if (tick % BLIND_SAMPLE_TICKS == 0 && !sim.smokeFields().activeFields().isEmpty()) {
                sampleOwnFire(sim);
                sampleBaseOfFire(sim);
            }
            breach.observe(sim, tick);
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
                    if (company.objectiveId() >= 0 && company.stage().equals("ASSAULT")) noteCarriers(sim, company.squadIds());
                    if (company.objectiveId() >= 0 && company.stage().equals("ASSAULT") && coverAtPlanTick < 0) noteCover(tick);
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
