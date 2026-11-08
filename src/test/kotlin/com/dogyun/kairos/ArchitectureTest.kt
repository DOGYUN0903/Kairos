package com.dogyun.kairos

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@AnalyzeClasses(packages = ["com.dogyun.kairos"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    companion object {
        private const val ROOT = "com.dogyun.kairos"
        private val MODULES = listOf("member", "deal", "order", "payment")
        private val MODULE_PAIRS = MODULES.flatMap { a -> MODULES.filter { it != a }.map { b -> a to b } }
    }

    @ArchTest
    fun `domain 은 프레임워크에 의존하지 않는다`(classes: com.tngtech.archunit.core.domain.JavaClasses) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "tools.jackson..",
                "com.fasterxml..",
            )
            .allowEmptyShould(true)
            .check(classes)
    }

    @ArchTest
    fun `application 은 adapter 에 의존하지 않는다`(classes: com.tngtech.archunit.core.domain.JavaClasses) {
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @ArchTest
    fun `모듈은 다른 모듈의 domain, adapter 에 의존하지 않는다`(classes: com.tngtech.archunit.core.domain.JavaClasses) {
        MODULE_PAIRS.forEach { (a, b) ->
            noClasses().that().resideInAPackage("$ROOT.$a..")
                .should().dependOnClassesThat().resideInAnyPackage("$ROOT.$b.domain..", "$ROOT.$b.adapter..")
                .allowEmptyShould(true)
                .check(classes)
        }
    }

    @ArchTest
    fun `다른 모듈 port in 호출은 adapter out module 에서만 한다`(classes: com.tngtech.archunit.core.domain.JavaClasses) {
        MODULE_PAIRS.forEach { (a, b) ->
            noClasses().that().resideInAPackage("$ROOT.$a..")
                .and().resideOutsideOfPackage("$ROOT.$a.adapter.out.module..")
                .should().dependOnClassesThat().resideInAPackage("$ROOT.$b.application.port.in..")
                .allowEmptyShould(true)
                .check(classes)
        }
    }
}
