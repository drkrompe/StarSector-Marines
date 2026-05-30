# S2 Slice 3 — Fighter-cover opt-in UI — ✅ SHIPPED (compile-verified)

Final slice of the S2 explicit-detachment arc. Passive fighter cover is now an
explicit opt-in commitment instead of an automatic whole-fleet scan, completing
the user's headline ask ("player opts-in for passive fighter cover and shuttles").

## What landed

**`battle.flyby.PlayerFleetWings` — committed-carrier resolution:**

- `CarrierBay` — a committable carrier (one fleet member with ≥1 mapped fighter
  bay): `shipName`, `profiles`, `bayCount()`.
- `committableCarriers()` — enumerates the player's carriers (fleet order).
- `rosterFrom(List<CarrierBay>)` — builds the marine-side `FlybyRoster` from the
  committed subset, staggering arrivals across committed bays.
- `fromPlayerFleet()` reimplemented as `rosterFrom(committableCarriers())` — the
  whole-fleet behavior is preserved as the default / display fallback.

**Both pre-battle screens — carrier opt-in toggles (mirror the transport rows):**

- `BriefingScreen` + `CommsConsolePanel`: `deselectedCarriers` set +
  `cachedCarriers` snapshot (reset/snapshot alongside the transport equivalents);
  one clickable `[x]`/`[ ]` toggle per carrier ("`<ship>` (N bays)" / "— held
  back"). Default committed, so the "just hit Accept" flow is unchanged.
- `onAccept` now passes `committedWings()` (the committed-carrier roster) to
  `MissionLaunch.buildSimulation` instead of the whole-fleet scan; `BriefingScreen`'s
  "Allied Air" summary reflects the committed subset.
- `CommsConsolePanel.computeExpandedHeight` grows the card for the new
  "Fighter cover:" section.

## Scope notes / follow-ups

- **Default = committed (opt-out toggles).** Preserves the existing accept flow +
  the transport-sufficiency gate. If we want default-OFF (true opt-in), it's a
  one-line flip per screen.
- **Power narrowing still rides the whole fleet.** Powers come from
  `DetachmentResolver.committedShips()` = the whole player fleet. Narrowing powers
  to a committed subset needs a *member-level* commitment surface (a recon-source
  ship like an Apogee is neither a transport nor a carrier, so the role-organized
  toggles can't commit it). **Follow-up:** a "support detachment" category (or a
  unified member picker) before power narrowing is meaningful. Tracked as the
  remaining work on the commitment layer.
- **Briefing info-zone vertical budget.** Carrier rows + transport rows + captains
  share the info zone; a carrier-heavy fleet can push captain rows toward the
  existing overflow limit. Pre-existing layout concern, deferred to a polish pass.

## Verified

`gradlew compileJava` + `compileTestJava` green.
