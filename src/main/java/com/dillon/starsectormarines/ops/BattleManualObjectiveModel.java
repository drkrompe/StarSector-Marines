package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.util.List;
import java.util.Map;

/** Read-only objective cards use the same public mission picture as command mode. */
final class BattleManualObjectiveModel {
    private final MutableSignal<List<BattleHudOverlayModel.MissionCard>> cards;
    private final MutableSignal<String> score;

    BattleManualObjectiveModel(Reactor reactor) {
        cards = reactor.signal(List.of());
        score = reactor.signal("");
    }

    void update(MissionObjectiveSnapshot snapshot) {
        cards.set(snapshot.objectives().stream()
                .map(item -> new BattleHudOverlayModel.MissionCard(item, () -> {})).toList());
        score.set(snapshot.objectives().stream()
                .filter(item -> item.state() == MissionObjectiveSnapshot.State.COMPLETE).count()
                + " / " + snapshot.objectives().size());
    }

    Map<String, Object> props() { return Map.of("cards", cards, "score", score); }
}
