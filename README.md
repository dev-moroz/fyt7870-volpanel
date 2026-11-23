# VolPanel — volume buttons overlay for FYT 7870

Кнопки громкости «+ / −» поверх всех приложений для магнитол FYT UIS7870 (Android 13). Без root, без ПК.

Штатные плавающие кнопки громкости прошивки не работают в сторонних лаунчерах и приложениях. VolPanel рисует свои.

## Возможности
- Две квадратные кнопки поверх всех приложений.
- Громкость через штатный сервис `com.syu.ms` — тот же уровень, что руль/ручка (а не `STREAM_MUSIC` Android).
- Кнопки перетаскиваются пальцем, положение запоминается. Тап — шаг, удержание — автоповтор.
- Автозапуск при включении (`LOCKED_BOOT_COMPLETED`, ~15 с после старта).

## Установка
1. Скачать `volpanel.apk` из [Releases](../../releases).
2. Установить, открыть один раз, разрешить «Поверх других окон».

## Как это работает
```
bindService(Intent("com.syu.ms.toolkit").setPackage("com.syu.ms"))
  → IRemoteToolkit.getRemoteModule(4)
  → IRemoteModule.cmd(0, int[]{arg}, null, null)
```
`arg`: `-1` = vol+, `-2` = vol−, `0..36` = абсолютное значение. Проверки подписи вызывающего нет.

## Сборка
Без Gradle, нужны Android SDK (build-tools 36, platform 35) и JDK:
```
KS=my.jks KS_PASS=... ./build.sh
```

## Проверено
FYT UIS7870 6/128, Android 13 (сборка 2026-05-08), темы launcher_v2 / launcher24. На других платах не проверялось. Без гарантий.

## Ограничения
Размер кнопок (180 px) задан в коде (`PanelService.java`).

Лицензия: MIT.
