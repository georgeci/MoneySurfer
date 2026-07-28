package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.fixtures.FakeAppVersionGate
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.sync.version.SyncVersionStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncVersionGateImplSpec : StringSpec({

    "supported app versions allow sync" {
        val domainGate = FakeAppVersionGate(AppVersionStatus.Supported)
        val gate = SyncVersionGateImpl(domainGate)

        gate.refresh() shouldBe SyncVersionStatus.Allowed
        gate.isSyncAllowed() shouldBe true
        domainGate.refreshCount shouldBe 1
    }

    "an available optional update still allows sync" {
        val domainGate = FakeAppVersionGate(AppVersionStatus.UpdateAvailable("Update available"))
        val gate = SyncVersionGateImpl(domainGate)

        gate.refresh() shouldBe SyncVersionStatus.Allowed
        gate.isSyncAllowed() shouldBe true
    }

    "an unsupported app version blocks sync with the domain message" {
        val domainGate = FakeAppVersionGate(AppVersionStatus.Unsupported("Upgrade required"))
        val gate = SyncVersionGateImpl(domainGate)

        gate.refresh() shouldBe SyncVersionStatus.Blocked("Upgrade required")
        gate.isSyncAllowed() shouldBe false
    }

    "each refresh re-reads the domain gate instead of caching the first result" {
        val domainGate = FakeAppVersionGate(AppVersionStatus.Supported)
        val gate = SyncVersionGateImpl(domainGate)

        gate.refresh() shouldBe SyncVersionStatus.Allowed
        domainGate.setStatus(AppVersionStatus.Unsupported("Version retired"))
        gate.refresh() shouldBe SyncVersionStatus.Blocked("Version retired")
        domainGate.refreshCount shouldBe 2
    }
})
