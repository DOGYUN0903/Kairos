# C3 혜택·후기 설계

## 1. 의도

C2 구매 위에 **쿠폰, 포인트, 후기, 상품 좋아요**를 얹는다. 쿠폰과 포인트는 결제 금액을 바꾸므로 C2의 "돈이 어긋나지 않는다"는 기준이 그대로 적용된다. 할인이 두 번 적용되거나, 취소했는데 쿠폰·포인트가 돌아오지 않거나(또는 두 번 돌아오거나), 판매자 정산이 플랫폼 쿠폰 때문에 줄어들면 기능이 동작해도 실패다.
후기와 좋아요는 돈이 아니라 **집계 정합성**(평균 별점, 후기 수, 좋아요 수가 실제 행 수와 같다)이 기준이다.

표기: 각 항목은 **사용자 결정**(이미 확정, 바꾸려면 사용자 승인) 또는 **Claude 결정**(작성자 판단, 빌더·리뷰에서 반박 가능)으로 표시한다.

## 2. 목표와 비목표

- 목표: 상품 쿠폰·장바구니 쿠폰(정액·정률, 최소 금액), 쿠폰 정책 생성과 사용자 발급(다운로드), 포인트 적립·사용·만료·환불, 부분 취소 시 쿠폰·포인트 되돌림, 결제 실패·만료 시 되돌림, 후기(작성·수정·삭제·판매자 답글·적립금), 상품 평균 별점·후기 수, 상품 좋아요·취소·내 좋아요 목록·좋아요 수·좋아요순 정렬.
- 비목표: 선착순 쿠폰의 동시성 최적화(C6 핫딜에서), 정산 집계(C4. 단 C4가 쓸 "판매자 부담 할인" 값은 지금 저장한다), 후기 신고·관리자 삭제, 후기 추천, 포인트 유료 충전, 쿠폰 코드 입력 방식, 등급별 적립률, 좋아요 알림.
- 안티 목표(실패로 보는 것): 쿠폰 한 장이 두 주문에 쓰임, 포인트 잔액이 음수가 됨, 취소 환불액 + 되돌린 포인트 + 되돌린 쿠폰이 원래 낸 것과 다름, 플랫폼 쿠폰이 판매자 정산을 깎음, 좋아요 수가 실제 행 수와 어긋남, 후기 하나로 적립금을 두 번 받음.

## 3. 모듈 구성 (Claude 결정)

- 새 모듈 `benefit`: 쿠폰 정책, 발급 쿠폰, 포인트(잔액·로트·원장). 미래의 "혜택 서비스" 경계.
- 새 모듈 `review`: 후기와 판매자 답글.
- 좋아요는 `catalog`에 둔다(`product_like` + `product.like_count`). 좋아요는 상품 집계의 일부이고 별도 서비스로 뗄 만큼 규칙이 없다. 후기 집계(`review_count`, `rating_sum`)도 product 컬럼이며 review 모듈이 catalog application 포트로 갱신한다.
- `order`는 `benefit`을 **포트(인터페이스)** 로만 부른다: `BenefitApplier`(주문 생성 시 쿠폰·포인트 확정), `BenefitReverter`(취소·만료 시 되돌림), `PointEarner`(구매 확정 시 적립). 구현은 `order.infrastructure`가 `benefit.application` 서비스를 호출한다(CLAUDE.md 모듈 규칙).
- 주문 생성 트랜잭션이 order·catalog(재고)에 더해 benefit(쿠폰 상태, 포인트 잔액)까지 바꾼다. ADR 0003의 "한 DB 트랜잭션" 결정의 연장이며 **ADR 0004**로 남긴다(Phase 4 Saga에서 분리).

## 4. 쿠폰 (사용자 결정)

- 종류는 둘.
  1. **상품 쿠폰**: 주문 항목 하나에 한 장. 발급 주체는 플랫폼 또는 판매자. 할인은 **발급 주체가 부담**한다. 판매자 발급이면 그 항목의 판매자 정산 기준 금액이 할인만큼 줄고, 플랫폼 발급이면 정산은 그대로다.
  2. **장바구니 쿠폰**: 주문 하나에 한 장. 플랫폼만 발급하고 플랫폼이 부담한다.
- 할인 유형: 정액, 정률(최대 할인 금액 필수). 최소 주문 금액은 선택.
- 적용 순서: **상품 쿠폰 → 장바구니 쿠폰 → 포인트(상한 30%) → PG 결제**.
- 장바구니 쿠폰 할인과 포인트는 **결제 시점에 항목별로 비례 배분해 항목에 저장**한다. 이후 환불·정산은 저장된 값만 쓴다(다시 계산하지 않는다).
- 되돌림: 항목 취소 시 그 항목의 상품 쿠폰은 만료 전이면 돌려준다. 부분 취소에서는 장바구니 쿠폰을 돌려주지 **않고**, 남은 금액이 최소 주문 금액 아래로 떨어져도 할인을 **회수하지 않는다**(플랫폼 부담). 주문이 전부 취소되면 만료 전인 모든 쿠폰을 돌려준다.

