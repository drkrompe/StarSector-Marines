package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
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
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How fires start (`fire-nouns.md`, Starting fires). One loop per case:
 * <ul>
 *   <li>{@code blasts}: on a meadow, an incendiary blast; an ordinary shell while the
 *   country is not dry; the same shell once it is; and a shell on a tree's crown.</li>
 *   <li>{@code kit}: incendiary grenadiers on a firebreak facing a cluster in grass,
 *   flamers on a firebreak facing one man in grass, and flamers standing in the
 *   meadow their targets stand in.</li>
 * </ul>
 * Verdicts are the ignition rules: an incendiary blast on fuel lights it, an ordinary
 * shell lights nothing while the country is not dry and catches grass once it is, and an
 * ordinary shell alone never lights a living crown. The kit loop's are the incendiary
 * carrier's rules: it throws from ground the fire cannot reach, one man in fuel is
 * worth a throw, and it does not light the meadow it stands in ({@code FireReach}).
 */
public final class FireIgnitionScene implements BehaviorScene {

    static final int WIDTH = 120, HEIGHT = 40;
    private static final long SEED = 20261022L;

    @Override public String id() { return "fire-ignition"; }

    @Override public String label() { return "Incendiaries always light; ordinary shells and wrecks only where the country is dry"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(blasts(frames), kit(frames));
    }

