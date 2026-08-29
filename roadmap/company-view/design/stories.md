# Company view open work

Status: ACTIVE — 10 open stories
Written: 2026-08-23
Updated: 2026-08-29 — the battle HUD rollup is folded; C15 remains the Armory
inspect/edit workflow.

Read `company-view-nouns.md` before changing a company-view story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `c2-formation-model.md` | Planned | Defines the shared read model required by C3 and C4. |
| `c3-company-card-stack.md` | Planned | Depends on C2; expands the shipped HQ roster area. |
| `c4-whereabouts-and-deployed-state.md` | Planned | Depends on C2 and C3; derives whereabouts rather than persisting them. |
| `c6-after-action-by-fireteam.md` | Planned | Depends on shipped deployment identity; coordinate officer outcomes with C13. Its squad attribution is shipped — the frozen `IDENTITY_CAMPAIGN_SQUAD_ID` the squad career fold uses. |
| `c8-lift-capacity-and-multi-pass-drops.md` | Ready | Slices 1–3 are folded; only explicit safe rejoin for late arrivals remains. |
| `c11-the-contract-board.md` | Planned | Uses the shipped campaign-map home; owns offers, not obligation clocks. |
| `c12-the-debug-company.md` | Deferred | Slices 1–2 are folded; combined arms waits for player-side vehicle deployment. |
| `c13-the-task-force.md` | Partial | Slice 1 is folded; per-officer outcomes and scalable assignment remain. |
| `c15-retained-fleet-armory.md` | In progress | Owned-company grid, formation drill-down, recovery clocks, reinforcement, persistent Weapon/Armor authoring and migration, atomic twelve-billet issue, and the spatial Mech Lab are retained. |
| `squad-id-terminology-cleanup.md` | Proposed | Terra finding: squad IDs cross APIs and persisted payloads under legacy fire-team names; requires an explicit save-compatibility plan. |
