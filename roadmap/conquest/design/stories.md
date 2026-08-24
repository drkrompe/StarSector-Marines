# Conquest stories

Status: ACTIVE — no Conquest-owned implementation story is currently contracted.

Written: 2026-08-23

The territorial model, compound loop, deliberate capture allocation, and
Conquest victory law are shipped. The remaining live validation concerns
reinforcement behavior exercised on Conquest maps and remains owned by the
Reinforcement feature:

| Story | State | Intent |
|---|---|---|
| `progressive-reinforcement.md` | PARKED | Manually verify defender frontline response, safe delivery, and supply degradation across a Conquest push. |
| `biome-counterattack.md` | PARKED | Manually tune and verify the telegraphed defender counterattack as a territorial swing. |

Do not create duplicate Conquest stories for those systems. Their standing
contracts live in `reinforcement-nouns.md`; this feature supplies the
territorial context in `conquest-nouns.md`.
