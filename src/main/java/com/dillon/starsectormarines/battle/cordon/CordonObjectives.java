package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Cordon's optional objectives ({@code cordon-nouns.md}, Optional objectives;
 * {@code optional-objectives.md}): the twelve story beats as a pool per variant, rolled per battle against
 * the set pieces the map's layout laid, run beside the cordon's story and handed to the campaign.
 */
public final class CordonObjectives {

    /**
     * Forces a battle's objective set instead of rolling it, for evidence: {@code all} (every beat of the
     * variant that fits the map), {@code none}, or a comma list of keys or codes ({@code L1}..{@code L6},
     * {@code R1}..{@code R6}). A permanent evidence lever: it is how each beat is exercised on demand.
     */
    public static final String FORCE_PROPERTY = "battle.cordon.objectives";
    /** The beats' prefix in the battle's story. */
    public static final String PREFIX = "cordon.";

    public static final String STRAGGLERS = "stragglers", GUNS_DRY = "guns_dry", SWITCH_LINE = "switch_line",
            BORE_BEHIND = "bore_behind", LAST_HAMLET = "last_hamlet", BURN_GRANARY = "burn_granary",
            CLEAR_ROAD = "clear_road", TRUCK_DOWN = "truck_down", LAST_LOADING = "last_loading",
            MILITIA = "militia", MAW_ON_ROAD = "maw_on_road", GAP_FALLS = "gap_falls";

