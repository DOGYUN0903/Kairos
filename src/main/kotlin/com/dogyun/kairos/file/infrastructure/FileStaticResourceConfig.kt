package com.dogyun.kairos.file.infrastructure

import com.dogyun.kairos.file.application.FileProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
class FileStaticResourceConfig(private val properties: FileProperties) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val location = Path.of(properties.localDir).toAbsolutePath().normalize().toUri().toString().trimEnd('/') + "/"
        registry.addResourceHandler("/files/**").addResourceLocations(location)
    }
}
