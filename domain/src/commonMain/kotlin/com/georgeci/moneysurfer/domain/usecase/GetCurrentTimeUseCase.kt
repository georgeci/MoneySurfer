package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.currentTimeMillis
import org.koin.core.annotation.Single

@Single
class GetCurrentTimeUseCase {

    operator fun invoke(): Long = currentTimeMillis()
}
