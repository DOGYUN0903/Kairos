# ADR 0004. C3 혜택(쿠폰·포인트) 적용은 주문 트랜잭션에 함께 묶고 락 순서를 고정한다

- 상태: 채택 (MSA 분리 시 Saga로 대체 예정, Phase 4)
- 날짜: 2026-11-10
- 관계: ADR 0003의 연장(대체 아님)

## 맥락
C3부터 주문 생성은 order, catalog(재고)에 더해 benefit(쿠폰 사용 상태, 포인트 잔액·로트·원장)을 한 번에 바꾼다. 취소·만료·결제 실패도 benefit 되돌림과 재고 복구를 함께 한다. 모듈이 늘어도 아직 모놀리식이고 Saga는 Phase 4에서 도입한다. 트랜잭션이 여러 행 락을 잡으므로 순서가 어긋나면 교착이 생긴다.

## 결정
- 주문 생성·취소·만료·결제 실패 중 benefit 변경은 ADR 0003과 같은 DB 트랜잭션에 묶는다. 외부 호출(PG 등)은 계속 트랜잭션 밖이다.
- 락 순서를 고정한다.
  - 주문 생성: `member_point → coupon → sku` (주문 행은 INSERT라 잠그지 않는다).
  - 취소·만료·구매 확정·결제 실패: `orders(FOR UPDATE) → member_point → coupon → sku`. benefit 되돌림을 재고 복구보다 먼저 호출한다.
  - 후기 작성: `member_point(적립) → product(집계 UPDATE)`. 취소 마무리의 `member_point → … → product(decreaseSales)`와 같은 순서다.
  - 좋아요: `product_like → product`.
  - 포인트 만료: `member_point → point_lot`.
- 쿠폰 수량·사용 상태, 좋아요 플래그, 상품 집계(좋아요 수·후기 수·별점 합)는 모두 조건부 UPDATE의 영향 행 수로 판정한다. 비관적 락과 Redis는 쓰지 않는다. 유일한 행 락은 `member_point`(FOR UPDATE)와 C2의 주문 행 락이다.
- 주문 생성에서 orderId가 아직 없으므로 `BenefitApplier`를 `reserve`(포인트 락, 쿠폰 `AVAILABLE → USED`)와 `commit`(주문 저장 뒤 `used_order_id` 기록, 포인트 FIFO 차감, 원장 `USE`)로 나눈다. 둘 다 같은 트랜잭션이라 실패하면 함께 롤백된다.
- 0원 주문은 PG를 거치지 않고 `OrderPaymentService`의 승인 확정 단계를 재사용해 즉시 `PAID`로 만든다. 승인 확정은 주문 생성 트랜잭션 커밋 후에 호출한다.
- 모듈 경계는 유지한다. order는 benefit을 포트(`BenefitApplier`, `BenefitReverter`, `PointEarner`)로만 부르고, 구현은 `order.infrastructure`가 `benefit.application` 서비스를 호출한다. 할인 계산은 order 도메인 `DiscountAllocator` 한 곳에서만 한다.

## 결과
- 쿠폰·포인트·재고·주문이 한 트랜잭션이라 중간 상태가 남지 않는다.
- 락 순서를 지키는 한 교착이 생기지 않는다. 새 경로를 추가할 때 이 순서를 따른다.
- 트랜잭션 중 프로세스가 죽어도 DB 롤백이 처리한다. PG 승인 전 크래시로 남은 `PENDING_PAYMENT` 주문은 15분 만료 작업이 benefit까지 되돌린다.
- 모듈 간 트랜잭션 결합은 MSA 분리 시 Saga로 대체한다(Phase 4).
