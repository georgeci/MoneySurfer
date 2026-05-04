# Navigation AI Summary

<!-- DOCS:TOC -->
## Contents
- [Navigation AI Summary](#navigation-ai-summary)
- [TL;DR for agents](#tldr-for-agents)
- [Summary](#summary)
<!-- DOCS:END -->

## TL;DR for agents

- Read before screen or back stack work.
- Keep navigation decisions outside leaf UI when possible.
- Do not fork route patterns.
- Use full `docs/architecture/navigation.md` if route ownership is unclear.

READ WHEN:
- adding screen
- changing navigation
- editing back stack logic
- adding deep links

<!-- AI:SECTION id=navigation-summary task=navigation,screen,summary -->
## Summary

- UI emits events; navigation host performs navigation.
- New destinations should follow existing module and route patterns.
- Back behavior is user-visible and needs targeted validation.
<!-- AI:END -->
