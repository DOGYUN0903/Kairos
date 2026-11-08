package com.dogyun.kairos.member.infrastructure

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.member.domain.Email
import com.dogyun.kairos.member.domain.Member
import com.dogyun.kairos.member.domain.MemberId
import com.dogyun.kairos.member.domain.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(private val jpaRepository: MemberJpaRepository) : MemberRepository {

    /** 회원은 아직 변경 가능한 상태가 없어 신규 저장만 지원한다. */
    override fun save(member: Member): Member {
        require(member.id == null) { "이미 저장된 회원은 다시 저장할 수 없습니다." }
        val entity = MemberEntity(member.email.value, member.passwordHash, member.name, member.role)
        return try {
            toDomain(jpaRepository.saveAndFlush(entity))
        } catch (e: DataIntegrityViolationException) {
            // 가입 동시 요청 또는 소프트 삭제된 회원의 이메일과 충돌한 경우 UNIQUE(email) 가 막는다.
            throw DomainException(ErrorCode.MEMBER_EMAIL_DUPLICATED)
        }
    }

    override fun findById(id: MemberId): Member? = jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findByEmail(email: Email): Member? = jpaRepository.findByEmail(email.value)?.let(::toDomain)

    override fun existsByEmail(email: Email): Boolean = jpaRepository.existsByEmail(email.value)

    private fun toDomain(entity: MemberEntity) =
        Member.restore(MemberId(entity.id), Email(entity.email), entity.password, entity.name, entity.role)
}
