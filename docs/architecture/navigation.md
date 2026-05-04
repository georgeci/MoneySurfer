# Navigation

<!-- DOCS:TOC -->
## Contents
- [Navigation](#navigation)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Navigation glue belongs in `navigation` and app/shared orchestration layers.
- Screens should not own global back stack policy.
- Do not duplicate route definitions across feature modules.
- Read this before adding screens, destinations, deep links, or back behavior.

READ WHEN:
- adding screen
- changing navigation
- editing back stack logic
- adding deep links

<!-- AI:SECTION id=navigation-rules task=navigation,screen,backstack -->
## Rules

- Keep destination contracts stable and typed where the current code does so.
- Feature screens expose UI events; navigation handling stays in the host layer.
- Back stack behavior must be tested when it changes user flow.
- Prefer existing route and argument patterns over new navigation abstractions.
<!-- AI:END -->
