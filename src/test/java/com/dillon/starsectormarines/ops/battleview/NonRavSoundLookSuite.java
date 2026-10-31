package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * How loud the marines' barks are against a fight with people and machines, for the owner rather than for a verdict:
 * a marine line of rifles and squad automatics carrying frags on real ground, an enemy rifle squad with frags of its
 * own advancing on it, and an enemy Warden walking up behind them, at play zoom, a frame every tick so the frames run
 * in real time at 30 fps. With {@code -Dsnapshot.lookSound=true} every sound the battle screen would start is logged
 * ({@link BattleSoundLog}) and mixed into the MP4's soundtrack from the game's own rules and levels
 * ({@link LookReview}), so the barks are heard at their place under the guns. Opt-in: a mixing study, not part of
 * {@code all}.
 */
public final class NonRavSoundLookSuite implements SnapshotSuite {

    static final int W = 64, H = 36;
    static final int TICKS = 900;
    static final int[] STILLS = {150, 300, 450, 600, 750};

    @Override public String id() { return "nonrav-sound"; }

    @Override public String label() { return "Non-Rav sound: a marine line against enemy riflemen and a Warden, to hear with -Dsnapshot.lookSound=true"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), new LookReview.Shot("nonrav-sound-firefight", LookGround.verdant(),
                W, H, NonRavSoundLookSuite::firefight,
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "enemy")),
                TICKS, 1, 2, STILLS));
    }

    /**
     * The line holding in the west, mortal enough to take wounds but lasting the shot; enemy riflemen advancing on it
     * from the east, frail enough that some fall; and a Warden coming up behind them into sight of the line.
     */
    private static BattleSimulation firefight(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        int x0 = placement.site().x0(), y0 = placement.site().y0();
        float[] home = placement.openNear(10f, H / 2f);
        int x = (int) home[0] - x0, y = (int) home[1] - y0;
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(x, y - 2)
                .primary("weapon.field-rifle").special("special.frag-grenade", 2, 3).health(300f).stationary().done();
        builder.squad("autos").faction(Faction.MARINE).size(2).at(x - 1, y + 3)
                .primary("weapon.squad-automatic").health(300f).stationary().done();
        float[] enemy = placement.openNear(38f, H / 2f);
        int toX = x + 8, toY = y;
        builder.squad("enemy").faction(Faction.DEFENDER).type(UnitType.MARINE).size(8)
                .at((int) enemy[0] - x0, (int) enemy[1] - y0)
                .primary("weapon.field-rifle").special("special.frag-grenade", 2, 2).health(140f)
                .assigned(id -> ObjectiveAssignment.attackMove(id, toX, toY)).done();
        float[] machine = placement.openNear(54f, H / 2f + 4f);
        int stopX = x + 20, stopY = y + 3;
        builder.squad("warden").faction(Faction.DEFENDER).size(1).at((int) machine[0] - x0, (int) machine[1] - y0)
                .mech(MechVariant.WARDEN, MechRole.BALANCED)
                .assigned(id -> ObjectiveAssignment.attackMove(id, stopX, stopY)).done();
        SceneWorld world = builder.build();
        placement.subject("line", world.members("rifles")[0]);
        placement.subject("enemy", world.members("enemy")[0]);
        return world.sim();
    }
}
