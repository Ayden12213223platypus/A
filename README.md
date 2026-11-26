# Item Exporter (Minecraft 26.3 / NeoForge)

A client-side mod that exports Minecraft-rendered item icons and JEI recipe layouts to image files.

## Features

- Captures item stacks through Minecraft's GUI item renderer, including GUI-rendered model layers, texture tints, foil/glint and effects.
- Saves static icons as transparent PNGs and visually animated icons as looping GIFs.
- Captures the exact main-hand stack (including components) or an item's default stack by registry ID.
- Uses JEI's recipe lookup and layout renderer to export a selected recipe category and recipe index as PNG.
- Saves exports in `<Minecraft instance>/item_exports/`.

## Commands

### Item icon

```text
/itemexport <item-id|hand>
/itemexportcancel
```

Examples: `/itemexport minecraft:diamond`, `/itemexport minecraft:diamond_sword`, `/itemexport hand`.

The icon is captured on a 20x20 GUI-pixel canvas and enlarged 10x using nearest-neighbour scaling, resulting in a 200x200 transparent PNG or GIF. Animation detection is automatic. The mod samples at client-tick cadence; if the image remains static for three seconds it writes PNG. If it animates, it records until the image returns exactly to its first frame. A 60-second safety limit prevents an unusual animation from capturing forever.

### JEI recipe

```text
/recipeexport <item-id> <method> [index] [scale]
```

Examples:

```text
/recipeexport minecraft:diamond_pickaxe crafting
/recipeexport minecraft:iron_ingot smelting
/recipeexport minecraft:diamond_pickaxe crafting 2 5
/recipeexport minecraft:stone_bricks stonecutting
```

Built-in method aliases include `crafting`, `smelting` (or `furnace`), `blasting`, `smoking`, `campfire`, `stonecutting`, `smithing`, `brewing`, `anvil`, and `grindstone`. Other JEI categories can be selected by their namespaced category ID. The index is one-based and defaults to 1. Scale is an optional integer from 1 to 10; when omitted, the mod chooses the largest scale that fits the current game window.

## Dependencies

- Minecraft Java Edition 26.3
- NeoForge 26.3.0.118 or newer in the 26.3 line
- Just Enough Items (JEI) 31.23.0.130 or newer for Minecraft 26.3
- Java 25 to build

JEI is required at runtime because recipe exports use JEI's live recipe categories and renderers.

## Build

With Java 25 installed, run:

```bash
gradle --no-daemon clean build
```

The mod JAR is created under `build/libs/`. GitHub Actions is configured to build on every push and pull request, and to upload the JAR as a workflow artifact.

## Limitations and project status

This repository contains the source project and an automated build workflow; it does not yet contain a compiled JAR. The source has not been compiled or tested in a running Minecraft 26.3 client. The build workflow is intended to verify the APIs and dependencies in CI.

Item export captures what Minecraft's GUI item renderer draws, not world, first-person, particle, or entity-context effects. GIF is limited to 256 palette entries. The 10x item output enlarges the rendered icon; it cannot invent detail beyond the game's GUI render resolution. Recipe exports use the JEI layout renderer and are limited by the available game-window dimensions.
