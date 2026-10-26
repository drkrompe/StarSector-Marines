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
import java.util.List;

/**
 * The Optional section of the shared mission picture: one row per side objective, in offer order, with its beacons. A
 * mission declares its jobs; it never writes these rows itself.
 */
public final class SideObjectiveRows {
    private SideObjectiveRows() { }

    public static List<Item> items(SideObjectiveService service, SideField field) {
        List<Item> items = new ArrayList<>();
        for (SideObjective job : service.jobs()) items.add(item(job, field));
        return items;
    }

    static Item item(SideObjective job, SideField field) {
        SideObjective.Hook hook = job.hook();
        SideObjective.State state = job.state();
        State shown = state.picture();
        String phase = hook.phase(state);
        SideTrigger trigger = job.trigger();
        boolean timed = state == SideObjective.State.WORKING && trigger.required() > 0f;
        List<Marker> markers = new ArrayList<>();
        SideTrigger.Spot target = trigger.target(field);
        Ground destination = trigger.destination();
        // A moving thing is marked while the job is open; a place done in is marked to the end.
        if (target != null && (destination == null || !state.isFinal()))
            markers.add(new Marker(id(job) + "-target", hook.target(), target.x(), target.y(), phase, shown, target.outline()));
        if (destination != null) markers.add(new Marker(id(job) + "-destination", hook.destination(),
                destination.anchor().x(), destination.anchor().y(), Strings.get(hook.prefix() + "DestinationStatus"), shown,
                destination.outline()));
        markers.addAll(job.context());
        return new Item(id(job), hook.name(), shown, phase, hook.steps(), phase,
                timed ? MissionObjectiveFamilies.timed(hook.timer(), trigger.worked(), trigger.required()) : phase,
                timed ? trigger.worked() / trigger.required() : -1, "", markers,
                markers.isEmpty() ? "" : markers.get(0).id(), true);
    }

    static String id(SideObjective job) { return "side-" + job.key(); }
}
