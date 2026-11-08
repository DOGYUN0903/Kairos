package com.dogyun.kairos

import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class KairosApiApplication

fun main(args: Array<String>) {
    runApplication<KairosApiApplication>(*args)
}
