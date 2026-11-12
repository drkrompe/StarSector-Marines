package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Long Survey's win against a fallen hull's claim ({@code long-survey-arc.md}, Chapters; {@code fallen-hull-nouns.md}):
 * the company's objective in chapters 2 to 4, read from the hull's own rules ({@link FallenHull#stakes()},
 * {@link FallenHull#forge()}), never from a list the setup keeps beside them.
 *
 * <ul>
 *   <li>{@link Goal#NAMED}, Stakes: each of the named stakes down, the ones the ground planted.</li>
 *   <li>{@link Goal#CLAIM}, The Line: every stake on the map down, the ones the claim plants as it goes as well.</li>
 *   <li>{@link Goal#FORGE}, The Surveyor: the autoforge out.</li>
 * </ul>
 *
 * A stake is down when it is scrapped or stranded (its chain to the hull cut): stranding counts, so cutting a chain
 * can win ground without fighting every stake on it. It never fails: the company is lost as every chapter is, by
 * {@link EliminateFactionObjective}.
 */
public final class HullClaimObjective implements Objective {

    /** What the company must bring down. */
    public enum Goal { NAMED, CLAIM, FORGE }

    private final FallenHull hull;
    private final Goal goal;
    private final Set<Integer> named;
    private final String name;
    private boolean complete;

    private HullClaimObjective(FallenHull hull, Goal goal, Collection<Integer> named, String name) {
        this.hull = Objects.requireNonNull(hull, "hull");
        this.goal = goal;
        this.named = Set.copyOf(new TreeSet<>(named));
        this.name = name;
    }

    /** Each of {@code stakes} (lattice point ids) down. */
    public static HullClaimObjective named(FallenHull hull, Collection<Integer> stakes, String name) {
        if (stakes.isEmpty()) throw new IllegalArgumentException("name at least one stake");
        return new HullClaimObjective(hull, Goal.NAMED, stakes, name);
    }

    /** Every stake on the map down, whenever it was planted. */
    public static HullClaimObjective claim(FallenHull hull, String name) {
        return new HullClaimObjective(hull, Goal.CLAIM, List.of(), name);
    }

    /** The hull's autoforge out. */
    public static HullClaimObjective forge(FallenHull hull, String name) {
        return new HullClaimObjective(hull, Goal.FORGE, List.of(), name);
    }

    /** Whether a stake is down: scrapped, stranded, or gone from the claim. */
    public static boolean down(SurveyStake stake) {
        return stake == null || stake.stage() == SurveyStake.Stage.SCRAP || stake.stranded();
    }

    /** Whether each of {@code ids} is down in {@code stakes}; an id the claim no longer holds is down. */
    public static boolean allDown(Map<Integer, SurveyStake> stakes, Collection<Integer> ids) {
        for (int id : ids) if (!down(stakes.get(id))) return false;
        return true;
    }

    /** The stakes this objective reads, by lattice point: the named ones, or every stake on the claim. */
    public List<Integer> targets() {
        return switch (goal) {
            case NAMED -> new ArrayList<>(new TreeSet<>(named));
            case CLAIM -> new ArrayList<>(new TreeSet<>(hull.stakes().keySet()));
            case FORGE -> List.of();
        };
    }

    public Goal goal() { return goal; }

    public FallenHull hull() { return hull; }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete) return;
        complete = switch (goal) {
            case NAMED -> allDown(hull.stakes(), named);
            case CLAIM -> hull.claimDown();
            case FORGE -> !hull.forge().lit();
        };
    }

    @Override public boolean isComplete() { return complete; }

    @Override public boolean isFailed() { return false; }

    @Override public String displayName() { return name; }
}
