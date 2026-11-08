package com.dogyun.kairos.common.domain

enum class ErrorCode(val httpStatus: Int, val message: String) {
    INVALID_REQUEST(400, "잘못된 요청입니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "권한이 없습니다."),

    MEMBER_EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다."),
    AUTH_INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),

    DEAL_INVALID(400, "딜 정보가 올바르지 않습니다."),
    DEAL_NOT_FOUND(404, "딜을 찾을 수 없습니다."),
    DEAL_ALREADY_OPENED(409, "이미 오픈된 딜은 수정하거나 삭제할 수 없습니다."),
    DEAL_NOT_ONGOING(409, "진행 중인 딜이 아닙니다."),

    ORDER_LIMIT_EXCEEDED(409, "인당 구매 수량 한도를 초과했습니다."),
    STOCK_SOLD_OUT(409, "재고가 모두 소진되었습니다."),
    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_CANCELLED(409, "이미 취소된 주문입니다."),
}
