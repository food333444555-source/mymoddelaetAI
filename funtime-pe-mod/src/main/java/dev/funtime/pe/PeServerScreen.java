package dev.funtime.pe;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class PeServerScreen extends Screen {
    private final Screen parent;
    private final PeServerEntry editing;
    private TextFieldWidget name;
    private TextFieldWidget host;
    private TextFieldWidget port;
    private TextFieldWidget protocol;
    private PeAuthMode authMode;
    private Text status = Text.literal("");

    public PeServerScreen(Screen parent, PeServerEntry editing) {
        super(Text.literal(editing == null ? "Добавить PE-сервер" : "Изменить PE-сервер"));
        this.parent = parent;
        this.editing = editing;
        this.authMode = editing == null ? PeAuthMode.MICROSOFT : editing.authMode();
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 120;
        final int fieldWidth = 240;
        this.name = field(left, 48, "Название", editing == null ? "FUNTIME" : editing.name(), 64);
        this.host = field(left, 86, "Адрес", editing == null ? "mc.funtime.su" : editing.host(), 255);
        this.port = field(left, 124, "Порт", Integer.toString(editing == null ? 19132 : editing.port()), 5);
        this.protocol = field(left, 162, "Протокол Bedrock", Integer.toString(
                editing == null ? BedrockVersion.DEFAULT.protocolVersion() : editing.protocolVersion()), 6);

        this.addDrawableChild(name);
        this.addDrawableChild(host);
        this.addDrawableChild(port);
        this.addDrawableChild(protocol);
        this.addDrawableChild(ButtonWidget.builder(authText(), ignored -> {
                    authMode = authMode == PeAuthMode.GUEST ? PeAuthMode.MICROSOFT : PeAuthMode.GUEST;
                    this.clearAndInit();
                })
                .dimensions(left, 200, fieldWidth, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), ignored -> save())
                .dimensions(left, 232, 115, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), ignored -> close())
                .dimensions(left + 125, 232, 115, 20)
                .build());
        if (editing != null) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Удалить"),
                            ignored -> delete())
                    .dimensions(left, 260, fieldWidth, 20)
                    .build());
        }
    }

    private TextFieldWidget field(int x, int y, String hint, String value, int maxLength) {
        final TextFieldWidget widget = new TextFieldWidget(this.textRenderer, x, y, 240, 20, Text.literal(hint));
        widget.setText(value);
        widget.setMaxLength(maxLength);
        return widget;
    }

    private Text authText() {
        return Text.literal("Вход: " + authMode.displayName()
                + (authMode == PeAuthMode.GUEST ? " (сервер должен разрешать)" : ""));
    }

    private void save() {
        final int parsedPort;
        final int parsedProtocol;
        try {
            parsedPort = Integer.parseInt(port.getText().trim());
            parsedProtocol = Integer.parseInt(protocol.getText().trim());
        } catch (NumberFormatException exception) {
            status = Text.literal("Порт и протокол должны быть числами.");
            return;
        }

        final String serverName = name.getText().trim();
        final String serverHost = host.getText().trim();
        if (serverName.isEmpty() || serverHost.isEmpty() || serverHost.contains(" ")
                || parsedPort < 1 || parsedPort > 65535 || parsedProtocol < 1) {
            status = Text.literal("Проверьте название, адрес, порт и протокол.");
            return;
        }

        if (editing == null) {
            PeConfig.addServer(new PeServerEntry(
                    java.util.UUID.randomUUID().toString(), serverName, serverHost,
                    parsedPort, authMode, parsedProtocol));
        } else {
            editing.update(serverName, serverHost, parsedPort, authMode, parsedProtocol);
            PeConfig.save();
        }
        close();
    }

    private void delete() {
        PeConfig.removeServer(editing);
        close();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Название"), this.width / 2 - 120, 38, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Адрес"), this.width / 2 - 120, 76, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Порт"), this.width / 2 - 120, 114, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Протокол"), this.width / 2 - 120, 152, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height - 16, 0xFFCC66);
        super.render(context, mouseX, mouseY, delta);
    }
}