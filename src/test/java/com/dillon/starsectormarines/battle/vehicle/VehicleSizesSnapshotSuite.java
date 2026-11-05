package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Every hull side by side at one scale, to judge their sizes against each other: longest first, in rows of seven, every hull
 * nose up with its tail on its row's line and its turret ahead. Owner-facing look frames only; there is no verdict.
 * The hulls' order and positions go to stdout, so the frames can be labelled.
 */
public final class VehicleSizesSnapshotSuite implements SnapshotSuite {

    private static final float COLUMN = 4f;
    private static final int PER_ROW = 7;

    @Override public String id() { return "vehicle-sizes"; }

    @Override public String label() { return "Every vehicle hull side by side at one scale"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), lineup());
    }

    static LookReview.Shot lineup() {
        List<VehicleType> types = new ArrayList<>(Arrays.asList(VehicleType.values()));
        types.sort(Comparator.comparingDouble((VehicleType t) -> t.drawnLengthCells).reversed());
        float longest = types.get(0).drawnLengthCells;
        float rowHeight = longest + 2f;
        int rows = (types.size() + PER_ROW - 1) / PER_ROW;
        int width = (int) (COLUMN * PER_ROW + 8);
        int height = (int) (rowHeight * rows + 6);
        return new LookReview.Shot("vehicle-sizes", LookGround.verdant(), width, height, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            List<Consumer<BattleSimulation>> holds = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) {
                VehicleType type = types.get(i);
                int row = i / PER_ROW;
                float x = placement.site().x(4f + COLUMN * (i % PER_ROW + .5f));
                float tail = placement.site().y(3f + rowHeight * row + 1f);
                float y = tail + type.drawnLengthCells / 2f;
                long id = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(x, y));
                GroundTurret turret = sim.convoy().turret(id);
                if (turret != null) {
                    turret.ammo = 0;
                    turret.unlimited = false;
                    turret.loaderSeconds = Float.POSITIVE_INFINITY;
                }
                placement.subject(type.name(), id);
                System.out.printf(Locale.ROOT, "SIZE %d %s x=%.2f tail=%.2f drawn=%.2f footprint=%.2fx%.2f%n", i,
                        type, x, tail, type.drawnLengthCells, type.visualLengthCells, type.visualWidthCells);
                holds.add(s -> {
                    s.convoy().body(id).teleport(x, y, 0f);
                    GroundTurret t = s.convoy().turret(id);
                    if (t != null) t.facingDeg = 0f;
                });
            }
            placement.beforeTick(s -> holds.forEach(h -> h.accept(s)));
            return sim;
        }, placement -> {
            String[] all = types.stream().map(Enum::name).toArray(String[]::new);
            return List.of(LookReview.View.followAtPlay(placement, all),
                    LookReview.View.follow("close", placement, COLUMN * PER_ROW + 4f, all));
        }, 12, 6, 1, new int[] {1});
    }
}
