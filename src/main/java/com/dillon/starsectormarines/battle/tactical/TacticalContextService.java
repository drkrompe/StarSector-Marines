package com.dillon.starsectormarines.battle.tactical;

import com.dillon.starsectormarines.battle.DefensePost;
import com.dillon.starsectormarines.battle.command.CommanderService;

import java.util.Collections;
import java.util.List;

/**
 * Battle-scoped tactical data produced by the map generator and consumed by
 * the AI / cleanup passes: the {@link TacticalMap} hint graph behaviors use
 * for waypoint sampling, and the {@link DefensePost} list cleanup uses to
 * release garrison patrol radii when a post is annihilated.
 *
 * <p>Owned by {@link com.dillon.starsectormarines.battle.BattleSimulation};
 * sibling slice to {@link com.dillon.starsectormarines.battle.fx.EffectsService},
 * {@link com.dillon.starsectormarines.battle.vision.VisionService},
 * {@link com.dillon.starsectormarines.battle.shots.ShotService},
 * {@link CommanderService},
 * {@link com.dillon.starsectormarines.battle.objective.ObjectivesService}.
 *
 * <p>Pure data holder — no tick logic. Set once by {@code BattleSetup}
 * during construction, read for the lifetime of the battle.
 */
public final class TacticalContextService {

    private TacticalMap tacticalMap = new TacticalMap(Collections.emptyList());
    private List<DefensePost> defensePosts = Collections.emptyList();

    /** Tactical hint graph produced by the map generator. Never null; an empty graph for legacy maps. */
    public TacticalMap getTacticalMap() { return tacticalMap; }

    /** Set the tactical map. Called once by {@code BattleSetup} right after sim construction, before the first {@code advance} call. Null collapses to an empty graph. */
    public void setTacticalMap(TacticalMap map) {
        this.tacticalMap = map != null ? map : new TacticalMap(Collections.emptyList());
    }

    /** Stamped defense posts. Conquest-only — empty for missions that don't stamp posts. */
    public List<DefensePost> getDefensePosts() { return defensePosts; }

    /** Stamped defense posts (conquest only). Called once by {@code BattleSetup} right after construction; safe to pass null/empty for missions without posts. */
    public void setDefensePosts(List<DefensePost> posts) {
        this.defensePosts = (posts != null && !posts.isEmpty()) ? posts : Collections.emptyList();
    }
}
