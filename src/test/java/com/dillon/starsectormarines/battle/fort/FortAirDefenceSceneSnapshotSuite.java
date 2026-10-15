package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.Locale;

/**
 * The fort anti-air loops' frames (1920x1080, 100% scale), from the same run that produced their verdicts. Every aspect is
 * labelled in the frame, the bunker with what its radar is doing for whom, and the Ridgewatch batteries as missile
 * launchers. The yard loop and the two spotting loops that matter most also get a close view at full size.
 */
public final class FortAirDefenceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    /** Which of the fort's Ridgewatch batteries (bastions) the aa-forced close view is centred on. */
    private static final int SAM_CLOSE_VIEW = 0;

    public FortAirDefenceSceneSnapshotSuite() {
        super(new FortAirDefenceScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    /** The close views are kept at the full frame size: the evidence is shown at 1920x1080, never scaled. */
    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected float[] closeView(String loopId) {
        return switch (loopId) {
            case "aa-forced" -> {
                FortAirDefenceScene.Stamped stamped = FortAirDefenceScene.stamp(FortKind.BATTERY_FORT, true);
                int[] battery = stamped.placed().plan().airBatteries().get(SAM_CLOSE_VIEW);
                int[] centre = stamped.placed().placement().cell(battery[0], battery[1]);
                yield new float[]{centre[0] + 0.5f, centre[1] + 0.5f, 30f};
            }
            case "fort-spots", "spots-taken" -> new float[]{FortAirDefenceScene.CX - 16f, FortAirDefenceScene.CY, 110f};
            default -> null;
        };
    }

    @Override
    protected int[] stillFrames(String loopId) {
        return switch (loopId) {
            case "aa-forced-close" -> new int[]{24, 26, 27, 28, 30};
            case "aa-forced", "shield-fort" -> new int[]{26};
            case "fort-spots-close" -> new int[]{5, 8, 11};
            case "spots-taken-close" -> new int[]{5, 8, 11};
            default -> new int[0];
        };
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (Fort fort : sim.forts().forts()) {
            for (Fort.Place place : fort.places()) {
                String text = place.aspect().label();
                if (place.aspect() == FortAspect.FIRE_CONTROL) {
                    Faction observer = FortSensors.observer(fort);
                    text += place.wrecked() ? " (RADAR WRECKED)" : observer == null ? " (RADAR SILENT)"
                            : String.format(Locale.ROOT, " + RADAR for %s, %.0f cells", observer.name().toLowerCase(Locale.ROOT),
                            FortSensors.range(fort));
                }
                marks.label(place.x(), place.y() + 3.2f, text, place.aspect() == FortAspect.AIR_DEFENCE
                        ? ReviewStyle.KEEP : ReviewStyle.OBJECTIVE);
            }
            for (long mount : fort.airMountIds()) {
                if (!sim.world().isAlive(mount) || sim.turretState().structure(mount).mount.weapon.guidance == null) continue;
                marks.label(sim.world().x(mount), sim.world().y(mount) - 2.4f, "RIDGEWATCH SAM", ReviewStyle.LANDING);
            }
        }
        return marks.build();
    }
}
