# OrbisEconomy

## PlaceholderAPI placeholders

> [!IMPORTANT]
> Vault placeholder compatibility is **default-currency only**. Any Vault/Economy placeholders
> exposed through other plugins read from the configured `coins` currency.
>
> For multi-currency usage, use OrbisEconomy's explicit currency-aware placeholders:
>
> - `%orbiseconomy_balance_<currencyId>%`
> - `%orbiseconomy_balance_formatted_<currencyId>%`
> - `%orbiseconomy_top_<currencyId>_<position>_name%`
> - `%orbiseconomy_top_<currencyId>_<position>_uuid%`
> - `%orbiseconomy_top_<currencyId>_<position>_balance%`
> - `%orbiseconomy_top_<currencyId>_<position>_balance_formatted%`

Top placeholders support both currency-aware and legacy richest formats:

- `%orbiseconomy_top_<currencyId>_<position>_name%`
- `%orbiseconomy_top_<currencyId>_<position>_uuid%`
- `%orbiseconomy_top_<currencyId>_<position>_balance%`
- `%orbiseconomy_top_<currencyId>_<position>_balance_formatted%`
- `%orbiseconomy_richest_<position>_<type>%` (uses `coins` as the default currency)

Supported `<type>` values:

- `name`
- `uuid`
- `balance`
- `balance_formatted` (alias: `formatted`)
- `entry`
- `entry_legacy`

`entry` and `entry_legacy` return a combined `player name + formatted amount` string. The
legacy variant uses section-sign (`§`) formatting for legacy text consumers.

When the requested position does not exist, each type uses its matching fallback from
`settings.placeholders` in `config.yml`.


## Placeholder fallback behavior

Fallbacks are intentionally split by category:

- **Player balance placeholders** (`%orbiseconomy_balance_<currencyId>%`, `%orbiseconomy_balance_formatted_<currencyId>%`) return `0` when the player/account/currency is unavailable.
- **Top placeholders** (`%orbiseconomy_top_<currencyId>_<position>_<type>%`) return `N/A` for malformed placeholder input (invalid currency, invalid position, unsupported type).
- **Top placeholders for missing rank entries** use per-type values from `settings.placeholders` in `config.yml` (default `N/A`).

Compatibility aliases are still supported after exact parsing:

- `%orbiseconomy_richest_<position>_<type>%` maps to `coins` currency.
- `%orbiseconomy_top_<currencyId>_<position>_formatted%` maps to `_balance_formatted%`.