### 4-1. 쿠폰 정책과 발급 (Claude 결정)

- `coupon_policy`(정책)는 관리자(플랫폼 쿠폰: 상품·장바구니)와 판매자(판매자 상품 쿠폰)가 만든다. 판매자 정책은 승인 없이 바로 쓴다(자기 돈이므로). 정책은 만든 뒤 할인 조건을 바꿀 수 없고 **종료**만 할 수 있다(이미 발급된 쿠폰의 계산이 바뀌면 안 되므로).
- 적용 대상(`target_type`): 플랫폼 상품 쿠폰은 `ALL_PRODUCTS` 또는 `PRODUCT`(상품 하나), 판매자 상품 쿠폰은 `SELLER`(자기 상품 전부) 또는 `PRODUCT`(자기 상품 하나). 장바구니 쿠폰은 대상이 없다.
- 발급은 **사용자가 다운로드(claim)** 한다. 1인 1장 고정(`UNIQUE(policy_id, member_id)`). 총 수량은 정책의 `total_quantity`(NULL이면 무제한)이고, 발급은 조건부 UPDATE `issued_count = issued_count + 1 WHERE issued_count < total_quantity`가 1행일 때만 쿠폰 행을 만든다. 0행이면 `COUPON_SOLD_OUT`. 선착순 쿠폰의 경합 측정은 C6로 미룬다(로드맵).
- 발급 쿠폰의 만료일 = `min(정책 valid_until, 발급 시각 + 정책 validity_days)`. 만료 쿠폰은 조회에서 `EXPIRED`로 보여 주고(계산값), 별도 만료 작업은 두지 않는다.
- 쿠폰 상태 `coupon_status`: `AVAILABLE` → `USED`(주문 생성, `used_order_id` 기록) → `AVAILABLE`(되돌림). 사용은 조건부 UPDATE `WHERE coupon_status = 'AVAILABLE' AND member_id = ?`가 1행일 때만 성공한다. 같은 쿠폰이 두 주문에 동시에 들어오면 한쪽만 성공하고 다른 쪽은 `COUPON_NOT_AVAILABLE`로 주문 전체가 롤백된다.

### 4-2. 할인 계산 규칙 (Claude 결정, 사용자 규칙을 숫자로 고정)

- 모든 금액은 원 단위 정수. 정률은 **버림**(floor). 할인은 적용 대상 금액을 넘지 못한다.
- 상품 쿠폰: 대상 = 그 항목의 `lineAmount`(판매가+추가금액)×수량. 최소 주문 금액은 `lineAmount` 기준. 정률 할인 = `floor(lineAmount × rate)`, `maxDiscount`로 자른다.
- 장바구니 쿠폰: 대상 = Σ(`lineAmount` − 상품 쿠폰 할인). 최소 주문 금액도 이 합계 기준. 정률·최대 할인 동일.
- 장바구니 쿠폰 배분: 각 항목 비중 = (`lineAmount` − 상품 쿠폰 할인)/대상 합계. 항목별 배분액은 버림, **나머지는 항목 ID(생성 순) 기준 마지막 항목에** 얹어 합계를 정확히 맞춘다.
- 포인트 배분: 대상 = 각 항목의 (`lineAmount` − 상품 쿠폰 − 장바구니 배분액). 같은 방식으로 비례 배분하고 나머지는 마지막 항목에.
- 항목 `paidAmount` = `lineAmount` − 상품 쿠폰 − 장바구니 배분 − 포인트 배분. 주문 결제(PG) 금액 = Σ `paidAmount` + 구매자 배송비.
- 이 계산은 order 도메인의 순수 함수 `DiscountAllocator` **한 곳**에서만 한다(C2의 `RefundCalculator`와 같은 원칙).

### 4-3. 무료배송 기준 금액 (Claude 결정, 권고)

C2의 무료배송 기준(5만 원, 구매자 기준)은 **할인 전 상품 금액 합계(`itemsAmount`)** 로 판단한다. 쿠폰·포인트는 보지 않는다.
- 근거: (1) `RefundCalculator`의 "남은 상품 금액"·3,000원 차감 로직이 그대로 유지된다. 할인 후 금액으로 바꾸면 부분 취소마다 항목별 할인 후 금액으로 기준을 다시 재야 하고, 장바구니 쿠폰을 회수하지 않는 규칙과 얽혀 환불액이 설명하기 어려워진다. (2) 할인은 발급 주체가 부담하는 판촉이지 상품 가치가 준 것이 아니다. (3) 배송비 부담 주체 판단은 C2 그대로다.
- 틀렸을 때 비용: 쿠폰·포인트로 실제 결제액이 5만 원 아래인 주문에도 플랫폼이 배송비(판매자 주문당 3,000원)를 부담한다. 주문당 상한이 작고 정합성 문제가 아니므로 C3에서 받아들인다. 바꾸려면 설정 하나가 아니라 `RefundCalculator` 규칙이 함께 바뀌므로 사용자 결정으로 올린다.

## 5. 포인트 (사용자 결정)

