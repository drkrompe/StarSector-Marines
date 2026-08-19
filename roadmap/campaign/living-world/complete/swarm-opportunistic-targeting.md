# Opportunistic swarm targeting — shipped

Shipped in `4fedb34a`.

## What landed

- Replaced the objective-first selector with one local candidate pool containing
  sensed marines and sensed, registered active evacuees. Distance—not unit
  category—now decides which exposed target is most attractive.
- Added 25% distance leeway for the current target. This prevents rapid target
  oscillation while still letting a substantially closer marine peel a runner
  away from a colonist.
- Preserved civilian fog-of-war: ambient civilians never qualify, registered
  evacuees remain unknown before first contact, and the sealed shelter still
  redirects all pressure to marines.
- Preserved purposeful swarm motion outside local contact. With no sensed
  candidate, a runner continues toward valid remembered prey or uses the
  nearest marine as its strategic pressure fallback.
- A genuine target change bypasses the ordinary 0.35-second repath throttle so
  the runner turns onto its new victim immediately.

## Verification

- Focused coverage proves marine peel, exposed-evacuee selection, ambient and
  hidden-civilian exclusion, target hysteresis, remembered prey, immediate
  path replacement, full-distance melee closure, and sustained eight-runner
  pack pressure.
- `gradlew.bat :test --tests
  com.dillon.starsectormarines.battle.evacuation.SwarmPressureBehaviorTest`
  passed.
- `gradlew.bat build` passed, including the root and asset-pipeline suites.

## Still manual

Play LOW/MEDIUM/HIGH rescue missions to tune the 25% target leeway if marines
feel either unable to peel or too effective at permanently distracting the
swarm. Roster size and kill cadence remain in the shared feel queue.
