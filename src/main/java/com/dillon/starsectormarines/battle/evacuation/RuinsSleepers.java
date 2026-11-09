package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rav under a ruins site ({@code ruins-expedition.md}, Occupants: Rav): the swarm director's ruins profile. An old
 * seeding site's sleepers lie in closed bores in the deeper layers. Each bore stirs with time spent below, with
 * gunfire near it and, loudest, with a vault breach under way; a bore stirred past its threshold opens and feeds the
 * director's surges. The breach itself wakes every bore still closed and lifts the field to full strength, so the
 * pressure peaks on the egress and follows the haul to the lift.
 *
 * <p>There is no mother bore, no harvest and no cohort: the director runs from the bores alone
 * ({@link BattleSimulation#configureSwarmFromBores}). The later strains come only at the larger grades, through the
 * tier a bore may grow to ({@link #ceiling}). Nothing here reaches the campaign bloom or a reservoir's rest.
 */
public final class RuinsSleepers {

    /** Stir a closed bore gains per second the company spends below. */
    static final float TIME_STIR_PER_SECOND = 1f;
    /** Stir every closed bore gains per second while the vault's breach is under way. */
    static final float BREACH_STIR_PER_SECOND = 4f;
    /** Stir per unit of a shot's loudness ({@link ShotEvent#noiseMagnitude}) heard within {@link #HEAR_CELLS}. */
    static final float SHOT_STIR = 0.6f;
    static final float HEAR_CELLS = 16f;
    /** The first bore opens at this much stir; each later one needs {@link #WAKE_STEP} more. */
    static final float WAKE_BASE = 45f;
    static final float WAKE_STEP = 30f;
    /** The field's share of the bores' capacity before the breach, and after it. */
    static final float QUIET_PRESSURE = 0.6f;
    static final float EGRESS_PRESSURE = 1f;
    /** Fewest cells between two bores. */
    static final int BORE_SPACING = 10;

    private final List<SwarmBore> bores;
    private final List<Integer> numbers;
    private final Stir stir;
    private final RuinsVaultObjective vault;
    private final RuinsGrade grade;
    private boolean egress;
    /** Shots already heard from this frame's list, which a frame may span several steps of. */
    private int shotsHeard;

    private RuinsSleepers(List<Integer> numbers, List<SwarmBore> bores, Stir stir, RuinsVaultObjective vault,
                          RuinsGrade grade) {
        this.grade = grade;
        this.numbers = numbers;
        this.bores = bores;
        this.stir = stir;
        this.vault = vault;
    }

    /** Closed bores at a grade: larger ruins sleep more. */
    static int boreCount(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> 2;
            case WIDESPREAD -> 3;
            case EXTENSIVE -> 4;
            case VAST -> 5;
        };
    }

    /** The tier a bore may grow to: runners only at scattered ruins, the heavy strains only at the larger grades. */
    static BoreTier ceiling(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> BoreTier.FISSURE;
            case WIDESPREAD -> BoreTier.BORE;
            case EXTENSIVE, VAST -> BoreTier.MAW;
        };
    }

    /**
     * Opens the grade's bores closed in the deeper layers and starts the director from them. Call once at setup, after
     * the vault objective is installed. Returns null, having changed nothing, when the battle has no ruins vault.
     *
     * @param delve the ground's delve; null places the bores along the vault's route out instead
     */
    public static RuinsSleepers install(BattleSimulation sim, RuinsDelve delve, RuinsGrade grade, long seed) {
        RuinsVaultObjective vault = null;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective ruins) vault = ruins;
        }
        if (vault == null || vault.liftPoints().isEmpty()) return null;
        int count = boreCount(grade);
        List<int[]> sites = delve != null ? sites(delve, count) : List.of();
        if (sites.isEmpty()) sites = sitesAlong(vault.liftPoints().get(0).route(), count);
        List<Integer> numbers = new ArrayList<>();
        List<SwarmBore> opened = new ArrayList<>();
        for (int i = 0; i < sites.size(); i++) {
            int number = i + 1;
            numbers.add(number);
            SwarmBore bore = sim.swarmBores().open(sim, number, false, sites.get(i)[0], sites.get(i)[1]);
            opened.add(bore);
            if (bore != null) close(sim, bore, grade);
        }
        sim.setSwarmPressureShare(QUIET_PRESSURE);
        sim.configureSwarmFromBores(seed);
        RuinsSleepers sleepers = new RuinsSleepers(numbers, opened, new Stir(sites.size()), vault, grade);
        sim.installRuinsSleepers(sleepers);
        return sleepers;
    }

    private static void close(BattleSimulation sim, SwarmBore bore, RuinsGrade grade) {
        sim.swarmBores().hold(bore);
        sim.swarmBores().capTier(bore, ceiling(grade));
    }

    /** One step: closes any bore that opened late, stirs the closed ones and opens those stirred enough. */
    public void tick(BattleSimulation sim) {
        adoptLateBores(sim);
        boolean breached = vault.breached();
        boolean breaching = !breached && vault.breachProgress() > 0f;
        float[] heard = new float[bores.size()];
        List<ShotEvent> frame = sim.getShotsThisFrame();
        if (frame.size() < shotsHeard) shotsHeard = 0;
        List<ShotEvent> shots = frame.subList(shotsHeard, frame.size());
        shotsHeard = frame.size();
        for (int i = 0; i < bores.size(); i++) {
            SwarmBore bore = bores.get(i);
            if (bore == null || stir.awake(i)) continue;
            for (ShotEvent shot : shots) {
                if (shot.shooterFaction == Faction.DEFENDER) continue;
                float dx = shot.fromX - (bore.x() + 0.5f);
                float dy = shot.fromY - (bore.y() + 0.5f);
                if (dx * dx + dy * dy <= HEAR_CELLS * HEAR_CELLS) heard[i] += shot.noiseMagnitude();
            }
        }
        int tick = sim.getSimTickIndex();
        for (int i : stir.advance(BattleSimulation.TICK_DT, heard, breaching, breached)) {
            SwarmBore bore = bores.get(i);
            if (bore != null) sim.swarmBores().surgeNow(bore, tick);
        }
        if (breached && !egress) {
            egress = true;
            sim.setSwarmPressureShare(EGRESS_PRESSURE);
        }
    }

    /** A bore whose site was blocked opens when it clears; it opens closed like the rest. */
    private void adoptLateBores(BattleSimulation sim) {
        for (int i = 0; i < bores.size(); i++) {
            if (bores.get(i) != null) continue;
            for (SwarmBore bore : sim.swarmBores().bores()) {
                if (bore.number() != numbers.get(i) || bore.mother()) continue;
                bores.set(i, bore);
                if (!stir.awake(i)) close(sim, bore, grade);
                else sim.swarmBores().capTier(bore, ceiling(grade));
            }
        }
    }

    /** How many bores have opened. */
    public int awake() {
        int n = 0;
        for (int i = 0; i < bores.size(); i++) if (stir.awake(i)) n++;
        return n;
    }

    /** Whether the breach has lifted the field to full strength. */
    public boolean egress() { return egress; }

    /**
     * Bore sites in the deeper layers (every layer but the landing's, or the one layer of a single-layer site):
     * side-room interiors first, then each layer's middle, taken deepest layer first and at least
     * {@link #BORE_SPACING} apart. At most {@code count}; fewer when the ground has no room for more.
     */
    static List<int[]> sites(RuinsDelve delve, int count) {
        List<RuinsDelve.Layer> deeper = new ArrayList<>();
        int first = delve.layers().size() > 1 ? 1 : 0;
        for (RuinsDelve.Layer layer : delve.layers()) if (layer.index() >= first) deeper.add(layer);
        deeper.sort((a, b) -> Integer.compare(b.index(), a.index()));
        List<List<int[]>> perLayer = new ArrayList<>();
        for (RuinsDelve.Layer layer : deeper) {
            List<int[]> candidates = new ArrayList<>();
            for (RuinsDelve.SideRoom room : layer.rooms()) {
                candidates.add(new int[]{room.interior().x(), room.interior().y()});
            }
            candidates.add(new int[]{layer.ground().centreX(), layer.ground().centreY()});
            perLayer.add(candidates);
        }
        List<int[]> chosen = new ArrayList<>();
        // Round the layers, so the bores spread through the deep ground rather than filling the vault's layer.
        for (int round = 0; chosen.size() < count; round++) {
            boolean any = false;
            for (List<int[]> candidates : perLayer) {
                if (round >= candidates.size()) continue;
                any = true;
                int[] site = candidates.get(round);
                if (spaced(chosen, site) && chosen.size() < count) chosen.add(site);
            }
            if (!any) break;
        }
        return chosen;
    }

    /** Fallback sites on the vault's route out, from the vault end over its deeper half. */
    static List<int[]> sitesAlong(int[] route, int count) {
        List<int[]> chosen = new ArrayList<>();
        int cells = route.length / 2;
        if (cells == 0) return chosen;
        for (int i = 0; i < count; i++) {
            int at = Math.min(cells - 1, (cells / 2) * (i + 1) / (count + 1));
            int[] site = {route[at * 2], route[at * 2 + 1]};
            if (spaced(chosen, site)) chosen.add(site);
        }
        return chosen;
    }

    private static boolean spaced(List<int[]> chosen, int[] site) {
        for (int[] other : chosen) {
            if (Math.abs(other[0] - site[0]) + Math.abs(other[1] - site[1]) < BORE_SPACING) return false;
        }
        return true;
    }

    /**
     * The waking rule, pure: each closed bore's stir and threshold. Time stirs every closed bore alike, gunfire stirs
     * the bores that hear it, and a breach under way stirs them all fast; the breach's end opens every one still
     * closed. Bore {@code i}'s threshold is {@link #WAKE_BASE} plus {@code i} steps, so they open one by one.
     */
    static final class Stir {
        private final float[] stir;
        private final boolean[] awake;

        Stir(int bores) {
            stir = new float[bores];
            awake = new boolean[bores];
        }

        static float threshold(int bore) {
            return WAKE_BASE + bore * WAKE_STEP;
        }

        boolean awake(int bore) { return awake[bore]; }

        float stir(int bore) { return stir[bore]; }

        /**
         * @param dt        seconds this step
         * @param heard     each bore's summed loudness of the shots it heard this step
         * @param breaching whether the vault's breach is under way
         * @param breached  whether the vault is breached
         * @return the bores that opened this step
         */
        List<Integer> advance(float dt, float[] heard, boolean breaching, boolean breached) {
            List<Integer> opened = new ArrayList<>();
            for (int i = 0; i < stir.length; i++) {
                if (awake[i]) continue;
                stir[i] += dt * (TIME_STIR_PER_SECOND + (breaching ? BREACH_STIR_PER_SECOND : 0f))
                        + SHOT_STIR * heard[i];
                if (breached || stir[i] >= threshold(i)) {
                    awake[i] = true;
                    opened.add(i);
                }
            }
            return opened;
        }
    }
}
