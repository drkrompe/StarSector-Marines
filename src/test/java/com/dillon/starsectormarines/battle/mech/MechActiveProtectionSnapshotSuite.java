package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Active protection on live mechs ({@code mechs-nouns.md}, one machine, one part catalog): each chassis bare, then with
 * active protection in its compartment, then fitted with its torso turned across, so the launchers and panels are seen
 * riding the torso. At play zoom and close on each chassis. Owner-facing look frames only; there is no verdict.
 */
public final class MechActiveProtectionSnapshotSuite implements SnapshotSuite {

    /** One column: whether the compartment carries active protection, and how far the torso is turned off the hips. */
    record Column(String key, boolean activeProtection, float torsoTurn) { }

    static final MechVariant[] CHASSIS = {MechVariant.BULWARK, MechVariant.HOUND, MechVariant.WARDEN};
    static final List<Column> COLUMNS = List.of(
            new Column("bare", false, 0f),
            new Column("aps", true, 0f),
            new Column("aps-turned", true, 50f));
    private static final float COLUMN = 4.5f;
    private static final float ROW = 7f;
    /** A spawned mech's torso bearing before it has anything to turn onto. */
    private static final float TORSO_AHEAD = 180f;

    @Override public String id() { return "mech-active-protection"; }

    @Override public String label() { return "Active protection on Bulwark, Hound and Warden"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int width = (int) (COLUMN * COLUMNS.size() + 10);
        int height = (int) (ROW * CHASSIS.length + 8);
        return new LookReview.Shot("mech-active-protection", LookGround.verdant(), width, height, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            List<String> keys = new ArrayList<>();
            List<float[]> spots = new ArrayList<>();
            for (int row = 0; row < CHASSIS.length; row++) {
                for (int c = 0; c < COLUMNS.size(); c++) {
                    Column column = COLUMNS.get(c);
                    float[] at = placement.openNear(5f + COLUMN * (c + .5f), 4f + ROW * (row + .5f));
                    int x = (int) at[0] - placement.site().x0();
                    int y = (int) at[1] - placement.site().y0();
                    int holdX = (int) at[0];
                    int holdY = (int) at[1];
                    String key = key(CHASSIS[row], column);
                    builder.squad(key).faction(Faction.MARINE).size(1).at(x, y)
                            .mech(deployment(CHASSIS[row], column.activeProtection()))
                            .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY))
                            .done();
                    keys.add(key);
                    spots.add(at);
                }
            }
            SceneWorld world = builder.build();
            List<Consumer<BattleSimulation>> holds = new ArrayList<>();
            for (int row = 0; row < CHASSIS.length; row++) {
                for (int c = 0; c < COLUMNS.size(); c++) {
                    String key = key(CHASSIS[row], COLUMNS.get(c));
                    long id = world.members(key)[0];
                    placement.subject(key, id);
                    float turn = COLUMNS.get(c).torsoTurn();
                    // The torso is held a fixed twist off its default bearing, so each column shows one pose.
                    holds.add(sim -> {
                        MechLoadoutComponent loadout = sim.world().mechLoadout(id);
                        if (loadout != null) {
                            loadout.torsoFacingDegrees = TORSO_AHEAD + turn;
                            loadout.torsoAngularVelocityDegrees = 0f;
                        }
                    });
                }
            }
            placement.beforeTick(s -> holds.forEach(h -> h.accept(s)));
            return world.sim();
        }, placement -> {
            List<LookReview.View> views = new ArrayList<>();
            List<String> all = new ArrayList<>();
            for (MechVariant chassis : CHASSIS) for (Column column : COLUMNS) all.add(key(chassis, column));
            views.add(LookReview.View.followAtPlay(placement, all.toArray(new String[0])));
            for (MechVariant chassis : CHASSIS) {
                List<String> row = new ArrayList<>();
                for (Column column : COLUMNS) row.add(key(chassis, column));
                views.add(LookReview.View.follow(chassis.id, placement, 15f, row.toArray(new String[0])));
            }
            return views;
        }, 12, 6, 1, new int[] {1});
    }

    private static String key(MechVariant chassis, Column column) {
        return chassis.id + "-" + column.key();
    }

    /** The chassis's factory fit, its compartment carrying active protection or nothing. */
    private static MechDeploymentSpec deployment(MechVariant chassis, boolean activeProtection) {
        MechFrame stock = MechFrame.stock(chassis);
        MechFrame frame = activeProtection
                ? new MechFrame(stock.armorCapacity(), stock.moveSpeed(), stock.fluxCapacity(),
                        stock.dissipationPerSecond(), MechCompartment.systems(List.of(MechInternal.ACTIVE_PROTECTION)))
                : stock;
        MechDeploymentSpec standard = MechDeploymentSpec.standard(chassis);
        return new MechDeploymentSpec(chassis, standard.role(), standard.missileReplenisher(), standard.arms(),
                standard.leftArm(), standard.rightArm(), standard.leftShoulder(), standard.rightShoulder(), null,
                frame, null);
    }
}
