# FUNTIME PE Bridge

Клиентский Fabric-мод для Minecraft Java 1.21.4. Он добавляет в меню
мультиплеера отдельную кнопку **PE / Bedrock**, экран настройки Bedrock-сервера
и готовый пресет FUNTIME (`mc.funtime.su:19132`).

## Возможности

- подключение к Bedrock-серверу через ViaFabricPlus/ViaBedrock;
- готовая кнопка и пресет FUNTIME;
- ручное изменение адреса, порта и названия сервера;
- обязательный официальный Microsoft device-code login перед подключением;
- сохранение последних настроек в `config/funtimepe.properties`;
- русские подписи и сообщения в экране подключения.

## Сборка

Нужны Java 21 и Gradle:

```bash
gradle build
```

Готовый мод появится в `build/libs/`. Для запуска нужны Minecraft Java 1.21.4,
Fabric Loader, Fabric API и ViaFabricPlus 3.6.1. Локальные библиотеки Via уже
лежат в `libs/` и используются Gradle при сборке. Никнейм не является заменой
Microsoft-аутентификации: без сохранённого Bedrock-аккаунта подключение
останавливается до открытия экрана входа.