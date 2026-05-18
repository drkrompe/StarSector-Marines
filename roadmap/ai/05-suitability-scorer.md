# 05 — Generic SuitabilityScorer System

**Stage 1, task 5. SUBAGENT TASK.**

## Goal

Reusable suitability-scoring layer for picking which squad members fill which
plan-step slots. Designed for Stage 2+ to lean on heavily (flanker selection,
anchor selection, suppressor selection, etc.) — Stage 1 only needs the
trivial "1 member, closest to target" case, but the abstraction is worth
landing now so Stage 2 actions don't reinvent it.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/scoring/Scorer.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/scoring/Scorers.java` (composition helpers)
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/scoring/RoleAssigner.java`

## API sketch

```
@FunctionalInterface
public interface Scorer<T> { float score(T candidate); }

public final class Scorers {
    public static <T> Scorer<T> weightedSum(List<WeightedScorer<T>> scorers);
    public static <T> Scorer<T> normalize(Scorer<T> s, float min, float max);
    public static <T> Scorer<T> negate(Scorer<T> s);   // for "lower is better"
}

public final class RoleAssigner {
    public static <C> Map<String, List<C>> assign(
        List<C> candidates,
        List<Slot<C>> slots);   // each slot: name, count, Scorer<C>

    public record Slot<C>(String name, int count, Scorer<C> scorer);
}
```

**Assignment algorithm:** greedy by slot-priority order (highest-mean-score
slot first), filling each slot with its top-N candidates. A swap-improvement
pass after the greedy: for each pair (slot-i candidate, slot-j candidate),
swap if the total score improves. Cheap and produces near-optimal results
for the small N (≤ 8 squadmates) we'll see. Hungarian-algorithm upgrade
later if profiling demands.

## Constraints

- Pure utility code. No `battle.*` imports beyond `Squad` / `Unit` if they're
  referenced for context. **Prefer generics with no battle-specific types.**
- No allocations in the hot path beyond the result Map.
- Stateless — same inputs produce same outputs.

## Acceptance

- A test: 4 candidates, 2 slots (1 of slot "anchor", 1 of slot "mover"), each
  with a Scorer that prefers different candidates → assignment picks the
  expected pair.
- A test: swap-improvement actually improves on the greedy result for a
  hand-crafted case where greedy is suboptimal.

## Subagent brief (when launching)

> "Build a generic suitability-scoring system at the paths in
> `roadmap/ai/05-suitability-scorer.md` (read that file first for the API and
> constraints). It's pure utility code with no `battle.*` dependencies; the
> Stage 2+ GOAP actions will use it for role assignment. Add JUnit-style smoke
> tests in `src/test/java/.../scoring/` if a test source set exists; otherwise
> a temporary `main` is fine. Build must pass: `./gradlew.bat build -x test`.
> Report what you added in under 200 words."
