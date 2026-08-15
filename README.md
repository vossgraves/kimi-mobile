# Kimi K3 — Free Client

A polished Android chat client for **Kimi K3** that rides the free web API (no subscription, no official-app capacity limits) through a lightweight proxy you run yourself.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple) ![AGP](https://img.shields.io/badge/AGP-8.9.2-green) ![Compose](https://img.shields.io/badge/Compose-Material3-3DDC84)

## What you get

- **Material 3 expressive UI** — dynamic color, rounded shapes, light/dark, spring-bouncy typing indicator
- **WebView login** — sign in to kimi.com in-app; the refresh token is extracted automatically from the page (no copy-pasting tokens)
- **Markdown rendering** — code blocks with a copy button, bold/italic/inline code, headings, lists, quotes
- **OpenAI-compatible streaming** — token-by-token responses via SSE
- **Settings** — base URL, model, token, test-connection button, sign out, clear conversation
- **Screenshot tests** — rendered in CI (Robolectric) so the UI is verifiable without a device

## Architecture

```
app/          Android client (Kotlin, Compose, Material 3)
server/       Kimi web-API proxy (kimi-free-api, patched for kimi.com)
```

## Quick start

1. **Run the proxy** (any machine with Node 18+):

   ```bash
   cd server
   npm install
   npm run build   # or: npx tsup src/index.ts --format cjs --sourcemap --clean --publicDir public
   npm run start
   # AUTH_TOKEN is optional; leave unset, the app's token is used per-request
   ```

   The proxy is patched to talk to `www.kimi.com` (global pool) instead of the China `kimi.moonshot.cn` pool — the original repo's default fails with tokens issued on kimi.com.

2. **Install the app** — grab the `apk-debug` artifact from the latest CI run.

3. **Sign in** — Settings → *Sign in with browser* → log in to Kimi → you're back in chat, connected.

   No browser? Paste your refresh token directly: `kimi.com` → F12 → Application → Local Storage → `refresh_token` (the long `eyJ...` JWT, not the short session one).

4. **Point the app at your proxy** — default base URL is `http://10.0.2.2:8000/v1` (emulator → host). On a real phone use your machine's LAN IP: `http://192.168.x.x:8000/v1`.

## Limits (honest answer)

- The proxy itself has **no artificial limits**.
- Upstream, Kimi free accounts allow roughly **30 long-context rounds per 3 hours**; short chats flow freely, and kimi.com's free tier caps flagship (K3) usage per day.
- Scale by adding more free accounts: pass their tokens comma-separated in the proxy config (`AUTH_TOKEN`) — the proxy rotates them automatically.

## Development

```bash
./gradlew assembleDebug                  # build
./gradlew testDebugUnitTest              # render UI screenshots to app/build/screenshots
```

New UI screens land in the `screenshots` CI artifact — check them before trusting a change.
