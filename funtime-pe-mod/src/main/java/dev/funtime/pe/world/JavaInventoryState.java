package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Java-shaped player inventory model. Network synchronization is deliberately
 * kept outside this model so server corrections can replace slots atomically.
 */
public final class JavaInventoryState {
    public static final int HOTBAR_SIZE = 9;
    public static final int PLAYER_SLOT_COUNT = 36;

    private final JavaItemStack[] slots = new JavaItemStack[PLAYER_SLOT_COUNT];
    private volatile int selectedSlot;
    private volatile int health = 20;
    private volatile int hunger = 20;
    private final Map<Integer, JavaItemStack[]> containers = new HashMap<>();
    private volatile int openContainerId = -1;

    public JavaInventoryState() {
        Arrays.fill(slots, JavaItemStack.empty());
    }

    public synchronized JavaItemStack slot(int index) {
        return index < 0 || index >= slots.length ? JavaItemStack.empty() : slots[index];
    }

    public synchronized void setSlot(int index, JavaItemStack stack) {
        if (index >= 0 && index < slots.length) {
            slots[index] = stack == null ? JavaItemStack.empty() : stack;
        }
    }

    public synchronized void replacePlayerInventory(JavaItemStack[] contents) {
        if (contents == null) {
            return;
        }
        final int count = Math.min(contents.length, slots.length);
        for (int index = 0; index < count; index++) {
            slots[index] = contents[index] == null
                    ? JavaItemStack.empty() : contents[index];
        }
    }

    public synchronized void clearPlayerInventory() {
        Arrays.fill(slots, JavaItemStack.empty());
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public void select(int slot) {
        selectedSlot = Math.max(0, Math.min(HOTBAR_SIZE - 1, slot));
    }

    public int health() {
        return health;
    }

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(20, health));
    }

    public int hunger() {
        return hunger;
    }

    public void setHunger(int hunger) {
        this.hunger = Math.max(0, Math.min(20, hunger));
    }

    public synchronized void replaceContainer(int containerId, JavaItemStack[] contents) {
        if (containerId < 0 || contents == null) {
            return;
        }
        final JavaItemStack[] copy = new JavaItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null
                    ? JavaItemStack.empty() : contents[index];
        }
        containers.put(containerId, copy);
    }

    public synchronized void setContainerSlot(int containerId, int slot,
                                               JavaItemStack stack) {
        if (containerId < 0 || slot < 0) {
            return;
        }
        final JavaItemStack[] contents = containers.computeIfAbsent(
                containerId, ignored -> {
                    final JavaItemStack[] empty = new JavaItemStack[Math.max(27, slot + 1)];
                    Arrays.fill(empty, JavaItemStack.empty());
                    return empty;
                });
        if (slot >= contents.length) {
            return;
        }
        contents[slot] = stack == null ? JavaItemStack.empty() : stack;
    }

    public synchronized JavaItemStack containerSlot(int containerId, int slot) {
        final JavaItemStack[] contents = containers.get(containerId);
        if (contents == null || slot < 0 || slot >= contents.length
                || contents[slot] == null) {
            return JavaItemStack.empty();
        }
        return contents[slot];
    }

    public int openContainerId() {
        return openContainerId;
    }

    public void openContainer(int containerId) {
        openContainerId = containerId;
    }

    public synchronized void closeContainer(int containerId) {
        containers.remove(containerId);
        if (openContainerId == containerId) {
            openContainerId = -1;
        }
    }
}