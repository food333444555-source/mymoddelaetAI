package dev.funtime.pe;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class PeServerScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget serverName;
    private TextFieldWidget host;
    private TextFieldWidget port;
    private Text status;

    public PeServerScreen(Screen parent) {
        super(Text.literal("PE / Bedrock — сервер"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 120;
        final int fieldWidth = 240;

        this.serverName = new TextFieldWidget(this.textRenderer, left, 52, fieldWidth, 20, Text.literal("Название сервера"));
        this.serverName.setText(PeConfig.serverName);
        this.serverName.setMaxLength(64);

        this.host = new TextFieldWidget(this.textRenderer, left, 92, fieldWidth, 20, Text.literal("Адрес"));
        this.host.setText(PeConfig.host);
        this.host.setMaxLength(255);

        this.port = new TextFieldWidget(this.textRenderer, left, 132, fieldWidth, 20, Text.literal("Порт"));
        this.port.setText(Integer.toString(PeConfig.port));
        this.port.setMaxLength(5);

        this.addDrawableChild(this.serverName);
        this.addDrawableChild(this.host);
        this.addDrawableChild(this.port);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Настройки FUNTIME"), ignored -> loadFuntimePreset())
                .dimensions(left, 172, 115, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal(PeConnection.hasMicrosoftAccount()
                                ? "Microsoft: подключён"
                                : "Войти через Microsoft"),
                        ignored -> startMicrosoftLogin())
                .dimensions(left + 125, 172, 115, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Подключиться"), ignored -> connect())
                .dimensions(left, 202, fieldWidth, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), ignored -> close())
                .dimensions(left, 232, fieldWidth, 20)
                .build());

        this.status = Text.literal("Протокол Bedrock: " + PeConnection.targetProtocolLabel());
    }

    private void loadFuntimePreset() {
        this.serverName.setText("FUNTIME");
        this.host.setText("mc.funtime.su");
        this.port.setText("19132");
        this.status = Text.literal("Загружено: mc.funtime.su:19132");
    }

    private void startMicrosoftLogin() {
        if (PeConnection.startMicrosoftLogin()) {
            this.status = Text.literal("Открыт официальный вход Microsoft по коду устройства.");
        } else {
            this.status = Text.literal("Не удалось открыть вход Microsoft. Проверьте ViaFabricPlus.");
        }
    }

    private void connect() {
        final int parsedPort;
        try {
            parsedPort = Integer.parseInt(this.port.getText().trim());
        } catch (NumberFormatException ignored) {
            this.status = Text.literal("Порт должен быть числом от 1 до 65535.");
            return;
        }

        final String address = this.host.getText().trim();
        if (parsedPort < 1 || parsedPort > 65535 || address.isEmpty() || address.contains(" ")) {
            this.status = Text.literal("Введите корректные адрес и порт Bedrock.");
            return;
        }

        PeConfig.serverName = this.serverName.getText().trim();
        PeConfig.host = address;
        PeConfig.port = parsedPort;
        PeConfig.save();

        PeConnection.connect(MinecraftClient.getInstance(), this, PeConfig.serverName,
                PeConfig.host, PeConfig.port);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Сервер"), this.width / 2 - 120, 40, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Адрес"), this.width / 2 - 120, 80, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Порт"), this.width / 2 - 120, 120, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, 270, 0xB8D8FF);
        super.render(context, mouseX, mouseY, delta);
    }
}