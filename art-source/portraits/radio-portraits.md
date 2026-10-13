# Marine radio portraits

Six original helmet-interior closeups for the active infantry radio cast,
generated with the built-in ImageGen tool. Kestrel established the framing;
the other five used his master as a style/composition reference with new faces.
Each actor keeps one face across all of that voice's spoken turns.

| Actor | Master | Final prompt | Runtime asset |
|---|---|---|---|
| Kestrel Two | [master](radio-kestrel.master.png) | [prompt](radio-kestrel.prompt.txt) | [256px PNG](../../mod/graphics/portraits/radio-kestrel.png) |
| Hammer Three | [master](radio-hammer.master.png) | [prompt](radio-hammer.prompt.txt) | [256px PNG](../../mod/graphics/portraits/radio-hammer.png) |
| Viper One | [master](radio-viper.master.png) | [prompt](radio-viper.prompt.txt) | [256px PNG](../../mod/graphics/portraits/radio-viper.png) |
| Jackal | [master](radio-jackal.master.png) | [prompt](radio-jackal.prompt.txt) | [256px PNG](../../mod/graphics/portraits/radio-jackal.png) |
| Ox | [master](radio-ox.master.png) | [prompt](radio-ox.prompt.txt) | [256px PNG](../../mod/graphics/portraits/radio-ox.png) |
| Rivet | [master](radio-rivet.master.png) | [prompt](radio-rivet.prompt.txt) | [256px PNG](../../mod/graphics/portraits/radio-rivet.png) |

Export from the repository root with `art-source/portraits/pack.ps1 -Name radio-<actor>`.
The exporter retains the full square composition and resamples to 256px.
The radio panel displays each portrait at 88px.

Run `gradlew.bat createSnapshots -Psnapshot=radio-portraits` for the production
overlay review sheet, including each actor's longest combat report and the
Castle/text-only controls.
