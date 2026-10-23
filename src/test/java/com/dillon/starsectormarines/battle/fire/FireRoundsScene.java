package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Tube;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Incendiary rounds from the support weapons (`fire-nouns.md`, Starting fires), on a meadow whose near end is bare.
 * <ul>
 *   <li>{@code mortar}: a squad's tube on an incendiary fire mission onto a militia section standing in grass.</li>
 *   <li>{@code mortar-friends-near}: the same, with two marines in the grass beside the mission's ground.</li>
 *   <li>{@code firebombs}: a comms operator calls a bomber with firebombs onto the militia.</li>
 *   <li>{@code firebombs-friends-near}: the same call with the two marines beside the target.</li>
 * </ul>
 * Verdicts are the round's rules: an incendiary round lights the grass it lands in, and a round whose fire could
 * come to the side's own goes out as an ordinary shell or stick. Kills are not asserted.
 */
public final class FireRoundsScene implements BehaviorScene {

    static final int WIDTH = 160, HEIGHT = 40;
    /** The bare ground the marines start on: everything west of it is a road, east of it grass. */
    private static final int BARE_TO = 30;
    private static final int ENEMY_X = 52, ROW = HEIGHT / 2;
    private static final int TICKS = 30 * 40;
    private static final String RIFLE = "weapon.field-rifle";

    @Override public String id() { return "fire-rounds"; }

    @Override public String label() { return "Incendiary mortar rounds and firebombs light where they land, unless the fire could come back"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        try {
            List<SceneReport> reports = new ArrayList<>();
            reports.add(mortar("mortar", false, frames));
            reports.add(mortar("mortar-friends-near", true, frames));
            reports.add(firebombs("firebombs", false, frames));
            reports.add(firebombs("firebombs-friends-near", true, frames));
            return reports;
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private static SceneBuilder meadow() {
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, x < BARE_TO ? CellTopology.GroundKind.DIRT : CellTopology.GroundKind.GRASS);
            }
        }
        return SceneBuilder.over(grid, topology).seed(20261022L).missionCompletion(false);
    }

    /**
     * Two marines standing in the grass beside the mission's ground: outside the ring a crew keeps clear of its own
     * (aim, scatter and margin), inside what a fire lit there would reach.
     */
    private static void friendsNear(SceneBuilder builder) {
        builder.squad("friends").faction(Faction.MARINE).size(2).at(ENEMY_X, ROW + 8).inFile(ENEMY_X, ROW + 8, 1, 0)
                .primary(RIFLE).stationary().done();
    }

    private static void militia(SceneBuilder builder) {
        builder.squad("enemy").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3).at(ENEMY_X, ROW)
                .inFile(ENEMY_X, ROW, 0, 1).primary(RIFLE).stationary().done();
    }

    private SceneReport mortar(String loop, boolean friends, FrameSink frames) throws Exception {
        SceneBuilder builder = meadow();
        FireMission mission = new FireMission(ENEMY_X, ROW, 6, true);
        builder.squad("team").faction(Faction.MARINE).size(8).at(12, ROW).primary(RIFLE)
                .special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1)
                .assigned(id -> ObjectiveAssignment.attackMove(id, WIDTH - 6, ROW).withFireMission(mission)).done();
        militia(builder);
        if (friends) friendsNear(builder);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long gunner = world.members("team")[0];
            long[] near = friends ? world.members("friends") : new long[0];
            // fired: rounds, mission rounds, incendiary rounds, incendiary rounds fired while a friend still stood.
            int[] fired = new int[4];
            SceneRun.of(world).observe((s, tick) -> {
                Tube tube = s.fieldMortars().tubeOfCarrier(gunner);
                if (tube == null) return;
                fired[0] = Math.max(fired[0], tube.roundsFired());
                fired[1] = Math.max(fired[1], tube.missionRoundsFired());
                int incendiary = tube.incendiaryRoundsFired();
                if (incendiary > fired[2] && anyAlive(s, near)) fired[3] += incendiary - fired[2];
                fired[2] = Math.max(fired[2], incendiary);
            }).frames(frames, loop, 30, tick -> loop + " / tick " + tick).run(TICKS);
            int burnt = touched(sim.fireFieldIfPresent(), ENEMY_X - 12, ENEMY_X + 12);
            List<Verdict> verdicts = new ArrayList<>();
            if (!friends) {
                verdicts.add(Verdict.of("incendiary-rounds-light-the-mission", fired[2] > 0 && burnt > 0,
                        fired[2] + " incendiary rounds of " + fired[0] + " fired, " + burnt + " cells caught round the mission"));
            } else {
                // The rule holds while the friends stand; once the militia has killed them the fire can come back
                // to nobody, and an incendiary round is right.
                verdicts.add(Verdict.of("shells-where-the-fire-could-come-back", fired[1] > 0 && fired[3] == 0,
                        fired[3] + " incendiary rounds fired while a marine stood in the mission's grass ("
                                + fired[2] + " of " + fired[1] + " mission rounds incendiary in all, " + burnt + " cells caught)"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("roundsFired", fired[0]);
            metrics.put("missionRounds", fired[1]);
            metrics.put("incendiaryRounds", fired[2]);
            metrics.put("cellsCaught", burnt);
            if (friends) metrics.put("incendiaryWithFriendsStanding", fired[3]);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static boolean anyAlive(BattleSimulation sim, long[] ids) {
        for (long id : ids) if (sim.world().isAlive(id)) return true;
        return false;
    }

    private SceneReport firebombs(String loop, boolean friends, FrameSink frames) throws Exception {
        SceneBuilder builder = meadow();
        builder.squad("observers").faction(Faction.MARINE).size(3).at(23, ROW).primary("weapon.smg")
                .special("special.comms-radio", 1).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 23, ROW)).done();
        militia(builder);
        if (friends) friendsNear(builder);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE,
                    AirPoolAllowance.of(new AirPoolStock(1, 1, 40f), null, /*aiDirected*/ false))));
            AirPoolService.Outcome[] outcome = new AirPoolService.Outcome[1];
            SceneRun.of(world).observe((s, tick) -> {
                if (tick == 30) outcome[0] = s.airPool().requestIncendiary(s, Faction.MARINE, ENEMY_X + 0.5f, ROW + 0.5f,
                        true, id -> true);
            }).frames(frames, loop, 30, tick -> loop + " / tick " + tick).run(TICKS);
            int burnt = touched(sim.fireFieldIfPresent(), ENEMY_X - 12, ENEMY_X + 12);
            boolean called = outcome[0] == AirPoolService.Outcome.CALLING;
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("the-call-goes-out", called, "outcome " + outcome[0]));
            if (!friends) {
                verdicts.add(Verdict.of("firebombs-light-the-target", burnt > 0, burnt + " cells caught round the target"));
            } else {
                verdicts.add(Verdict.of("an-ordinary-stick-where-the-fire-could-come-back", burnt == 0,
                        burnt + " cells caught round a target with two marines in its grass"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("cellsCaught", burnt);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static int touched(FireField f, int x0, int x1) {
        if (f == null) return 0;
        int n = 0;
        for (int y = 0; y < HEIGHT; y++)
            for (int x = Math.max(0, x0); x < Math.min(WIDTH, x1); x++)
                if (f.stateAt(y * WIDTH + x) != FireField.UNBURNT) n++;
        return n;
    }
}
