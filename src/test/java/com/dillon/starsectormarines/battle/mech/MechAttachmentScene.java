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
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An attachment on open ground: a marine squad attack-moves east with a Brawler Hound and a
 * Long-Range Warden attached to it and halts in sight of a defender post, trading fire from range. On the march, out of
 * contact, the question is whether both mechs walk with the squad rather than off on their doctrine;
 * once the squad is in contact, whether each takes its doctrine's side of it — the Hound ahead, the
 * Warden behind; and whether a direct order to one releases it and only it ({@code mechs-nouns.md},
 * attachment).
 */
public final class MechAttachmentScene implements BehaviorScene {

    static final int TICKS = 1500;
    /** The tick the Hound is given a move of its own. */
    static final int DIRECT_ORDER_TICK = 1400;
    /** Readings start once the mechs have had time to fall in with the squad. */
    static final int SETTLE_TICKS = 240;
    /** Where the squad halts, short of the post, so it trades fire from range and the mechs can close. */
    static final int HALT_X = 40;
    /** The defender post's column. */
    static final int POST_X = 64;
    /** Contact ticks the mechs are given to close on the halted squad before doctrine is read. */
    static final int CLOSE_TICKS = 300;
    /** Contact readings needed before the doctrine verdicts mean anything. */
    static final int MIN_CONTACT_SAMPLES = 60;

