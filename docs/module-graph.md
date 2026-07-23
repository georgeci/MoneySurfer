# Module Dependency Graph

<!-- DOCS:TOC -->
## Contents
- [Module Dependency Graph](#module-dependency-graph)
<!-- DOCS:END -->

```mermaid
%%{
  init: {
    'theme': 'neutral'
  }
}%%

graph LR
  subgraph :feature
    :feature:account["account"]
    :feature:settings["settings"]
    :feature:workspace["workspace"]
    :feature:budget["budget"]
    :feature:category["category"]
    :feature:dashboard["dashboard"]
    :feature:login["login"]
    :feature:transaction["transaction"]
  end
  subgraph :sync
    :sync:api["api"]
    :sync:default["default"]
    :sync:no-op["no-op"]
  end
  :feature:account --> :domain
  :feature:account --> :navigation
  :feature:account --> :sync:api
  :feature:account --> :uikit
  :feature:account --> :utils
  :feature:account --> :domain-test-fixtures
  :feature:account --> :sync-test-fixtures
  :sync-surfer --> :domain
  :sync-surfer --> :sync:api
  :sync-surfer --> :sync:default
  :sync-surfer --> :data-local
  :sync-surfer --> :data-remote
  :sync-surfer --> :data-test-fixtures
  :sync-surfer --> :domain-test-fixtures
  :data-local --> :domain
  :data-local --> :sync:api
  :data-local --> :domain-test-fixtures
  :feature:settings --> :domain
  :feature:settings --> :navigation
  :feature:settings --> :sync:api
  :feature:settings --> :uikit
  :feature:settings --> :utils
  :feature:settings --> :domain-test-fixtures
  :feature:settings --> :sync-test-fixtures
  :feature:workspace --> :domain
  :feature:workspace --> :navigation
  :feature:workspace --> :sync:api
  :feature:workspace --> :uikit
  :feature:workspace --> :utils
  :feature:workspace --> :domain-test-fixtures
  :feature:workspace --> :sync-test-fixtures
  :androidApp-offline --> :composeAppOffline
  :androidApp-offline --> :shared
  :shared --> :domain
  :shared --> :uikit
  :shared --> :utils
  :shared --> :data-local
  :shared --> :navigation
  :shared --> :feature:account
  :shared --> :feature:budget
  :shared --> :feature:category
  :shared --> :feature:dashboard
  :shared --> :feature:login
  :shared --> :feature:settings
  :shared --> :feature:transaction
  :shared --> :feature:workspace
  :shared --> :domain-test-fixtures
  :shared --> :sync-test-fixtures
  :integration-test --> :domain
  :integration-test --> :sync:api
  :integration-test --> :sync:default
  :integration-test --> :data-local
  :integration-test --> :data-remote
  :integration-test --> :sync-surfer
  :integration-test --> :domain-test-fixtures
  :integration-test --> :sync-test-fixtures
  :integration-test --> :data-test-fixtures
  :androidApp --> :composeApp
  :androidApp --> :shared
  :composeAppOffline --> :shared
  :composeAppOffline --> :domain
  :composeAppOffline --> :feature:login
  :composeAppOffline --> :feature:transaction
  :composeAppOffline --> :sync:api
  :composeAppOffline --> :sync:no-op
  :composeAppOffline --> :data-local
  :feature:budget --> :domain
  :feature:budget --> :navigation
  :feature:budget --> :sync:api
  :feature:budget --> :uikit
  :feature:budget --> :utils
  :feature:budget --> :domain-test-fixtures
  :feature:budget --> :sync-test-fixtures
  :feature:transaction --> :domain
  :feature:transaction --> :navigation
  :feature:transaction --> :sync:api
  :feature:transaction --> :uikit
  :feature:transaction --> :utils
  :feature:transaction --> :domain-test-fixtures
  :feature:transaction --> :sync-test-fixtures
  :sync:default --> :domain
  :sync:default --> :sync:api
  :sync:default --> :sync-test-fixtures
  :sync:no-op --> :sync:api
  :data-remote --> :domain
  :feature:login --> :domain
  :feature:login --> :navigation
  :feature:login --> :sync:api
  :feature:login --> :uikit
  :feature:login --> :utils
  :feature:login --> :domain-test-fixtures
  :feature:login --> :sync-test-fixtures
  :domain --> :sync:api
  :domain --> :domain-test-fixtures
  :feature:category --> :domain
  :feature:category --> :navigation
  :feature:category --> :sync:api
  :feature:category --> :uikit
  :feature:category --> :utils
  :feature:category --> :domain-test-fixtures
  :feature:category --> :sync-test-fixtures
  :data-test-fixtures --> :domain
  :navigation --> :domain
  :navigation --> :sync:api
  :navigation --> :uikit
  :domain-test-fixtures --> :domain
  :composeApp --> :shared
  :composeApp --> :domain
  :composeApp --> :feature:login
  :composeApp --> :feature:transaction
  :composeApp --> :data-remote
  :composeApp --> :sync-surfer
  :composeApp --> :sync:default
  :composeApp --> :data-local
  :composeApp --> :composeAppOffline
  :composeApp --> :data-test-fixtures
  :composeApp --> :domain-test-fixtures
  :composeApp --> :integration-test
  :composeApp --> :navigation
  :composeApp --> :sync-test-fixtures
  :composeApp --> :uikit
  :composeApp --> :utils
  :composeApp --> :feature:account
  :composeApp --> :feature:budget
  :composeApp --> :feature:category
  :composeApp --> :feature:dashboard
  :composeApp --> :feature:settings
  :composeApp --> :feature:workspace
  :composeApp --> :sync:api
  :composeApp --> :sync:no-op
  :feature:dashboard --> :domain
  :feature:dashboard --> :navigation
  :feature:dashboard --> :sync:api
  :feature:dashboard --> :uikit
  :feature:dashboard --> :utils
  :feature:dashboard --> :domain-test-fixtures
  :feature:dashboard --> :sync-test-fixtures
  :sync-test-fixtures --> :domain
  :sync-test-fixtures --> :sync:api
  :sync-test-fixtures --> :sync:default
```
