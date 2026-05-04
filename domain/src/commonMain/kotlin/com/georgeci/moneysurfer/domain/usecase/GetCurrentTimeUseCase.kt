package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.currentInstant
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class GetCurrentTimeUseCase {

    operator fun invoke(): Instant = currentInstant()
}
