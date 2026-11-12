plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.13"
    // 돈 계산 순수 클래스의 뮤테이션 테스트(./gradlew pitest). check/test에는 묶지 않는다.
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.dogyun"
version = "0.0.1-SNAPSHOT"
description = "kairos-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-mysql")
    // Micrometer 지표를 Prometheus 포맷으로 노출한다(/actuator/prometheus). 컴파일 의존이 없어 runtimeOnly.
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    // 요청마다 traceId/spanId를 MDC에 넣고 W3C traceparent를 전파한다(로그 상관관계, MSA 전환 대비). exporter는 두지 않는다.
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    // Boot 4에서는 OTel 브리지 자동설정이 별도 모듈이다. 없으면 NOOP Tracer가 되어 traceId가 비어 있다.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.14")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.github.openfeign.querydsl:querydsl-jpa:7.7")
    ksp("io.github.openfeign.querydsl:querydsl-ksp-codegen:7.7")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("querydsl.includedPackages", "com.dogyun.kairos")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 서버 기본 시간대는 Asia/Seoul로 고정한다(LocalDateTime.now() 기준).
    systemProperty("user.timezone", "Asia/Seoul")
    jvmArgs("-Duser.timezone=Asia/Seoul")
}

// 돈 계산 클래스만 대상으로 하는 뮤테이션 테스트. 빠른 순수 단위 테스트만 돌린다(Testcontainers·Spring 제외).
pitest {
    pitestVersion = "1.30.2"
    junit5PluginVersion = "1.2.3"
    // 플러그인이 옛 junit-platform-launcher(1.12)를 끼워 넣으면 Boot 4의 JUnit 6 엔진과 어긋나 실패한다. Boot BOM 버전을 그대로 쓴다.
    addJUnitPlatformLauncher = false
    targetClasses = listOf(
        "com.dogyun.kairos.order.domain.DiscountAllocator*",
        "com.dogyun.kairos.order.domain.CouponTerms*",
        "com.dogyun.kairos.order.domain.RefundCalculator*",
        "com.dogyun.kairos.order.domain.RefundPlan*",
        "com.dogyun.kairos.order.domain.ReturnRefundCalculator*",
        "com.dogyun.kairos.order.domain.ReturnRefundPlan*",
        "com.dogyun.kairos.settlement.domain.SettlementCalculator*",
        "com.dogyun.kairos.settlement.domain.Settlement",
        "com.dogyun.kairos.settlement.domain.Settlement\$*",
    )
    targetTests = listOf(
        "com.dogyun.kairos.order.domain.*Test",
        "com.dogyun.kairos.settlement.domain.*Test",
    )
    // Kotlin data class 가 만드는 equals/hashCode/toString/componentN/copy 는 돈 계산이 아니라 제외한다.
    excludedMethods = listOf("equals", "hashCode", "toString", "component*", "copy", "copy\$default")
    mutators = listOf("STRONGER")
    outputFormats = listOf("HTML", "XML")
    timestampedReports = false
    threads = 4
    jvmArgs = listOf("-Duser.timezone=Asia/Seoul")
    // 측정값은 뮤테이션 356/413 = 86%, 라인 302/303 = 99%. 뮤테이션은 2%p 여유를 두고 84 로 고정한다. 떨어지면 ./gradlew pitest 가 실패한다.
    // 남은 생존 뮤턴트는 대부분 등가(방어용 check 불변식, Kotlin 인라인 stdlib 빈 컬렉션 분기, 중복 상태 가드)다.
    mutationThreshold = 84
    coverageThreshold = 99
}
