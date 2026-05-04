package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.Clock
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class GetCurrentTimeUseCase(private val clock: Clock) {

    operator fun invoke(): Instant = clock.now()
}
