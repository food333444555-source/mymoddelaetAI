# FUNTIME PE — независимый Fabric-мод

## Source version

```text
Minecraft Java 1.21.4
Bedrock 26.50
Bedrock protocol 2193
Mod version 0.4.1
```

Клиентский мод для Minecraft Java 1.21.4 с отдельным Bedrock/PE-разделом.
Мод не является ViaFabricPlus, ViaBedrock или ViaVersion и не требует их
установки.

## Целевой Bedrock

Основной профиль:

```text
Bedrock 26.50
Protocol 2193
```

Версия вынесена в отдельный enum, а старое значение `766` автоматически
заменяется на `2193` при загрузке конфигурации.

## Реализованный сетевой фундамент

- RakNet offline ping и полный connection handshake;
- правильный reliable ordered data frame;
- ACK и NAK;
- повторная отправка неподтверждённых кадров;
- фрагментация и сборка больших кадров;
- Bedrock NetworkSettings;
- little-endian protocol fields;
- zlib raw compression;
- Bedrock login packet;
- Microsoft/Xbox или гостевой профиль;
- отдельный PE-список серверов;
- ввод адреса, порта и protocol ID;
- отдельный экран аккаунта.
- отдельная play-сессия с Java-style экраном и декодированным Bedrock-миром;
- отправка Bedrock movement, chat, player-action и local-player-init пакетов;
- разделение Bedrock `MovePlayer` и `MoveActorAbsolute` с обработкой серверной
  коррекции позиции;
- перехват Java block-breaking hooks и перевод начала/завершения ломания в
  Bedrock `PlayerAction`;
- приём Bedrock `InventoryContent`, `InventorySlot` и `MobEquipment` в
  Java-facing инвентарь;
- отправка выбора хотбар-слота через Bedrock `MobEquipment`;
- базовый Bedrock `InventoryTransaction` для использования предмета по блоку;
- корректная последовательность ответов resource-pack negotiation:
  `HAVE_ALL_PACKS` после `ResourcePacksInfo` и `COMPLETED` после
  `ResourcePackStack`;
- загрузка объявленных resource-pack через `ResourcePackDataInfo`,
  `ResourcePackChunkRequest` и `ResourcePackChunkData` в память активной
  сессии;
- сохранение `ContainerOpen`, `ContainerClose` и inventory-содержимого
  контейнера в Java-facing состоянии;
- базовое окно контейнера с выбором двух слотов для обмена;
- ответ на `NetworkStackLatency`, обработка `Respawn` и `ChangeDimension`;
- очередь серверных `UpdateBlock` для синхронизации с Java chunk bridge;
- удержание кнопки при ломании блока с завершением по отпусканию;
- базовая физика fallback-игровой поверхности: гравитация, прыжок,
  горизонтальные столкновения и raycast блока;
- отображение загруженных Bedrock-чанков в Java-style игровой поверхности;
- version-scoped packet registry;
- Java-facing block mapper с резервным отображением неизвестных блоков в air.

Код не считает подключение готовым только после RakNet handshake: он ждёт
NetworkSettings, отправляет login и проверяет PlayStatus/StartGame. Если сервер
отказывает, причина передаётся в интерфейс.

## Авторизация

Гостевой режим работает только на сервере, который действительно разрешает
гостевой/offline вход. Microsoft/Xbox-вход обязателен на серверах с проверкой
Xbox Live. Никнейм не заменяет эту проверку.

## Java-style play-сессия

После Bedrock StartGame мод устанавливает vanilla Java runtime bridge:
`ClientPlayNetworkHandler`, `ClientWorld`, `ClientPlayerEntity` и реальные
`WorldChunk`/`ChunkSection`. Загруженные Bedrock-блоки маппятся в Java
`BlockState`, vanilla world renderer получает чанки напрямую, а Java HUD,
камера и игровой lifecycle остаются обычными для Minecraft Java.

Bedrock transport остаётся внутренней деталью и не показывается игроку после
входа. Чат и движение из vanilla Java-сессии переводятся обратно в Bedrock
пакеты. Старый Java-style screen оставлен только как fallback, если vanilla
world bootstrap не может быть создан на конкретной сборке.

Java 1.21.4 и Bedrock 26.50 используют разные реестры, поэтому entities,
inventory containers, resource packs и custom blocks требуют отдельных
packet translators и не должны подменяться неподходящими Java пакетами.
Скачанные resource-pack bytes сохраняются в активной сессии, но пока не
применяются к Java resource registry: для этого нужен отдельный адаптер
формата Bedrock pack и runtime-палитры.

Ломание блоков уже имеет отдельный Bedrock action path. Выбор хотбар-слота,
приём базовых inventory-пакетов, первый путь `InventoryTransaction` для
использования предмета по блоку и базовый обмен двух слотов контейнера
добавлены, но протокол компонентов предметов, crafting и полный item runtime
registry ещё требуют отдельных version-scoped модулей. Неизвестные item
runtime IDs сохраняются в модели как `bedrock:item_<id>` и не подменяются
случайным Java-предметом.

## Сборка у пользователя

Проверка сборки:

```bash
# Должно вывести 21 или новее
java -version
gradle compileJava
gradle build
```

Нужны Java 21+, Fabric Loader, Fabric API и Gradle для Minecraft 1.21.4.
Проект сам использует Java toolchain 21 и остановит сборку с понятным
сообщением, если Gradle запущен на старой Java.
В проекте нет Via-зависимостей. Для сборки требуется JDK 21; Gradle toolchain
проверяет эту версию автоматически. Сервер всё ещё видит Bedrock-клиента на
уровне wire protocol: «выглядит как Java» относится к клиентскому flow и UI,
а не к невозможной подмене Bedrock handshake на Java handshake.