package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.fort.Fort;
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
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * Land, muster, assault: one battery fort in its production layout, a garrison inside it, and the attacker's squads
 * spread out along the way in, nearest the fort first. Each loop changes one thing, the commander's doctrine.
 *
 * <ul>
 *   <li>{@code control-trickle}: {@code battle.command.muster=false}, today's behaviour. The first squad to see the
 *       fort discloses it, and the structure planner detaches one or two squads at a time to the bunker while the
 *       rest are on the lane. This is the control: what a single squad does against this fort is what a fort is built
 *       for.</li>
 *   <li>{@code muster}: the same world with the muster on. The squads that can be spared gather at a rally point,
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
    /** The marines' strength per squad. Eight squads of ten is a force of eighty. */
    private static final int SQUAD_SIZE = 10;
    private static final int SQUADS = 8;
    /** Cells between one squad and the next along the way in, nearest the fort first. */
    private static final int SPACING = 16;
    private static final int FIRST_STANDOFF = 30;
    private static final int GARRISON = 6;
    /** "In reach of the fort" for the readings: well inside the bunker's approaches, outside the walls' nests. */
    private static final int RING = 45;
    private static final String RIFLE = "weapon.smg";

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
        for (String loop : List.of("control-trickle", "muster")) {
            boolean restore = ConquestCommand.musterEnabled();
            ConquestCommand.setMusterEnabled(loop.equals("muster"));
            try {
                reports.add(report(loop, playOne(loop, frames)));
            } finally {
                ConquestCommand.setMusterEnabled(restore);
            }
        }
        return reports;
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
        for (int i = 0; i < SQUADS; i++) {
            int standoff = i == 0 ? FIRST_STANDOFF : FIRST_STANDOFF + 40 + SPACING * i;
            int[] at = clamp(terrain.cell(gate.mouthX() + gate.outX() * standoff, gate.mouthY() + gate.outY() * standoff));
            String key = "marines-" + i;
            keys.add(key);
            builder.squad(key).faction(Faction.MARINE).size(SQUAD_SIZE).at(at[0], at[1]).primary(RIFLE).done();
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
        Fort fort = sim.forts().forts().get(0);
        sim.setAutonomousCommander(Faction.MARINE,
                new ConquestCommand(new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT)),
                ConquestCommandDisclosure.INSTANCE);
        Trace trace = new Trace(fort, keys, world);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 90, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                        loop, tick, trace.caption()))
                .run(TICKS);
        return new Run(trace, sim, fort);
    }

    private static int[] clamp(int[] cell) {
        return new int[] {Math.max(4, Math.min(WIDTH - 5, cell[0])), Math.max(4, Math.min(HEIGHT - 5, cell[1]))};
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        boolean muster = loop.equals("muster");
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
        } else {
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
        private String caption = "";

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

        @Override public void observe(BattleSimulation sim, int tick) {
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
            var snapshot = sim.getCommanderSnapshot(Faction.MARINE);
            if (snapshot != null && snapshot.detail() instanceof ConquestFrontSnapshot front) {
                boolean foothold = false;
                for (ConquestFrontSnapshot.CompanyState company : front.companies()) {
                    if (company.stage().equals("FOOTHOLD")) foothold = true;
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
