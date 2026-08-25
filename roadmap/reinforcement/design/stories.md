# Reinforcement stories

Status: ACTIVE — open implementation board.

Written: 2026-08-23

Updated: 2026-08-25 — moved target-faction ground rosters into content/live-acceptance follow-through after shipping the data and creation-path backbone.

| Story | State | Intent |
|---|---|---|
| `marine-side-reinforcement.md` | PLANNED | Define and implement marine-side triggers, supply gates, and delivery eligibility without treating the current defender ladder as implicitly symmetric. |
| `reinforcement-strength-scaling.md` | PLANNED | Make SMALL/MEDIUM/LARGE a coherent force and pacing contract across walk-in, convoy, and shuttle delivery. |
| `reinforcement-status-readout.md` | PLANNED | Surface player-side reinforcement tickets and inbound commitments from the simulation's authoritative resource and delivery state. |
| `scripted-reinforcement-triggers.md` | PLANNED | Add bounded mission/scripted trigger inputs through the shared request contract, with authored timing and side semantics. |
| `means-dispatch-transaction.md` | PLANNED | Make fulfillment report success so a post-check delivery failure can fall through or recover without silently consuming the request. |
| `progressive-reinforcement.md` | PARKED | Manually accept and tune the shipped Conquest front-line response; no new implementation scope. |
| `biome-counterattack.md` | PARKED | Manually accept and tune the shipped Conquest counterattack presentation and pacing; no new implementation scope. |
| `target-faction-ground-rosters.md` | IN PROGRESS | Data and creation-path backbone shipped; add future equipment content, merged-submod discovery, deterministic Conquest fixtures, and live faction-read acceptance. |
