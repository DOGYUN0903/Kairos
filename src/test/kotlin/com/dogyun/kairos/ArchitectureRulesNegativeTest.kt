package com.dogyun.kairos

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** 규칙이 실제로 실패할 수 있음을 archfixture 의 위반 클래스로 증명한다. */
class ArchitectureRulesNegativeTest {

    private val fixtureRoot = "com.dogyun.kairos.archfixture"
    private val rules = ArchitectureRules(fixtureRoot)
    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeJars())
        .importPackages(fixtureRoot)

    private fun assertViolation(rule: ArchRule, fixtureClass: String) {
        val error = assertThrows<AssertionError> { rule.check(classes) }
        assertThat(error.message).contains(fixtureClass)
    }

    @Test
    fun `domain 이 Spring 에 의존하면 규칙이 실패한다`() =
        assertViolation(rules.domainHasNoFramework(), "archfixture.order.domain.BadOrder")

    @Test
    fun `domain 이 infrastructure 에 의존하면 규칙이 실패한다`() =
        assertViolation(rules.domainHasNoOtherLayer(), "archfixture.order.domain.BadLayeredOrder")

    @Test
    fun `application 이 presentation 에 의존하면 규칙이 실패한다`() =
        assertViolation(
            rules.applicationNotOnPresentationInfrastructure(),
            "archfixture.order.application.BadPresentationUser",
        )

    @Test
    fun `presentation 이 infrastructure 에 의존하면 규칙이 실패한다`() =
        assertViolation(rules.presentationNotOnInfrastructure(), "archfixture.order.presentation.BadOrderController")

    @Test
    fun `order 가 deal domain 에 의존하면 order-deal 쌍 규칙이 실패한다`() =
        assertViolation(rules.moduleIsolation("order", "deal"), "archfixture.order.application.BadOrderService")

    @Test
    fun `order 가 infrastructure 밖에서 deal application 을 호출하면 order-deal 쌍 규칙이 실패한다`() =
        assertViolation(
            rules.crossModuleApplicationOnlyFromInfrastructure("order", "deal"),
            "archfixture.order.application.BadOrderService",
        )
}
