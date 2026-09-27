Status: IN PROGRESS
Written: 2026-09-27

# Command-frame cost attribution

`mission-command-nouns.md` owns frozen perspective input and the paired pulse.
Stage and host-CPU attribution is implemented, including opt-in roster, member,
contact and publication slices plus input counts for every measured pulse.
Tiny direct tests preserve frame contents with diagnostics enabled or disabled.

The remaining obligation is a measured explanation of episodic squad-row CPU
cost before changing frame construction. Current eight-mech paced evidence has
roughly eleven-millisecond frame freezes with nine milliseconds in squad rows;
cost is spread across roster/member gathering and publication, not a demonstrated
large contact scan. Compound facts are under one millisecond in those samples,
so caching garrison geometry is not the supported fix for that spike. Distinguish
allocation/compilation effects from changed input volume with JFR and the retained
per-pulse counts; do not extrapolate a single stack sample into an attribution.
Also separate that CPU-heavy case from elapsed stalls: an ordinary replay records
a roughly twenty-two-millisecond frame with only seven milliseconds host CPU,
three-and-a-half milliseconds squad rows and under two milliseconds facts. A
larger FRAME wall timer alone is not evidence of more geometry computation.

Remove demonstrated redundant work only after attribution, preserving same-tick
perspective freeze, immutable published facts, topology invalidation and legal
disclosure. Shared geometry may be reused; mutable objective state must remain
fresh. Verify exact content and mutation isolation on tiny inputs and repeat the
paced support-lance capture. Preserve controls for same-build comparisons.
