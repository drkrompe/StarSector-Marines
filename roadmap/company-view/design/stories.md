# Company view open work

Status: ACTIVE — 12 open stories
Written: 2026-08-23
Updated: 2026-08-23 — C15 records the retained Fleet Armory rewrite after the UI foundation.

Read `company-view-nouns.md` before changing a company-view story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `c2-formation-model.md` | Planned | Defines the shared read model required by C3, C4, and C5. |
| `c3-company-card-stack.md` | Planned | Depends on C2; expands the shipped HQ roster area. |
| `c4-whereabouts-and-deployed-state.md` | Planned | Depends on C2 and C3; derives whereabouts rather than persisting them. |
| `c5-battle-hud-company-rollup.md` | Planned | Depends on the shipped deployment-identity law and C2. |
| `c6-after-action-by-fireteam.md` | Planned | Depends on shipped deployment identity; coordinate officer outcomes with C13. |
| `c8-lift-capacity-and-multi-pass-drops.md` | Ready | Slices 1–3 are folded; only explicit safe rejoin for late arrivals remains. |
| `c11-the-contract-board.md` | Planned | Uses the shipped campaign-map home; owns offers, not obligation clocks. |
| `c12-the-debug-company.md` | Deferred | Slices 1–2 are folded; combined arms waits for player-side vehicle deployment. |
| `c13-the-task-force.md` | Partial | Slice 1 is folded; per-officer outcomes and scalable assignment remain. |
| `c14-fire-team-equipment-templates.md` | Partial | Slices 1–4 ship templates, assignment, availability, fast swap and squad arrangements; conformance is next. |
| `c15-retained-fleet-armory.md` | Planned | Depends on UI U1–U4 and C14 conformance; rewrites the Armory around formation, template, billet, and transaction context. |
| `squad-id-terminology-cleanup.md` | Proposed | Terra finding: squad IDs cross APIs and persisted payloads under legacy fire-team names; requires an explicit save-compatibility plan. |
