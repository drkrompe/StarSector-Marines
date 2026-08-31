# Surface Relief — Open Stories

Status: ACTIVE — one implemented story awaits manual acceptance; one requested story and one stretch direction remain open.

Written: 2026-08-23

Read `surface-relief-nouns.md` before changing either story.

| Story | State | Scope |
| --- | --- | --- |
| `s5-roofs-cast-shadows.md` | Requested, not started | A roof is the tallest thing on a building and casts nothing today, because only `CellTopology.isWall` has a height. Give a roofed footprint its own macro height so the building casts as a solid block, and make destroying a roof stop that shadow — the destruction already exists and would become visible from outside. |
| `s3-dynamic-bump-lighting.md` | Awaiting manual acceptance | Confirm live shader/effect behavior, timing, and tuning before shipping the implemented ground-light pass. |
| `s4-unit-relief.md` | Deferred stretch | Add lighting-only unit normals after S3 acceptance; do not apply ground parallax to sprites. |
