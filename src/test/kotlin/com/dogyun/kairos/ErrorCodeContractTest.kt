package com.dogyun.kairos

import com.dogyun.kairos.common.domain.ErrorCode
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** ErrorCode enum과 OpenAPI의 ErrorResponse.code enum이 같은 집합인지 검증한다. 스펙이 기준이다. */
class ErrorCodeContractTest {

    @Test
    fun 에러_코드_enum과_OpenAPI_code_enum이_일치한다() {
        val root = Yaml().load<Map<String, Any?>>(Files.readString(Path.of("docs/api/openapi.yaml")))
        val specCodes = root.map("components").map("schemas").map("ErrorResponse").map("properties").map("code")
            .let { (it["enum"] as List<*>).map { code -> code as String }.toSet() }
        val actualCodes = ErrorCode.entries.map { it.name }.toSet()

        assertEquals(specCodes, actualCodes, "스펙에만 있음: ${specCodes - actualCodes}, 구현에만 있음: ${actualCodes - specCodes}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key] as Map<String, Any?>
}
