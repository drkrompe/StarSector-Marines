# Conquest stories

Status: ACTIVE — Conquest objective and territorial work remains here; its paired commander acceptance is tracked by Mission Command.

Written: 2026-08-23

Updated: 2026-08-27 — moved commander-specific work to `conquest-command.md`
and the Mission Command board.

The territorial model, compound loop, and Conquest victory law are implemented.
`conquest-command.md` owns the attacker/defender strategy and its active live
acceptance stories. Reinforcement mechanics remain owned by the Reinforcement
feature:

| Story | State | Intent |
|---|---|---|
| `progressive-reinforcement.md` | PARKED | Manually verify defender frontline response, safe delivery, and supply degradation across a Conquest push. |
| `biome-counterattack.md` | PARKED | Manually tune and verify the telegraphed defender counterattack as a territorial swing. |

Do not create duplicate Conquest stories for those systems. Their standing
contracts live in `reinforcement-nouns.md`; this feature supplies the
territorial context in `conquest-nouns.md`, and Mission Command adapts that
context through `conquest-command.md`.
