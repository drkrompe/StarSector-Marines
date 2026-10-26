package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.Transition;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Side-objective notices: the jobs on offer when the battle opens, then each one done, lapsed or failed. Jobs that
 * change the same way on the same tick share one notice (the alarm lapses every quiet-phase job at once). Notices wait
 * for an idle plate rather than displacing mission warnings; the checklist row carries the same state, so a notice is
 * never the only record.
 */
public final class SideObjectiveCommsPresenter {
    private SideObjectiveService current;
    private int cursor;
    private boolean offered;
    private final Deque<BattleCommsFeed.Notice> pending = new ArrayDeque<>();

    public void update(BattleSimulation sim, BattleCommsFeed feed) {
        if (sim == null) return;
        SideObjectiveService service = sim.getSideObjectives();
        if (current != service) { current = service; cursor = 0; offered = false; pending.clear(); }
        if (service.isEmpty()) return;
        if (!offered) {
            offered = true;
            List<String> names = new ArrayList<>();
            for (SideObjective job : service.jobs()) names.add(job.hook().name());
            pending.add(new BattleCommsFeed.Notice(Strings.get("battleCommsSideOfferedTitle"),
                    Strings.format("battleCommsSideOfferedBody", Strings.join("separatorDot", names.toArray(String[]::new))),
                    BattleCommsFeed.Tone.STATUS, 7));
        }
        List<Transition> changes = service.transitionsAfter(cursor);
        cursor += changes.size();
        pending.addAll(notices(service, changes));
        if (feed.activeNotice() == null && !pending.isEmpty() && !sim.isComplete()) feed.post(pending.poll());
    }

    /** One notice per tick and outcome among the final changes; a job taken up or put down is shown on its row only. */
    static List<BattleCommsFeed.Notice> notices(SideObjectiveService service, List<Transition> transitions) {
        List<BattleCommsFeed.Notice> out = new ArrayList<>();
        List<Transition> changes = new ArrayList<>(transitions);
        for (int i = 0; i < changes.size(); i++) {
            Transition first = changes.get(i);
            if (!first.state().isFinal() || first.tick() < 0) continue;
            List<String> names = new ArrayList<>();
            String body = null;
            for (int j = i; j < changes.size(); j++) {
                Transition t = changes.get(j);
                if (t.tick() != first.tick() || t.state() != first.state()) continue;
                SideObjective job = service.job(t.key());
                names.add(job.hook().name());
                if (body == null) body = job.hook().phase(t.state());
                if (j > i) changes.set(j, new Transition(-1, t.key(), t.state()));
            }
            String joined = Strings.join("separatorDot", names.toArray(String[]::new));
            out.add(new BattleCommsFeed.Notice(Strings.get("battleCommsSide" + first.state().keyPart() + "Title"),
                    names.size() == 1 ? Strings.format("battleCommsSideBody", joined, body)
                            : Strings.format("battleCommsSideManyBody", joined), tone(first.state()), 6));
        }
        return out;
    }

    private static BattleCommsFeed.Tone tone(State state) {
        return switch (state) {
            case DONE -> BattleCommsFeed.Tone.GOOD_NEWS;
            case FAILED -> BattleCommsFeed.Tone.WARNING;
            default -> BattleCommsFeed.Tone.STATUS;
        };
    }
}
