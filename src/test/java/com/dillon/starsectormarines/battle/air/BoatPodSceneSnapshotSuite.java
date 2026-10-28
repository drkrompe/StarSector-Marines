package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.List;

/**
 * The boat pod loop from the run that produced its verdicts, and the look the owner is shown:
 * {@code look/boat-pod-*}, the same Hermes on station over a militia picket on real generated ground,
 * followed close enough that her twin-laser and twin-missile pods read.
 */
public final class BoatPodSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public BoatPodSceneSnapshotSuite() {
        super(new BoatPodScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected boolean showImpactEffects() { return true; }

    /** Close on the ground she circles, so the pods on her hull read. */
    @Override protected float[] closeView(String loopId) {
        return new float[]{44.5f, 24.5f, 40f};
    }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("boat-pod", 64, 40, placement -> {
            InstalledHullSpecs.install();
            InstalledWeaponSlots.install();
            InstalledVanillaData.install();
            float[] picket = placement.openNear(40f, 20f);
            SceneBuilder builder = placement.builder();
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(10)
                    .at((int) picket[0] - placement.site().x0(), (int) picket[1] - placement.site().y0())
                    .primary("weapon.smg").stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, (int) picket[0], (int) picket[1])).done();
            BattleSimulation sim = builder.build().sim();
            placement.subject("hermes", sim.spawnGunship(BoatPodScene.podBoat(), Faction.MARINE,
                    picket[0], picket[1], placement.site().x(-12f), picket[1], placement.site().x(-12f), picket[1]));
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "hermes"), onTheHull(placement, 22f)), 900));
    }

    /**
     * A close camera on the hull as it is drawn: the craft's ground point lifted by its altitude offset, which a
     * following view on the ground point leaves out, so the pods stay in the middle of the frame.
     */
    private static LookReview.View onTheHull(LookReview.Placement placement, float cellsAcross) {
        float[] last = {placement.site().centreX(), placement.site().centreY()};
        return new LookReview.View("close", sim -> {
            Long craft = placement.subjects().get("hermes");
            ShuttleMission mission = craft == null ? null : sim.world().mission(craft);
            if (mission != null && mission.isOnMap()) {
                AirBody body = sim.world().kinematics(craft);
                last[0] = body.x;
                last[1] = body.y + AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(craft));
            }
            return new float[]{last[0], last[1], cellsAcross};
        });
    }
}
