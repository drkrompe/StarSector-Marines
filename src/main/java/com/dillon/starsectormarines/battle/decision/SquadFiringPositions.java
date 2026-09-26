package com.dillon.starsectormarines.battle.decision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Bounded, squad-owned firing-position proposals and member reservations.
 * Geometry generation is shared; a member validates its own selected cell.
 * Callbacks run under the squad-local monitor and must not re-enter this cache.
 * No world references or callbacks are retained after a request.
 */
public final class SquadFiringPositions {
    public static final int MAX_CONTEXTS = 8;
    public static final int MAX_CANDIDATES = 64;
    public static final int NEGATIVE_TTL_TICKS = 4;
    private static final double RESERVED_NEIGHBOR_COST = 4.0;

    public record Key(long targetId, float range, float selfAir, float targetAir,
                      int anchorX, int anchorY, float leash, int zone) { }

    /** staticScore is added to the requesting member's distance/occupancy score. */
    public record Candidate(int x, int y, float staticScore) { }

    public enum Outcome { HIT, ASSIGNED, NEGATIVE, DEFERRED }
    public enum RefreshReason {
        NONE, COLD, TTL, TARGET_MOVED, SQUAD_MOVED, EPOCH, TOPOLOGY, KEY_CHANGE
    }

    /** A deferred refresh can still return a validated incumbent position. */
    public record Result(Candidate position, Outcome outcome,
                         boolean built, boolean assigned, RefreshReason refreshReason) { }

    private static final class Context {
        List<Candidate> candidates;
        int builtTick;
        float targetX, targetY, centroidX, centroidY;

        void update(List<Candidate> candidates, int tick,
                    float targetX, float targetY, float centroidX, float centroidY) {
            this.candidates = candidates;
            this.builtTick = tick;
            this.targetX = targetX;
            this.targetY = targetY;
            this.centroidX = centroidX;
            this.centroidY = centroidY;
        }
    }

    private static final class Member {
        final Key key;
        Context context;
        Candidate position;
        int lastRequestTick;
        int negativeUntilTick;
        RefreshReason pendingRefreshReason = RefreshReason.NONE;

        Member(Key key, Context context, int tick) {
            this.key = key;
            this.context = context;
            this.lastRequestTick = tick;
        }
    }

    private final int ttlTicks;
    private final Map<Key, Context> contexts = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Long, Member> members = new HashMap<>();
    private final Map<Long, Long> reservations = new HashMap<>();
    private final double[] candidateScores = new double[MAX_CANDIDATES];
    private boolean initialized;
    private long epoch;
    private long topologyRevision;

    public SquadFiringPositions(int squadId) {
        ttlTicks = 12 + Math.floorMod(squadId, 6);
    }

    /**
     * Registers current demand and returns one exclusive position, or no position.
     * A denied refresh never invokes the generator or an individual-search fallback.
     * Member/context changes and substantial target/centroid movement are hard
     * invalidations; age alone may retain a still-legal incumbent under pressure.
     * Callback exceptions propagate with the reservation maps left consistent.
     */
    public synchronized Result request(long memberId, int tick, long epoch,
            long topologyRevision, float targetX, float targetY,
            float centroidX, float centroidY, Key key,
            Supplier<List<Candidate>> generator, Predicate<Candidate> validator,
            ToDoubleFunction<Candidate> scorer, BooleanSupplier refreshPermit) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(generator);
        Objects.requireNonNull(validator);
        Objects.requireNonNull(scorer);
        Objects.requireNonNull(refreshPermit);
        RefreshReason refreshReason = RefreshReason.NONE;
        Member previous = members.get(memberId);
        boolean changedKey = previous != null && !previous.key.equals(key);
        if (!initialized || this.epoch != epoch
                || this.topologyRevision != topologyRevision) {
            refreshReason = !initialized ? RefreshReason.COLD
                    : this.epoch != epoch ? RefreshReason.EPOCH : RefreshReason.TOPOLOGY;
            clear();
            initialized = true;
            this.epoch = epoch;
            this.topologyRevision = topologyRevision;
        }
        expireMembers(tick);
        Context context = contexts.get(key);
        if (context != null) {
            boolean targetMoved = moved(context.targetX, context.targetY, targetX, targetY, 1f);
            boolean squadMoved = moved(context.centroidX, context.centroidY, centroidX, centroidY, 4f);
            if (targetMoved || squadMoved) {
                refreshReason = targetMoved ? RefreshReason.TARGET_MOVED : RefreshReason.SQUAD_MOVED;
                contexts.remove(key);
                retire(context);
                context = null;
            }
        }

