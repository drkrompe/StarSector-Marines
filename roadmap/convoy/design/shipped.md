# Convoy — Shipped Ledger

Status: SHIPPED

Written: 2026-08-23

| Story | Shipped | Commit(s) | Folded into |
| --- | --- | --- | --- |
| `convoy-proof-admission.md` | 2026-09-27 | `5eb32140b`, `ceb2267c0`; burst/FIFO/invalidation/deadline tests and production-paced evidence | `convoy-nouns.md` admission and lifetime limits; `reinforcement-nouns.md` retryable latency; diagnostic semantics in `CLAUDE.md` |
| `v1-foundation.md` | 2026-05-20 | `76fe54da` | `convoy-nouns.md` — delivery role and initial ground-vehicle seam |
| `v1-polish.md` | 2026-05-27 | `b5227e19`, `8eedc630`, `5f9dd432`, `2703184c`, `7f958fd3`, `a6e4fc2c`, `3e7dfa97`, `b1c405a4`, `7202a08b` | `convoy-nouns.md` — body-driven motion, docking, safety, and APC behavior |
| `reinforcement-integration.md` | 2026-05-27 | `12424419`, `11b2c9f1`, `11b012f5`, `ef4cfebc`, `1315241a`, `4096a1d0` | `convoy-nouns.md` — reinforcement boundary, ARMORY gate, defender approach, and payload authority |
| `slice-0-cost-and-clearance.md` | 2026-06-02 | `30e8791c` | `convoy-nouns.md` — terrain preference and clearance law |
| `slice-1-route-planner.md` | 2026-06-02 | `caeac067` | `convoy-nouns.md` — cost-biased, clearance-valid advisory routing |
| `slice-2-wire-spawn.md` | 2026-06-02 | `896aea60` | `convoy-nouns.md` — live route wiring |
| `slice-0-controller-seam.md` | 2026-06-02 | `c621819a` | `convoy-nouns.md` — corridor/controller authority boundary |
| `slice-1-local-planner.md` | 2026-06-02 | `73359a06`, `d5f214cd` | `convoy-nouns.md` — rolling local planning |
| `slice-2-live-tracking.md` | 2026-06-02 | `966df57d`, `56976d7d` | `convoy-nouns.md` — always-kinematic tracking and off-map departure |
| `vehicle-damage.md` | 2026-08-27 | `fcc669d7` | `convoy-nouns.md` — shared armor/structure, combat targeting, once-only wrecks, passenger fate, and route obstruction |
| `route-proof-budget.md` | 2026-09-03 | `18bee14f`, `9afd0354` | `convoy-nouns.md` — mask reachability settled before searching, and a route proof bounded in searches; `reinforcement-nouns.md` — the probe/proof gap must be bounded |
| `route-proof-job.md` | 2026-09-03 | `0571ae4d`, `21fcedbe` | `convoy-nouns.md` — the mask and its labels held against the grid's own revision, and a route proof spread across ticks as a resumable job; `reinforcement-nouns.md` — a means may prepare across ticks, and the dispatcher drains on the tick preparation finishes |
| `progressive-route-fields.md` | 2026-09-23 | `cbf40e820`, `c88417723` | `convoy-nouns.md` — local entrance feasibility, frozen on-demand clearance and cost, incremental reachability, and shared per-tick route expansion budget |
