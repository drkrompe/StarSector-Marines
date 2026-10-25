package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.command.RescueEscortCommand;
import com.dillon.starsectormarines.battle.command.StrikeDetachment;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Colony Breach story: the breach clock, the eruption schedule and the
 * beats the noun doc's table names.
 *
 * <p>{@link #tick} is the simulation half. It notices the cohort's release,
 * starts the tremor a few seconds later, ends the perimeter seepage there, and
 * opens each {@link ColonyEruptionPlan} site at its scheduled tick, handing it
 * to the installed {@link EruptionHandler}. {@link #observe} is the
 * presentation half and only ever writes beats: they are evidence and radio
 * cues, never inputs.
 *
 * <p>The battle installs a handler that opens a bore on each site
 * ({@link SwarmBores#open}); at the tremor the director stops spawning at the
 * perimeter and spawns only from living bores. The bores' own facts (a tier
 * reached, a collapse, a surge) are published here as beats, read off
 * {@link SwarmBores#events} with a cursor.
 */
public final class ColonyBreachSequence {

    /** Every beat this sequence writes carries this prefix. */
    public static final String BEAT_PREFIX = "colony_breach.";
    /** Seconds from the cohort's release to the tremor. */
    public static final float TREMOR_DELAY_SECONDS = 4f;
    /** How far a Marine sees a militia post from, for {@code militia_contact}. */
    public static final float MILITIA_CONTACT_CELLS = 20f;
    /** Ticks between militia-contact scans; a sighting is not a frame-exact fact. */
    private static final int CONTACT_SCAN_TICKS = 15;

    /** Where the breach is. */
    public enum Phase { LANDFALL, SEEPAGE, BREACH, TERMINAL }

    /**
     * What happens on the field when a site erupts. The battle installs one
     * that opens a bore on the site ({@link SwarmBores#open}), and creep then
     * spreads from the bore ({@code BoreCreep}); without one nothing happens.
     */
    @FunctionalInterface
    public interface EruptionHandler {
        void erupted(BattleSimulation sim, ColonyEruptionPlan.Site site, int tick);
    }

    /**
     * One published picture of the breach, for evidence and diagnostics.
     *
     * @param eruptionTicks per site in {@link ColonyEruptionPlan#sites()} order:
     *                      the tick it erupted or will erupt, or {@code -1}
     *                      before the tremor has scheduled it
     * @param erupted       per site, whether it has opened
     * @param colonists     colonists placed outside the shelter at setup
     * @param colonistsAlive of those, how many are still on the field
     * @param harvest       what the swarm has dragged to its bores so far
     */
    public record Snapshot(int tick, Phase phase, int landfallTick, int shelterReachedTick,
                           int tremorTick, List<ColonyEruptionPlan.Site> sites,
                           List<Integer> eruptionTicks, List<Boolean> erupted,
                           int colonists, int colonistsAlive, List<CivilianRedoubt> redoubts,
                           Harvest harvest) {
        public Snapshot {
            redoubts = List.copyOf(redoubts);
        }
    }

    /**
     * Harvest so far: bodies seized (a re-seizure of a dropped body and an
     * impale count again), delivered into a bore, dropped dead by a carrier that
     * died, and being dragged now; and of the living, how many were impaled,
     * freed and taken; and how many of the swarm's own dead were recycled into a bore.
     */
    public record Harvest(int firstTick, int seized, int delivered, int dropped, int carrying,
                          int impaled, int freed, int taken, int recycled) { }

    private final ColonyEruptionPlan plan;
    private final List<Long> colonists;
    private final StoryBeatLog log;
    private final boolean[] erupted;
    private final int[] eruptedAt;
    private EruptionHandler handler = (sim, site, tick) -> { };
    private int landfallTick = -1;
    private int shelterReachedTick = -1;
    private int tremorTick = -1;
    private boolean militiaSeen;
    private boolean terminal;
    private int boreEventCursor;
    private int harvestEventCursor;
    private int strikeCursor;

    public ColonyBreachSequence(ColonyEruptionPlan plan, List<Long> colonists, StoryBeatLog log) {
        if (plan == null || log == null) throw new IllegalArgumentException("A breach needs its plan and beat log");
        this.plan = plan;
        this.colonists = colonists == null ? List.of() : List.copyOf(colonists);
        this.log = log;
        this.erupted = new boolean[plan.sites().size()];
        this.eruptedAt = new int[plan.sites().size()];
        Arrays.fill(eruptedAt, -1);
    }

    /** The colony's population outside the shelter, in spawn order: what the swarm harvests. */
    public List<Long> colonists() { return colonists; }

    /** The sites this battle breaks open at, and when relative to the tremor. */
    public ColonyEruptionPlan plan() { return plan; }

    /** Installs what eruption does on the field. One handler; the last one installed wins. */
    public void setEruptionHandler(EruptionHandler handler) {
        this.handler = handler == null ? (sim, site, tick) -> { } : handler;
    }

    /** The tick of the tremor, or {@code -1} before it. */
    public int tremorTick() { return tremorTick; }

    /** Whether the site at this index in {@link ColonyEruptionPlan#sites()} has opened. */
    public boolean isErupted(int index) { return erupted[index]; }

    /** The tick a site opens, or {@code -1} while the tremor has not scheduled it. */
    public int scheduledEruptionTick(int index) {
        return tremorTick < 0 ? -1 : ColonyEruptionPlan.eruptionTick(plan.sites().get(index), tremorTick);
    }

    public Phase phase() {
        if (terminal) return Phase.TERMINAL;
        if (tremorTick >= 0) return Phase.BREACH;
        return landfallTick >= 0 ? Phase.SEEPAGE : Phase.LANDFALL;
    }

    /** Simulation half: the clock, the seepage switch, and eruption. */
    public void tick(BattleSimulation sim) {
        if (terminal) return;
        int tick = sim.getSimTickIndex();
        // The side objectives run on the breach's clock (ColonyBreachObjectives).
        ObjectiveBoard objectives = sim.getOptionalObjectives();
        if (objectives != null) objectives.advance(tick);
        if (landfallTick < 0 && marineOnTheGround(sim)) landfallTick = tick;
        if (shelterReachedTick < 0 && !sim.isCivilianShelterProtected()) {
            shelterReachedTick = tick;
            tremorTick = tick + Math.round(TREMOR_DELAY_SECONDS / BattleSimulation.TICK_DT);
        }
        if (tremorTick >= 0 && tick >= tremorTick) {
            sim.endSwarmSeepage();
            sim.beginSwarmBoreSourcing();
        }
        if (tremorTick < 0) return;
        List<ColonyEruptionPlan.Site> sites = plan.sites();
        for (int i = 0; i < sites.size(); i++) {
            if (erupted[i] || tick < ColonyEruptionPlan.eruptionTick(sites.get(i), tremorTick)) continue;
            erupted[i] = true;
            eruptedAt[i] = tick;
            handler.erupted(sim, sites.get(i), tick);
        }
    }

    /** Presentation half: beats from facts already settled this tick. */
    public void observe(BattleSimulation sim) {
        if (terminal) return;
        int tick = sim.getSimTickIndex();
        if (landfallTick >= 0) emit(tick, "landfall");
        if (!militiaSeen && landfallTick >= 0 && tick % CONTACT_SCAN_TICKS == 0) {
            militiaSeen = marineSeesMilitia(sim);
        }
        if (militiaSeen) emit(tick, "militia_contact");
        if (shelterReachedTick >= 0) emit(tick, "shelter_reached");
        List<CivilianRedoubt> redoubts = sim.getCivilianRedoubts();
        if (redoubts.size() > 1) {
            for (CivilianRedoubt redoubt : redoubts) {
                if (redoubt.released()) emit(tick, "redoubt_reached_" + redoubt.number());
                if (redoubt.lineBroke()) emit(tick, "redoubt_line_broke_" + redoubt.number());
                if (redoubt.fallen()) emit(tick, "redoubt_fell_" + redoubt.number());
            }
        }
        if (tremorTick >= 0 && tick >= tremorTick) emit(tick, "tremor");
        if (sim.swarmHarvest().firstHarvestTick() >= 0) emit(tick, "first_harvest");
        List<SwarmHarvest.Event> held = sim.swarmHarvest().events();
        for (; harvestEventCursor < held.size(); harvestEventCursor++) {
            SwarmHarvest.Event event = held.get(harvestEventCursor);
            if (event.faction() != Faction.MARINE) continue;
            String key = switch (event.kind()) {
                case IMPALED -> "marine_impaled";
                case FREED -> "marine_freed";
                case TAKEN -> "marine_taken";
            };
            log.emit(tick, BEAT_PREFIX + key, Audience.PLAYER);
        }
        List<ColonyEruptionPlan.Site> sites = plan.sites();
        for (int i = 0; i < sites.size(); i++) {
            if (!erupted[i]) continue;
            ColonyEruptionPlan.Site site = sites.get(i);
            emit(tick, site.mother() ? "mother_bore_erupted" : "bore_erupted_" + site.number());
        }
        List<SwarmBores.Event> boreEvents = sim.swarmBores().events();
        for (; boreEventCursor < boreEvents.size(); boreEventCursor++) {
            SwarmBores.Event event = boreEvents.get(boreEventCursor);
            SwarmBore bore = event.bore();
            switch (event.kind()) {
                case GREW -> emit(tick, bore.mother()
                        ? "mother_bore_grew_" + event.tier().beatName
                        : "bore_grew_" + bore.number() + "_" + event.tier().beatName);
                case COLLAPSED -> emit(tick, bore.mother()
                        ? "mother_bore_collapsed" : "bore_collapsed_" + bore.number());
                case SURGE -> log.emit(tick, BEAT_PREFIX + "surge_" + bore.beatKey(), Audience.OBSERVER_ONLY);
                case FED -> log.emit(tick, BEAT_PREFIX + "bore_fed_" + bore.beatKey(), Audience.OBSERVER_ONLY);
                case MATRIARCH -> emit(tick, "matriarch_emerged");
            }
        }
        // The commander's strike decisions, read off its published picture with
        // a cursor: a detachment may be sent, recalled and sent again.
        if (sim.getCommander(Faction.MARINE) instanceof RescueEscortCommand command
                && command.rescueSnapshot() != null) {
            List<StrikeDetachment.Decision> decisions = command.rescueSnapshot().hive().decisions();
            for (; strikeCursor < decisions.size(); strikeCursor++) {
                StrikeDetachment.Decision decision = decisions.get(strikeCursor);
                String beat = switch (decision.kind()) {
                    case COMMITTED -> "strike_committed_";
                    case RECALLED -> "strike_recalled_";
                    default -> null;
                };
                if (beat != null) log.emit(tick, BEAT_PREFIX + beat + decision.boreKey(), Audience.OBSERVER_ONLY);
            }
        }
        ShuttleMission pickup = sim.getCivilianPickupMission();
        if (pickup != null && pickup.state != ShuttleState.PENDING) emit(tick, "lift_inbound");
        if (pickup != null && pickup.state == ShuttleState.LANDED) emit(tick, "lift_down");
        CivilianEvacuationTracker tracker = sim.getCivilianEvacuationTracker();
        if (tracker.registeredCount() > 0 && tracker.activeCount() == 0) {
            emit(tick, tracker.evacuatedCount() > 0 ? "cohort_boarded" : "colony_lost");
            terminal = true;
        } else if (sim.isComplete()) {
            if (sim.getWinner() != Faction.MARINE) emit(tick, "colony_lost");
            terminal = true;
        }
    }

    /** The published picture. Built per call; read it once. */
    public Snapshot snapshot(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        int alive = 0;
        for (long id : colonists) if (sim.resolveUnit(id) != 0L) alive++;
        List<Integer> ticks = new ArrayList<>();
        List<Boolean> open = new ArrayList<>();
        for (int i = 0; i < plan.sites().size(); i++) {
            ticks.add(erupted[i] ? eruptedAt[i] : scheduledEruptionTick(i));
            open.add(erupted[i]);
        }
        return new Snapshot(tick, phase(), landfallTick, shelterReachedTick, tremorTick,
                plan.sites(), List.copyOf(ticks), List.copyOf(open), colonists.size(), alive,
                sim.getCivilianRedoubts(), harvest(sim.swarmHarvest()));
    }

    private static Harvest harvest(SwarmHarvest harvest) {
        return new Harvest(harvest.firstHarvestTick(), harvest.seized(), harvest.delivered(),
                harvest.dropped(), harvest.carrying(), harvest.impaled(), harvest.freed(), harvest.taken(),
                harvest.recycled());
    }

    private void emit(int tick, String key) {
        log.emitOnce(tick, BEAT_PREFIX + key, Audience.PLAYER);
    }

    private static boolean marineOnTheGround(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE && !sim.isRiding(unit)) return true;
        }
        return false;
    }

    private static boolean marineSeesMilitia(BattleSimulation sim) {
        int n = sim.liveUnitCount();
        List<long[]> militia = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.ALLY
                    && sim.identity().type(unit) == UnitType.MILITIA) {
                militia.add(new long[]{sim.world().cellX(unit), sim.world().cellY(unit)});
            }
        }
        if (militia.isEmpty()) return false;
        float range = MILITIA_CONTACT_CELLS;
        for (int i = 0; i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.isRiding(unit)) continue;
            int mx = sim.world().cellX(unit), my = sim.world().cellY(unit);
            for (long[] post : militia) {
                long dx = post[0] - mx, dy = post[1] - my;
                if (dx * dx + dy * dy > range * range) continue;
                if (sim.getGrid().hasLineOfSightWithin(mx, my, (int) post[0], (int) post[1], range)) return true;
            }
        }
        return false;
    }
}
