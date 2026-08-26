package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabCameraControllerTest {

    @Test
    void socketFocusEasesFromLanceOverviewToSelectedGantry() {
        MechLabCameraController controller = new MechLabCameraController();
        controller.snap(false, 0, 3);
        MechLabCameraController.CameraPose wide = controller.pose();
        MechLabCameraController.CameraPose focused =
                MechLabCameraController.fittingPose(0);

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
        MechLabCameraController controller = new MechLabCameraController();
        controller.snap(true, 2, 3);

        controller.target(false, 2, 3);
        controller.advance(MechLabCameraController.TRANSITION_SECONDS);

        assertEquals(MechLabCameraController.widePose(3), controller.pose());
    }
}
