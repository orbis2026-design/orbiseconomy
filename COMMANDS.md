# COMMANDS.md

## Currency command storage behavior

- Currency command handlers must read/write balances through `PlayerAccount#getBalance(currencyId)` and `PlayerAccount#setBalance(currencyId, amount)`.
- Currency-specific shims (for example `getOrbsBalance` / `setOrbsBalance`) are retained only for legacy compatibility and must not be used in command/service implementations.