- 적립: **구매 확정 시** 결제 금액의 1%. 결제 승인 시점이 아니다.
- 사용 조건: 잔액 1,000P 이상일 때만, **10P 단위**, 주문당 상한 = 쿠폰 적용 후 상품 금액의 30%. 배송비는 포인트로 낼 수 없다.
- 만료: 적립 후 1년. 소비는 **FIFO**(먼저 만료되는 것부터).
- 부분 취소: 결제 시점에 저장한 항목별 배분만큼 되돌린다.

### 5-1. 적립 세부 (Claude 결정)

- "결제 금액의 1%"의 결제 금액 = 확정되는 **판매자 주문**의 `ORDERED` 항목 `paidAmount` 합계(쿠폰·포인트 차감 후 PG로 낸 금액, 배송비 제외). 포인트로 낸 부분은 적립 대상이 아니다(포인트로 포인트를 낳지 않는다). 적립액 = `floor(합계 × 1%)`, 1P 단위(10P 단위는 사용에만 적용). 0P면 적립 행을 만들지 않는다.
- 적립은 `OrderShippingService.confirmPurchase`/`autoConfirm`의 **같은 트랜잭션**(주문 행 락 아래)에서 `PointEarner` 포트로 한다. 멱등성은 `point_transaction`의 `UNIQUE(ref_type, ref_id)` = `(PURCHASE_CONFIRM, sellerOrderId)`가 보장한다.
- 구매 확정 뒤에는 취소가 불가능하므로(C2: 배송 전까지만 취소) 적립 회수 규칙은 필요 없다. 반품(로드맵 5장)이 들어오면 그때 정한다.

### 5-2. 잔액·로트·원장 (Claude 결정)

- `member_point`(회원당 1행, `UNIQUE(member_id)`): `balance`. **포인트를 바꾸는 모든 트랜잭션은 이 행을 `SELECT ... FOR UPDATE`로 먼저 잠근다**(주문 행 락과 같은 원칙). 잔액 검사는 락 뒤에 한다.
- `point_lot`(적립 단위): `amount`, `remaining_amount`, `expires_at`, 출처. 잔액 = Σ 만료되지 않은 로트의 `remaining_amount`이며 `member_point.balance`와 같아야 한다(테스트로 증명).
- `point_transaction`(원장): 유형 `EARN_PURCHASE`, `EARN_REVIEW`, `USE`, `REFUND`, `EXPIRE`, 부호 있는 `amount`, `balance_after`, `ref_type`/`ref_id`(`UNIQUE`). 사용 시 어느 로트에서 얼마를 뺐는지 `point_lot_usage`(transaction_id, lot_id, amount)에 남긴다.
- 사용(FIFO): 락 → 만료 안 된 로트를 `expires_at, id` 오름차순으로 `remaining_amount`에서 차감 → 원장 `USE`(ref = `ORDER`, orderId) → `balance` 감소.
- 되돌림(취소·만료·결제 실패): 되돌릴 금액을 그 `USE`의 `point_lot_usage`를 **역순**으로 되감는다. 로트가 아직 만료 전이면 `remaining_amount`를 되돌리고, 이미 만료된 로트 몫은 새 로트(`REFUND` 출처, 만료 = 되돌린 날 + 30일, 설정값)로 만든다. 원장 `REFUND`(ref = `ORDER_ITEM`, itemId / 결제 전 닫힘은 `ORDER`, orderId). 틀렸을 때 비용: 만료 직전 로트를 쓰고 취소한 사용자에게 30일을 더 주는 소액 관대함. 반대로 "새 로트 1년"은 만료 우회가 되고, "원 로트만 되돌림"은 만료된 몫이 증발한다.
- 만료 작업: 하루 한 번, `expires_at < now AND remaining_amount > 0`인 로트를 id keyset으로 훑어 회원별로 락 → 원장 `EXPIRE`(ref = `POINT_LOT`, lotId) → `remaining_amount = 0`, `balance` 감소. ADR 0003대로 작업 메서드에 `@Transactional`을 붙이지 않고 건별 트랜잭션.
- 후기 적립금: 텍스트 100P, 사진 포함 500P(합산 아님, 사진 후기는 500P). 로트 만료 1년. ref = `(REVIEW, reviewId)`. 후기 삭제 시 회수하지 않는다(사용자 결정). 수정으로 사진을 뒤늦게 붙여도 **추가 적립하지 않는다**(Claude 결정: 적립은 작성 시점 한 번. 틀렸을 때 비용: 사진을 나중에 올린 사용자가 400P를 못 받는 불만. 반대 방향은 "글만 쓰고 적립 → 사진 추가 → 추가 적립 → 사진 삭제"가 열린다).

### 5-3. 사용 검증 (Claude 결정)

