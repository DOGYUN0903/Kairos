package com.dogyun.kairos

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** 규칙이 실제로 실패할 수 있음을 archfixture 의 위반 클래스로 증명한다. */
class ArchitectureRulesNegativeTest {

    private val fixtureRoot = "com.dogyun.kairos.archfixture"
    private val rules = ArchitectureRules(fixtureRoot)
    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeJars())
        .importPackages(fixtureRoot)

    @Test
    fun `domain 이 Spring 에 의존하면 규칙이 실패한다`() {
        assertThrows<AssertionError> { rules.domainHasNoFramework().check(classes) }
    }

    @Test
    fun `다른 모듈 domain 에 의존하면 규칙이 실패한다`() {
        assertThrows<AssertionError> { rules.moduleIsolation().forEach { it.check(classes) } }
    }

    @Test
    fun `다른 모듈 application 을 infrastructure 밖에서 호출하면 규칙이 실패한다`() {
        assertThrows<AssertionError> {
            rules.crossModuleApplicationOnlyFromInfrastructure().forEach { it.check(classes) }
        }
    }
}
