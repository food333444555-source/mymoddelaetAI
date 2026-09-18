package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import dev.funtime.pe.world.JavaBlockState;
import dev.funtime.pe.world.JavaChunkView;
import dev.funtime.pe.world.JavaEntityView;
import dev.funtime.pe.world.JavaInventoryState;
import dev.funtime.pe.world.JavaItemStack;
import dev.funtime.pe.world.JavaWorldState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

/**
 * Java-style play surface for a translated Bedrock session.
 *
 * <p>The network implementation stays isolated because a Bedrock server
 * cannot consume Java play packets. The player-facing surface deliberately
 * follows the Java multiplayer flow: the connection opens into the normal
 * game HUD, the server details are hidden after login, and movement, chat,
 * inventory and pause controls are available from the same screen.</p>
 */
public class PePlayScreen extends Screen {
    private final Screen parent;
    private final JavaPlaySession connection;
    private TextFieldWidget chat;
    private Text status = Text.literal("Мир загружается...");
    private long tick;
    private float cameraYaw;
    private float cameraPitch = 18.0f;
    private boolean cameraDragging;
    private int selectedSlot;
    private int lastMouseX;
    private int lastMouseY;
    private double verticalVelocity;
    private BlockTarget breakingTarget;

    public PePlayScreen(Screen parent, JavaPlaySession connection) {
        super(Text.literal(SourceVersion.javaTitle()));
        this.parent = parent;
        this.connection = connection;
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 155;
        chat = new TextFieldWidget(this.textRenderer, left, this.height - 44, 250, 20,
                Text.literal("Сообщение"));
        chat.setMaxLength(256);
        this.addDrawableChild(chat);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Отправить"),
                        ignored -> sendChat())
                .dimensions(left + 258, this.height - 44, 90, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Отключиться"),
                        ignored -> close())
                .dimensions(left + 353, this.height - 44, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (!connection.isJoined()) {
            status = Text.literal("Соединение закрыто.");
            return;
        }

        final JavaWorldState world = connection.world();
        double x = world.playerX();
        double y = world.playerY();
        double z = world.playerZ();
        final boolean sprinting = InputUtil.isKeyPressed(window(), GLFW.GLFW_KEY_LEFT_SHIFT);
        final double speed = sprinting ? 0.14 : 0.08;
        boolean moved = false;
        final double angle = Math.toRadians(cameraYaw);
        final double forwardX = -Math.sin(angle);
        final double forwardZ = Math.cos(angle);
        final double rightX = Math.cos(angle);
        final double rightZ = Math.sin(angle);
        double horizontalX = 0.0;
        double horizontalZ = 0.0;
        if (InputUtil.isKeyPressed(window(), GLFW.GLFW_KEY_W)) {
            horizontalX += forwardX * speed;
            horizontalZ += forwardZ * speed;
            moved = true;
        }
        if (InputUtil.isKeyPressed(window(), GLFW.GLFW_KEY_S)) {
            horizontalX -= forwardX * speed;
            horizontalZ -= forwardZ * speed;
            moved = true;
        }
        if (InputUtil.isKeyPressed(window(), GLFW.GLFW_KEY_A)) {
            horizontalX -= rightX * speed;
            horizontalZ -= rightZ * speed;
            moved = true;
        }
        if (InputUtil.isKeyPressed(window(), GLFW.GLFW_KEY_D)) {
            horizontalX += rightX * speed;
            horizontalZ += rightZ * speed;
            moved = true;
        }
        if (!collidesAt(x + horizontalX, y, z)) {
            x += horizontalX;
        }
        if (!collidesAt(x, y, z + horizontalZ)) {
            z += horizontalZ;
        }
        final boolean grounded = isGrounded(x, y, z);
        if (InputUtil.isKeyPressed(window(), GLFW.GLFW_KEY_SPACE) && grounded
                && verticalVelocity <= 0.0) {
            verticalVelocity = 0.42;
        }
        verticalVelocity = Math.max(-0.65, verticalVelocity - 0.08);
        final double nextY = y + verticalVelocity;
        if (!collidesAt(x, nextY, z)) {
            y = nextY;
            moved = true;
        } else if (verticalVelocity < 0.0) {
            verticalVelocity = 0.0;
        }
        if (collidesAt(x, y, z)) {
            y = Math.max(y, world.clientWorld().bottomY() + 1.0);
        }
        if (moved || tick % 10 == 0) {
            world.moveLocalPlayer(x, y, z, cameraYaw, cameraPitch);
            try {
                connection.sendMove(x, y, z, cameraYaw, cameraPitch, true, tick);
            } catch (IOException exception) {
                status = Text.literal("Ошибка движения: " + safe(exception));
            }
        }
        tick++;
    }

