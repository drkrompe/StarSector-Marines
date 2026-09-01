package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A map has one keep, and it is the garrison's.
 *
 * <p>On a map with no biome every settlement's military-base compound emitted a
 * {@code COMMAND_POST}, which is a second keep and then a third:
 * {@code ConquestCommand.canonicalKeep} returns null for more than one, so the
 * keep phase of the command model quietly switches off. What a settlement's base
 * actually is on a map about an installation is a supply hub — exactly what the
 * port's and the city's bases are on the stock recipe, where the biome says so.
 *
 * <p>Asked of a whole generated map because the fault is about how many there
 * are, which is a property of the map and not of one filler call.
 */
class PrecinctOneKeepTest {

    private static final int W = 560;
    private static final int H = 336;
    private static final long SEED = 42L;

    private static final TargetProfile WORLD = new TargetProfile(
            7, 50, 6, 3, "", EnumSet.noneOf(EconomicFunction.class),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    @Test
    void theGarrisonHoldsTheOnlyCommandPost() {
        PrecinctPlan plan = PrecinctPlan.derive(WORLD, PrecinctPlan.Sprawl.BALANCED,
                Fortification.Demand.UNSTATED, MapPlacement.NORTH, MapPlacement.SOUTH,
                W, H, new Random(SEED));
        MapResult map = new BspCityGenerator().generate(W, H, SEED, null, WORLD, plan);
        Precinct objective = plan.objective();

        List<TacticalNode> keeps = map.tacticalMap.ofKind(TacticalNode.Kind.COMMAND_POST);
        assertEquals(1, keeps.size(), "the map carries " + keeps.size()
                + " command posts; more than one disables the keep phase entirely");

        // The settlement bases are the ones that used to be keeps. They are
        // identified by distance from the garrison seed rather than through a
        // claim lookup: what matters is that a base out in the town emits a
        // supply node, and a node inside the garrison's own ground would satisfy
        // the assertion for the wrong reason.
        List<TacticalNode> stores = new ArrayList<>();
        stores.addAll(map.tacticalMap.ofKind(TacticalNode.Kind.BARRACKS));
        stores.addAll(map.tacticalMap.ofKind(TacticalNode.Kind.ARMORY));
        int far = 0;
        for (TacticalNode node : stores) {
            int dx = node.anchorX - objective.seedX();
            int dy = node.anchorY - objective.seedY();
            if (dx * dx + dy * dy > FAR * FAR) far++;
        }
        assertTrue(far > 0, "no supply node stands more than " + FAR + " cells from the "
                + "garrison seed, so nothing out in the settlement is emitting one");

        TacticalNode keep = keeps.get(0);
        int dx = keep.anchorX - objective.seedX();
        int dy = keep.anchorY - objective.seedY();
        assertTrue(dx * dx + dy * dy <= FAR * FAR, "the one command post stands "
                + Math.round(Math.sqrt(dx * dx + dy * dy)) + " cells from the garrison seed, "
                + "so the keep belongs to something other than the objective");
    }

    /**
     * How far from the garrison's seed is "somewhere else".
     *
     * <p>Comfortably outside a garrison's own claim at this map size and
     * comfortably inside the separation the plan keeps between two seeds, so a
     * node either belongs to the installation or belongs to another place.
     */
    private static final int FAR = 55;
}
