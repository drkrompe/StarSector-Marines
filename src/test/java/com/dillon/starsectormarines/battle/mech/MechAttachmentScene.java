package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An attachment on open ground: a marine squad attack-moves east with a Brawler Hound and a
 * Long-Range Warden attached to it, and the question is whether each mech keeps its doctrine's side of
 * the squad while the squad walks — the Hound ahead, the Warden behind, both within reach — and whether
 * a direct order to one releases it and only it ({@code mechs-nouns.md}, attachment).
 */
public final class MechAttachmentScene implements BehaviorScene {

    static final int TICKS = 900;
    /** The tick the Hound is given a move of its own. */
    static final int DIRECT_ORDER_TICK = 750;
    /** Readings start once the mechs have had time to reach their slots. */
    static final int SETTLE_TICKS = 240;
    /** Farther than this from the squad's centre a follower is not keeping company. */
    static final float LEASH_CELLS = 14f;

    @Override public String id() { return "mech-attachment"; }
    @Override public String label() { return "Attached mechs keep their doctrine's side of a walking squad"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(72, 32);
        builder.squad("alpha").faction(Faction.MARINE).size(4).inFile(8, 15, 0, 1)
                .assigned(id -> ObjectiveAssignment.attackMove(id, 62, 16)).done();
        builder.squad("hound").faction(Faction.MARINE).size(1).inFile(5, 6, 1, 0)
                .mech(MechVariant.HOUND, MechRole.ASSAULT).done();
        builder.squad("warden").faction(Faction.MARINE).size(1).inFile(5, 26, 1, 0)
                .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT).done();
        SceneWorld scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            sim.random().setSeed(20261026L);
            int alpha = scene.squadId("alpha");
            int houndSquad = scene.squadId("hound");
            int wardenSquad = scene.squadId("warden");
            long hound = scene.members("hound")[0];
            long warden = scene.members("warden")[0];
            MechAttachmentService attachments = sim.getMechAttachmentService();
            attachments.requestAttach(houndSquad, alpha);
            attachments.requestAttach(wardenSquad, alpha);

            boolean stayedAttached = true;
            float houndLead = 0f;
            float wardenLead = 0f;
            int samples = 0;
            float worstReach = 0f;
            boolean released = false;
            boolean wardenKept = false;
            for (int tick = 0; tick < TICKS; tick++) {
                if (tick == DIRECT_ORDER_TICK) sim.getMechMoveOrderService().requestMove(hound, 40, 4);
                sim.advance(BattleSimulation.TICK_DT);
                Squad squad = sim.getSquad(alpha);
                if (tick < DIRECT_ORDER_TICK) {
                    stayedAttached &= attachments.anchorOf(houndSquad) == alpha
                            && attachments.anchorOf(wardenSquad) == alpha;
                    if (tick >= SETTLE_TICKS) {
                        houndLead += sim.world().x(hound) - squad.centroidX;
                        wardenLead += sim.world().x(warden) - squad.centroidX;
                        samples++;
                        worstReach = Math.max(worstReach, Math.max(
                                distance(sim, hound, squad), distance(sim, warden, squad)));
                    }
                } else if (tick == DIRECT_ORDER_TICK + 1) {
                    released = !attachments.isAttached(houndSquad);
                    wardenKept = attachments.anchorOf(wardenSquad) == alpha;
                }
                if (tick % 15 == 0) frames.frame("main", sim, tick, "mech-attachment / " + tick);
            }
            houndLead /= Math.max(1, samples);
            wardenLead /= Math.max(1, samples);

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("stays-attached", stayedAttached,
                    "neither attachment ended before a direct order was given"));
            verdicts.add(Verdict.of("brawler-leads", houndLead > 1f,
                    "the Hound's mean lead on the squad along its advance was " + houndLead + " cells"));
            verdicts.add(Verdict.of("long-range-shadows", wardenLead < -1f,
                    "the Warden's mean lead on the squad along its advance was " + wardenLead + " cells"));
            verdicts.add(Verdict.of("leashed", worstReach <= LEASH_CELLS,
                    "the farthest either mech stood from the squad's centre was " + worstReach + " cells"));
            verdicts.add(Verdict.of("direct-order-detaches", released && wardenKept,
                    "a move given to the Hound alone released it" + (wardenKept ? " and kept the Warden" : " but the Warden was released too")));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("houndLead", houndLead);
            metrics.put("wardenLead", wardenLead);
            metrics.put("worstReach", worstReach);
            metrics.put("squadEndX", sim.getSquad(alpha).centroidX);
            return List.of(new SceneReport(id(), "main", TICKS, verdicts, metrics));
        }
    }

    private static float distance(BattleSimulation sim, long mech, Squad squad) {
        float dx = sim.world().x(mech) - squad.centroidX;
        float dy = sim.world().y(mech) - squad.centroidY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
