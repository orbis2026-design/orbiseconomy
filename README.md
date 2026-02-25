# OrbisEconomy

## PlaceholderAPI placeholders

Top placeholders support both currency-aware and legacy richest formats:

- `%orbiseconomy_top_<currencyId>_<position>_<type>%`
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
