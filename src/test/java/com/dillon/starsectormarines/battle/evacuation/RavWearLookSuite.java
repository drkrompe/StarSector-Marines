package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Organic hit feedback on the plated Rav ({@code colony-breach-nouns.md}, Matriarch and Brute): worn chitin
 * ({@code RavWear}) and the puff or glance each landed round sends up ({@code SwarmHitFeedback}). Owner-facing look
 * frames only; there is no verdict. Opt-in: a look study, not part of {@code all}.
 * <ul>
 *   <li>{@code rav-wear-matriarch}: Matriarchs held still at whole, 85, 66, 40 and 15% of their durability (plate
 *       first, as the armour law spends it), and a whelp, an adult and an elder at 40%.</li>
 *   <li>{@code rav-fire-plated}: a rifle and squad-automatic line on a Matriarch in full plate: most rounds glance.</li>
 *   <li>{@code rav-fire-stripped}: the same line on a Matriarch whose plate is gone: every round bites into flesh.</li>
 *   <li>{@code rav-fire-heavy}: a heavy-cannon mech, anti-materiel rifles and marksman rifles on a plated Matriarch.</li>
 *   <li>{@code rav-wear-brute}: Brutes at the same durability levels.</li>
 *   <li>{@code rav-fire-brute}: the rifle line on three plated Brutes.</li>
 * </ul>
 */
public final class RavWearLookSuite implements SnapshotSuite {

    /** Durability left, as a share of plate and health together, west to east. */
    static final float[] LEVELS = {1f, 0.85f, 0.66f, 0.40f, 0.15f};
    static final int W = 64, H = 36;
    private static final int FIRE_TICKS = 360;

    @Override public String id() { return "rav-wear"; }

    @Override public String label() { return "Rav wear and hit feedback: worn chitin, puffs and glances on the Matriarch and Brute"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<LookReview.Shot> shots = List.of(
                wearRow("rav-wear-matriarch", UnitType.SWARM_MATRIARCH, 8f),
                fire("rav-fire-plated", false, Gun.RIFLES, UnitType.SWARM_MATRIARCH),
                fire("rav-fire-stripped", true, Gun.RIFLES, UnitType.SWARM_MATRIARCH),
                fire("rav-fire-heavy", false, Gun.HEAVY, UnitType.SWARM_MATRIARCH),
                wearRow("rav-wear-brute", UnitType.SWARM_BRUTE, 4f),
                fire("rav-fire-brute", false, Gun.RIFLES, UnitType.SWARM_BRUTE));
        // -Dsnapshot.ravWear.shots=<prefix> renders only the shots whose names start with it (rav-wear, rav-fire).
        String only = System.getProperty("snapshot.ravWear.shots", "");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (LookReview.Shot shot : shots) {
            if (shot.name().startsWith(only)) out.addAll(LookReview.record(context.modRoot(), shot));
        }
        return out;
    }

