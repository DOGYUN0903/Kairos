package com.dogyun.kairos.deal.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface DealJpaRepository : JpaRepository<DealEntity, Long>
