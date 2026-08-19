# Mech roster — next session

## State of play

The existing heavy mech has been inventoried and the first specialist family is
specified in [`stories/s1-specialist-striders.md`](stories/s1-specialist-striders.md).
This was a design-only session: no production code or balance values changed.

The recommendation is to preserve the current heavy as the Bulwark apex/control,
then build two lighter variants on the renderer and GOAP behavior already in
the game:

- Hound: fast chaingun/SRM breacher with no LRM;
- Sirocco: fragile linear-cannon/LRM support with no SRM.

Needle, a fast unpodded scout, is documented but deferred until it can ship with
real recon/spotting behavior.

## First decision

Confirm or revise the provisional names and approve Hound/Sirocco as the first
pair. Preserve the role identities even if the fiction or names change.

## First implementation slice

1. Add a stable `MechVariant` profile/catalog without multiplying `UnitType`.
2. Add `LINEAR_CANNON`; generalize the direct-fire track and make both missile
   tracks genuinely optional.
3. Apply profile stats, physical dimensions, loadout, and layered appearance
   through one variant-aware construction seam.
4. Add Bulwark, Hound, and Sirocco to a deterministic comparison battle while
   leaving production rosters on Bulwark.
5. Verify missing-slot behavior, heavy regression, appearance, and consistent
   small-body geometry; then playtest and tune.

Only after that comparison is accepted should defender generation adopt the
budgeted mixed-lance rules in S1.

## Relevant code seams

- `UnitType.HEAVY_MECH` is the sole current pre-spawn mech tag and owns several
  physical/render values that light profiles must override consistently.
- `MechLoadoutComponent.defaultLoadout(role)` currently constructs all three
  mandatory weapon tracks.
- `MechWeapon` contains chaingun, SRM, and LRM but no linear cannon.
- `UnitRosterService` hardcodes the current layered appearance before the
  loadout is attached.
- `DefenderRoster` exposes only a mech count; production integration will need
  a deterministic profile/budget representation.
- `MechSupportPayload` should deliberately remain heavy-only for this story.

## Known traps

- Do not render a light mech smaller while leaving heavy-sized picking,
  separation, hit, or AoE geometry underneath it.
- Do not encode doctrine into the variant enum; roles and hardware are separate.
- Do not use dummy weapons to represent empty mounts; absent bands must be real.
- Do not let light bodies increase encounter threat merely because their count
  is higher.
- Do not make the Sirocco's backup cannon strong enough to erase its close-range
  weakness.