    /** One row of {@code type} at each of {@link #LEVELS}; for the Matriarch, a second row of her three stages. */
    private static LookReview.Shot wearRow(String name, UnitType type, float spacing) {
        boolean matriarch = type == UnitType.SWARM_MATRIARCH;
        return new LookReview.Shot(name, LookGround.verdant(), W, H, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            for (int i = 0; i < LEVELS.length; i++) {
                float[] at = placement.openNear(W / 2f + (i - 2f) * spacing, matriarch ? 11f : H / 2f);
                long id = sim.spawn(still(SwarmStrains.spec(type.name() + " " + i, type, (int) at[0], (int) at[1])));
                hurtTo(sim, id, LEVELS[i]);
                placement.subject(type.name().toLowerCase() + "-" + i, id);
            }
            if (matriarch) {
                SwarmStage[] stages = {SwarmStage.WHELP, SwarmStage.ADULT, SwarmStage.ELDER};
                for (int i = 0; i < stages.length; i++) {
                    float[] at = placement.openNear(W / 2f + (i - 1f) * 11f, 25f);
                    long id = sim.spawn(still(SwarmStrains.spec("Matriarch stage " + i, type, (int) at[0], (int) at[1],
                            stages[i])));
                    hurtTo(sim, id, 0.40f);
                    placement.subject("stage-" + i, id);
                }
            }
            return sim;
        }, placement -> {
            String first = type.name().toLowerCase() + "-0";
            String last = type.name().toLowerCase() + "-" + (LEVELS.length - 1);
            List<LookReview.View> views = new ArrayList<>();
            views.add(LookReview.View.followAtPlay(placement, first, last));
            views.add(LookReview.View.follow("close", placement, matriarch ? 22f : 12f,
                    type.name().toLowerCase() + "-2", type.name().toLowerCase() + "-4"));
            return views;
        }, 30, 6, 1);
    }

    private enum Gun { RIFLES, HEAVY }

    /**
     * A firing line in the west on {@code type} standing in the east: one Matriarch, or three Brutes. Its targets are
     * held still and given health to last the shot; {@code stripped} takes their plate off first.
     */
    private static LookReview.Shot fire(String name, boolean stripped, Gun gun, UnitType type) {
        boolean matriarch = type == UnitType.SWARM_MATRIARCH;
        return new LookReview.Shot(name, LookGround.verdant(), W, H, placement -> {
            SceneBuilder builder = placement.builder();
            float[] at = placement.openNear(12f, H / 2f);
            int x = (int) at[0] - placement.site().x0(), y = (int) at[1] - placement.site().y0();
            if (gun == Gun.RIFLES) {
                builder.squad("rifles").faction(Faction.MARINE).size(6).at(x, y - 2)
                        .primary("weapon.field-rifle").health(600f).stationary().done();
                builder.squad("autos").faction(Faction.MARINE).size(2).at(x - 1, y + 3)
                        .primary("weapon.squad-automatic").health(600f).stationary().done();
            } else {
                builder.squad("rifles").faction(Faction.MARINE).size(2).at(x, y - 3)
                        .primary("weapon.dmr").special("special.anti-materiel-rifle", 2, 40)
                        .health(600f).stationary().done();
                int holdX = (int) at[0], holdY = (int) at[1] + 4;
                builder.squad("mech").faction(Faction.MARINE).size(1).at(x - 2, y + 4)
                        .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT)
                        .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY))
                        .done();
            }
            SceneWorld world = builder.build();
            placement.subject("line", world.members("rifles")[0]);
            BattleSimulation sim = world.sim();
            int count = matriarch ? 1 : 3;
            for (int i = 0; i < count; i++) {
                float[] t = placement.openNear(matriarch ? 31f : 29f, H / 2f + (i - (count - 1) / 2f) * 4f);
                EntitySpec spec = still(SwarmStrains.spec(type.name() + " " + i, type, (int) t[0], (int) t[1]));
                long id = sim.spawn(spec);
                float hp = sim.world().maxHp(id) * (matriarch ? 6f : 10f);
                sim.world().setMaxHp(id, hp);
                sim.world().setHp(id, stripped ? hp * 0.5f : hp);
                if (stripped && sim.world().hasArmor(id)) sim.world().setArmor(id, 0f);
                placement.subject("target-" + i, id);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "line", "target-0"),
                LookReview.View.follow("close", placement, matriarch ? 16f : 12f, "target-0")),
                FIRE_TICKS, 2, 3);
    }

    /** Held where it stands: a look study of its body, not of its hunt. */
    private static EntitySpec still(EntitySpec spec) {
        return spec.moveSpeed(0f);
    }

    /**
     * Spends {@code id}'s durability down to {@code left} of its plate and health together, plate first, as the armour
     * law does: whatever is lost comes off the plate until it is gone, then off the health.
     */
    static void hurtTo(BattleSimulation sim, long id, float left) {
        float hp = sim.world().maxHp(id);
        float plate = sim.world().hasArmor(id) ? sim.world().maxArmor(id) : 0f;
        float lost = (hp + plate) * (1f - left);
        float plateLost = Math.min(plate, lost);
        if (plate > 0f) sim.world().setArmor(id, plate - plateLost);
        sim.world().setHp(id, Math.max(1f, hp - (lost - plateLost)));
    }
}