주문 생성 요청의 `pointsToUse`는 다음을 모두 만족해야 한다. 아니면 `POINT_INVALID_AMOUNT`/`POINT_EXCEEDS_LIMIT`/`POINT_INSUFFICIENT`.
- 0 이상, 10의 배수.
- 0보다 크면 잔액(락 후 읽은 `balance`) ≥ 1,000 이고 `pointsToUse` ≤ 잔액.
- `pointsToUse` ≤ `floor(쿠폰 적용 후 상품 금액 × 30%)`를 10P 단위로 내린 값.
- 쿠폰 적용 후 상품 금액이 0이면 포인트를 쓸 수 없다.

## 6. 계산 예시 (숫자로 고정)

주문: 판매자 B의 b1 = 30,000원 × 1, 판매자 C의 c1 = 20,000원 × 2 = 40,000원. `itemsAmount` = 70,000 ≥ 50,000 → 무료배송(플랫폼 부담, 판매자 주문당 3,000원).

1. 상품 쿠폰: b1에 플랫폼 정액 2,000원, c1에 판매자 정률 10%(최대 5,000) → `floor(40,000 × 0.10)` = 4,000. 상품 쿠폰 합 6,000. 항목 금액: b1 28,000, c1 36,000, 합 64,000.
2. 장바구니 쿠폰(플랫폼, 10%, 최대 5,000, 최소 50,000): 대상 64,000 ≥ 50,000, `floor(6,400)` → 5,000 상한. 배분: b1 = `floor(5,000 × 28,000 / 64,000)` = 2,187, c1 = 5,000 − 2,187 = 2,813(마지막 항목에 나머지). 항목 금액: b1 25,813, c1 33,187, 합 59,000.
3. 포인트: 잔액 20,000P. 상한 = `floor(59,000 × 30%)` = 17,700 → 10P 단위 17,700. 사용 10,000P. 배분: b1 = `floor(10,000 × 25,813 / 59,000)` = 4,375, c1 = 5,625. `paidAmount`: b1 21,438, c1 27,562, 합 49,000.
4. PG 결제 금액 = 49,000 + 구매자 배송비 0 = **49,000원**. 주문 저장값: `itemsAmount` 70,000, `productCouponDiscount` 6,000, `cartCouponDiscount` 5,000, `pointsUsed` 10,000, `totalAmount` 49,000.

구매 확정 적립: B 확정 시 `floor(21,438 × 1%)` = 214P, C 확정 시 `floor(27,562 × 1%)` = 275P.

구매자가 c1 취소(배송 전):
- 남은 `itemsAmount` 30,000 < 50,000이고 이 취소로 처음 깨짐 → 기본 배송비 3,000원 차감(C2 5장 그대로).
- PG 환불 = `paidAmount` 27,562 − 3,000 = **24,562원**. 포인트 되돌림 **5,625P**. c1 상품 쿠폰(판매자 발급) 만료 전이면 `AVAILABLE`로. 장바구니 쿠폰은 돌려주지 않고 b1의 배분 2,187원도 회수하지 않는다(남은 금액 28,000 < 최소 50,000이지만 플랫폼 부담).
- 불변식: 결제 49,000 − 누적 환불 24,562 = 24,438 = 남은 `paidAmount` 21,438 + 회수 배송비 3,000. ✓
- 정산 기준(C4가 쓸 값): B = 30,000(플랫폼 쿠폰·장바구니 쿠폰·포인트는 영향 없음) + 배송비 3,000. C = 0.
- 만약 c1이 취소되지 않고 확정됐다면 C의 정산 기준 = 40,000 − 4,000(판매자 부담 쿠폰) = 36,000 + 배송비 3,000.

이어서 b1도 취소(마지막 취소):
- PG 환불 = 21,438 + 앞서 차감한 3,000 반환 = **24,438원**. 누적 환불 49,000 = 결제 금액 → 결제 `CANCELED`. 포인트 되돌림 4,375P → 누적 10,000P = 사용액. 장바구니 쿠폰과 b1 상품 쿠폰도 만료 전이면 되돌린다.

차감이 현금 환불보다 클 때(Claude 결정): 어떤 항목의 `paidAmount`가 3,000원보다 작으면(대부분 포인트로 낸 경우) 차감은 C2 규칙대로 **현금 환불액(`paidAmount` + 배송비 환불)까지만** 깎고 포인트에서는 깎지 않는다. 틀렸을 때 비용: 플랫폼이 3,000원 미만을 회수하는 드문 경우. 반대로 포인트까지 깎으면 C2 불변식과 포인트 되돌림 합계 규칙("배분액 그대로")이 둘 다 복잡해진다.

## 7. 주문 생성·결제·취소 흐름의 변경

### 7-1. 주문 생성 (`OrderService.place`, 한 트랜잭션)

