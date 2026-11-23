# Thaumcraft aspect comparison

This table records aspect identities and unordered component pairs from three public API snapshots. The first column after the name shows the earliest of the three snapshots with that name. An em dash means the name has no default declaration in that snapshot. For example, the gap for Praecantatio in TC5 is a catalog change. The comparison covers base defaults and excludes addon registrations. Each entry describes historical metadata.

- **TC4, 48 aspects.** [Azanor's Minecraft 1.7.10 API revision](https://github.com/Azanor/thaumcraft-api/blob/ced30ed35097d53cdb2a843d705477e52896e54b/aspects/Aspect.java) · [4.2.3.5 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2227552)
- **TC5, 35 aspects.** [Azanor's Thaumcraft 5 API](https://github.com/Azanor/thaumcraft-5-api/blob/master/aspects/Aspect.java) · [5.2.4 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2287496)
- **TC6, 37 aspects.** [Azanor's Thaumcraft API mainline](https://github.com/Azanor/thaumcraft-api/blob/master/aspects/Aspect.java) · [6.1.BETA26 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023)

The release pages establish the named builds and Minecraft versions. Exact equivalence of every API row to each released JAR remains unverified. Parent order has no significance here. All 52 rows were checked against the default declarations in those three snapshots. The selected Thaumic Legacy catalog uses the TC6 formula wherever published and adds the TC4/TC5 definition for identities that TC6 omits.

| Historical name | First in comparison | TC4 | TC5 | TC6 | Selected formula source |
| --- | --- | --- | --- | --- | --- |
| Aer | 4 | Primary | Primary | Primary | TC6 |
| Alienis | 4 | Tenebrae + Vacuos | Tenebrae + Vacuos | Tenebrae + Vacuos | TC6 |
| Alkimia | 6 | — | — | Aqua + Praecantatio | TC6 |
| Aqua | 4 | Primary | Primary | Primary | TC6 |
| Arbor | 4 | Aer + Herba | — | — | TC4 |
| Auram | 4 | Aer + Praecantatio | Aer + Potentia | Aer + Praecantatio | TC6 |
| Aversio | 5 | — | Perditio + Spiritus | Perditio + Spiritus | TC6 |
| Bestia | 4 | Motus + Victus | Motus + Victus | Motus + Victus | TC6 |
| Cognitio | 4 | Ignis + Spiritus | Ignis + Spiritus | Ignis + Spiritus | TC6 |
| Corpus | 4 | Bestia + Mortuus | — | — | TC4 |
| Desiderium | 5 | — | Spiritus + Vacuos | Spiritus + Vacuos | TC6 |
| Exanimis | 4 | Mortuus + Motus | Mortuus + Motus | Mortuus + Motus | TC6 |
| Fabrico | 4 | Humanus + Instrumentum | Instrumentum + Permutatio | Instrumentum + Permutatio | TC6 |
| Fames | 4 | Vacuos + Victus | — | — | TC4 |
| Gelum | 4 | Ignis + Perditio | Ignis + Perditio | Ignis + Perditio | TC6 |
| Herba | 4 | Terra + Victus | Terra + Victus | Terra + Victus | TC6 |
| Humanus | 4 | Bestia + Cognitio | Spiritus + Victus | Spiritus + Victus | TC6 |
| Ignis | 4 | Primary | Primary | Primary | TC6 |
| Instrumentum | 4 | Humanus + Ordo | Metallum + Potentia | Metallum + Potentia | TC6 |
| Iter | 4 | Motus + Terra | — | — | TC4 |
| Limus | 4 | Aqua + Victus | — | — | TC4 |
| Lucrum | 4 | Fames + Humanus | — | — | TC4 |
| Lux | 4 | Aer + Ignis | Aer + Ignis | Aer + Ignis | TC6 |
| Machina | 4 | Instrumentum + Motus | Instrumentum + Motus | Instrumentum + Motus | TC6 |
| Messis | 4 | Herba + Humanus | — | — | TC4 |
| Metallum | 4 | Terra + Vitreus | Ordo + Terra | Ordo + Terra | TC6 |
| Meto | 4 | Instrumentum + Messis | — | — | TC4 |
| Mortuus | 4 | Perditio + Victus | Aqua + Perditio | Aqua + Perditio | TC6 |
| Motus | 4 | Aer + Ordo | Aer + Ordo | Aer + Ordo | TC6 |
| Ordo | 4 | Primary | Primary | Primary | TC6 |
| Pannus | 4 | Bestia + Instrumentum | — | — | TC4 |
| Perditio | 4 | Primary | Primary | Primary | TC6 |
| Perfodio | 4 | Humanus + Terra | — | — | TC4 |
| Permutatio | 4 | Ordo + Perditio | Ordo + Perditio | Ordo + Perditio | TC6 |
| Potentia | 4 | Ignis + Ordo | Ignis + Ordo | Ignis + Ordo | TC6 |
| Praecantatio | 4 | Potentia + Vacuos | — | Aer + Potentia | TC6 |
| Praemunio | 5 | — | Spiritus + Terra | Spiritus + Terra | TC6 |
| Sano | 4 | Ordo + Victus | — | — | TC4 |
| Sensus | 4 | Aer + Spiritus | Aer + Spiritus | Aer + Spiritus | TC6 |
| Spiritus | 4 | Mortuus + Victus | Mortuus + Victus | Mortuus + Victus | TC6 |
| Telum | 4 | Ignis + Instrumentum | — | — | TC4 |
| Tempestas | 4 | Aer + Aqua | — | — | TC4 |
| Tenebrae | 4 | Lux + Vacuos | Lux + Vacuos | Lux + Vacuos | TC6 |
| Terra | 4 | Primary | Primary | Primary | TC6 |
| Tutamen | 4 | Instrumentum + Terra | — | — | TC4 |
| Vacuos | 4 | Aer + Perditio | Aer + Perditio | Aer + Perditio | TC6 |
| Venenum | 4 | Aqua + Perditio | — | — | TC4 |
| Victus | 4 | Aqua + Terra | Aqua + Terra | Aqua + Terra | TC6 |
| Vinculum | 4 | Motus + Perditio | Motus + Perditio | Motus + Perditio | TC6 |
| Vitium | 4 | Perditio + Praecantatio | Perditio + Potentia | Perditio + Praecantatio | TC6 |
| Vitreus | 4 | Ordo + Terra | Aer + Terra | Aer + Terra | TC6 |
| Volatus | 4 | Aer + Motus | Aer + Motus | Aer + Motus | TC6 |

## Legacy selection

- The complete TC6 snapshot contributes 37 aspects. TC4 contributes 15 additional identities with their TC4 formulas: Arbor, Corpus, Fames, Iter, Limus, Lucrum, Messis, Meto, Pannus, Perfodio, Sano, Telum, Tempestas, Tutamen, and Venenum.
- Every TC5 aspect ID is present in TC6. Auram and Vitium change components from TC5 to TC6. Relative to TC4, the modern formulas selected for Fabrico, Humanus, Instrumentum, Metallum, Mortuus, Praecantatio, and Vitreus differ from TC4.
- TC4 Venenum shares the pair Aqua and Perditio with TC6 Mortuus. Both definitions are reachable and can coexist because the lens supplies evidence for a specific aspect ID. A future system that synthesizes an aspect from a pair alone would need an explicit output selector or a specimen catalyst that disambiguates the two. Neither formula is changed here.

## Material profiles

The public declarations verify identities and component formulas. Standalone historical material values for these specimens remain unverified. The [SimpleMinecraft guide](https://f.simpleminecraft.ru/index.php?/topic/193-thaumcraft-4235-aspekty-recepty-polucheniya-i-v-chyom-ih-iskat/) describes an addon-enabled environment, so its quantities never serve as standalone versions in this comparison. See [assignment provenance and gameplay](ASPECTS.md#assignment-provenance) for the modern profiles. The [98 specimen profiles](../src/main/resources/data/thaumiclegacy/thaumiclegacy/assignments/base.json) list all target IDs and actual quantities, and the [active catalog](../src/main/resources/data/thaumiclegacy/thaumiclegacy/aspects/base.json) defines the complete graph.
