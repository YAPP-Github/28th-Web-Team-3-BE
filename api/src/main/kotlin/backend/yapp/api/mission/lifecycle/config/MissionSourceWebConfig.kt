package backend.yapp.api.mission.lifecycle.config

import backend.yapp.core.mission.generation.service.MissionSource
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class MissionSourceWebConfig : WebMvcConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(MissionSourceConverter)
    }
}

internal object MissionSourceConverter : Converter<String, MissionSource> {
    override fun convert(source: String): MissionSource =
        MissionSource.entries.firstOrNull { it.name.equals(source, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported mission source")
}
