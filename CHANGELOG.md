# CHANGELOG.md

## Unreleased

- Migrated Orbs command logic to canonical generic currency storage (`balances` map) via `getBalance("orbs")` / `setBalance("orbs", amount)`.
- Added fail-fast startup validation for normalized currency ID collisions in `settings.currencies`.
- Added Gradle `legacySchedulerGuard` verification task and wired it into `check`.
- Replaced legacy Bukkit scheduler usage in balance-top scheduling with Paper/Folia global region scheduling API.
