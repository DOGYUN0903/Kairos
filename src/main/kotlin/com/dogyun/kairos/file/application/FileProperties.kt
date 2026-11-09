package com.dogyun.kairos.file.application

import org.springframework.boot.context.properties.ConfigurationProperties

// application 이 설정값을 읽으므로 infrastructure 가 아니라 여기에 둔다(ArchitectureTest: application → infrastructure 금지).
@ConfigurationProperties("kairos.file")
data class FileProperties(
    val localDir: String = "uploads",
    val baseUrl: String = "http://localhost:8080",
)
