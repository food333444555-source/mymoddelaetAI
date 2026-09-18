package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import dev.funtime.pe.protocol.BedrockClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.function.Consumer;

final class PeConnection {
    private static BedrockClient activeClient;

    private PeConnection() {
    }

    static String targetProtocolLabel() {
        return BedrockVersion.DEFAULT.displayName() + " (" + BedrockVersion.DEFAULT.protocolVersion() + ")";
    }

    static void connect(MinecraftClient client, Screen parent, PeServerEntry server,
                        Consumer<Text> statusConsumer) {
        final PeAccountState account = PeConfig.account();
        if (server.authMode() == PeAuthMode.MICROSOFT && !account.isMicrosoftConnected()) {
            client.setScreen(new PeNoticeScreen(parent,
                    "Для этого сервера нужен Microsoft/Xbox вход.\n"
                            + "Откройте раздел аккаунта и завершите вход по коду устройства."));
            return;
        }

        if (activeClient != null) {
            activeClient.close();
            activeClient = null;
        }

        BedrockClient.connect(server, account,
                        message -> statusConsumer.accept(Text.literal(javaStatus(message))))
                .whenComplete((connected, error) -> client.execute(() -> {
                    if (error != null) {
                        if (activeClient != null) {
                            activeClient.close();
                            activeClient = null;
                        }
                        final Throwable cause = error.getCause() == null ? error : error.getCause();
                        statusConsumer.accept(Text.literal("Подключение не выполнено: "
                                + safeMessage(cause)));
                        return;
                    }
                    activeClient = connected;
                    statusConsumer.accept(Text.literal(
                            "Сетевая игра подключена. Загружается мир."));
                    final JavaPlaySession session = new JavaPlaySession(connected);
                    try {
                        VanillaJavaBridgeRegistry.install(
                                VanillaJavaBridge.enter(client, server, session));
                    } catch (RuntimeException exception) {
                        statusConsumer.accept(Text.literal(
                                "Не удалось открыть Java-мир: " + safeMessage(exception)));
                        client.setScreen(new JavaPlayScreen(parent, session));
                    }
                }));
    }

    static void closeActive() {
        if (activeClient != null) {
            activeClient.close();
            activeClient = null;
        }
    }

    private static String safeMessage(Throwable error) {
        final String message = error.getMessage();
        return message == null || message.isBlank() ? "неизвестная ошибка" : message;
    }

    private static String javaStatus(String message) {
        if (message == null || message.isBlank()) {
            return "Подключение к серверу...";
        }
        return message
                .replace("Подключение к RakNet", "Подключение к серверу")
                .replace("RakNet установлен. Запрашиваю настройки Bedrock...",
                        "Соединение установлено. Согласование параметров...")
                .replace("NetworkSettings получен. Bedrock login отправлен.",
                        "Параметры получены. Выполняется авторизация...")
                .replace("Bedrock login принят. Ожидаю StartGame...",
                        "Авторизация принята. Ожидаю готовность мира...")
                .replace("Игрок Bedrock создан.", "Игрок создан.")
                .replace("Bedrock encryption включён.", "Защищённое соединение включено.")
                .replace("StartGame получен. Bedrock-сессия создана.",
                        "Мир готов. Игровая сессия создана.")
                .replace("Bedrock-сессия", "Игровая сессия")
                .replace("Bedrock session", "Игровая сессия")
                .replace("Bedrock disconnect", "Соединение закрыто")
                .replace("Bedrock", "Сетевая игра")
                .replace("PE-мир", "мир");
    }
}