package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabCameraControllerTest {

    /**
     * Stated anchors rather than a ship's. The controller's job is the easing
     * between two poses; where those poses are is the ship's business.
     */
    private static final MechLabCameraController.CameraPose WIDE =
            new MechLabCameraController.CameraPose(12f, 8f,
                    MechLabCameraController.WIDE_ZOOM_NOTCHES);
    private static final MechLabCameraController.CameraPose BERTH =
            new MechLabCameraController.CameraPose(19f, 5f,
                    MechLabCameraController.FITTING_ZOOM_NOTCHES);

    private static MechLabCameraController controller() {
        return new MechLabCameraController(new MechLabCameraController.Anchors() {
            @Override
            public MechLabCameraController.CameraPose wide() {
                return WIDE;
            }

            @Override
            public MechLabCameraController.CameraPose berth(int index) {
                return BERTH;
            }
        });
    }

    @Test
    void socketFocusEasesFromLanceOverviewToSelectedGantry() {
        MechLabCameraController controller = controller();
        controller.snap(false, 0, 3);
        MechLabCameraController.CameraPose wide = controller.pose();
        MechLabCameraController.CameraPose focused = BERTH;

        controller.target(true, 0, 3);
        controller.advance(MechLabCameraController.TRANSITION_SECONDS * 0.5f);
        MechLabCameraController.CameraPose midpoint = controller.pose();

        assertTrue(midpoint.worldX() > Math.min(wide.worldX(), focused.worldX()));
        assertTrue(midpoint.worldX() < Math.max(wide.worldX(), focused.worldX()));
        assertTrue(midpoint.zoomNotches() > wide.zoomNotches());
        assertTrue(midpoint.zoomNotches() < focused.zoomNotches());

        controller.target(true, 0, 3);
        controller.advance(MechLabCameraController.TRANSITION_SECONDS);
        assertEquals(focused, controller.pose());
    }

    @Test
    void overviewResetEasesBackWithoutChangingTheRequestedGantry() {
        MechLabCameraController controller = controller();
        controller.snap(true, 2, 3);

        controller.target(false, 2, 3);
        controller.advance(MechLabCameraController.TRANSITION_SECONDS);

        assertEquals(WIDE, controller.pose());
    }
}
