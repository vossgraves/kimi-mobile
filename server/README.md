# Proxy — Kimi web API

This is [xiaoY233/Kimi-Free-API](https://github.com/xiaoY233/Kimi-Free-API) (the maintained, audited fork of the poisoned/dead `LLM-Red-Team/kimi-free-api` — do not use the original), **patched to talk to the global `www.kimi.com` pool** instead of the China `kimi.moonshot.cn` pool.

**The patch is already applied in `src/api/controllers/chat.ts`** (12 occurrences). If you ever re-clone, re-apply:

```bash
sed -i 's|https://kimi\.moonshot\.cn|https://www.kimi.com|g' src/api/controllers/chat.ts
```

## Run

```bash
npm install
npm run build   # or: npx tsup src/index.ts --format cjs --sourcemap --clean --publicDir public
npm run start   # listens on :8000
```

Optional env: `PORT`, `AUTH_TOKEN` (a refresh token, or comma-separated list for rotation). If `AUTH_TOKEN` is left unset, the server still works per-request with the app's `Authorization: Bearer <refresh_token>` header.

## Verify

```bash
curl http://127.0.0.1:8000/ping                      # pong
curl http://127.0.0.1:8000/v1/models -H "Authorization: Bearer <refresh_token>"
```

## Security note

The refresh token is a credential for your Kimi account. The proxy only uses it to mint access tokens for chat; still, run it on a machine you trust and don't commit tokens.
