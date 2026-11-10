# ADR 0003. C2 주문 생성·승인·취소는 한 DB 트랜잭션으로 묶고 외부 호출은 트랜잭션 밖에서 한다

- 상태: 채택 (MSA 분리 시 Saga로 대체 예정, Phase 4)
- 날짜: 2026-11-09
- 대체: ADR 0001

## 맥락
C2의 주문 생성·승인·취소는 order, catalog(재고), payment 세 모듈의 데이터를 함께 바꾼다. 설계 원칙은 "유스케이스 하나에서 한 애그리거트만 변경"이지만, 아직 모놀리식이고 Saga는 Phase 4에서 도입한다.

## 결정
- 모듈 간 변경은 한 DB 트랜잭션으로 묶는다.
- 재고는 조건부 UPDATE(`UPDATE sku SET stock = stock - ? WHERE id = ? AND stock >= ?`)로 차감하고, 0행이면 전체를 롤백한다.
- PG·택배 같은 외부 호출은 DB 트랜잭션 밖에서 한다.
- 외부 호출 결과가 불확실한 상태는 정리 작업이 PG 조회로 확정한다.
- 주문 상태를 바꾸는 모든 트랜잭션은 주문 행을 `SELECT ... FOR UPDATE`로 먼저 잠근다. 이 행 락은 Phase 1 "재고 락 금지"의 대상이 아니다(재고 차감 방식과 무관한 주문 단위 직렬화다).
- 판매자 주문 엔드포인트(배송 시작·완료, 판매자 취소)는 주문 ID를 트랜잭션 밖에서 일반 조회(`findOrderIdBySellerOrderId`)로 찾고, 트랜잭션 안에서 `findByIdForUpdate`로 잠근다. 잠금 전에 트랜잭션 안에서 일반 SELECT를 하면 REPEATABLE READ에서 그 시점의 스냅샷이 고정되어, 락을 얻은 뒤에도 낡은 상태를 읽기 때문이다.
- 스케줄러 작업(만료, 대사, 자동 구매 확정)은 이 서비스들을 감싸는 `@Transactional`을 붙이지 않는다. 바깥 트랜잭션이 있으면 같은 스냅샷 문제가 생기고 PG 호출이 트랜잭션 안으로 들어온다.
- PG 결제 되돌리기(`voidMismatch`, `voidClosedOrder`)는 `OrderPaymentService` 한 곳에 둔다. 취소 결과가 불확실하면 결제 `UNKNOWN`, 항목 `CANCEL_REQUESTED`로 남기고 대사 작업이 PG 조회로 맞춘다. 토스 승인의 `ALREADY_PROCESSED_PAYMENT`와 일시 오류 코드도 `UNKNOWN`으로 본다.

## 결과
- 단순하고 정합성이 쉽게 보장된다.
- 외부 호출 중간 상태가 커밋되므로 `IN_PROGRESS`/`UNKNOWN`/`CANCEL_REQUESTED` 상태와 정리 작업이 필요하다.
- 모듈 간 트랜잭션 결합은 MSA 분리 시 Saga로 대체한다.
