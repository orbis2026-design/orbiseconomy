# SCHEDULING.md

## Scheduler context map

- **Global context**: repeating balance-top refresh task is scheduled with `getServer().getGlobalRegionScheduler().runAtFixedRate(...)`.
- **Async context**: balance-top aggregation work is executed through `CompletableFuture.supplyAsync(...)`.
- **Hop points**: after async aggregation completes, the in-memory `balanceTop` snapshot and running-flag are updated in the completion callback.

## Guardrail

- The Gradle `legacySchedulerGuard` task fails the build if legacy scheduler APIs are detected in Java source (`Bukkit.getScheduler()` and `.runTask*` calls).
