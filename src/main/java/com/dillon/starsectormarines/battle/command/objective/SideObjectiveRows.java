package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.objective.side.Ground;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Optional section of the shared mission picture: one row per known side objective, in offer order, with its
 * beacons; a hidden job joins only once found, marked new for a moment. A mission declares its jobs; it never writes
 * these rows itself.
 */
public final class SideObjectiveRows {
    private SideObjectiveRows() { }

    public static List<Item> items(SideObjectiveService service, SideField field) {
        List<Item> items = new ArrayList<>();
        for (SideObjective job : service.jobs()) if (job.known()) items.add(item(job, field));
        return items;
    }

    /**
     * How close a company combatant must be to a job's place for its row to be listed on the rail while it is still
     * offered. UNTUNED first pass; the prompt on the field comes up closer ({@code DustLedgerSideObjectives.PROMPT_CELLS}).
     */
    public static final float IN_REACH_CELLS = 24f;

    /**
     * The ids ({@link Item#id}) of the rows to leave off the rail because the company is not in reach of them: a job
     * still only offered whose place no company combatant is near. A job being worked or settled stays listed, and so
     * does one with no place to be near.
     */
    public static Set<String> far(SideObjectiveService service, SideField field) {
        Set<String> out = new HashSet<>();
        for (SideObjective job : service.jobs()) {
            if (!job.known() || job.state() != SideObjective.State.OFFERED) continue;
            SideTrigger.Spot place = job.trigger().target(field);
            if (place != null && field.nearest(SideField.Who.COMBATANT, place.x() + .5f, place.y() + .5f, IN_REACH_CELLS) == 0L)
                out.add(id(job));
        }
        return out;
    }

    static Item item(SideObjective job, SideField field) {
        SideObjective.Hook hook = job.hook();
        SideObjective.State state = job.state();
        State shown = state.picture();
        String phase = job.fresh() ? Strings.format("sideObjectiveNew", hook.phase(state)) : hook.phase(state);
        SideTrigger trigger = job.trigger();
        boolean timed = state == SideObjective.State.WORKING && trigger.required() > 0f;
        List<Marker> markers = new ArrayList<>();
        SideTrigger.Spot target = trigger.target(field);
        Ground destination = trigger.destination();
        // A moving thing is marked while the job is open, a place done in to the end; where it goes, while it is open.
        if (target != null && (destination == null || !state.isFinal()))
            markers.add(new Marker(id(job) + "-target", hook.target(), target.x(), target.y(), phase, shown, target.outline()));
        if (destination != null && !state.isFinal() && (state == SideObjective.State.WORKING || trigger.destinationShownIdle()))
            markers.add(new Marker(id(job) + "-destination", hook.destination(),
                destination.anchor().x(), destination.anchor().y(), Strings.get(hook.prefix() + "DestinationStatus"), shown,
                destination.outline()));
        markers.addAll(job.context());
        String pays = job.ret().credits() > 0 ? Strings.format("sideObjectivePays", job.ret().credits()) : "";
        // What the job earns (credits, the authored reward, or both), else who offered it; the row has one line for it.
        String earns = Strings.join("separatorDot", pays, hook.reward());
        String offeredBy = earns.isEmpty() ? Strings.format("sideObjectiveOfferedBy", hook.offerer()) : earns;
        return new Item(id(job), hook.name(), shown, phase, hook.steps(), offeredBy,
                timed ? MissionObjectiveFamilies.timed(hook.timer(), trigger.worked(), trigger.required()) : offeredBy,
                timed ? trigger.worked() / trigger.required() : -1, "", markers,
                markers.isEmpty() ? "" : markers.get(0).id(), true);
    }

    static String id(SideObjective job) { return "side-" + job.key(); }
}
