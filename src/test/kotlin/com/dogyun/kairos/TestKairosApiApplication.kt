package com.dogyun.kairos

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<KairosApiApplication>().with(TestcontainersConfiguration::class).run(*args)
}
