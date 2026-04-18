# Design Tokens

## Status Chip Colors

| Status | Background token | On-background token |
|---|---|---|
| `TO_BE_DONE` | `surfaceVariant` | `onSurfaceVariant` |
| `EXECUTING` | `primaryContainer` | `onPrimaryContainer` |
| `PAUSED` | `secondaryContainer` | `onSecondaryContainer` |
| `COMPLETED` | `tertiaryContainer` | `onTertiaryContainer` |
| `DISCARDED` | `errorContainer` | `onErrorContainer` |

All tokens map to Material 3 color-scheme roles and automatically adapt to light/dark themes.  
Do **not** hardcode hex values — always reference `MaterialTheme.colorScheme.<token>`.

## Typography

| Usage | Token |
|---|---|
| Task title | `bodyLarge` |
| Task subtitle / metadata | `bodySmall` |
| Section header | `labelMedium` |
| CTA button | `labelLarge` |

## Spacing

Use multiples of `4.dp` for all padding and spacing:  
`4 · 8 · 12 · 16 · 24 · 32 · 48`

Standard screen padding: `16.dp` horizontal, `12.dp` vertical.

## Shape

| Component | Shape |
|---|---|
| Status chip | `RoundedCornerShape(50%)` (pill) |
| Card | `MaterialTheme.shapes.medium` |
| Bottom sheet | `MaterialTheme.shapes.large` (top corners only) |
| FAB | `MaterialTheme.shapes.large` |
