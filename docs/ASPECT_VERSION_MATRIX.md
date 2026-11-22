# Thaumcraft aspect comparison

This table records aspect identities and unordered component pairs from three public API snapshots. The first column after the name shows the earliest of the three snapshots with that name. An em dash means the name has no default declaration in that snapshot. For example, the gap for Praecantatio in TC5 is a catalog change. The comparison covers base defaults and excludes addon registrations. Each entry describes historical metadata.

- **TC4, 48 aspects.** [Azanor's Minecraft 1.7.10 API revision](https://github.com/Azanor/thaumcraft-api/blob/ced30ed35097d53cdb2a843d705477e52896e54b/aspects/Aspect.java) · [4.2.3.5 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2227552)
- **TC5, 35 aspects.** [Azanor's Thaumcraft 5 API](https://github.com/Azanor/thaumcraft-5-api/blob/master/aspects/Aspect.java) · [5.2.4 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2287496)
- **TC6, 37 aspects.** [Azanor's Thaumcraft API mainline](https://github.com/Azanor/thaumcraft-api/blob/master/aspects/Aspect.java) · [6.1.BETA26 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023)

The release pages establish the named builds and Minecraft versions. Exact equivalence of every API row to each released JAR remains unverified. Parent order has no significance here. The 52 distinct names are the union of the three snapshots.

| Historical name | First in comparison | TC4 | TC5 | TC6 |
| --- | --- | --- | --- | --- |
| Aer | 4 | Primary | Primary | Primary |
| Alienis | 4 | Tenebrae + Vacuos | Tenebrae + Vacuos | Tenebrae + Vacuos |
| Alkimia | 6 | — | — | Aqua + Praecantatio |
| Aqua | 4 | Primary | Primary | Primary |
| Arbor | 4 | Aer + Herba | — | — |
| Auram | 4 | Aer + Praecantatio | Aer + Potentia | Aer + Praecantatio |
| Aversio | 5 | — | Perditio + Spiritus | Perditio + Spiritus |
| Bestia | 4 | Motus + Victus | Motus + Victus | Motus + Victus |
| Cognitio | 4 | Ignis + Spiritus | Ignis + Spiritus | Ignis + Spiritus |
| Corpus | 4 | Bestia + Mortuus | — | — |
| Desiderium | 5 | — | Spiritus + Vacuos | Spiritus + Vacuos |
| Exanimis | 4 | Mortuus + Motus | Mortuus + Motus | Mortuus + Motus |
| Fabrico | 4 | Humanus + Instrumentum | Instrumentum + Permutatio | Instrumentum + Permutatio |
| Fames | 4 | Vacuos + Victus | — | — |
| Gelum | 4 | Ignis + Perditio | Ignis + Perditio | Ignis + Perditio |
| Herba | 4 | Terra + Victus | Terra + Victus | Terra + Victus |
| Humanus | 4 | Bestia + Cognitio | Spiritus + Victus | Spiritus + Victus |
| Ignis | 4 | Primary | Primary | Primary |
| Instrumentum | 4 | Humanus + Ordo | Metallum + Potentia | Metallum + Potentia |
| Iter | 4 | Motus + Terra | — | — |
| Limus | 4 | Aqua + Victus | — | — |
| Lucrum | 4 | Fames + Humanus | — | — |
| Lux | 4 | Aer + Ignis | Aer + Ignis | Aer + Ignis |
| Machina | 4 | Instrumentum + Motus | Instrumentum + Motus | Instrumentum + Motus |
| Messis | 4 | Herba + Humanus | — | — |
| Metallum | 4 | Terra + Vitreus | Ordo + Terra | Ordo + Terra |
| Meto | 4 | Instrumentum + Messis | — | — |
| Mortuus | 4 | Perditio + Victus | Aqua + Perditio | Aqua + Perditio |
| Motus | 4 | Aer + Ordo | Aer + Ordo | Aer + Ordo |
| Ordo | 4 | Primary | Primary | Primary |
| Pannus | 4 | Bestia + Instrumentum | — | — |
| Perditio | 4 | Primary | Primary | Primary |
| Perfodio | 4 | Humanus + Terra | — | — |
| Permutatio | 4 | Ordo + Perditio | Ordo + Perditio | Ordo + Perditio |
| Potentia | 4 | Ignis + Ordo | Ignis + Ordo | Ignis + Ordo |
| Praecantatio | 4 | Potentia + Vacuos | — | Aer + Potentia |
| Praemunio | 5 | — | Spiritus + Terra | Spiritus + Terra |
| Sano | 4 | Ordo + Victus | — | — |
| Sensus | 4 | Aer + Spiritus | Aer + Spiritus | Aer + Spiritus |
| Spiritus | 4 | Mortuus + Victus | Mortuus + Victus | Mortuus + Victus |
| Telum | 4 | Ignis + Instrumentum | — | — |
| Tempestas | 4 | Aer + Aqua | — | — |
| Tenebrae | 4 | Lux + Vacuos | Lux + Vacuos | Lux + Vacuos |
| Terra | 4 | Primary | Primary | Primary |
| Tutamen | 4 | Instrumentum + Terra | — | — |
| Vacuos | 4 | Aer + Perditio | Aer + Perditio | Aer + Perditio |
| Venenum | 4 | Aqua + Perditio | — | — |
| Victus | 4 | Aqua + Terra | Aqua + Terra | Aqua + Terra |
| Vinculum | 4 | Motus + Perditio | Motus + Perditio | Motus + Perditio |
| Vitium | 4 | Perditio + Praecantatio | Perditio + Potentia | Perditio + Praecantatio |
| Vitreus | 4 | Ordo + Terra | Aer + Terra | Aer + Terra |
| Volatus | 4 | Aer + Motus | Aer + Motus | Aer + Motus |

## Material profiles

These sources establish names and combinations. Version-pinned material quantities for standalone builds remain unverified. The [SimpleMinecraft guide](https://f.simpleminecraft.ru/index.php?/topic/193-thaumcraft-4235-aspekty-recepty-polucheniya-i-v-chyom-ih-iskat/) covers an addon-enabled server. For example, old wood variants and modern individual logs require registry-aware comparison. For the current mod, the [98 Thaumic Legacy profiles](../src/main/resources/data/thaumiclegacy/thaumiclegacy/assignments/base.json) specify the actual block, item, and creature quantities. The [Thaumic Legacy definitions](../src/main/resources/data/thaumiclegacy/thaumiclegacy/aspects/base.json) supply the active graph.

## Save compatibility for a future catalog revision

The Overworld data file `thaumiclegacy_observations.dat` stores the original per-player block records. The separate `thaumiclegacy_aspect_discoveries.dat` stores player UUIDs, typed specimen IDs, and discovered aspect IDs. The current reader has no catalog-revision field.

A future migration should version the discovery data independently of Minecraft's data version. Preserve the raw original knowledge and the typed specimen history before projecting knowledge into a new catalog. Map an old ID only after its meaning and prerequisites have been verified, and keep ambiguous IDs in a per-player archive. Keep the first research milestone and player UUID isolation. Validate an idempotent migration against legacy fixtures, unmapped discoveries, and a second save/load before activating a new catalog.
