# Captain briefing authority

**Shipped:** `f6247ace` (2026-08-12)

Captain command now reaches the canonical deployment path. Ordinary missions
initialize their whole-fireteam selection from the active captain's persisted
home formation. Players may borrow any other line team for that mission without
rewriting home command, but the selected captain's rank caps the total selected
teams.

The briefing and fireteam-assignment surfaces display selected/max teams,
disable additions at capacity, and block ordinary deployment when the commander
is missing, unavailable, or over capacity. Debug personnel fixtures remain
captain-free; stationing retains its pre-existing captain plus anonymous-marine
contract.

The deployment boundary is fail-closed: an initialized empty selection means
zero named personnel. It cannot fall through to the legacy convenience overload
that interprets an absent selection as the entire ready line roster.

Focused tests cover home-formation defaults, bounded borrowing, unavailable
captains, explicit-empty readiness, and explicit-empty deployment freezing.
Those tests passed, followed by the complete isolated Gradle build on the
settled shared tree.
