package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.PeriodTotals
import com.georgeci.moneysurfer.domain.model.calculatePeriodTotalsFromList
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single

@Single
class CalculatePeriodTotalsUseCase(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(
        workspaceId: WorkspaceId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): PeriodTotals {
        val list = transactionRepository.getByWorkspaceId(workspaceId).first()
        return calculatePeriodTotalsFromList(list, fromDate, toDate, timeZone)
    }
}
