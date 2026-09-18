package dev.funtime.pe;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class PeAccountScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget guestName;
    private Text status = Text.literal("");

    public PeAccountScreen(Screen parent) {
        super(Text.literal("Аккаунт PE"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 120;
        this.guestName = new TextFieldWidget(this.textRenderer, left, 68, 240, 20,
                Text.literal("Ник гостя"));
        this.guestName.setText(PeConfig.account().guestName());
        this.guestName.setMaxLength(16);
        this.addDrawableChild(guestName);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить гостя"), ignored -> saveGuest())
                .dimensions(left, 100, 240, 20)
                .build());
        final ButtonWidget microsoftButton = ButtonWidget.builder(Text.literal(
                        PeConfig.account().isMicrosoftConnected()
                                ? "Microsoft: " + PeConfig.account().microsoftName()
                                : "Войти через Microsoft"),
                        ignored -> startMicrosoftLogin())
                .dimensions(left, 132, 240, 20)
                .build();
        this.addDrawableChild(microsoftButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Выйти из Microsoft"),
                        ignored -> {
                            PeConfig.account().clearMicrosoftSession();
                            PeConfig.save();
                            status = Text.literal("Microsoft-профиль отключён.");
                            this.clearAndInit();
                        })
                .dimensions(left, 164, 240, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), ignored -> close())
                .dimensions(left, 220, 240, 20)
                .build());
    }

    private void saveGuest() {
        final String name = guestName.getText().trim();
        if (name.isEmpty() || name.length() > 16 || !name.matches("[A-Za-z0-9_А-Яа-яёЁ-]+")) {
            status = Text.literal("Ник: от 1 до 16 букв, цифр, '_' или '-'.");
            return;
        }
        PeConfig.account().setGuestName(name);
        PeConfig.save();
        status = Text.literal("Гостевой ник сохранён.");
    }

    private void startMicrosoftLogin() {
        status = Text.literal("Запрашиваю код Microsoft...");
        MinecraftClient client = MinecraftClient.getInstance();
        MicrosoftDeviceLogin.start(
                code -> client.execute(() -> status = Text.literal(
                        "Откройте " + code.verificationUri() + " и введите код " + code.userCode())),
                session -> client.execute(() -> {
                    PeConfig.account().setMicrosoftSession(session);
                    PeConfig.save();
                    status = Text.literal("Вход выполнен: " + session.gamertag());
                    this.clearAndInit();
                }),
                error -> client.execute(() -> status = Text.literal(
                        "Ошибка Microsoft: " + safeMessage(error))));
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "неизвестная ошибка" : message;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Microsoft нужен для серверов с Xbox Live-проверкой."),
                this.width / 2, 40, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Гостевой ник"), this.width / 2 - 120, 56, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height - 24, 0xFFCC66);
        super.render(context, mouseX, mouseY, delta);
    }
}