1. 요청: 항목 + 배송지 + `productCoupons[{orderLine, couponId}]` + `cartCouponId?` + `pointsToUse` + `expectedTotalAmount`(클라이언트가 본 PG 결제 금액. C2의 가격 확인을 확장. 서버 계산과 다르면 `ORDER_PRICE_CHANGED`).
2. 잠금 순서(데드락 방지): **`member_point` 행 → 쿠폰 조건부 UPDATE → SKU 조건부 UPDATE(SKU ID 오름차순)**. 주문 행은 이 시점에 새로 INSERT되므로 잠그지 않는다.
3. 쿠폰 검증(benefit): 본인 소유·`AVAILABLE`·만료 전·대상 일치·최소 금액 충족. 한 항목에 상품 쿠폰 1장, 주문에 장바구니 쿠폰 1장. 같은 쿠폰 ID 중복 지정은 `ORDER_INVALID`.
4. `DiscountAllocator`로 항목별 할인·포인트 배분·`paidAmount`·`totalAmount` 계산 → `Order.create`.
5. benefit이 쿠폰을 `USED`로, 포인트를 FIFO로 차감(원장 `USE`). 어느 하나라도 실패하면 전부 롤백(재고 포함).
6. `Payment` `READY`(금액 = `totalAmount`). PG 승인 금액 검증은 C2 그대로 `totalAmount`와 비교한다. `totalAmount`가 0원이면(쿠폰·포인트로 전액) **PG 없이 즉시 승인**한다(Claude 결정: `Payment`를 `paymentKey` 없이 `APPROVED`로 만들고 `markPaid`. 승인 API는 `PAYMENT_AMOUNT_MISMATCH` 대신 즉시 결과 반환. 틀렸을 때 비용: 0원 결제 경로가 PG 경로와 분기되어 테스트 하나가 더 필요하다. 반대로 PG에 0원을 보내면 토스가 거절한다).

### 7-2. 결제 실패·만료 (C2 7장, `OrderExpiryService`·`closeUnpaid`·`voidClosedOrder`)

주문이 `PENDING_PAYMENT`에서 `CANCELED`로 닫히는 모든 경로(만료 작업, 승인 거절, 금액 불일치 void)에서 재고 복구와 **같은 트랜잭션**에 `BenefitReverter.revertAll(orderId)`를 넣는다: 쿠폰 전부 `AVAILABLE`(만료 전이면), 포인트 `pointsUsed` 전액 되돌림(원장 `REFUND`, ref `(ORDER, orderId)` — UNIQUE라 두 번 실행돼도 한 번만). 결제 전이므로 쿠폰 만료 여부와 무관하게 돌려주는 것이 맞지만, 규칙을 하나로 두기 위해 "만료 전이면"을 그대로 둔다(15분 안에 만료될 쿠폰은 어차피 쓸 수 없었다).

### 7-3. 항목 취소 (`OrderCancelService`, tx1 → PG → tx2)

- `RefundPlan`에 `pointsRefund`(= 항목 `pointsAllocated`)와 `productCouponReturn`(쿠폰 ID 또는 null)을 더한다. `itemAmount` 자리에 **`paidAmount`** 를 쓴다(`refundAmount = paidAmount + shippingFeeRefund − shippingFeeDeducted + shippingFeeReturned`). PG 부분 취소 금액·멱등 키(`refund-{itemId}-{금액}`)는 이 `refundAmount`다.
- tx2(`finishCancel`)에서 PG 환불이 확인되면 재고 복구·판매량 감소와 **같은 트랜잭션**에서 `BenefitReverter.revertItem(orderId, itemId, pointsRefund, productCouponId, lastItem)`을 호출한다. 마지막 항목(`remainingAfter == 0`)이면 장바구니 쿠폰도 되돌린다. 포인트 원장 ref `(ORDER_ITEM, itemId)` UNIQUE로 멱등.
- `revertCancel`(PG 실패로 되돌림) 경로에서는 benefit을 건드리지 않는다(아직 아무것도 되돌리지 않았으므로).
- `RefundPlan`의 환불 합계 불변식을 확장한다: (결제 금액 − 누적 환불액) = 남은 항목 `paidAmount` 합 + 남은 구매자 배송비 + 회수 배송비. 그리고 누적 되돌린 포인트 ≤ `pointsUsed`, 마지막 취소 뒤에는 정확히 같다.

### 7-4. 구매 확정

`confirmPurchase`/`autoConfirm` 트랜잭션 안에서 `PointEarner.earnForPurchase(memberId, sellerOrderId, Σ paidAmount)`. 적립은 판매자 주문 단위이므로 판매자가 둘이면 두 번 적립된다(각각 다른 ref).

## 8. 후기 (사용자 결정)

- 대상: **구매 확정된 주문 항목**만(판매자 주문 `CONFIRMED`이고 항목 `ORDERED`). 항목당 한 개(`UNIQUE(order_item_id)`).
- 내용: 별점 1~5, 본문 10자 이상, 사진 최대 3장(file 모듈 재사용. 본인이 올린 이미지만 — catalog의 `ImageFileReader`와 같은 포트를 review에 둔다).
- 적립: 텍스트 100P, 사진 포함 500P. 삭제해도 회수하지 않는다.
- 작성자가 수정·소프트 삭제. 판매자 답글 하나.
- 상품에 평균 별점과 후기 수를 비정규화해 쓰기·삭제 때 갱신한다.

### 8-1. 세부 (Claude 결정)

