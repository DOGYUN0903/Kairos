package com.dogyun.kairos

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** 4계층 DDD 아키텍처 규칙. root 를 바꿔 fixture 에도 동일하게 적용할 수 있다. */
class ArchitectureRules(private val root: String) {

    private val modules = listOf("member", "deal", "cart", "order", "payment", "seller", "catalog", "file", "benefit", "review", "settlement")
    private val pairs = modules.flatMap { a -> modules.filter { it != a }.map { b -> a to b } }

    fun domainHasNoFramework(): ArchRule =
        noClasses().that().resideInAPackage("$root..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "tools.jackson..",
                "com.fasterxml..",
            )
            .allowEmptyShould(true)

    fun domainHasNoOtherLayer(): ArchRule =
        noClasses().that().resideInAPackage("$root..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "$root..application..",
                "$root..presentation..",
                "$root..infrastructure..",
            )
            .allowEmptyShould(true)

    fun applicationNotOnPresentationInfrastructure(): ArchRule =
        noClasses().that().resideInAPackage("$root..application..")
            .should().dependOnClassesThat().resideInAnyPackage("$root..presentation..", "$root..infrastructure..")
            .allowEmptyShould(true)

    fun presentationNotOnInfrastructure(): ArchRule =
        noClasses().that().resideInAPackage("$root..presentation..")
            .should().dependOnClassesThat().resideInAPackage("$root..infrastructure..")
            .allowEmptyShould(true)

    /** a -> b 쌍마다 하나의 규칙. common 은 modules 에 없으므로 예외. */
    fun moduleIsolation(): List<ArchRule> = pairs.map { (a, b) -> moduleIsolation(a, b) }

    fun moduleIsolation(a: String, b: String): ArchRule =
        noClasses().that().resideInAPackage("$root.$a..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "$root.$b.domain..",
                "$root.$b.presentation..",
                "$root.$b.infrastructure..",
            )
            .allowEmptyShould(true)

    /** demo(시더)는 modules 밖이지만 각 모듈의 application 만 쓴다. */
    fun demoUsesOnlyApplication(): ArchRule =
        noClasses().that().resideInAPackage("$root.demo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                *modules.flatMap { listOf("$root.$it.domain..", "$root.$it.presentation..", "$root.$it.infrastructure..") }.toTypedArray(),
            )
            .allowEmptyShould(true)

    fun crossModuleApplicationOnlyFromInfrastructure(): List<ArchRule> =
        pairs.map { (a, b) -> crossModuleApplicationOnlyFromInfrastructure(a, b) }

    fun crossModuleApplicationOnlyFromInfrastructure(a: String, b: String): ArchRule =
        noClasses().that().resideInAPackage("$root.$a..")
            .and().resideOutsideOfPackage("$root.$a.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("$root.$b.application..")
            .allowEmptyShould(true)
}
