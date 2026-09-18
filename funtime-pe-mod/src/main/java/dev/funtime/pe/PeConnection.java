package dev.funtime.pe;

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
                            + "Откройте PE → Аккаунт и завершите device-code вход."));
            return;
        }

        if (activeClient != null) {
            activeClient.close();
            activeClient = null;
        }

        BedrockClient.connect(server, account, message -> statusConsumer.accept(Text.literal(message)))
                .whenComplete((connected, error) -> client.execute(() -> {
                    if (error != null) {
                        final Throwable cause = error.getCause() == null ? error : error.getCause();
                        statusConsumer.accept(Text.literal("Подключение не выполнено: "
                                + safeMessage(cause)));
                        return;
                    }
                    activeClient = connected;
                    statusConsumer.accept(Text.literal(
                            "RakNet/Bedrock transport подключён. Синхронизация мира продолжается."));
                }));
    }

    private static String safeMessage(Throwable error) {
        final String message = error.getMessage();
        return message == null || message.isBlank() ? "неизвестная ошибка" : message;
    }
}