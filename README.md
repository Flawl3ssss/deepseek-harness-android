# DeepSeek Harness — Android wrapper

Android-обёртка для [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)
(`dsh web` через WebView): proot Ubuntu + Node + DSH + Zen-адаптер sidecar. APK собирается
через GitHub Actions.

- DSH пин: **0.1.7-alpha.1** (alpha-канал). Обновление — только по кнопке в настройках
  (проверить → обновить в idle → откат), конфиги и плагины сохраняются (бэкап → staged → swap).
- Порты (только loopback): Zen-адаптер `:8787`, DSH web `:3080`.
- Ключ Zen — только из настроек приложения (шифрованное хранилище → env). Встроенных
  ключей в коде нет (`FALLBACK_KEY = ""`, проверяется secret-scan в CI).
- Проектная документация (ресерч → план → архитектура → план разработки): в Minis-папке
  `dsh-android/` (research/plan/arch), сюда копируются релизные срезы.

## Сборка

Workflows: `app.yml` (APK), `payload.yml` (payload-1: DSH + zen-adapter), `rootfs.yml`
(Ubuntu rootfs + Node-слой + proot). Релизная подпись — через Secrets (`KEYSTORE_B64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); без них — dev fresh-key.
Подробности подписи: `docs/keystore.md`.
