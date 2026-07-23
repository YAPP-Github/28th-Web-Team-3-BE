package backend.yapp.infra.mission.generation

import backend.yapp.core.mission.generation.service.MissionLifecycleService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "mission.lifecycle",
    name = ["scheduler-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class MissionWeeklyLifecycleScheduler(
    private val service: MissionLifecycleService,
) {
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    fun closeOverdueMissions() {
        service.markOverdueIncomplete()
    }
}
