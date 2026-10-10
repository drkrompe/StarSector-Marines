package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFacts.Place;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stateful intent over public places and own-force reports only; never referee occupancy. */
public final class CounterattackTactics {
    public record Report(int id, int alive, float x, float y, boolean contact) {}
    public record Intent(Place place, Reason reason) {}
    private record Post(int stage, int strength) {}
    private final List<Place> stages;
    private final boolean defending;
    private final Map<Integer, Post> posts = new HashMap<>();
    private final Map<Integer, Integer> progress = new HashMap<>();
    private boolean reserveCommitted;
    private boolean gateLost;
    private int rearGuard = -1;

    public CounterattackTactics(List<Place> stages, boolean defending) {
        if (stages.size() != 3) throw new IllegalArgumentException("Depot requires gate, yard and warehouse");
        this.stages = List.copyOf(stages);
        this.defending = defending;
    }

    public void observe(List<Report> own) {
        if (!defending) return;
        for (Report r : own) if (r.alive > 0) {
            posts.computeIfAbsent(r.id, id -> new Post(nearest(r), r.alive));
            Post post = posts.get(r.id);
            if (post.stage < 2 && (r.contact || r.alive < post.strength)) reserveCommitted = true;
        }
        if (rearGuard < 0) rearGuard = posts.entrySet().stream()
                .filter(e -> e.getValue().stage == 2).mapToInt(Map.Entry::getKey).min().orElse(-1);
        boolean knewGate = posts.values().stream().anyMatch(p -> p.stage == 0);
        boolean gateAlive = own.stream().anyMatch(r -> r.alive > 0 && posts.containsKey(r.id) && posts.get(r.id).stage == 0);
        gateLost |= knewGate && !gateAlive;
    }

    public Intent intent(Report r) {
        if (!defending) {
            int stage = progress.getOrDefault(r.id, 0);
            Place next = stages.get(stage);
            float dx = r.x - next.x(), dy = r.y - next.y();
            if (stage < 2 && dx * dx + dy * dy <= 64) stage++;
            progress.put(r.id, stage);
            return new Intent(stages.get(stage), switch (stage) {
                case 0 -> Reason.DEPOT_APPROACH_GATE;
                case 1 -> Reason.DEPOT_CROSS_YARD;
                default -> Reason.BANDIT_DEPOT_SECURE;
            });
        }
        Post post = posts.get(r.id);
        int stage = progress.getOrDefault(r.id, post.stage);
        if (post.stage < 2 && (r.alive * 2 <= post.strength || post.stage == 1 && gateLost && r.contact))
            stage = Math.max(stage, post.stage + 1);
        progress.put(r.id, stage);
        if (stage > post.stage) return new Intent(stages.get(stage), Reason.DEPOT_FALLBACK);
        if (post.stage == 2 && r.id != rearGuard && reserveCommitted && !gateLost)
            return new Intent(stages.get(1), Reason.DEPOT_RESERVE_COMMITTED);
        return new Intent(stages.get(stage), switch (stage) {
            case 0 -> Reason.DEPOT_GATE_GUARD;
            case 1 -> Reason.DEPOT_YARD_GUARD;
            default -> Reason.DEPOT_REAR_GUARD;
        });
    }

    private int nearest(Report r) {
        int best = 0; float distance = Float.MAX_VALUE;
        for (int i = 0; i < stages.size(); i++) {
            Place p = stages.get(i);
            float dx = r.x - p.x(), dy = r.y - p.y();
            if (dx * dx + dy * dy < distance) { best = i; distance = dx * dx + dy * dy; }
        }
        return best;
    }
}
