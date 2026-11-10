package com.dogyun.kairos.order.infrastructure

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
@ConditionalOnProperty("kairos.delivery.provider", havingValue = "sweettracker")
class SweetTrackerConfig {

    @Bean
    fun sweetTrackerDeliveryTracker(properties: DeliveryProperties): SweetTrackerDeliveryTracker {
        val timeout = properties.sweetTracker.timeout
        val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build())
        factory.setReadTimeout(timeout)
        return SweetTrackerDeliveryTracker(RestClient.builder().requestFactory(factory).build(), properties)
    }
}
