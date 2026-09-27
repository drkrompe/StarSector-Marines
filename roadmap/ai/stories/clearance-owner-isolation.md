Status: IN PROGRESS
Written: 2026-09-27

# Clearance cancellation owner isolation

`ai-nouns.md` owns route intent and body-specific movement obligations. Ordinary
infantry path replacement cannot own a mech clearance proof and should not join
its cancellation critical section. JFR records infantry hold/setPath callers
blocked on the clearance service monitor during a mech request.

Prove the owner/type invariant before skipping cancellation for non-mechs. Keep
mech cancellation, completed-witness retention and stale-proof rejection intact.
Verify the actual NavigationService seam with tiny direct tests, then repeat the
eight-mech paced capture. This does not claim to remove the remaining mech-to-mech
monitor, snapshot allocation or GC costs.
