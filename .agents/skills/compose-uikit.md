# Skill - Compose UI + UIKit

Use for screens, feature UI, reusable components, resources, and UI state.

## Required Reading

- [../../uikit/README.md](../../uikit/README.md)

## Rules

- Use Material 3 via project theme wrappers.
- Use `AppTheme.materialColors`, `AppTheme.typography`, `AppTheme.shapes`, and
  `AppTheme.spacing`.
- Do not hard-code colors in screens/components.
- Do not access `MaterialTheme.colorScheme.*` directly from screens/components.
- Public components should wrap internal atoms and own click/ripple behavior.
- String placeholders in Compose resources must be indexed (`%1$s`, `%1$d`).

## Validation

- Compile affected module metadata first after resource changes.
- Run the smallest UI/module compile task that covers the change.
