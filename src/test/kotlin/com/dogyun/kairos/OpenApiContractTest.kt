package com.dogyun.kairos

import org.springframework.test.web.servlet.get
import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment

/**
 * 스펙(docs/api/openapi.yaml)과 구현(springdoc이 생성한 /v3/api-docs)의 (method, path) 집합이 같은지 검증한다.
 * 스펙이 기준이다. 차이가 나면 구현을 고친다.
 */
class OpenApiContractTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var environment: Environment

    @Test
    fun `스펙과 구현의 엔드포인트 집합이 일치한다`() {
        val spec = specOperations()
        val actual = actualOperations()

        assertEquals(spec, actual, "스펙에만 있음: ${spec - actual}, 구현에만 있음: ${actual - spec}")
    }

    private fun specOperations(): Set<String> {
        val root = Yaml().load<Map<String, Any?>>(Files.readString(Path.of("docs/api/openapi.yaml")))
        val paths = root["paths"] as Map<*, *>
        // Demo 태그 operation 은 demo 프로필에서만 컨트롤러가 뜬다. 그 밖의 프로필에서는 스펙 쪽에서만 뺀다.
        if (DEMO_PROFILE in environment.activeProfiles) return operations(paths)
        val withoutDemo = paths.mapValues { (_, item) ->
            (item as Map<*, *>).filterValues { op -> !(op is Map<*, *> && DEMO_TAG in (op["tags"] as? List<*>).orEmpty()) }
        }
        return operations(withoutDemo)
    }

    private fun actualOperations(): Set<String> {
        val body = mockMvc.get("/v3/api-docs").andReturn().response.contentAsString
        val root = objectMapper.readValue(body, Map::class.java)
        return operations(root["paths"] as Map<*, *>)
    }

    private fun operations(paths: Map<*, *>): Set<String> =
        paths.flatMap { (path, item) ->
            (item as Map<*, *>).keys
                .map { it.toString().lowercase() }
                .filter { it in HTTP_METHODS }
                .map { "${it.uppercase()} ${normalize(path.toString())}" }
        }.toSet()

    // {dealId} 와 {id} 처럼 변수명만 다른 경우를 같게 본다. 끝의 슬래시도 무시한다.
    private fun normalize(path: String) =
        path.replace(Regex("\\{[^}]+}"), "{}").trimEnd('/')

    private companion object {
        const val DEMO_PROFILE = "demo"
        const val DEMO_TAG = "Demo"
        val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete", "head", "options")
    }
}
