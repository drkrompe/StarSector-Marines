# Garrison airfield: make it worth taking

Status: PLANNED

Written: 2026-08-28

Read `reinforcement-nouns.md` and `mapgen-nouns.md` before implementing this.
The airfield itself is shipped: the Conquest ward sites an apron in the yard the
packing leaves, marks four hardstands, authors a `LandingPad` on each with
`GARRISON_AIRFIELD` purpose, and publishes one `AIRBASE` tactical node. Seven of
eight canonical seeds get one.

What is missing is the reason to care about it. Right now it is a readable
objective and a distinct piece of open ground to cross, and nothing more: the
shuttle means still scores its own landing zone with `LandingZoneScorer` and
never looks at the authored field.

## Scope

- The shuttle means prefers an authored `GARRISON_AIRFIELD` berth over a scored
  patch of open ground when one is defender-held and reachable from the delivery
  hint.
- Losing the airfield degrades air delivery rather than ending it — the means
  falls back to the scorer, landing somewhere worse.

## Constraints

- **Do not make the airfield the shuttle supply gate.** The gate is a
  defender-held `COMMAND_POST`, and swapping it would strand every map that has
  no airfield — which is every non-fortress map and one ward seed in eight.
  Whether a command post authorises a drop or an airfield flies it is a real
  question, and it belongs with the work that gives ordinary compounds an
  airfield, not here.
- Law 2 stands: the landing area is not the objective. A sortie may land on the
  field and be assigned somewhere else entirely.
- Law 4 stands: the means creates ordinary air actors. No airfield-specific
  delivery path.

## Acceptance

- With a defender-held airfield, a committed shuttle reinforcement lands on an
  authored hardstand rather than on scored ground.
- With the airfield marine-held, the same request still commits, and lands
  elsewhere.
- No map without an airfield changes behaviour at all.