- 작성 기한: 구매 확정 후 **90일**(설정값). 틀렸을 때 비용: 설정값 하나.
- 삭제한 항목에 다시 쓸 수 없다(`UNIQUE(order_item_id)`가 소프트 삭제 행에도 걸린다). 의도한 제한이다: 적립금을 이미 받았으므로 "삭제 → 재작성 → 재적립"을 막는다.
- 평균 별점은 `product.rating_sum`(정수 합)과 `product.review_count`로 계산한다(`avg = rating_sum / review_count`, 소수 첫째 자리 반올림은 응답에서). 평균을 직접 저장하면 수정·삭제마다 부동소수 오차가 쌓인다. 갱신은 원자적 UPDATE(`review_count = review_count + 1, rating_sum = rating_sum + ?`; 수정은 별점 차이만, 삭제는 `−1, −별점`)로 후기 트랜잭션 안에서 한다.
- 판매자 답글은 review 테이블의 컬럼(`reply_content`, `replied_at`)이다. 답글 하나이므로 테이블을 나누지 않는다. 수정은 덮어쓰기(PATCH, 멱등).
- 후기 목록: 상품별 최신순(`?page&size`). 작성자 표시는 마스킹한 이메일/닉네임(member 조회 포트). 판매자는 자기 상품 후기 목록을 본다.
- 작성 시 주문 항목 소유·확정 여부는 order application 조회 포트(`OrderItemReader.findConfirmedItem(memberId, orderItemId)`)로 확인한다. 결과에 `productId`, `confirmedAt`, `sellerId`를 담는다.

## 9. 좋아요 (사용자 결정 + Claude 결정)

- 상품 좋아요·취소, 내 좋아요 목록, 상품 `like_count`, 상품 목록 좋아요순 정렬.
- API는 토글 대신 **`PUT /like`(좋아요)와 `DELETE /like`(취소)** 로 둔다(Claude 결정). 둘 다 멱등이라 `Idempotency-Key`가 필요 없고 재시도가 안전하다. 토글은 재시도하면 반대로 뒤집힌다.
- 행 모델: `product_like(product_id, member_id, liked BIT)`, `UNIQUE(product_id, member_id)`. 소프트 딜리트(`status`) 대신 `liked` 플래그를 도메인 상태로 쓴다. `status = DELETED`로 하면 `@SQLRestriction` 때문에 다시 좋아요할 때 UNIQUE에 걸리기 때문이다(CLAUDE.md "도메인 상태는 `status`가 아닌 이름" 규칙에 맞춘다).
- 수 정합성: 좋아요 = `UPDATE product_like SET liked = 1 WHERE product_id = ? AND member_id = ? AND liked = 0`; 0행이고 행이 없으면 INSERT(동시 INSERT는 UNIQUE 위반 → 다시 UPDATE 시도). 취소 = `SET liked = 0 WHERE ... AND liked = 1`. **실제로 바뀐 행 수(0 또는 1)만큼** `UPDATE product SET like_count = like_count ± 1`. 같은 트랜잭션. 이렇게 하면 같은 회원이 동시에 10번 눌러도 수는 1만 변한다.
- 상품 삭제·판매 종료돼도 좋아요 행은 남기고 목록에서 상품 상태를 표시한다.
- 좋아요순 정렬은 `like_count DESC, id DESC`. 인덱스는 추가하지 않는다(3-2에서 측정 후).

## 10. 데이터 모델 (Flyway V17~, 빌더가 컬럼 세부 확정)

PK/UNIQUE 외 인덱스는 두지 않는다. 모든 테이블은 `BaseEntity` 컬럼을 가진다.

- `product` 변경: `like_count BIGINT NOT NULL DEFAULT 0`, `review_count BIGINT NOT NULL DEFAULT 0`, `rating_sum BIGINT NOT NULL DEFAULT 0`.
- `coupon_policy`: `issuer_type`(PLATFORM/SELLER), `seller_id NULL`, `coupon_type`(PRODUCT/CART), `target_type`(ALL_PRODUCTS/SELLER/PRODUCT/NONE), `target_product_id NULL`, `discount_type`(FIXED/RATE), `discount_value`(원 또는 bp), `max_discount NULL`, `min_order_amount NULL`, `total_quantity NULL`, `issued_count`, `validity_days NULL`, `valid_from`, `valid_until`, `policy_status`(ACTIVE/ENDED), `name`.
- `coupon`: `policy_id`, `member_id`, `coupon_status`, `used_order_id NULL`, `issued_at`, `expires_at`, `used_at NULL`. 정책 값(유형·할인) 스냅샷은 두지 않는다 — 정책이 불변이므로 조인(ID 조회)으로 읽는다. `UNIQUE(policy_id, member_id)`.
- `member_point`: `member_id` `UNIQUE`, `balance`.
- `point_lot`: `member_id`, `amount`, `remaining_amount`, `source_type`, `source_id`, `expires_at`.
- `point_transaction`: `member_id`, `transaction_type`, `amount`(부호), `balance_after`, `ref_type`, `ref_id`, `UNIQUE(ref_type, ref_id)`.
- `point_lot_usage`: `transaction_id`, `lot_id`, `amount`.
- `orders` 추가: `product_coupon_discount`, `cart_coupon_id NULL`, `cart_coupon_discount`, `points_used`, `points_refunded`.
- `order_item` 추가: `product_coupon_id NULL`, `product_coupon_discount`, `product_coupon_issuer NULL`(PLATFORM/SELLER — C4 정산이 판매자 부담분을 가르는 열), `cart_coupon_allocated`, `points_allocated`, `paid_amount`, `points_refunded`.
- `review`: `order_item_id` `UNIQUE`, `product_id`, `member_id`, `seller_id`, `rating`, `content`, `reply_content NULL`, `replied_at NULL`, `reward_points`. `review_image`: `review_id`, `file_id`, `display_order`(같은 애그리거트, `@OneToMany` 허용).
- `product_like`: `product_id`, `member_id`, `liked BIT`, `UNIQUE(product_id, member_id)`.

