package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

class Brand(
    val id: BrandId?,
    val sellerId: Long,
    name: String,
    englishName: String?,
    logoFileId: Long?,
    description: String?,
    status: BrandStatus,
    rejectReason: String?,
) {
    var name: String = name
        private set
    var englishName: String? = englishName
        private set
    var logoFileId: Long? = logoFileId
        private set
    var description: String? = description
        private set
    var status: BrandStatus = status
        private set
    var rejectReason: String? = rejectReason
        private set

    /** 반려된 브랜드만 고쳐서 다시 신청할 수 있다. 반려 사유는 지워진다. */
    fun reapply(name: String, englishName: String?, logoFileId: Long?, description: String?) {
        if (status != BrandStatus.REJECTED) throw DomainException(ErrorCode.BRAND_INVALID_STATE_TRANSITION)
        validate(name, englishName, description)
        this.name = name
        this.englishName = englishName
        this.logoFileId = logoFileId
        this.description = description
        this.rejectReason = null
        this.status = BrandStatus.PENDING
    }

    fun approve() {
        requirePending()
        status = BrandStatus.APPROVED
    }

    fun reject(reason: String) {
        requirePending()
        status = BrandStatus.REJECTED
        rejectReason = reason
    }

    private fun requirePending() {
        if (status != BrandStatus.PENDING) throw DomainException(ErrorCode.BRAND_INVALID_STATE_TRANSITION)
    }

    companion object {
        const val MAX_NAME_LENGTH = 50
        const val MAX_ENGLISH_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 1000

        fun apply(
            sellerId: Long,
            name: String,
            englishName: String?,
            logoFileId: Long?,
            description: String?,
        ): Brand {
            validate(name, englishName, description)
            return Brand(null, sellerId, name, englishName, logoFileId, description, BrandStatus.PENDING, null)
        }

        fun restore(
            id: BrandId,
            sellerId: Long,
            name: String,
            englishName: String?,
            logoFileId: Long?,
            description: String?,
            status: BrandStatus,
            rejectReason: String?,
        ) = Brand(id, sellerId, name, englishName, logoFileId, description, status, rejectReason)

        private fun validate(name: String, englishName: String?, description: String?) {
            if (name.isBlank() || name.length > MAX_NAME_LENGTH) throw DomainException(ErrorCode.BRAND_INVALID)
            if ((englishName?.length ?: 0) > MAX_ENGLISH_NAME_LENGTH) throw DomainException(ErrorCode.BRAND_INVALID)
            if ((description?.length ?: 0) > MAX_DESCRIPTION_LENGTH) throw DomainException(ErrorCode.BRAND_INVALID)
        }
    }
}
