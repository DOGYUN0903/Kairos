package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.util.Base64

@Configuration
@ConditionalOnProperty("kairos.pg.provider", havingValue = "toss")
class TossPgConfig {

    @Bean
    fun tossPgClient(properties: PgProperties): PgClient {
        val timeout = properties.toss.timeout
        val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build())
        factory.setReadTimeout(timeout)
        return TossPgClient(restClient(RestClient.builder().requestFactory(factory), properties.toss))
    }

    companion object {
        /** baseUrl 과 Basic 인증(`base64("$secretKey:")`)이 설정된 RestClient. 시크릿 키는 로그에 남기지 않는다. */
        fun restClient(builder: RestClient.Builder, toss: PgProperties.Toss): RestClient {
            val credentials = Base64.getEncoder().encodeToString("${toss.secretKey}:".toByteArray(Charsets.UTF_8))
            return builder
                .baseUrl(toss.baseUrl.trimEnd('/'))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $credentials")
                .build()
        }
    }
}
