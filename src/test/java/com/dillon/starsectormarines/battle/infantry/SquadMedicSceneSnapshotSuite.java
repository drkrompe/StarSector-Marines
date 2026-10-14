package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.Locale;

/**
 * {@link SquadMedicScene} recorded at 1920x1080: the medic leaving the file for
 * a wounded squadmate, kneeling over them, and the squad marching on.
 *
 * <p>Stills are written for the first seconds of the march, where the scene's
 * own wounds are treated, and for the arrival, where the target starts adding
 * patients of its own.
 */
public final class SquadMedicSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public SquadMedicSceneSnapshotSuite() {
        super(new SquadMedicScene(), 1920, 1080);
    }

    /**
     * Labels the medic, and the patient while one is chosen, because at whole-map
     * scale every marine is the same cyan dot. Read-only: the marks come off the
     * squad's published medic state, which the frame is already drawn from.
     */
    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (Squad squad : sim.getSquads()) {
            SquadMedic medic = squad.medic;
            if (medic == null) continue;
            long carrier = sim.resolveUnit(medic.medicId());
            if (carrier != 0L) {
                marks.label(sim.world().x(carrier), sim.world().y(carrier) + 1.5f,
                        "MEDIC " + medic.phase().name().toLowerCase(Locale.ROOT),
                        ReviewStyle.LANDING);
            }
            long patient = medic.patientId() == 0L ? 0L : sim.resolveUnit(medic.patientId());
            if (patient != 0L) {
                marks.label(sim.world().x(patient), sim.world().y(patient) - 1.5f,
                        "PATIENT", ReviewStyle.KEEP);
            }
        }
        return marks.build();
    }

    /**
     * A close view of the opening treatments, at the full frame size: at
     * whole-map scale a kneeling medic is a few pixels, and the kneel, the kit,
     * the medic's cross and the treatment ring are the things to read.
     */
    @Override
    protected float[] closeView(String loopId) {
        return new float[] {9.5f, 16.5f, 9f};
    }

    @Override
    protected float closeViewScale() { return 1f; }

    @Override
    protected int[] stillFrames(String loopId) {
        return new int[] {2, 6, 12, 64};
    }
}
