package backend.yapp.api.health.controller

import backend.yapp.api.health.dto.HealthResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    fun health(): HealthResponse = HealthResponse(status = "UP")
}