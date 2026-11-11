package com.dogyun.kairos

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest

@AnalyzeClasses(
    packages = ["com.dogyun.kairos"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ArchitectureTest.ExcludeArchFixture::class],
)
class ArchitectureTest {

    class ExcludeArchFixture : ImportOption {
        override fun includes(location: com.tngtech.archunit.core.importer.Location): Boolean =
            !location.contains("/archfixture/")
    }

    private val rules = ArchitectureRules("com.dogyun.kairos")

    @ArchTest
    fun `domain 은 프레임워크에 의존하지 않는다`(classes: JavaClasses) =
        rules.domainHasNoFramework().check(classes)

    @ArchTest
    fun `domain 은 다른 계층에 의존하지 않는다`(classes: JavaClasses) =
        rules.domainHasNoOtherLayer().check(classes)

    @ArchTest
    fun `application 은 presentation 과 infrastructure 에 의존하지 않는다`(classes: JavaClasses) =
        rules.applicationNotOnPresentationInfrastructure().check(classes)

    @ArchTest
    fun `presentation 은 infrastructure 에 의존하지 않는다`(classes: JavaClasses) =
        rules.presentationNotOnInfrastructure().check(classes)

    @ArchTest
    fun `모듈은 다른 모듈의 domain presentation infrastructure 에 의존하지 않는다`(classes: JavaClasses) =
        rules.moduleIsolation().forEach { it.check(classes) }

    @ArchTest
    fun `다른 모듈 application 호출은 infrastructure 에서만 한다`(classes: JavaClasses) =
        rules.crossModuleApplicationOnlyFromInfrastructure().forEach { it.check(classes) }

    @ArchTest
    fun `demo 는 다른 모듈의 domain presentation infrastructure 에 의존하지 않는다`(classes: JavaClasses) =
        rules.demoUsesOnlyApplication().check(classes)
}