    /** The owner's codes for the beats, in order. */
    static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("L1", STRAGGLERS), Map.entry("L2", GUNS_DRY), Map.entry("L3", SWITCH_LINE),
            Map.entry("L4", BORE_BEHIND), Map.entry("L5", LAST_HAMLET), Map.entry("L6", BURN_GRANARY),
            Map.entry("R1", CLEAR_ROAD), Map.entry("R2", TRUCK_DOWN), Map.entry("R3", LAST_LOADING),
            Map.entry("R4", MILITIA), Map.entry("R5", MAW_ON_ROAD), Map.entry("R6", GAP_FALLS));

    private CordonObjectives() {}

    /**
     * The variant's pool. {@code beats} is the battle the runtimes will run in; {@code null} for a pool only
     * read (the briefing's titles), whose runtimes are never started.
     */
    public static List<OptionalObjective> pool(CordonVariant variant, CordonBeats beats) {
        List<OptionalObjective> out = new ArrayList<>();
        if (variant == CordonVariant.LINE) {
            out.add(beat(STRAGGLERS, "Stragglers", "A farm family in front of the line radios in: bring them in before the first tide.",
                    Set.of(CordonSetPieces.STRAGGLERS_HOME), Set.of(), 2, pay(3_000, "food", 20), cost("bloom.cordon.refugees_lost", 6),
                    () -> new StragglersBeat(beats), beats));
            out.add(beat(GUNS_DRY, "The guns run dry", "A redoubt's guns are low: see the ammo truck up its supply road.",
                    Set.of(CordonSetPieces.AMMO_DUMP), Set.of(), 2, pay(2_500, "supplies", 15), cost("bloom.cordon.redoubt_shaken", 1),
                    () -> new GunsDryBeat(beats), beats));
            out.add(beat(SWITCH_LINE, "The switch line", "In the second tide a redoubt is given up: cover its garrison back to the switch line.",
                    Set.of(CordonPlan.SWITCH_LINE), Set.of(), 2, pay(3_000, "heavy_machinery", 5), cost("bloom.cordon.garrison_lost", 1),
                    () -> new SwitchLineBeat(beats), beats));
            out.add(beat(BORE_BEHIND, "A bore behind the line", "A bore opens behind the line: collapse it before it grows.",
                    Set.of(CordonPlan.ERUPTION_SITE), Set.of(), 1, pay(4_000, "metals", 20), cost("bloom.heart.satellites", 1),
                    () -> new BoreBeat(beats, false), beats));
            out.add(beat(LAST_HAMLET, "The hamlet that will not leave", "The far hamlet's last people wait at the bell for Marines.",
                    Set.of(CordonSetPieces.BELL_SQUARE), Set.of(), 1, pay(2_500, "food", 15), cost("bloom.cordon.refugees_lost", 6),
                    () -> new HamletBeat(beats), beats));
            out.add(beat(BURN_GRANARY, "Burn the granary", "The colony's granary is biomass: burn it before the second tide and that tide comes weaker.",
                    Set.of(CordonSetPieces.GRANARY), Set.of(), 1, pay(2_000, null, 0), cost("bloom.heart.biomass", 1),
                    () -> new GranaryBeat(beats), beats));
        } else {
            out.add(beat(CLEAR_ROAD, "Clear the road", "A wreck lies across the corridor: escort the base's dozer to push it off before the first convoy.",
                    Set.of(CordonPlan.CORRIDOR_WRECK), Set.of(MAW_ON_ROAD), 2, pay(3_000, "heavy_machinery", 5), cost("bloom.cordon.road_delayed", 1),
                    () -> new ClearRoadBeat(beats), beats));
            out.add(beat(TRUCK_DOWN, "A truck goes down", "A loaded truck breaks down in the suburb: cover its people back through the gap.",
                    Set.of(), Set.of(), 2, pay(2_500, "supplies", 10), cost("bloom.cordon.townspeople_lost", 6),
                    () -> new TruckDownBeat(beats), beats));
            out.add(beat(LAST_LOADING, "The last loading", "The last people board slowly: hold the town two minutes longer.",
                    Set.of(), Set.of(), 2, pay(3_000, "food", 20), cost("bloom.cordon.townspeople_lost", 12),
                    () -> new LastLoadingBeat(beats), beats));
            out.add(beat(MILITIA, "The militia ride out", "On the last convoy the town's militia come out to hold the gap.",
                    Set.of(), Set.of(), 1, new Reward(2_000, Map.of(), Map.of("cordon.militia_joined", 1)),
                    cost("bloom.cordon.militia_lost", 1), () -> new MilitiaBeat(beats), beats));
            out.add(beat(MAW_ON_ROAD, "A maw on the road", "A bore opens beside the corridor: collapse it or the convoys go the long way round.",
                    Set.of(CordonPlan.SECOND_ROAD), Set.of(CLEAR_ROAD), 1, pay(4_000, "metals", 20), cost("bloom.heart.satellites", 1),
                    () -> new BoreBeat(beats, true), beats));
            out.add(beat(GAP_FALLS, "The gap falls", "If a gap redoubt falls, hold the gap until the last convoy is home.",
                    Set.of(CordonSetPieces.GAP_POSITIONS), Set.of(), 1, pay(3_500, "supplies", 20), cost("bloom.cordon.gap_lost", 1),
                    () -> new GapFallsBeat(beats), beats));
        }
        return out;
    }

    private static OptionalObjective beat(String key, String title, String briefing, Set<String> needs,
                                          Set<String> excludes, int weight, Reward reward, Cost cost,
                                          Supplier<CordonBeat> make, CordonBeats beats) {
        Function<ObjectiveContext, ObjectiveRuntime> start = ctx -> {
            if (beats == null) throw new IllegalStateException("A read-only Cordon pool cannot start " + key);
            CordonBeat runtime = beats.register(make.get());
            if (runtime instanceof ClearRoadBeat road) road.setUp(ctx.sim());
            return runtime;
        };
        return new OptionalObjective(key, title, briefing, needs, excludes, weight, reward, cost, start);
    }

    private static Reward pay(int credits, String commodity, int quantity) {
        return new Reward(credits, commodity == null ? Map.of() : Map.of(commodity, quantity), Map.of());
    }

    private static Cost cost(String key, int size) {
        return new Cost(key, size);
    }

    /** How many beats a battle rolls at a density, {@code {min, max}}: fewer in a city, where fewer set pieces stand. */
    public static int[] range(CordonPlan.Density density) {
        return density == CordonPlan.Density.URBAN ? new int[]{1, 2} : new int[]{2, 3};
    }

    /** The battle's set: forced by {@link #FORCE_PROPERTY} when it is set, else rolled. */
    public static List<OptionalObjective> roll(CordonVariant variant, CordonPlan.Density density, LaidLayout laid,
                                               long battleSeed, CordonBeats beats) {
        List<OptionalObjective> pool = pool(variant, beats);
        String forced = System.getProperty(FORCE_PROPERTY);
        if (forced != null && !forced.isBlank()) return forced(forced, pool, laid);
        int[] range = range(density);
        return ObjectiveRoll.pick(pool, laid, battleSeed, range[0], range[1]);
    }

    /** The beats a forcing spec names, in pool order, of those that fit the map. */
    static List<OptionalObjective> forced(String spec, List<OptionalObjective> pool, LaidLayout laid) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.equals("none")) return List.of();
        Set<String> wanted = new HashSet<>();
        for (String part : s.split(",")) {
            String p = part.trim();
            wanted.add(CODES.getOrDefault(p.toUpperCase(Locale.ROOT), p));
        }
        List<OptionalObjective> out = new ArrayList<>();
        for (OptionalObjective o : pool) {
            if ((s.equals("all") || wanted.contains(o.key())) && ObjectiveRoll.fits(o, laid)) out.add(o);
        }
        return out;
    }

    /** The titles the briefing shows: the same roll the battle will make. */
    public static List<String> titles(CordonVariant variant, CordonPlan.Density density, LaidLayout laid,
                                      long battleSeed) {
        List<String> out = new ArrayList<>();
        for (OptionalObjective o : roll(variant, density, laid, battleSeed, null)) out.add(o.title());
        return out;
    }

    /**
     * Rolls the battle's beats and starts them on its board, at setup: the board is installed on the battle
     * and advanced from the cordon's sequence.
     */
    public static CordonBeats install(BattleSimulation sim, CordonSequence sequence, CordonVariant variant,
                                      CordonPlan.Density density, PrecinctPlan plan, LaidLayout laid,
                                      List<int[]> corridor, long battleSeed) {
        CordonBeats beats = new CordonBeats(variant, plan, laid, corridor, battleSeed, sequence);
        List<OptionalObjective> rolled = roll(variant, density, laid, battleSeed, beats);
        ObjectiveBoard board = new ObjectiveBoard(rolled, ObjectiveContext.forBattle(sim, laid, battleSeed, PREFIX));
        beats.setBoard(board);
        sequence.attachBeats(beats);
        sim.installOptionalObjectives(board);
        board.start(sim.getSimTickIndex());
        return beats;
    }
}
