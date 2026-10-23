# Unshipped armor paints

Baked paint variants for the Specter heavy (`oxide`, `sand`) and the boarding
jack (`hull-grey`, `oxide-green`, `soot`) that no runtime code loads. Only
`specter-heavy/paints/olive` and `boarding-jack/paints/pirate` are drawn in
play, so these were moved out of `mod/` to stop shipping them.

If campaign palette selection is implemented, move a paint back to
`mod/graphics/battle/marine-modular-topdown/variants/armor/<family>/paints/<paint>/`
and register it where `BattleSprites` loads the olive and pirate paints.