        Member member = members.get(memberId);
        if (member != null && (!member.key.equals(key) || member.context != context)) {
            release(memberId, member);
            members.remove(memberId);
            member = null;
        }
        if (member == null) {
            member = new Member(key, context, tick);
            members.put(memberId, member);
        }
        member.lastRequestTick = tick;
        boolean built = false;
        boolean refresh = context == null || tick - context.builtTick >=
                (context.candidates.isEmpty() ? NEGATIVE_TTL_TICKS : ttlTicks);
        if (refresh) {
            if (refreshReason == RefreshReason.NONE) {
                refreshReason = context != null ? RefreshReason.TTL
                        : changedKey ? RefreshReason.KEY_CHANGE
                        : member.pendingRefreshReason != RefreshReason.NONE
                        ? member.pendingRefreshReason : RefreshReason.COLD;
            }
            member.pendingRefreshReason = refreshReason;
            if (!refreshPermit.getAsBoolean()) {
                if (member.position != null && !validator.test(member.position)) {
                    release(memberId, member);
                }
                return new Result(member.position, Outcome.DEFERRED, false, false, refreshReason);
            }
            // Normalize entirely before changing a published pool. A failed
            // generator leaves the prior context and its assignments intact.
            List<Candidate> candidates = normalize(generator.get());
            if (context == null) {
                context = new Context();
                if (contexts.size() == MAX_CONTEXTS) {
                    Iterator<Context> oldest = contexts.values().iterator();
                    Context evicted = oldest.next();
                    oldest.remove();
                    retire(evicted);
                }
                contexts.put(key, context);
                member.context = context;
            }
            context.update(candidates, tick, targetX, targetY, centroidX, centroidY);
            member.pendingRefreshReason = RefreshReason.NONE;
            // A refreshed pool can answer a previously negative request.
            for (Member participant : members.values()) {
                if (participant.context == context) participant.negativeUntilTick = 0;
            }
            built = true;
        }

        if (member.position != null) {
            if (validator.test(member.position)) {
                return new Result(member.position, Outcome.HIT, built, false, refreshReason);
            }
            release(memberId, member);
        }
        if (tick < member.negativeUntilTick) {
            return new Result(null, Outcome.NEGATIVE, built, false, refreshReason);
        }
        Candidate chosen = choose(context.candidates, validator, scorer);
        if (chosen == null) {
            member.negativeUntilTick = tick + NEGATIVE_TTL_TICKS;
            return new Result(null, Outcome.NEGATIVE, built, false, refreshReason);
        }
        member.position = chosen;
        member.negativeUntilTick = 0;
        reservations.put(cell(chosen), memberId);
        return new Result(chosen, Outcome.ASSIGNED, built, true, refreshReason);
    }

    /** Explicit departure, in addition to the bounded last-request lease. */
    public synchronized void release(long memberId) {
        Member member = members.remove(memberId);
        if (member != null) release(memberId, member);
    }

    public synchronized void clear() {
        contexts.clear();
        members.clear();
        reservations.clear();
    }

    private Candidate choose(List<Candidate> candidates,
            Predicate<Candidate> validator, ToDoubleFunction<Candidate> scorer) {
        long rejected = 0L;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (reservations.containsKey(cell(candidate))) {
                rejected |= 1L << i;
            } else {
                candidateScores[i] = candidate.staticScore() + scorer.applyAsDouble(candidate)
                        + neighborPenalty(candidate);
            }
        }
        for (int attempt = 0; attempt < candidates.size(); attempt++) {
            Candidate best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            int bestIndex = -1;
            for (int i = 0; i < candidates.size(); i++) {
                if ((rejected & (1L << i)) != 0L) continue;
                Candidate candidate = candidates.get(i);
                double score = candidateScores[i];
                if (score < bestScore) {
                    best = candidate;
                    bestIndex = i;
                    bestScore = score;
                }
            }
            if (best == null) return null;
            if (validator.test(best)) return best;
            rejected |= 1L << bestIndex;
        }
        return null;
    }

    private double neighborPenalty(Candidate candidate) {
        int count = 0;
        for (long reserved : reservations.keySet()) {
            double dx = candidate.x() - (double) (int) (reserved >> 32);
            double dy = candidate.y() - (double) (int) reserved;
            if (dx * dx + dy * dy <= 4.0) count++;
        }
        return count * RESERVED_NEIGHBOR_COST;
    }

    private void expireMembers(int tick) {
        Iterator<Map.Entry<Long, Member>> iterator = members.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Member> entry = iterator.next();
            if (tick - entry.getValue().lastRequestTick >= ttlTicks) {
                release(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
    }

    private void retire(Context context) {
        Iterator<Map.Entry<Long, Member>> iterator = members.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Member> entry = iterator.next();
            if (entry.getValue().context == context) {
                release(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
    }

    private void release(long memberId, Member member) {
        if (member.position == null) return;
        reservations.remove(cell(member.position), memberId);
        member.position = null;
    }

    private static List<Candidate> normalize(List<Candidate> supplied) {
        Objects.requireNonNull(supplied, "candidate generator returned null");
        List<Candidate> candidates = new ArrayList<>(Math.min(supplied.size(), MAX_CANDIDATES));
        Set<Long> seen = new HashSet<>();
        for (Candidate candidate : supplied) {
            Objects.requireNonNull(candidate, "null firing candidate");
            if (!Float.isFinite(candidate.staticScore())) {
                throw new IllegalArgumentException("candidate score must be finite");
            }
            if (seen.add(cell(candidate))) candidates.add(candidate);
            if (candidates.size() == MAX_CANDIDATES) break;
        }
        return List.copyOf(candidates);
    }

    private static boolean moved(float beforeX, float beforeY,
                                 float afterX, float afterY, float squaredLimit) {
        double dx = afterX - (double) beforeX;
        double dy = afterY - (double) beforeY;
        return dx * dx + dy * dy >= squaredLimit;
    }

    private static long cell(Candidate candidate) {
        return ((long) candidate.x() << 32) | (candidate.y() & 0xffffffffL);
    }
}
