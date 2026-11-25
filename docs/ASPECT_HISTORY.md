# Aspect history

See the [version matrix](ASPECT_VERSION_MATRIX.md) for the base aspect inventory and component pair comparison.

Thaumcraft used Latin-style names for aspects. The six historical primals were `Aer` (air), `Terra` (earth), `Ignis` (fire), `Aqua` (water), `Ordo` (order), and `Perditio` (entropy). Compounds use two components, with membership and some pairs changing across versions. The counts below come from the default declarations in Azanor's public APIs. The release records identify builds and Minecraft versions. [Thaumcraft 4 API](https://github.com/Azanor/thaumcraft-api/blob/ced30ed35097d53cdb2a843d705477e52896e54b/aspects/Aspect.java#L786), [Thaumcraft 5 API](https://github.com/Azanor/thaumcraft-5-api/blob/master/aspects/Aspect.java#L819), [Thaumcraft 6 API](https://github.com/Azanor/thaumcraft-api/blob/master/aspects/Aspect.java#L841).

## Thaumcraft 4

The Minecraft 1.7.10 API revision has 48 default aspects, including 42 compounds. The 4.2.3.5 release targets Minecraft 1.7.10. The API declares `Lux` (light) from air and fire, and `Vitreus` (crystal) from earth and order. Other landmarks in this catalog include `Tempestas` (weather) and `Arbor` (trees). [Thaumcraft 4 API](https://github.com/Azanor/thaumcraft-api/blob/ced30ed35097d53cdb2a843d705477e52896e54b/aspects/Aspect.java#L786-L889), [4.2.3.5 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2227552).

The SimpleMinecraft forum guide identifies its version as 4.2.3.5 and reports specimen quantities from a server with addons including Forbidden Magic. The FTB wiki uses 51 as a Thaumcraft 4 comparison figure with unspecified build and addon scope. The API count refers to default aspect types. [SimpleMinecraft forum topic 193](https://f.simpleminecraft.ru/index.php?/topic/193-thaumcraft-4235-aspekty-recepty-polucheniya-i-v-chyom-ih-iskat/), [FTB wiki comparison](https://ftb.fandom.com/wiki/Aspects_(Thaumcraft_5)).

## Thaumcraft 5

The Thaumcraft 5 API declares 35 defaults, including 29 compounds. This generation moved `Vitreus` to earth and air and introduced `Praemunio` (protection). The author published version 5.2.4 for Minecraft 1.8.9. [Thaumcraft 5 API](https://github.com/Azanor/thaumcraft-5-api/blob/master/aspects/Aspect.java#L819-L895), [5.2.4 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2287496).

## Thaumcraft 6

The Thaumcraft 6 API declares 37 defaults, including 31 compounds. Compared with the Thaumcraft 5 API, it adds `Alkimia` (alchemy) and brings back `Praecantatio` (magic), now composed of energy and air. The author published 6.1.BETA26 for Minecraft 1.12.2. [Thaumcraft 6 API](https://github.com/Azanor/thaumcraft-api/blob/master/aspects/Aspect.java#L841-L921), [6.1.BETA26 release](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023).

## Thaumic Legacy

Thaumic Legacy uses the 37 aspect definitions found in the Thaumcraft 6 API and 15 additional aspects from the Thaumcraft 4 API. The [version matrix](ASPECT_VERSION_MATRIX.md) identifies formula differences and the legacy extension that shares a pair with Mortuus. The Thaumometer and Research Journal use stable namespaced IDs, original colors and glyphs, and independently balanced specimen quantities. See the [bundled catalog](../src/main/resources/data/thaumiclegacy/thaumiclegacy/aspects/base.json) and [specimen assignments](../src/main/resources/data/thaumiclegacy/thaumiclegacy/assignments/base.json).
