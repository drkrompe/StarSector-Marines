# W5 — Submod merge

> Deferred until a real submod exists.

**Status:** deferred, deliberately.

## Scope

Discovery of weapon files across enabled mods, load order, id override
semantics, and validation with actionable errors.

## Why deferred

the `moddable-tilesets` track made the
same call for its Phase 3 and the reasoning carries: merge semantics
designed without a consumer are guesses, and the questions that matter —
does a submod override a built-in weapon or only add, what happens when two
submods claim one id, is load order declared or discovered — are answered
by the first real case, not in advance.

**Solve it once for both catalogs.** Tiles and weapons want identical merge
machinery. Whichever track reaches a real submod first should build the
shared mechanism, and the other should adopt it rather than growing a
parallel one.