    private static NavigationGrid meadow(CellTopology topology) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                // Bare strips between the test plots so one plot's fire cannot reach the next.
                boolean bare = x % 30 >= 24;
                topology.setGroundKind(x, y, bare ? CellTopology.GroundKind.DIRT : CellTopology.GroundKind.GRASS);
            }
        }
        return grid;
    }

    private static PendingDetonation shell(float x, float y) {
        return new PendingDetonation(CombatTelemetryService.NO_ATTACKER, x, y, 0f, 3f, 0f, 0f, 0, Faction.MARINE, true);
    }

    private SceneReport blasts(FrameSink frames) throws Exception {
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = meadow(topology);
        List<Doodad> doodads = new ArrayList<>();
        // The crown plot is bare ground under its tree, so only the crown itself is fuel.
        for (int y = 0; y < HEIGHT; y++) for (int x = 90; x < 114; x++) topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
        ForestField.Tree tree = new ForestField.Tree(98, 16, TreeCanopy.OAK, 0);
        ForestBrush.stampTrees(List.of(tree), grid, topology, doodads, null, false);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.unit("a", Faction.MARINE, UnitType.MARINE, 1, 1);
        builder.unit("b", Faction.DEFENDER, UnitType.MARINE, WIDTH - 2, HEIGHT - 2);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            for (Doodad d : doodads) sim.addDoodad(d);
            int[] litBy = new int[4];
            SceneRun.of(world).observe((s, tick) -> {
                if (tick == 10) {
                    s.detonateNow(shell(12f, 20f).incendiary(2600));
                    s.fireField().setTinder(false);
                    s.detonateNow(shell(42f, 20f));
                }
                if (tick == 40) {
                    s.fireField().setTinder(true);
                    s.detonateNow(shell(72f, 20f));
                    s.detonateNow(shell(100.5f, 18.5f));
                }
                if (tick == 120) {
                    FireField f = s.fireField();
                    litBy[0] = touched(f, 0, 24);
                    litBy[1] = touched(f, 30, 54);
                    litBy[2] = touched(f, 60, 84);
                    litBy[3] = touched(f, 90, 114);
                }
            }).frames(frames, "blasts", 15, tick -> "blasts / tick " + tick).run(150);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("incendiary-lights-fuel", litBy[0] > 0, litBy[0] + " cells caught round the incendiary"));
            verdicts.add(Verdict.of("ordinary-shell-lights-nothing-unless-dry", litBy[1] == 0,
                    litBy[1] + " cells caught round an ordinary shell on a country that is not dry"));
            verdicts.add(Verdict.of("ordinary-shell-catches-dry-grass", litBy[2] > 0,
                    litBy[2] + " cells caught round an ordinary shell on dry grass"));
            verdicts.add(Verdict.of("ordinary-shell-never-lights-a-crown", litBy[3] == 0,
                    litBy[3] + " crown cells caught from an ordinary shell alone"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("incendiaryCells", litBy[0]);
            metrics.put("dryShellCells", litBy[2]);
            return new SceneReport(id(), "blasts", 150, verdicts, metrics);
        }
    }

    private SceneReport kit(FrameSink frames) throws Exception {
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = meadow(topology);
        // A firebreak on the first two plots for the carriers to stand on: wider than heat crosses.
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x <= 8; x++) topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
            for (int x = 30; x <= 35; x++) topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
        }
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.squad("grenadiers").at(3, 8).inFile(3, 8, 0, 2).size(4)
                .primary("weapon.smg").special("special.incendiary-grenade", 4).stationary().done();
        builder.squad("flamers").at(31, 8).inFile(31, 8, 0, 2).size(4)
                .primary("weapon.smg").special("special.flamer", 4).stationary().done();
        builder.squad("in-meadow").at(64, 8).inFile(64, 8, 0, 2).size(4)
                .primary("weapon.smg").special("special.flamer", 4).stationary().done();
        builder.squad("targets-a").faction(Faction.DEFENDER).at(11, 10).inFile(11, 10, 0, 1).size(5).unarmed().stationary().done();
        // A lone man hard enough to outlast the flamers' rifles while they ready a throw.
        builder.squad("target-b").faction(Faction.DEFENDER).at(37, 11).inFile(37, 11, 0, 1).size(1).health(600f).unarmed().stationary().done();
        // Close enough that a fire lit on them would carry back over the squad's own meadow.
        builder.squad("targets-c").faction(Faction.DEFENDER).at(67, 10).inFile(67, 10, 0, 1).size(3).unarmed().stationary().done();
        SceneWorld world = builder.build();
        String[] carriers = {"grenadiers", "flamers", "in-meadow"};
        try (BattleSimulation sim = world.sim()) {
            int issued = 0;
            int[] start = new int[carriers.length], left = new int[carriers.length];
            for (int c = 0; c < carriers.length; c++) {
                for (long id : world.members(carriers[c])) {
                    if (!sim.world().hasSecondaryWeapon(id)) continue;
                    issued++;
                    start[c] += sim.world().secondaryAmmo(id);
                }
                left[c] = start[c];
            }
            int ticks = 30 * 30;
            SceneRun.of(world).observe((s, tick) -> {
                for (int c = 0; c < carriers.length; c++) {
                    int ammo = 0;
                    boolean all = true;
                    for (long id : world.members(carriers[c])) {
                        if (s.resolveUnit(id) == 0L) { all = false; continue; }
                        if (s.world().hasSecondaryWeapon(id)) ammo += s.world().secondaryAmmo(id);
                    }
                    // Rounds still carried by the living; a death takes its rounds with it, so read while all live.
                    if (all) left[c] = ammo;
                }
            }).frames(frames, "kit", 30, tick -> "kit / tick " + tick).run(ticks);
            FireField f = sim.fireFieldIfPresent();
            int grenadeFire = f == null ? 0 : touched(f, 0, 24);
            int flamerFire = f == null ? 0 : touched(f, 30, 54);
            int meadowFire = f == null ? 0 : touched(f, 60, 84);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("kit-is-issued", issued == 12, issued + " of 12 carriers hold their special"));
            verdicts.add(Verdict.of("throws-from-a-firebreak", start[0] - left[0] > 0 && grenadeFire > 0,
                    (start[0] - left[0]) + " incendiary grenades thrown from the firebreak at a cluster in grass, "
                            + grenadeFire + " cells caught"));
            verdicts.add(Verdict.of("one-target-in-fuel-is-worth-a-flame", start[1] - left[1] > 0 && flamerFire > 0,
                    (start[1] - left[1]) + " flamer rounds at a lone man in grass, " + flamerFire + " cells caught"));
            verdicts.add(Verdict.of("holds-fire-in-its-own-meadow", start[2] == left[2] && meadowFire == 0,
                    (start[2] - left[2]) + " flamer rounds thrown by a squad standing in the meadow it would light, "
                            + meadowFire + " cells caught"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("grenadeCellsBurnt", grenadeFire);
            metrics.put("flamerCellsBurnt", flamerFire);
            metrics.put("meadowCellsBurnt", meadowFire);
            for (int c = 0; c < carriers.length; c++) {
                int alive = 0;
                for (long id : world.members(carriers[c])) if (sim.resolveUnit(id) != 0L) alive++;
                metrics.put(carriers[c] + "Alive", alive);
            }
            return new SceneReport(id(), "kit", ticks, verdicts, metrics);
        }
    }

    private static int touched(FireField f, int x0, int x1) {
        int n = 0;
        for (int y = 0; y < HEIGHT; y++)
            for (int x = x0; x < x1; x++)
                if (f.stateAt(y * WIDTH + x) != FireField.UNBURNT) n++;
        return n;
    }
}
