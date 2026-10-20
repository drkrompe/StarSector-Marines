# Machine guard

Status: IN PROGRESS

Written: 2026-10-20

## Intent

A mech, a lance or an armed vehicle takes no posts, but beside a garrison it is
the natural heavy support: it stands just outside the trench or building, off
the garrison's lanes of fire, watching the way the attack is believed to come,
and answers whatever comes with its own doctrine or turret. Owner decision
2026-10-20 (`garrisonables.md`, Decided direction): a machine screens a manned
place it cannot man. Prefer what adds fun: the player gets a verb that makes a
tank and a rifle squad read as one position, and the AI gets a lance that walks
up beside a pressed trench rather than wandering the lane.

## Scope

1. **Player.** A mech, lance or armed vehicle whose click lands on a
   fighting-position run or held place its own side **mans** — somebody of the
   side standing in it, or an infantry squad of the side ordered to it — and
   that the side does not believe enemy-held (that stays ASSAULT) gets
   **GUARD**, announced in the friendly colour ("GUARD / HEAVY SUPPORT"). An
   unarmed hull only moves. The order:
   - drives to a **guard cell**: connected to the machine, three to seven cells
     outside the footprint (inside reach), never in the **garrison's lanes** —
     the band in front of the place along the threat axis, as wide as the place
     plus two cells — preferring a clear line of fire to a watch point twelve
     cells past the place's front, then a cell abreast of the front edge, then a
     nearer ring; lance-mates keep three cells apart;
   - reads the **threat axis** from the side's believed contacts within 48
     cells of the place (nearer weighing more), falling back to the direction
     the machine came from;
   - owns the machine's locomotion: doctrine, lance cohesion and the hull's
     duel band never walk it off or into the place; its targeting and weapons
     are its own, so it engages what approaches;
   - faces the axis while nothing is in its sights (a mech's hips; a turret's
     idle rest), and re-reads it every three seconds, moving a mech to a new
     cell when the threat has swung more than 45 degrees (a hull keeps its
     ground and turns its gun);
   - **ends** five seconds after the side last manned the place (the garrison
     left or died), on any new order, on a withdrawal, a crippled chassis, a
     disarmed hull or direct control.
2. **AI.** A commander-tier `MachineGuardDirector`, side-agnostic like the
   manning director: given the places its side mans this pulse (and which are
   pressed to the start level), its free lances, and its lance count, it
   attaches at most one free, useful lance a pulse to a manned, pressed place
   nobody guards, one lance a place, never more at once than half the side's
   lances rounded up and never more than two; a lance must be able to arrive
   within the manning arrival bound. A guard stands down when its place is no
   longer manned, has stayed below the start level for the manning dwell, or its
   lance falls below half strength, or is taken up by other work. Orders go out
   through `CommandEffects.guardPlace` / `standDownGuard` as a commanded guard on
   each chassis; a player's order on a chassis outranks the commander's.
   - Defender (`ConquestDefenderCommand`): a lance is free when it is in the
     mobile pool and this pulse gave it no track response or structure task; a
     lance in contact is not in the pool's candidates.
   - Attacker hold (`ConquestCommand`, beside `HeldGround`): momentum first — a
     guarding lance stays in the pool, so a company that recruits it or a
     capture that commits it takes it and the guard stands down; none in the
     convergence phases.
   - No side fields armed vehicles under a commander today, so the AI half is
     lances only.

Out of scope: guards for compounds; a guard that turns into an assault when the
garrison is overrun; vehicles repositioning under a guard.

## Hook boundary

`GarrisonDirector` and `GarrisonableRegistry` internals are untouched (a
concurrent story is cutting runs into stretches). The commanders read the
manning director's published `hotPlaces()` and their own squads' run orders.

## Acceptance

- Unit tests (`MachineGuardOrderTest`, `MachineGuardDirectorTest`): intent
  resolution (GUARD / ASSAULT / MOVE, armed vs unarmed hull, a squad sent to a
  run), threat axis, guard cell outside and off the lanes, lance spacing,
  release, commanded vs player precedence, director cap, one per place, arrival
  and strength bounds, stand-down rules.
- Scene `machine-guard`: player mech and tank loops (cursor GUARD, guard cell
  outside, never inside, off the lanes, faces the threat, engages approaching
  enemies, ends after the garrison leaves), an AI loop on a production fort
  (the defender attaches at most the bound, each guard outside its run).
- `machine-assault`, `reactive-belt`, `attacker-holds-ground`,
  `player-reactive-manning` stay green.
