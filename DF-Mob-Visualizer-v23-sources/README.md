# DF Mob Visualizer v23

This is a complete Fabric Loom project containing the client-side visual layer
reconstructed around the attached
entity-transmitter class. It keeps the existing scanner idea, but adds:

- `F8` — toggle the in-game HUD;
- `F9` — toggle the chunk overlay;
- `F7` — toggle selected mob models through blocks (the original model is
  rendered in a second, depth-disabled pass; no tracer/ray/box overlay);
- `F10` — open the in-game settings screen;
- persistent chunk marks in `config/df-mob-visualizer-chunks.json`;
- persistent color and threshold settings in
  `config/df-mob-visualizer.json`;
- purple marking for IDs below 10,000;
- configurable dark-red, red, dark-orange, and orange percentage bands;
- gold `ALERT` marks in the HUD and chunk overlay;
- glowing entity outlines through blocks when the option is enabled.
- charged creepers are tracked and displayed with their own status and color.
- `F5` — clear the session immediately; `F6` — clear chunk history immediately.
- `HURT` highlights an entity while it is damaged; `HURT*` persists it in the
  session when the “Мобы после удара” option is enabled.
- HUD rows include the exact ID percentage with two decimal places. ID color
  rules are configured separately from percentage rules; ID rules always win.
- Each color-rule field accepts up to ten rules. Example:
  `id<=10001=#C855E8FF` and
  `percent<30=#FFFF2020;percent<50=#FFFFB000`.
- v3 adds a visual ten-row editor for both rule groups. Thresholds are
  controlled with sliders, and each row opens the normal color palette;
  rule syntax does not need to be typed manually.
- v4 adds a separate LOW_ID session toggle and an entity checklist. When the
  checklist is non-empty, only selected entity types are pinned by LOW_ID.
- v5 makes the selected ID/percentage rule color the actual HUD row color.
  ALERT and HURT stay as text tags instead of overwriting the configured rule
  color.
- v6 adds a separate entity checklist for ALERT mobs that are allowed to enter
  the session. An empty checklist keeps the previous behavior and allows all
  ALERT entity types.
- v7 adds colored block-level outlines and top-surface tinting inside marked
  chunks for grass, dirt, leaves, and water. The effect uses the stored
  ID/percentage rule color and is limited to the upper part of each column for
  render performance.
- v8 removes the separate active chunk-color rules. Marked chunks now use the
  same ID/percentage rules as HUD entities. Each matching chunk also creates a
  one-chunk ring around itself using a translucent version of the source
  color; an actual marked chunk always replaces a ring mark.
- v9 renders chunk fills and block outlines in an explicit blended,
  depth-disabled world pass so marked blocks remain visible over terrain and
  through nearby geometry.
- v10 removes the confusing chunk-wide surface quad from the renderer and
  draws one complete 1x1x1 wireframe for the actual surface block in every
  column of a marked chunk. Water, grass, dirt, leaves and other supported
  surface blocks are outlined individually, so the terrain remains readable.
- v11 adds a depth-independent 3D bounding-box fallback for through-wall mob
  highlighting. This keeps selected mobs visible even when Minecraft's normal
  entity render layer restores depth testing.
- v12 uses a dedicated immediate vertex provider for the through-wall pass,
  preventing Minecraft 1.21.4's `Not building!` crash when the shared world
  buffer has already been flushed.
- v13 removes the unavailable `BufferAllocator` dependency and renders
  through-wall bounding boxes directly with `BufferBuilder`/`Tessellator`,
  flushing the finished buffer explicitly.
- v14 uses `ShaderProgramKeys.POSITION_COLOR` instead of the unavailable
  `GameRenderer.getPositionColorProgram()` method.
- v15 avoids ending an empty `BufferBuilder` and restores rendering of the
  actual mob model during the through-wall pass, while keeping the box as a
  fallback marker.
- v16 colors and outlines the top block of every column in a marked chunk,
  rather than filtering to a small set of natural block types.
- translucent colored surfaces over discovered chunks, with configurable
  fill and border opacity in `config/df-mob-visualizer.json`.
- chunk history uses the same ID/percentage rules as the HUD. For example:
  `id<=10001=#C855E8FF;percent<30=#FFFF2020;percent<50=#FFFFB000`.

Chunk display can be controlled without restarting the game: `F9` toggles
both the persistent chunk borders and their colored surface fill. The
`F12` key is an emergency surface-overlay toggle: it enables the visualizer,
the saved chunk map, and surface fill in one press without deleting history.
While enabled, it also draws a magenta probe over the player's current chunk,
so the world-render path can be checked even when the saved map is empty.
The same "Карта поверхности чанков" switch is available in the F10 settings
screen, so the active state can be verified without relying on a key press.
The world overlay is registered at `AFTER_TRANSLUCENT`: this keeps the
surface visible after terrain rendering while avoiding writes to the closed
world buffer used by `LAST`.
`chunkOpacity` and `chunkBorderOpacity` settings accept values from `0.0`
(invisible) to `1.0` (fully opaque). `chunkFillStrength` is an additional
surface visibility multiplier from `0.1` to `3.0`; it is useful for testing
whether a missing overlay is caused by insufficient opacity. The current
defaults show the surface fill, use `0.55` opacity, and no longer apply an
additional hidden `0.35` multiplier.

The F10 screen controls HUD scale, chunk opacity, scan interval, chunk render
distance, session threshold, session pinning rules, custom entity types for
pinning, and cleanup actions for session data and chunk history. Session data
is saved to `config/df-mob-visualizer-session.json` when persistence is enabled.

Entity types can be entered as a comma-separated list in the F10 field, for
example `minecraft:zombie, minecraft:creeper`. A discovered chunk keeps the
most significant color it has seen instead of being downgraded by an ordinary
entity found later.

The original uploaded class is bytecode-only, so this source is a clean
reconstruction rather than a line-for-line decompile. The large numeric
`info.txt.txt` resource is not used and is intentionally excluded.

## Chunk marking rules

When an entity matches an ID or percentage color rule, its chunk is added to
the history using that same color. Rules are checked from left to right and
the first match wins. Supported comparisons are `<`, `<=`, `>`, `>=`, and `=`:

```text
id<=10001=#C855E8FF;percent<30=#FFFF2020;percent<50=#FFFFB000
```

`id` compares the entity ID directly. `percent` compares the entity ID to the
current maximum entity ID. Colors may be written as RGB (`#RRGGBB`) or ARGB
(`0xAARRGGBB`/`#AARRGGBB`). Every matching chunk creates a translucent
one-chunk ring around itself. Empty ring chunks do not overwrite real marked
chunks. This feature is entirely client-side and does not send packets to the
server.

The current alert rule mirrors the console's suspicious-gap rule:
`maxId - entityId > 100000`, excluding players and endermen. If the
transmitter later emits an explicit alert flag, `TrackedMob.alert` should use
that flag instead.

## Build

Use Java 21 and run:

```text
./gradlew build
```

The finished JAR is written to `build/libs/`.