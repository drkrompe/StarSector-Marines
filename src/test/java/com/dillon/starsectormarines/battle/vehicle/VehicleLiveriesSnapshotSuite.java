package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.appearance.VehiclePaint;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.ReviewFactions;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Hegemony vehicle line in every paint ({@link VehiclePaint}): a row a paint, each the APC, its two carriers, the
 * medium tank, the Onager and a wrecked APC, nose up and turret ahead. The marine side is painted as an Independent
 * employer's, the defenders as pirates and civilians keep the Hegemony's masters, so the bottom row is the Hegemony,
 * the middle Independent and the top pirates. Owner-facing look frames only; there is no verdict.
 */
public final class VehicleLiveriesSnapshotSuite implements SnapshotSuite {

    private static final List<VehicleType> LINE = List.of(VehicleType.HEAVY_APC, VehicleType.FIRE_SUPPORT_APC,
            VehicleType.LRM_CARRIER, VehicleType.MEDIUM_TANK, VehicleType.BREAKTHROUGH_TANK);
    /** Bottom to top: the Hegemony's masters, then the marine side as Independent, then the defenders as pirates. */
    private static final List<Faction> ROWS = List.of(Faction.CIVILIAN, Faction.MARINE, Faction.DEFENDER);
    private static final float COLUMN = 4f;
    private static final float ROW = 5.4f;

    @Override public String id() { return "vehicle-liveries"; }

    @Override public String label() { return "The Hegemony vehicle line in the Hegemony, Independent and pirate paints"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        try (ReviewFactions ignored = ReviewFactions.bind("independent", "pirates")) {
            return LookReview.record(context.modRoot(), lineup());
        }
    }

    static LookReview.Shot lineup() {
        int columns = LINE.size() + 1;
        return new LookReview.Shot("vehicle-liveries", LookGround.verdant(), (int) (COLUMN * columns + 8),
                (int) (ROW * ROWS.size() + 6), placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            List<Consumer<BattleSimulation>> holds = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (int r = 0; r < ROWS.size(); r++) {
                Faction side = ROWS.get(r);
                for (int c = 0; c < columns; c++) {
                    boolean wreck = c == LINE.size();
                    VehicleType type = wreck ? VehicleType.HEAVY_APC : LINE.get(c);
                    float x = placement.site().x(4f + COLUMN * (c + .5f));
                    float y = placement.site().y(3f + ROW * (r + .5f));
                    long id = sim.convoy().spawn(type, side, VehicleMission.deployed(x, y));
                    GroundTurret turret = sim.convoy().turret(id);
                    if (turret != null) {
                        turret.ammo = 0;
                        turret.unlimited = false;
                        turret.loaderSeconds = Float.POSITIVE_INFINITY;
                    }
                    String key = side + "-" + c;
                    placement.subject(key, id);
                    keys.add(key);
                    if (wreck) {
                        sim.convoy().body(id).teleport(x, y, 0f);
                        sim.applyDamage(id, 100000, 100000);
                    } else {
                        holds.add(s -> {
                            s.convoy().body(id).teleport(x, y, 0f);
                            GroundTurret t = s.convoy().turret(id);
                            if (t != null) t.facingDeg = 0f;
                        });
                    }
                }
            }
            placement.beforeTick(s -> holds.forEach(h -> h.accept(s)));
            return sim;
        }, placement -> {
            List<String> keys = new ArrayList<>();
            for (Faction side : ROWS) for (int c = 0; c <= LINE.size(); c++) keys.add(side + "-" + c);
            String[] all = keys.toArray(new String[0]);
            String[] marines = Arrays.stream(all).filter(k -> k.startsWith(Faction.MARINE.name())).toArray(String[]::new);
            return List.of(LookReview.View.follow("close", placement, COLUMN * (LINE.size() + 1) + 4f, all),
                    LookReview.View.follow("independent", placement, 16f, marines),
                    LookReview.View.followAtPlay(placement, all));
        }, 60, 6, 1, new int[] {9});
    }
}
