package dev.funtime.pe;

import de.florianmichael.viafabricplus.protocoltranslator.ProtocolTranslator;
import de.florianmichael.viafabricplus.settings.impl.BedrockSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

import java.lang.reflect.Method;

final class PeConnection {
    private PeConnection() {
    }

    static String targetProtocolLabel() {
        return BedrockProtocolVersion.bedrockLatest.getName();
    }

    static void connect(MinecraftClient client, Screen parent, String name, String host, int port) {
        if (!hasMicrosoftAccount()) {
            client.setScreen(new PeNoticeScreen(parent,
                    "Сначала войдите через Microsoft, затем подключайтесь к серверу."));
            return;
        }

        // This selects ViaBedrock's latest protocol supported by the 1.21.4 bridge.
        // The server-side endpoint discovered for FUNTIME is mc.funtime.su:19132.
        ProtocolTranslator.setTargetVersion(BedrockProtocolVersion.bedrockLatest, true);

        final String address = host + ":" + port;
        final ServerInfo info = new ServerInfo(
                name.isBlank() ? "Bedrock server" : name,
                address,
                ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, client, ServerAddress.parse(address), info, false, null);
    }

    static boolean startMicrosoftLogin() {
        try {
            final BedrockSettings settings = BedrockSettings.global();
            if (settings == null) {
                return false;
            }

            // ViaFabricPlus keeps this entry point private and starts its own
            // device-code worker. Invoke it on the Minecraft client thread so
            // the screen/browser callbacks are scheduled safely.
            final Method login = BedrockSettings.class.getDeclaredMethod("openBedrockAccountLogin");
            login.setAccessible(true);
            login.invoke(settings);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            System.err.println("[FUNTIME PE] Microsoft login could not start: " + exception.getMessage());
            return false;
        }
    }

    static boolean hasMicrosoftAccount() {
        return BedrockSettings.global() != null
                && de.florianmichael.viafabricplus.ViaFabricPlus.global()
                .getSaveManager().getAccountsSave().getBedrockAccount() != null;
    }
}