인덱스가 필요해 보이지만 일부러 두지 않는 조회(3-2 측정 후보로 표시): `point_lot`·`point_transaction`의 `member_id` 조회(내 포인트 내역, FIFO 소비, 만료 작업), `coupon.member_id`(내 쿠폰), `review.product_id`(상품 후기 목록), `product_like.member_id`(내 좋아요). 지금은 풀스캔을 받아들이고 부하 측정에서 수치로 근거를 만든다.

## 11. API (SDD, `docs/api/openapi.yaml` 먼저)

사용자
- `GET /api/v1/coupons/policies` 다운로드 가능한 정책 목록(상품 상세용 `?productId=`), `POST /api/v1/coupons/policies/{policyId}/claim`(Idempotency-Key), `GET /api/v1/coupons/me?status=`
- `GET /api/v1/points/me`(잔액, 30일 내 만료 예정), `GET /api/v1/points/me/transactions`
- `POST /api/v1/orders` 요청 확장: `productCoupons`, `cartCouponId`, `pointsToUse`, `expectedTotalAmount`. 응답·상세에 할인·포인트·`paidAmount` 항목별 노출. `POST /api/v1/orders/quote`(Claude 결정: 쿠폰·포인트 조합의 금액 미리보기. 상태를 바꾸지 않으므로 Idempotency-Key 없음)
- `POST /api/v1/orders/{orderId}/items/{orderItemId}/reviews`(Idempotency-Key), `PATCH /api/v1/reviews/{reviewId}`, `DELETE /api/v1/reviews/{reviewId}`, `GET /api/v1/products/{productId}/reviews`, `GET /api/v1/reviews/me`
- `PUT /api/v1/products/{productId}/like`, `DELETE /api/v1/products/{productId}/like`, `GET /api/v1/products/me/likes`. `GET /api/v1/products` 정렬에 `LIKES` 추가, 응답에 `likeCount`, `reviewCount`, `averageRating`, 로그인 시 `liked`.

판매자
- `POST /api/v1/seller/coupons/policies`(Idempotency-Key), `GET /api/v1/seller/coupons/policies`, `PATCH /api/v1/seller/coupons/policies/{policyId}/end`
- `GET /api/v1/seller/reviews`, `PATCH /api/v1/seller/reviews/{reviewId}/reply`(멱등 덮어쓰기)
- 판매자 주문 상세에 항목별 `productCouponDiscount`·`productCouponIssuer` 노출(정산 미리보기용).

관리자
- `POST /api/v1/admin/coupons/policies`(Idempotency-Key), `GET`, `PATCH .../end`
- `GET /api/v1/admin/members/{memberId}/points`(문제 확인용)

## 12. 동시성과 멱등성

- POST(쿠폰 발급·정책 생성·후기 작성·주문 생성)는 C2 8장의 `Idempotency-Key` 필터를 그대로 탄다. 그 아래 마지막 방어선: 쿠폰 발급 `UNIQUE(policy_id, member_id)`, 후기 `UNIQUE(order_item_id)`, 포인트 원장 `UNIQUE(ref_type, ref_id)`.
- PATCH(후기 수정·답글·정책 종료)와 PUT/DELETE(좋아요)는 멱등. 이미 목표 상태면 성공.
- 락 순서 고정: 주문 생성 `member_point → coupon → sku`; 취소·만료·확정 `orders(FOR UPDATE) → member_point → coupon`. 주문 생성은 `orders` 행을 잠그지 않으므로 두 순서가 순환하지 않는다. 후기 적립은 `member_point`만, 좋아요는 `product_like → product`만 잠근다.
- 쿠폰 수량·사용 상태·좋아요 플래그는 전부 **조건부 UPDATE의 영향 행 수**로 판정한다(재고와 같은 방식). 비관적 락·Redis는 쓰지 않는다(Phase 규율).
- 스케줄러(포인트 만료)는 ADR 0003대로 건별 트랜잭션 + keyset 순회.

## 13. 에러 코드 (ErrorCode enum과 OpenAPI 일치)

