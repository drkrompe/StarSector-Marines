package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

/** Native canvas pass that can also expose its GL-free simulation frame to tools. */
public interface BattleSceneHostPass extends CanvasHostPass {

    BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult);
}
