# OrbisCloud Premium Economy (Orbs & Votepoints)

This document defines the secure network-wide premium economy path for OrbisEconomy (`orbs`, `votepoints`) when used with EconomyBridge and OrbisCloud services.

## Architecture

1. **Master ledger (`orbis-bot`)**: authoritative balances and append-only transaction log.
2. **Signed bridge (`OrbisPaperAgent`)**: HMAC-signed transport for economy reads/writes.
3. **Execution edge (`OrbisEconomy`)**: caches premium balances at login and routes premium mutations to bot API.

## Implemented in this repository

- Premium cache model: `PremiumBalance` (`orbs`, `votepoints`, `available`).
- Per-player premium cache: `ConcurrentHashMap<UUID, PremiumBalance>` on plugin singleton.
- Async pre-login hydrate path:
  - `AsyncPlayerPreLoginEvent` fetches `/api/economy/{uuid}` through signed OrbisPaperAgent call.
  - On API failure, player enters **ghost state** (`available=false`, premium reads return `0`).
  - Cache is evicted on quit.
- EconomyBridge premium intercept:
  - Intercepts only `orbs`, `votepoints`.
  - Reads premium balances from cache.
  - Sends premium mutations to `POST /api/economy/transaction` with idempotency key (`UUID.randomUUID()`).
  - Strict fail if API/cache is unavailable.
  - Prevents server-thread blocking I/O by hard-failing premium mutation attempts on main thread.

## Operational fail-safes

- **No local premium authority**: OrbisEconomy never acts as source of truth for premium currencies.
- **Ghost state**: if premium API cannot be loaded at login, reads are clamped to `0`, premium spends are denied.
- **Idempotency key per mutation**: safe retry behavior for network failures.
- **Main-thread safety**: premium HTTP path is forbidden on server thread to preserve tick stability.

## Required external integration (remaining work)

### 1) `orbis-bot` ledger/API hardening

- Add `economy` and `economy_transactions` tables.
- Implement:
  - `GET /api/economy/{uuid}`
  - `POST /api/economy/transaction` (atomic updates + idempotency check).
- Enforce HMAC auth for all premium endpoints.
- Add explicit constraints and tests:
  - Non-negative balance invariant.
  - Allowed currency enum (`orbs`, `votepoints`).
  - Transaction id uniqueness.

### 2) `OrbisPaperAgent` bridge contract

- Ensure `makeSignedApiCall(String method, String path, JsonObject payload)` is publicly exposed and stable.
- Keep signing and clock-skew behavior deterministic across all game nodes.
- Include structured error responses for upstream callers.

### 3) Shop/plugin execution model alignment

OrbisEconomy now correctly rejects main-thread premium mutations. Ensure EconomyBridge consumers execute premium spends in async-safe flows (or provide callback-based pre-check/debit semantics) so purchases do not call premium writes on the primary thread.

### 4) Observability and recovery playbook

- Log correlation IDs (transaction_id) across Paper Agent + bot.
- Add admin command/playbook for transaction audit/refund using `economy_transactions`.
- Add alerts for API 401/5xx rates, timeout spikes, and repeated ghost-state logins.

## Recommended test plan

1. Login with API healthy -> cache available with correct values.
2. Login with API offline -> `available=false`, premium reads `0`.
3. Premium debit with insufficient funds -> bot rejects, purchase denied.
4. Retry same transaction ID -> idempotent accept without double-debit.
5. Main-thread premium mutation attempt -> immediate fail by OrbisEconomy.
