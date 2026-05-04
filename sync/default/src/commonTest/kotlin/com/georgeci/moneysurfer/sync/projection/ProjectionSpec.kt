package com.georgeci.moneysurfer.sync.projection

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProjectionSpec : StringSpec({

    "ProjectionScope.Workspace exposes key" {
        val scope: ProjectionScope = ProjectionScope.Workspace("ws-1")
        (scope as ProjectionScope.Workspace).key shouldBe "ws-1"
    }

    "ProjectionScope.All is a singleton" {
        (ProjectionScope.All as ProjectionScope) shouldBe ProjectionScope.All
    }

    "ProjectionSummary carries the recalculated count" {
        ProjectionSummary(recalculatedCount = 17).recalculatedCount shouldBe 17
    }

    "ProjectionSummary copy preserves equality semantics" {
        val a = ProjectionSummary(recalculatedCount = 5)
        val b = a.copy()
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
    }
})
