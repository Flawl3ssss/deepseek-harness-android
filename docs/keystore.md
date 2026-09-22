# Подпись релизов (TASK-002)

## Dev-сборки

По умолчанию `app.yml` подписывает APK свежим ключом (fresh-key), сгенерированным
внутри job. Такие APK ставятся рядом/поверх друг друга только если совпадает ключ —
для повседневных тестовых сборок достаточно. `apksigner verify` в job подтверждает подпись.

## Релизы (keystore через Secrets)

1. Сгенерировать keystore локально (один раз, на своей машине — НЕ в CI, НЕ в репо):
   ```sh
   keytool -genkeypair -keystore dsh-release.keystore -alias dsh \
     -keyalg RSA -keysize 2048 -validity 9125 -storetype JKS
   ```
2. Положить в GitHub → Settings → Secrets → Actions (репозиторий `deepseek-harness-android`):
   - `KEYSTORE_B64` — `base64 -w0 dsh-release.keystore` (целиком, одной строкой)
   - `KEYSTORE_PASSWORD` — пароль keystore
   - `KEY_ALIAS` — `dsh`
   - `KEY_PASSWORD` — пароль ключа
3. Следующий запуск `app.yml` подпишет релизным ключом автоматически (ветка `if [ -n "$KEYSTORE_B64" ]`).
4. Бэкап keystore хранить ОФЛАЙН (потеря = невозможность обновлений поверх, только переустановка).

## Проверка

`apksigner verify --print-certs dsh-android.apk` — отпечаток должен совпадать с релизным
после первого релиза на keystore (и меняться не должен никогда).
