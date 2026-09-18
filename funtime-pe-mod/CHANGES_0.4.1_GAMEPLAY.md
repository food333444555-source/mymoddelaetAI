# FUNTIME PE 0.4.1 — gameplay pass

This source snapshot was edited without running Gradle, Java compilation, or
JAR packaging.

## Added

- Bedrock `InventoryContent` decoding for the player inventory.
- Bedrock `InventorySlot` decoding for individual server corrections.
- Bedrock `MobEquipment` decoding and hotbar selection updates.
- A version-scoped compact item-stack codec.
- A small initial Bedrock item runtime registry with stable fallback names.
- Client-to-server hotbar selection through `MobEquipment`.
- Client-to-server item use on a block through the first
  `InventoryTransaction` path.
- Right-click wiring in the gameplay screen.
- Corrected the inventory screen's main-inventory/hotbar slot order.
- Resource-pack response ordering now distinguishes `HAVE_ALL_PACKS` from
  `COMPLETED` instead of claiming completion at the first negotiation packet.
- Server block updates are queued and applied to the real Java chunk bridge.
- Fallback gameplay block breaking now stays active until mouse release.
- Resource-pack data-info/chunk requests now download advertised pack bytes
  into the active session instead of immediately claiming completion.
- Container open/close and container inventory corrections are retained in
  the Java-facing inventory state.
- Added a basic container view with two-slot swap submission.
- Added `NetworkStackLatency` response handling plus basic respawn and
  dimension-change state resets.

## Deliberately not claimed as finished

This pass does not pretend that the entire Bedrock gameplay protocol is done.
Resource-pack application, the complete item/component registry, validated
container click transactions, crafting, entities and full server correction
handling still need separate version-scoped implementations.