    @Override public String id() { return "mech-attachment"; }
    @Override public String label() { return "Attached mechs walk with a squad, and take their doctrine's side of it in a fight"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(80, 32);
        builder.squad("alpha").faction(Faction.MARINE).size(4).inFile(8, 15, 0, 1)
                .assigned(id -> ObjectiveAssignment.attackMove(id, HALT_X, 16)).done();
        builder.squad("hound").faction(Faction.MARINE).size(1).inFile(5, 6, 1, 0)
                .mech(MechVariant.HOUND, MechRole.ASSAULT).done();
        builder.squad("warden").faction(Faction.MARINE).size(1).inFile(5, 26, 1, 0)
                .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT).done();
        builder.squad("post").faction(Faction.DEFENDER).size(4).inFile(POST_X, 14, 0, 1).stationary().done();
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
            float marchDistance = 0f;
            float marchLead = Float.NEGATIVE_INFINITY;
            int marchSamples = 0;
            float houndLead = 0f;
            float wardenLead = 0f;
            int contactSamples = 0;
            int firstContact = -1;
            boolean released = false;
            boolean wardenKept = false;
            for (int tick = 0; tick < TICKS; tick++) {
                if (tick == DIRECT_ORDER_TICK) sim.getMechMoveOrderService().requestMove(hound, 40, 4);
                sim.advance(BattleSimulation.TICK_DT);
                Squad squad = sim.getSquad(alpha);
                if (tick < DIRECT_ORDER_TICK) {
                    stayedAttached &= attachments.anchorOf(houndSquad) == alpha
                            && attachments.anchorOf(wardenSquad) == alpha;
                    boolean contact = squad != null && MechAttachmentSystem.inContact(squad);
                    if (contact && firstContact < 0) firstContact = tick;
                    if (squad != null && tick >= SETTLE_TICKS) {
                        if (firstContact < 0) {
                            marchDistance += Math.max(distance(sim, hound, squad), distance(sim, warden, squad));
                            marchLead = Math.max(marchLead, Math.max(sim.world().x(hound), sim.world().x(warden))
                                    - squad.centroidX);
                            marchSamples++;
                        } else if (contact && tick >= firstContact + CLOSE_TICKS) {
                            float[] axis = attachments.axisOf(alpha);
                            float ax = axis == null ? 1f : axis[0];
                            float ay = axis == null ? 0f : axis[1];
                            houndLead += (sim.world().x(hound) - squad.centroidX) * ax
                                    + (sim.world().y(hound) - squad.centroidY) * ay;
                            wardenLead += (sim.world().x(warden) - squad.centroidX) * ax
                                    + (sim.world().y(warden) - squad.centroidY) * ay;
                            contactSamples++;
                        }
                    }
                } else if (tick == DIRECT_ORDER_TICK + 1) {
                    released = !attachments.isAttached(houndSquad);
                    wardenKept = attachments.anchorOf(wardenSquad) == alpha;
                }
                if (tick % 15 == 0) frames.frame("main", sim, tick, "mech-attachment / " + tick);
            }
            marchDistance /= Math.max(1, marchSamples);
            houndLead /= Math.max(1, contactSamples);
            wardenLead /= Math.max(1, contactSamples);
            boolean fought = contactSamples >= MIN_CONTACT_SAMPLES;

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("stays-attached", stayedAttached,
                    "neither attachment ended before a direct order was given"));
            verdicts.add(Verdict.of("no-doctrine-on-the-march", marchSamples > 0 && marchLead <= 1f,
                    "out of contact neither mech led the squad (most either got ahead: " + marchLead
                            + " cells); the farther trailed " + marchDistance + " cells on average, at its own pace"));
            verdicts.add(Verdict.of("brawler-leads-in-contact", fought && houndLead > 1f,
                    "in contact (" + contactSamples + " readings) the Hound's mean lead along the squad's axis was " + houndLead + " cells"));
            verdicts.add(Verdict.of("long-range-shadows-in-contact", fought && wardenLead < -1f,
                    "in contact the Warden's mean lead along the squad's axis was " + wardenLead + " cells"));
            verdicts.add(Verdict.of("direct-order-detaches", released && wardenKept,
                    "a move given to the Hound alone released it" + (wardenKept ? " and kept the Warden" : " but the Warden was released too")));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("marchDistance", marchDistance);
            metrics.put("marchLead", marchLead);
            metrics.put("firstContactTick", firstContact);
            metrics.put("contactSamples", contactSamples);
            metrics.put("houndLead", houndLead);
            metrics.put("wardenLead", wardenLead);
            return List.of(new SceneReport(id(), "main", TICKS, verdicts, metrics), playLine(frames));
        }
    }

    /** Ticks of the line loop. */
    static final int LINE_TICKS = 2100;
    /** Ticks before first contact the posts are read over. */
    static final int LINE_QUIET_TICKS = 120;
    /** Ticks after first contact the mechs are given to take their doctrine's places before they are read. */
    static final int LINE_STEP_TICKS = 60;
    /** A chassis this near its post's cell centre stands on it: it is over a cell and a half wide. */
    static final float ON_POST_CELLS = 2f;

    /**
     * A squad with the Hound and the Warden attached is given a dragged line facing a defender post
     * walking in from far out of sight: while it is quiet both mechs stand on their posts within the line, the Hound at a
     * third and the Warden at two thirds; once the post walks into view, the Hound steps out ahead of
     * the line and the Warden back behind it, both still attached.
     */
    private SceneReport playLine(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(100, 40);
        builder.squad("alpha").faction(Faction.MARINE).size(6).inFile(16, 17, 0, 1).done();
        // Started a few cells behind where their posts will be, so the line is formed before contact.
        builder.squad("hound").faction(Faction.MARINE).size(1).inFile(22, 16, 1, 0)
                .mech(MechVariant.HOUND, MechRole.ASSAULT).done();
        builder.squad("warden").faction(Faction.MARINE).size(1).inFile(22, 24, 1, 0)
                .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT).done();
        // Hardy, so the fight lasts long enough to read where the mechs stand in it.
        builder.squad("post").faction(Faction.DEFENDER).size(4).inFile(96, 18, 0, 1).health(600f)
                .assigned(id -> ObjectiveAssignment.attackMove(id, 48, 20)).done();
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
            sim.advance(BattleSimulation.TICK_DT);
            Selection selection = new Selection();
            selection.selectSquad(alpha);
            SelectionOrders.requestLine(sim, selection, 30, 8, 30, 32);

            float[] houndOff = new float[LINE_TICKS];
            float[] wardenOff = new float[LINE_TICKS];
            float houndAhead = 0f;
            float wardenAhead = 0f;
            int contactSamples = 0;
            boolean stayedAttached = true;
            boolean posted = true;
            int lineContact = -1;
            for (int tick = 0; tick < LINE_TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                stayedAttached &= attachments.anchorOf(houndSquad) == alpha && attachments.anchorOf(wardenSquad) == alpha;
                MechAttachmentService.Post houndPost = attachments.postOf(houndSquad);
                MechAttachmentService.Post wardenPost = attachments.postOf(wardenSquad);
                posted &= houndPost != null && wardenPost != null;
                if (houndPost == null || wardenPost == null) continue;
                Squad squad = sim.getSquad(alpha);
                boolean contact = squad != null && MechAttachmentSystem.inContact(squad);
                if (contact && lineContact < 0) lineContact = tick;
                houndOff[tick] = offPost(sim, hound, houndPost);
                wardenOff[tick] = offPost(sim, warden, wardenPost);
                if (contact && lineContact >= 0 && tick >= lineContact + LINE_STEP_TICKS) {
                    houndAhead += houndPost.anchorLine().ahead(sim.world().x(hound), sim.world().y(hound));
                    wardenAhead += wardenPost.anchorLine().ahead(sim.world().x(warden), sim.world().y(warden));
                    contactSamples++;
                }
                if (tick % 15 == 0) frames.frame("line", sim, tick, "mech-attachment line / " + tick);
            }
            float quietHoundOff = 0f;
            float quietWardenOff = 0f;
            int quietSamples = 0;
            int quietEnd = lineContact < 0 ? LINE_TICKS : lineContact;
            for (int tick = Math.max(0, quietEnd - LINE_QUIET_TICKS); tick < quietEnd; tick++) {
                quietHoundOff += houndOff[tick];
                quietWardenOff += wardenOff[tick];
                quietSamples++;
            }
            quietHoundOff /= Math.max(1, quietSamples);
            quietWardenOff /= Math.max(1, quietSamples);
            houndAhead /= Math.max(1, contactSamples);
            wardenAhead /= Math.max(1, contactSamples);

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("posts-stand", stayedAttached && posted,
                    "both mechs stayed attached and held posts on the line for the whole loop"));
            verdicts.add(Verdict.of("on-posts-while-quiet", quietSamples > 0 && quietHoundOff <= ON_POST_CELLS && quietWardenOff <= ON_POST_CELLS,
                    "before contact the Hound stood " + quietHoundOff + " and the Warden " + quietWardenOff + " cells off their posts"));
            verdicts.add(Verdict.of("brawler-steps-out", contactSamples >= MIN_CONTACT_SAMPLES && houndAhead > 2f,
                    "in contact (" + contactSamples + " readings) the Hound stood " + houndAhead + " cells ahead of the line"));
            verdicts.add(Verdict.of("long-range-steps-back", contactSamples >= MIN_CONTACT_SAMPLES && wardenAhead < -3f,
                    "in contact the Warden stood " + wardenAhead + " cells ahead of the line"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("firstContactTick", lineContact);
            metrics.put("quietSamples", quietSamples);
            metrics.put("quietHoundOff", quietHoundOff);
            metrics.put("quietWardenOff", quietWardenOff);
            metrics.put("contactSamples", contactSamples);
            metrics.put("houndAhead", houndAhead);
            metrics.put("wardenAhead", wardenAhead);
            return new SceneReport(id(), "line", LINE_TICKS, verdicts, metrics);
        }
    }

    private static float offPost(BattleSimulation sim, long mech, MechAttachmentService.Post post) {
        float dx = sim.world().x(mech) - (post.x() + .5f);
        float dy = sim.world().y(mech) - (post.y() + .5f);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float distance(BattleSimulation sim, long mech, Squad squad) {
        float dx = sim.world().x(mech) - squad.centroidX;
        float dy = sim.world().y(mech) - squad.centroidY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
