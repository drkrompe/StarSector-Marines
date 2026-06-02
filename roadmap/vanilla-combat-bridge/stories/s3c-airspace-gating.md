# S3c — Airspace banding / AI gating (stub)

> The hard de-risk. Vanilla ship AI dogpiles all enemies in a flat 2D plane; the vision
> wants the fleet to fight *above* and skirt planetary defenses rather than charge into
> the ground band. Scoped, not built. The one piece where "it just works natively"
> stops being true.

## Goal

Resolve the spatial fork from [`../architecture.md`](../architecture.md) and, if needed,
influence ship AI so the fleet holds an "above the surface" band and treats planetary
defenses as threats to ward away from — until the fleet destroys them or ground forces
break through.

## The fork to decide first

- **Loose convention (cheap):** ground proxies sit in a map band ships have no reason to
  enter; carriers hang back, *fighters* do the air-to-ground. May need zero AI work —
  test whether vanilla AI naturally leaves a band of stationary low-value targets alone
  while it fights the enemy fleet.
- **Hard banding (expensive):** ships are actively gated out of the ground band and
  steer around planetary-defense threat zones. Needs a custom `ShipAIPlugin` and/or
  combat assignments / threat fields.

Decide empirically: set up a fleet fight + a ground band of defense proxies and *watch*
what unmodified AI does. The result picks the path.

## Scope (once the fork is decided)

**In (hard-banding path):** custom `ShipAIPlugin` or assignment/threat steering that
keeps the fleet in its band and skirts defense proxies; verify it composes with the
proxy damage loop (ships still *can* destroy defenses, just don't suicide into them).

**Out:** real altitude (doesn't exist in vanilla — banding is always a convention);
campaign-fleet consequences; tuning the full "fleet vs ground forces race" economy.

## Open questions

- Does unmodified AI already behave acceptably against a band of stationary low-priority
  targets (making this story mostly a no-op)?
- If we override `ShipAIPlugin`, how much vanilla behavior must we reimplement vs. wrap?
- How do planetary defenses signal "threat" to ship AI — real weapons on the proxy
  (engine resolves it) vs. a synthetic threat field?

## Acceptance

In a fleet-vs-fleet fight over a defended ground band, ships fight the enemy fleet and
engage/avoid planetary defenses sensibly rather than ignoring the band or charging into
it. Verdict: which fork, and how much AI work the chosen one actually needs.