| 코드 | HTTP | 뜻 |
|---|---|---|
| `COUPON_POLICY_INVALID` | 400 | 정책 값 오류(정률인데 최대 할인 없음, 기간 역전 등) |
| `COUPON_POLICY_NOT_FOUND` | 404 | |
| `COUPON_POLICY_ENDED` | 409 | 종료·기간 밖 정책 발급 시도 |
| `COUPON_SOLD_OUT` | 409 | 총 수량 소진 |
| `COUPON_ALREADY_ISSUED` | 409 | 1인 1장 초과 |
| `COUPON_NOT_FOUND` | 404 | 내 쿠폰 아님 포함 |
| `COUPON_NOT_AVAILABLE` | 409 | 사용됨·만료됨(동시 사용 경합 포함) |
| `COUPON_NOT_APPLICABLE` | 400 | 대상 상품·종류 불일치, 한 항목에 두 장 |
| `COUPON_MIN_AMOUNT_NOT_MET` | 400 | 최소 주문 금액 미달 |
| `POINT_INVALID_AMOUNT` | 400 | 10P 단위 아님, 음수 |
| `POINT_EXCEEDS_LIMIT` | 400 | 30% 상한 초과, 1,000P 미만 잔액에서 사용 |
| `POINT_INSUFFICIENT` | 409 | 잔액 부족(락 후 판정) |
| `REVIEW_INVALID` | 400 | 별점 범위, 본문 길이, 사진 수·소유 |
| `REVIEW_NOT_ELIGIBLE` | 409 | 미확정·취소 항목, 기한 경과 |
| `REVIEW_ALREADY_WRITTEN` | 409 | 항목당 한 개(삭제 후 재작성 포함) |
| `REVIEW_NOT_FOUND` | 404 | 남의 후기 포함 |

좋아요는 전용 코드가 없다(`PRODUCT_NOT_FOUND` 재사용). `ORDER_PRICE_CHANGED`는 `expectedTotalAmount` 불일치에 재사용한다.

## 14. 증명할 테스트 (완료 기준)

- 6장 예시 숫자 그대로: 배분·`paidAmount`·PG 금액 49,000, c1 취소 환불 24,562·포인트 5,625, b1 취소 환불 24,438·결제 `CANCELED`·포인트 누적 10,000, 적립 214·275.
- 배분 나머지: 3개 항목 10,000원 할인을 3,333/3,333/3,334로 나누고 합이 정확히 10,000.
- 불변식(property 식 랜덤 조합): 항목 수·금액·쿠폰·포인트를 무작위로 만들어 모든 항목을 순서 무작위로 취소했을 때 누적 환불 = 결제 금액, 누적 포인트 되돌림 = 사용 포인트, 쿠폰 전부 `AVAILABLE`(만료 전), `member_point.balance` = Σ 로트 잔량.
- 같은 쿠폰으로 두 주문 동시 생성 → 하나만 성공, 실패한 주문은 재고·포인트까지 롤백.
- 총 수량 1인 정책에 동시 발급 10건 → 1건 성공, `issued_count = 1`.
- 포인트 동시 사용(잔액 1,000P, 두 주문이 각 1,000P) → 하나만 성공, 잔액 0.
- 결제 만료(15분)·승인 거절·금액 불일치 void 각각에서 쿠폰·포인트 되돌림. 같은 주문에 되돌림이 두 번 실행돼도 원장 한 건.
- 0원 결제 주문 즉시 승인.
- FIFO: 만료일이 다른 로트 셋에서 사용 후 가장 빠른 만료 로트부터 비고, 취소하면 그 로트로 돌아옴. 만료된 로트 몫은 새 30일 로트.
- 구매 확정 중복(사용자 확정과 자동 확정 동시) → 적립 한 번.
- 후기: 미확정 항목 거절, 같은 항목 두 번 거절(Idempotency-Key 없이도), 삭제 후 재작성 거절, 사진 후기 500P 한 번, 수정·삭제 후 `rating_sum`/`review_count`가 실제 행과 일치.
- 좋아요: 같은 회원이 PUT 20회 동시 → `like_count` 1, 서로 다른 회원 100명 동시 → 100, DELETE 후 0, 재PUT 1.
- 무료배송 기준이 할인 전 `itemsAmount`임을 고정하는 테스트(쿠폰으로 결제액 4만 원이 돼도 무료배송).
- `ArchitectureTest`: `benefit`·`review`가 다른 모듈 domain을 import하지 않음, order → benefit 호출은 포트 경유.

## 15. 빌더에게 맡기는 것

테이블 컬럼 세부(길이·NULL), `DiscountAllocator`·`RefundPlan` 시그니처, `BenefitApplier`/`BenefitReverter`/`PointEarner` 포트의 DTO 모양, 포인트 만료 작업 주기(`kairos.jobs.point-expiry-delay`), 설정 키 이름(`kairos.benefit.*`: 적립률 1%, 최소 사용 잔액 1,000, 사용 단위 10, 상한 30%, 적립 만료 1년, 환불 로트 30일, 후기 적립 100/500, 후기 기한 90일), 후기 작성자 마스킹 규칙, OpenAPI 스키마 세부, ADR 0004 본문.
