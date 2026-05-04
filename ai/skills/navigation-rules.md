# Skill - Navigation Rules

<!-- DOCS:TOC -->
## Contents
- [Skill - Navigation Rules](#skill---navigation-rules)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Keep global flow policy outside leaf UI.
- Follow existing route patterns.
- Validate changed back stack behavior.
- Read this before navigation work.

READ WHEN:
- adding screen
- adding route
- changing back stack logic
- adding deep links

<!-- AI:SECTION id=navigation-skill task=navigation,screen,backstack -->
## Rules

- Screens emit events; navigation host performs navigation.
- Do not duplicate route definitions.
- Keep route arguments consistent with existing patterns.
- Treat back behavior as user-visible behavior that needs targeted validation.
<!-- AI:END -->
