# Classic magical tools

## Thaumometer and Goggles of Revealing

Craft a Thaumometer at a mundane crafting table to start discovery. It shares the server-validated observation rules of the Field Lens. Use it on blocks and creatures, aim at a dropped item, or crouch and use it with an item in the offhand. Its aura reading reports the local chunk's Vis and Flux. Aimed readings display the Crucible's mixture. The legacy Field Lens remains usable and craftable. It is absent from the creative tab and amethyst collection no longer advertises its recipe to new players. Both tools use the same saved discoveries.

Craft Goggles of Revealing at the Arcane Workbench after Arcane Foundations. Use two Attuned Lenses in the outer slots of the top row and three leather below. The recipe costs 24 Vis. Wear them in the head slot and look at a Crucible or a supported aura-relevant source to read it without holding a tool. The client displays a server reading. Aura nodes will need their own world implementation and reveal provider. [Thaumcraft beta journal text](https://raw.githubusercontent.com/Azanor/thaumcraft-beta/master/en_us.lang)

## Wand

Craft the starter wand with a stick between two iron nuggets in a vertical line. The item has six independent, initially empty Vis reservoirs. Its tooltip displays Aer, Aqua, Ignis, Terra, Ordo, and Perditio with a capacity of 25 per aspect. Minecraft's item data component persists the amounts when an inventory is saved and synchronizes the item to clients. The cap is a Thaumic Legacy gameplay value.

The chunk aura keeps its existing scalar Vis balance and Flux mechanics. A wand charge operation acts on one primal reservoir and does not modify the aura ledger. This phase supplies bounded storage and operations for future charging. Wands start empty in survival. [Azanor's introduction to the original wooden wand](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1292130-thaumcraft-6-1-beta26-no-longer-being-developed)

Future nodes can persist six typed reserves at a dimension and position. A server-side node interaction will validate reach, active chunk, knowledge, node availability, and wand headroom before transferring one aspect. The accepted amount should be removed from the node and placed into the wand in the same operation. Keep node regeneration, type, and identity in the world's saved data. Add the node's reveal data through a dedicated world provider when nodes exist.
