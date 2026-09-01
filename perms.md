# Права CodeEssentials

Полный перечень нод прав мода. Источник истины при расхождении: класс
`internal/command/Nodes` в коде. Сюда же вносятся новые ноды тем же коммитом, что и код.

Ноды прав и ключи перевода не пересекаются: подсказки лежат под `codeessentials.command.usage.*`,
сообщения под `codeessentials.message.*`, отказы под `codeessentials.error.*`, причины снятия под
`codeessentials.cancel.*` и `codeessentials.failed.*`. Всё остальное под `codeessentials.` это ноды
из таблиц ниже и мета-ключи из последнего раздела.

Ноды спрашиваются через `PermissionService` ядра. Пока в реестре стоит встроенная json-реализация
CodeCore, ноды выдаются её файлом; после установки CodePerms теми же именами, но с группами,
весами и контекстами. Нет ни одного мода с `PermissionService`: все проверки отвечают «нет», в лог
уходит строка `No mod holds PermissionService`.

## Дома

| Нода | Команда | Что даёт |
|---|---|---|
| `codeessentials.home` | `/home [имя]`, `/homes` | перенос к своему дому и список своих домов |
| `codeessentials.home.set` | `/sethome [имя]` | поставить или переставить дом |
| `codeessentials.home.delete` | `/delhome <имя>` | убрать свой дом |

Сколько домов разрешено, решает мета `codeessentials.maxhomes`, а не нода.

## Варпы

| Нода | Команда | Что даёт |
|---|---|---|
| `codeessentials.warp` | `/warp <имя>`, `/warps` | пользоваться варпами и видеть список |
| `codeessentials.warp.go.<имя>` | `/warp <имя>` | доступ к конкретному варпу |
| `codeessentials.warp.set` | `/setwarp <имя>` | создать варп или переставить его на свою точку |
| `codeessentials.warp.delete` | `/delwarp <имя>` | убрать варп |

Нода на каждый варп обязательная и выводится из имени: новый варп по умолчанию закрыт всем.
`/warps` показывает только те варпы, чья нода у игрока есть, а `/warp` на закрытый варп отвечает
`codeessentials.error.warp_denied`. Открыть все сразу: `codeessentials.warp.go.*`. В имя варпа
допускаются только `[a-z0-9_-]`, поэтому точки и звёздочки в ноду попасть не могут.

Флаг `/setwarp <имя> --force` пропускает проверку безопасной точки и требует отдельно
`codeessentials.admin.teleport`.

## Спавн

| Нода | Команда | Что даёт |
|---|---|---|
| `codeessentials.spawn` | `/spawn` | перенос на спавн текущего измерения или общий |
| `codeessentials.spawn.set` | `/setspawn [--dim]` | записать точку спавна |

## Возврат

| Нода | Команда | Что даёт |
|---|---|---|
| `codeessentials.back` | `/back` | возврат по стеку |
| `codeessentials.back.death` | `/back` | возврат к месту гибели |
| `codeessentials.back.crossworld` | `/back` | возврат в другое измерение |

Верхняя запись стека помечена смертью или переносом. Без `codeessentials.back.death` возврат к
месту гибели отклоняется, запись при этом остаётся в стеке. То же с чужим измерением и
`codeessentials.back.crossworld`. Глубину стека задаёт мета `codeessentials.backdepth`.

## Просьбы о переносе

| Нода | Команда | Что даёт |
|---|---|---|
| `codeessentials.tpa` | `/tpa <ник>`, `/tpaccept [ник]`, `/tpdeny [ник]`, `/tpacancel` | просить перенос к игроку и отвечать на чужие просьбы |
| `codeessentials.tpa.here` | `/tpahere <ник>` | звать игрока к себе |
| `codeessentials.tpa.toggle` | `/tpatoggle` | закрыть и открыть приём просьб |

## Администрирование

| Нода | Команда | Что даёт |
|---|---|---|
| `codeessentials.admin.teleport` | `/tp`, `/tppos`, `/ecancel <ник>`, `--force` у `/setwarp` | двигать чужих игроков, снимать чужой тёплый телепорт, обходить проверку точки |
| `codeessentials.admin.reload` | `/essentials reload` | перечитать файлы настроек |
| `codeessentials.admin.cooldown` | `/essentials cooldown <ник> [clear]` | смотреть и снимать кулдауны игрока |
| `codeessentials.admin.jobs` | `/essentials jobs [ник]` | смотреть идущие переносы |

`/tp` и `/tppos` работают от консоли и командного блока, автор пишется в журнал как `console`,
`rcon` или `commandblock@x,y,z` при включённом `audit.logChanges`. `/ecancel` без аргумента снимает
свой тёплый телепорт и ноды не требует; `/essentials` без действия перечисляет только те ветки, чьи
ноды у отправителя есть.

## Обход

| Нода | Что даёт |
|---|---|
| `codeessentials.bypass.cooldown` | снимает оба класса кулдаунов: и кулдаун причины переноса, и срок между tpa-просьбами |

## Мета вместо чисел в конфиге

Три значения берутся не нодой, а метой того же `PermissionService`. Меты нет или значение кривое:
работает число из `config/codeessentials/config.json` и в лог уходит строка.

| Ключ | Что задаёт | Запасное значение | Потолок |
|---|---|---|---|
| `codeessentials.maxhomes` | сколько домов разрешено игроку | `homes.defaultMax` (3) | `limits.homesPerPlayer` (128) |
| `codeessentials.warmup` | сколько секунд длится тёплая задержка | `teleport.warmupSeconds` (3) | `limits.warmupSeconds` (300) |
| `codeessentials.backdepth` | глубина стека `/back` | 1 | `limits.backDepth` (10) |

Лимит домов неретроактивный: он читается в момент `/sethome` и запрещает только новое имя. Падение
лимита оставляет уже поставленные дома на месте, а перезапись существующего дома лимит не тратит.

## Вес в реестре сервисов ядра

`config/codeessentials/config.json`, поле `servicePriority`: `BUILTIN`, `ADDON` или `OVERRIDE`,
по умолчанию `ADDON`. Мод с большим весом забирает `TeleportService`, `HomeService`, `WarpService`,
`SpawnService` и `BackService` у мода с меньшим. Непонятное значение заменяется на `ADDON` с записью
в лог. Вес читается один раз при загрузке мода: `/essentials reload` его не меняет, нужен
перезапуск сервера.
