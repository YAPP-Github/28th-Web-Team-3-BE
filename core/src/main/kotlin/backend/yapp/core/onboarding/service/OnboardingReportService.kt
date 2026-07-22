package backend.yapp.core.onboarding.service

import backend.yapp.common.exception.BaseException
import backend.yapp.common.exception.ErrorCode
import backend.yapp.core.onboarding.calculator.FinancialReport
import backend.yapp.core.onboarding.calculator.FinancialReportCalculator
import backend.yapp.core.onboarding.domain.OnboardingProfileRepository
import backend.yapp.core.onboarding.port.FinanceStatisticsPort
import backend.yapp.core.onboarding.port.OnboardingConfigPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingReportService(
    private val profileRepository: OnboardingProfileRepository,
    private val configPort: OnboardingConfigPort,
    private val statisticsPort: FinanceStatisticsPort,
) {
    @Transactional(readOnly = true)
    fun report(guestUserId: Long): FinancialReport {
        val profile = profileRepository.findByGuestUserId(guestUserId)
            ?: throw BaseException(ErrorCode.ONBOARDING_PROFILE_NOT_FOUND)
        if (!profile.isReportReady()) throw BaseException(ErrorCode.ONBOARDING_INCOMPLETE)

        return FinancialReportCalculator(configPort.current(), statisticsPort.current()).calculate(profile)
    }
}
