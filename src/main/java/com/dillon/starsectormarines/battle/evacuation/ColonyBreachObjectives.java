package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.ambient.BuildingWork;
import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.staffed.Loading;
import com.dillon.starsectormarines.battle.staffed.LoadingObjective;
import com.dillon.starsectormarines.battle.staffed.StaffedBuildings;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.MapResult;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Colony Breach's optional objectives ({@code optional-objectives.md}): today one, "Secure the
 * loading" ({@code staffed-buildings.md}). The colony's largest warehouse is worked by its own
 * people, and they are loading the colony's stores onto a truck at the door while the colony is
 * being eaten; the company that holds the yard until the truck is away is paid out of what it
 * carries.
 *
 * <p>Beside the rescue, never instead of it: the cohort and the lift are still the contract, and
 * this succeeds or fails only itself.
 */
public final class ColonyBreachObjectives {

    /** Crates aboard before the truck leaves: a few minutes' carrying for a full crew left alone. */
    public static final int LOADING_CRATES = 16;
    private static final long SALT = 0x4C4F4144494E4700L;

    private ColonyBreachObjectives() { }

    /**
     * Staffs the colony's largest warehouse, parks its truck, rolls the pool against it, and installs
     * and starts the board on the battle; null where the colony has no warehouse to staff or no truck
     * could stand at its door and leave, and then no objective is offered.
     */
    public static ObjectiveBoard install(BattleSimulation sim, MapResult map, long battleSeed) {
        // A freight yard's warehouse first: its bay is where a truck is meant to stand (set-piece-blocks.md),
        // where a truck standing there can drive off the map. With no such bay, any warehouse but a yard's:
        // a yard whose street a truck cannot follow out is a cul-de-sac for one parked by search too.
        List<LoadingBay> bays = Loading.drivable(sim.getGrid(), sim.getTopology(), map.loadingBays);
        List<StaffedBuildings.Staffed> staffed = StaffedBuildings.install(sim, map, battleSeed ^ SALT,
                EnumSet.of(BuildingWork.Kind.WAREHOUSE), 1,
                bays.isEmpty() ? building -> Loading.bayOf(building, map.loadingBays) == null
                        : building -> Loading.bayOf(building, bays) != null);
        if (staffed.isEmpty()) return null;
        Loading loading = Loading.install(sim, staffed.get(0), LOADING_CRATES, bays);
        if (loading == null) return null;
        List<OptionalObjective> rolled = ObjectiveRoll.pick(pool(loading), map.layout, battleSeed ^ SALT, 1, 1);
        ObjectiveBoard board = new ObjectiveBoard(rolled, ObjectiveContext.forBattle(sim, map.layout, battleSeed,
                ColonyBreachSequence.BEAT_PREFIX));
        board.start(sim.getSimTickIndex());
        sim.installOptionalObjectives(board);
        return board;
    }

    static List<OptionalObjective> pool(Loading loading) {
        return List.of(LoadingObjective.entry("Secure the loading",
                "The colony's stores are going onto a truck at the warehouse: hold the yard until it is away.",
                1, new Reward(3_500, Map.of("supplies", 20), Map.of()), new Cost("bloom.colony.stores_lost", 1),
                () -> loading));
    }
}
