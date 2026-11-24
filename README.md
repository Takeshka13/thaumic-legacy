# ✨ Thaumic Legacy

*Rediscover the forgotten art of thaumaturgy.*

**Thaumic Legacy** is an independent, open-source Minecraft magic mod inspired by the legendary Thaumcraft experience.

Our goal is to reimagine the atmosphere and depth of Thaumcraft 4, 5, and 6 for modern Minecraft while introducing original ideas, mechanics, visuals, and improvements.

**Minecraft 1.21.1 | Fabric | Java 21**

> ⚠️ **Early Development**
>
> Thaumic Legacy is currently under active development. The mod is not yet ready for a public release.

**🌐 Languages:** English | [Русский](README_RU.md)

---

## 🔮 About Thaumic Legacy

Magic is not simply something you craft. It is something you discover.

Explore the world, investigate its hidden properties, uncover magical aspects, experiment with mysterious substances, and gradually master the forgotten art of thaumaturgy.

Every discovery should lead to new questions.

Every experiment should have consequences.

And every great thaumaturge must learn that knowledge comes at a price.

## ✨ Features

### 🔍 Exploration & Discovery

- Scan blocks, items, creatures, and aura nodes with the Thaumometer. Record observations and discover magical aspects.
- Wear Goggles of Revealing to locate nearby aura nodes and inspect alchemical equipment.

### 🌀 Magical Aspects

- Discover 52 classical primal and compound aspects. Material profiles and aspect combinations drive research and alchemy.

### 📖 Research

- The Thaumonomicon organizes research into eight categories, with separate observation and aspect views.
- Discoveries, prerequisites, and experiments unlock recipes and techniques for each player.

### ⚗️ Alchemy

- Brew alchemical recipes in the Crucible with dissolved materials and catalysts.
- Distill materials with an Alchemical Furnace and up to four stacked Arcane Alembics.
- Move essentia by phial or through Essentia Transport tubes, valves, and filters. Store it in Warded Jars and reserve an aspect with Jar Labels.
- Split compounds into their parent aspects with the Essentia Centrifuge. Automate known alchemical recipes with the Thaumatorium.

### 🌌 Aura & Magical Consequences

- Measure local Aura, Vis, and Flux. Ambient Vis recovers over time, and Flux slows its recovery.
- Discover regenerating aura nodes in newly generated Overworld chunks. Charge a Wand from their six primal Vis reserves.

### 🪄 Arcane Crafting & Future Thaumaturgy

- Craft magical equipment at the Arcane Workbench using local Vis and unlocked research.
- Upcoming work includes Magical Artifice, the Infernal Furnace, the Infusion Altar with Arcane Pedestals and a Runic Matrix, and Golemancy.
- A visual pass will revisit graphics, models, and interfaces. Spellcasting, broader world generation, and advanced Flux consequences remain planned.

---

## 🛠️ Development Progress

| System | Status |
|---|---|
| Fabric 1.21.1 foundation and 52 classical aspects | ✅ Implemented |
| Thaumometer scanning and Goggles of Revealing | ✅ Implemented |
| Thaumonomicon and research | 🚧 Foundation implemented. More research branches and book refinements planned. |
| Local Aura, Vis, and Flux | 🚧 Recovery, pollution, and remediation implemented. Balance and broader consequences remain open. |
| Aura nodes and Wand charging | ✅ Implemented. Nodes generate in new Overworld chunks. |
| Arcane Workbench and Crucible alchemy | ✅ Implemented |
| Alchemical Furnace, Arcane Alembics, and stacked distillation | ✅ Implemented |
| Warded Jars, phials, and Jar Labels | ✅ Implemented |
| Essentia Transport | 🚧 Tubes, valves, filters, and bounded routing implemented. Suction simulation remains planned. |
| Essentia Centrifuge and Thaumatorium | ✅ Implemented |
| Magical Artifice and Infernal Furnace | 📋 Planned |
| Infusion Altar, Arcane Pedestals, and Runic Matrix | 📋 Planned |
| Golemancy and spellcasting | 📋 Planned |
| Broader world generation, graphics, models, and interface overhaul | 📋 Planned |

Development plans are documented in:

- [Development Roadmap](docs/ROADMAP.md)
- [Feature Matrix](docs/FEATURE_MATRIX.md)
- [Gameplay Systems](docs/GAMEPLAY_SYSTEMS.md)
- [Project Architecture](docs/ARCHITECTURE.md)
- [Advanced Alchemy](docs/ADVANCED_ALCHEMY.md)

## 💻 Building from Source

### Requirements

- Java Development Kit 21
- Git
- An internet connection for downloading Gradle dependencies

### Clone the Repository

```bash
git clone https://github.com/Takeshka13/thaumic-legacy.git
cd thaumic-legacy
```

### Build

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The compiled mod JAR will be available in `build/libs/`.

### Run the Development Client

Windows:

```powershell
.\gradlew.bat runClient
```

Linux or macOS:

```bash
./gradlew runClient
```

### Run the Development Server

Windows:

```powershell
.\gradlew.bat runServer
```

Linux or macOS:

```bash
./gradlew runServer
```

A Minecraft server requires acceptance of its EULA before normal operation.

## 🧩 Add-on Support

Thaumic Legacy is being designed with modularity and extensibility in mind.

A public API for independent add-ons is part of the long-term architectural vision.

However, the current development priority is completing the core gameplay experience.

**Add-on integrations are not currently implemented or scheduled for release.**

## 🤝 Contributing

Contributions, bug reports, suggestions, translations, and constructive feedback are welcome.

As the project evolves, contribution guidelines and development documentation will be expanded.

Please check existing issues before reporting a new problem.

## 📜 License

The repository uses **CC0 1.0 Universal**. See [LICENSE](LICENSE) for the license text.

Only content that we have the rights to distribute is intended to be included in this repository. Third-party content may be subject to separate terms.

## ❤️ Credits & Disclaimer

Thaumic Legacy is inspired by **Thaumcraft**, originally created by **Azanor**.

We are grateful for the creativity and inspiration behind the original mod.

**Thaumic Legacy is an independent fan-made project.**

It is not an official continuation of Thaumcraft and is not affiliated with, endorsed by, or sponsored by the original creators or rights holders.

The project aims to use independently developed code, original artistic resources, and its own interpretation of magical gameplay systems.

**Minecraft** is a trademark of Mojang Studios. This project is not affiliated with Mojang Studios or Microsoft.

---

*The world is full of mysteries. Some are better left undiscovered.*