    private boolean isGrounded(double x, double y, double z) {
        return connection.world().chunkCount() > 0
                && solidAt(x, y - 0.08, z);
    }

    private boolean collidesAt(double x, double y, double z) {
        if (connection.world().chunkCount() == 0) {
            return false;
        }
        final double[] offsets = {-0.28, 0.28};
        for (double offsetX : offsets) {
            for (double offsetZ : offsets) {
                if (solidAt(x + offsetX, y, z + offsetZ)
                        || solidAt(x + offsetX, y + 1.7, z + offsetZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean solidAt(double x, double y, double z) {
        return !connection.world().clientWorld().getBlockState(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isAir();
    }

    private long window() {
        return MinecraftClient.getInstance().getWindow().getHandle();
    }

    private void sendChat() {
        final String message = chat.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        try {
            connection.sendChat(message);
            chat.setText("");
        } catch (IOException exception) {
            status = Text.literal("Ошибка чата: " + safe(exception));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && chat.isFocused()) {
            sendChat();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            selectedSlot = keyCode - GLFW.GLFW_KEY_1;
            connection.inventory().select(selectedSlot);
            try {
                connection.sendHotbarSelect(selectedSlot);
            } catch (IOException exception) {
                status = Text.literal("Ошибка выбора предмета: " + safe(exception));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            this.client.setScreen(new PeInventoryScreen(this, connection));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(new PePauseScreen(this, parent, connection));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cameraDragging = true;
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            return true;
        }
        final int centerX = this.width / 2;
        final int centerY = this.height / 2;
        if (mouseX >= centerX - 190 && mouseX <= centerX + 190
                && mouseY >= 48 && mouseY <= centerY + 145) {
            final BlockTarget target = traceBlock();
            if (target == null) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            try {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    breakingTarget = target;
                    connection.sendBlockBreakStart(target.position().getX(),
                            target.position().getY(), target.position().getZ(), target.face());
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    final BlockPos block = target.position();
                    final Direction face = target.direction();
                    connection.sendUseItemOnBlock(
                            block, face,
                            0.5, 0.5, 0.5);
                }
            } catch (IOException exception) {
                status = Text.literal("Ошибка действия: " + safe(exception));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private BlockTarget traceBlock() {
        final JavaWorldState world = connection.world();
        final double pitch = Math.toRadians(cameraPitch);
        final double yaw = Math.toRadians(cameraYaw);
        final double cosPitch = Math.cos(pitch);
        final double dx = -Math.sin(yaw) * cosPitch;
        final double dy = -Math.sin(pitch);
        final double dz = Math.cos(yaw) * cosPitch;
        double previousX = world.playerX();
        double previousY = world.playerY() + 1.62;
        double previousZ = world.playerZ();
        for (double distance = 0.25; distance <= 5.0; distance += 0.1) {
            final double x = world.playerX() + dx * distance;
            final double y = world.playerY() + 1.62 + dy * distance;
            final double z = world.playerZ() + dz * distance;
            if (!world.clientWorld().getBlockState(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isAir()) {
                final int currentX = (int) Math.floor(x);
                final int currentY = (int) Math.floor(y);
                final int currentZ = (int) Math.floor(z);
                final int previousBlockX = (int) Math.floor(previousX);
                final int previousBlockY = (int) Math.floor(previousY);
                final int previousBlockZ = (int) Math.floor(previousZ);
                final Direction face;
                if (previousBlockX < currentX) {
                    face = Direction.WEST;
                } else if (previousBlockX > currentX) {
                    face = Direction.EAST;
                } else if (previousBlockY < currentY) {
                    face = Direction.DOWN;
                } else if (previousBlockY > currentY) {
                    face = Direction.UP;
                } else if (previousBlockZ < currentZ) {
                    face = Direction.NORTH;
                } else {
                    face = Direction.SOUTH;
                }
                return new BlockTarget(new BlockPos(currentX, currentY, currentZ), face);
            }
            previousX = x;
            previousY = y;
            previousZ = z;
        }
        return null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && breakingTarget != null) {
            final BlockTarget target = breakingTarget;
            breakingTarget = null;
            try {
                connection.sendBlockBreakStop(
                        target.position().getX(), target.position().getY(),
                        target.position().getZ(), target.face());
            } catch (IOException exception) {
                status = Text.literal("Ошибка завершения ломания: " + safe(exception));
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cameraDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (cameraDragging && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cameraYaw += (float) deltaX * 0.45f;
            cameraPitch = Math.max(-70.0f, Math.min(70.0f,
                    cameraPitch - (float) deltaY * 0.35f));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void close() {
        connection.close();
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        renderJavaWorld(context);
        renderJavaHud(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderJavaWorld(DrawContext context) {
        final JavaWorldState world = connection.world();
        final int centerX = this.width / 2;
        final int viewLeft = 8;
        final int viewTop = 8;
        final int viewRight = this.width - 8;
        final int viewBottom = this.height - 74;
        final int horizon = Math.max(viewTop + 42,
                Math.min(viewBottom - 40, this.height / 2 - (int) cameraPitch));
        final double focalLength = Math.max(180.0, this.width * 0.72);
        final double yaw = Math.toRadians(cameraYaw);
        final double sinYaw = Math.sin(yaw);
        final double cosYaw = Math.cos(yaw);

        context.fill(viewLeft, viewTop, viewRight, viewBottom, 0xFF79B7E6);
        context.fill(viewLeft, horizon, viewRight, viewBottom, 0xFF3E332A);
        context.fill(viewLeft, horizon, viewRight, horizon + 28, 0xFF4A813F);
        context.fill(viewLeft, viewTop, viewRight, viewTop + 28, 0x8010141A);

        for (JavaChunkView chunk : world.chunks()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    final JavaBlockState state = chunk.topBlock(x, z);
                    if (state.isAir()) {
                        continue;
                    }
                    final double blockX = chunk.chunkX() * 16.0 + x + 0.5 - world.playerX();
                    final double blockZ = chunk.chunkZ() * 16.0 + z + 0.5 - world.playerZ();
                    final double depth = -blockX * sinYaw + blockZ * cosYaw;
                    if (depth <= 0.35 || depth > 96.0) {
                        continue;
                    }
                    final double side = blockX * cosYaw + blockZ * sinYaw;
                    final double scale = Math.max(1.0, Math.min(42.0, focalLength / depth));
                    final int px = centerX + (int) Math.round(side * focalLength / depth);
                    final int py = horizon - (int) Math.round(
                            (chunk.topY(x, z) + 1.0 - world.playerY()) * focalLength / depth);
                    final int blockColor = shadeColor(color(state),
                            Math.max(-30, Math.min(24, (int) Math.round(18.0 - depth * 0.18))));
                    final int width = Math.max(1, (int) Math.round(scale));
                    final int height = Math.max(1, (int) Math.round(scale * 0.82));
                    if (px >= viewLeft && py >= viewTop + 30
                            && px <= viewRight && py <= viewBottom) {
                        context.fill(px - width / 2, py - height, px + width / 2 + 1,
                                py + 1, blockColor);
                        context.fill(px - width / 2, py - height,
                                px + width / 2 + 1, py - height + Math.max(1, height / 8),
                                shadeColor(blockColor, 22));
                    }
                }
            }
        }
        for (JavaEntityView entity : world.entities()) {
            final double blockX = entity.x() - world.playerX();
            final double blockZ = entity.z() - world.playerZ();
            final double depth = -blockX * sinYaw + blockZ * cosYaw;
            if (depth <= 0.35 || depth > 96.0) {
                continue;
            }
            final double side = blockX * cosYaw + blockZ * sinYaw;
            final double scale = Math.max(1.0, Math.min(36.0, focalLength / depth));
            final int ex = centerX + (int) Math.round(side * focalLength / depth);
            final int ey = horizon - (int) Math.round(
                    (entity.y() - world.playerY()) * focalLength / depth);
            final int size = Math.max(2, (int) Math.round(scale * 0.35));
            context.fill(ex - size, ey - size * 2, ex + size,
                    ey + size, 0xFFE05A5A);
        }
        context.fill(viewLeft, horizon, viewRight, horizon + 1, 0xB04F6B7A);
    }

    private void renderJavaHud(DrawContext context) {
        final JavaWorldState world = connection.world();
        final JavaInventoryState inventory = connection.inventory();
        final int bottom = this.height - 8;
        final int hotbarWidth = 182;
        final int hotbarLeft = this.width / 2 - hotbarWidth / 2;
        final int hotbarTop = bottom - 28;

        context.drawTextWithShadow(this.textRenderer, Text.literal(SourceVersion.javaTitle()),
                14, 12, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, status, 14, 42, 0xFFCC66);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Чанков: " + world.chunkCount()
                        + "  XYZ: " + Math.round(world.playerX()) + " "
                        + Math.round(world.playerY()) + " "
                        + Math.round(world.playerZ())),
                14, 58, 0xD0D0D0);

        final int crosshairX = this.width / 2;
        final int crosshairY = Math.max(40, this.height / 2 - 12);
        context.fill(crosshairX - 5, crosshairY, crosshairX + 6, crosshairY + 1, 0xE8FFFFFF);
        context.fill(crosshairX, crosshairY - 5, crosshairX + 1, crosshairY + 6, 0xE8FFFFFF);

        context.fill(hotbarLeft - 2, hotbarTop - 2,
                hotbarLeft + hotbarWidth + 2, hotbarTop + 26, 0xC0101014);
        for (int slot = 0; slot < 9; slot++) {
            final int left = hotbarLeft + slot * 20;
            final int color = slot == inventory.selectedSlot() ? 0xFFFFD54A : 0xFF777777;
            context.fill(left, hotbarTop, left + 18, hotbarTop + 18, 0xFF2B2B32);
            context.fill(left, hotbarTop, left + 18, hotbarTop + 1, color);
            context.fill(left, hotbarTop + 17, left + 18, hotbarTop + 18, color);
            final JavaItemStack stack = inventory.slot(slot);
            if (!stack.isEmpty()) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(shortItemName(stack.itemId())),
                        left + 2, hotbarTop + 3, 0xFFFFFF);
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(Integer.toString(stack.count())),
                        left + 12, hotbarTop + 11, 0xFFFFFF);
            }
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(Integer.toString(slot + 1)), left + 6, hotbarTop + 5, 0xE0FFFFFF);
        }
        context.fill(14, bottom - 45, 114, bottom - 36, 0xC0101014);
        context.fill(16, bottom - 43, 16 + inventory.health() * 5, bottom - 38, 0xFF8D2020);
        context.fill(16, bottom - 43, 16 + inventory.health() * 5, bottom - 40, 0xFFE43F3F);
        context.drawTextWithShadow(this.textRenderer, Text.literal("❤ " + inventory.health()),
                120, bottom - 45, 0xFFFF7777);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("WASD  мышь  Shift  Space  •  Enter — чат"),
                14, bottom - 62, 0xB0B0B0);
    }

    private static int color(JavaBlockState state) {
        final String name = state.name();
        if (name.contains("water")) {
            return 0xFF2F75B5;
        }
        if (name.contains("grass") || name.contains("leaves")) {
            return 0xFF4C9A45;
        }
        if (name.contains("sand")) {
            return 0xFFD9C27A;
        }
        if (name.contains("wood") || name.contains("planks")) {
            return 0xFF95633D;
        }
        if (name.contains("stone") || name.contains("cobble")) {
            return 0xFF777777;
        }
        if (name.contains("dirt")) {
            return 0xFF795548;
        }
        return "minecraft:air".equals(name) ? 0xFF182028 : 0xFFB05C3C;
    }

    private static int shadeColor(int color, int amount) {
        final int alpha = color & 0xFF000000;
        final int red = Math.max(0, Math.min(255, ((color >>> 16) & 0xFF) + amount));
        final int green = Math.max(0, Math.min(255, ((color >>> 8) & 0xFF) + amount));
        final int blue = Math.max(0, Math.min(255, (color & 0xFF) + amount));
        return alpha | red << 16 | green << 8 | blue;
    }

    private static String shortItemName(String itemId) {
        final int separator = itemId.indexOf(':');
        final String name = separator >= 0 ? itemId.substring(separator + 1) : itemId;
        return name.length() > 7 ? name.substring(0, 7) : name;
    }

    private static String safe(IOException exception) {
        return exception.getMessage() == null ? "сетевая ошибка" : exception.getMessage();
    }

    private record BlockTarget(BlockPos position, Direction direction) {
        private int face() {
            return switch (direction) {
                case DOWN -> 0;
                case UP -> 1;
                case NORTH -> 2;
                case SOUTH -> 3;
                case WEST -> 4;
                case EAST -> 5;
            };
        }
    }
}