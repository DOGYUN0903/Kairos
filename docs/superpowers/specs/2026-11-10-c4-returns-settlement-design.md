# C4 반품·정산 설계

## 1. 의도

C2 구매, C3 혜택 위에 **배송 후 반품**과 **판매자 정산**을 얹는다. 둘 다 돈이다. 반품은 "환불액 + 되돌린 포인트·쿠폰 + 배송비 회수"가 원래 낸 것과 어긋나면 실패이고, 정산은 "판매자에게 주는 돈 = 상품 매출 − 판매자 부담 할인 − 수수료 + 배송비 − 판매자 귀책 반품 배송비 ± 조정"이 **원 단위로 재현 가능**하지 않으면 실패다. 플랫폼이 부담한 쿠폰·포인트·무료배송이 판매자 몫을 깎으면 기능이 돌아도 실패다.

로드맵(`2026-11-09-commerce-roadmap-design.md`)의 C4 행은 "판매자별 매출 집계, 수수료, 정산 내역"이다. 반품은 정산 금액을 바꾸는 마지막 입력이라 C4에 함께 넣는다(사용자 결정).

표기: **사용자 결정**(확정, 바꾸려면 사용자 승인) / **Claude 결정**(작성자 판단, 빌더·리뷰에서 반박 가능. "틀렸을 때 비용"을 적는다).

## 2. 목표와 비목표

- 목표: 항목 단위 반품(요청 → 판매자 승인/거절 → 수거 확인 → 환불), 반품 사유별 배송비 부담, 판매자 미응답 자동 승인, 반품 중 구매 확정 보류, 주간 정산 생성(판매자·기간당 하나), 수수료 10%, 정산 명세(판매자 주문별 계산 내역), 관리자 정산 확정·지급 처리·조정.
- 비목표: 교환, 반품 택배 접수(송장 발급)·수거 추적, 실제 계좌 이체·세금계산서, 판매자별 수수료율 차등, 정산 주기 변경(월 정산 등), 반품 거절 뒤 재요청·분쟁 중재 UI, 구매 확정 뒤 반품(관리자 조정으로 CS가 처리), **Spring Batch**(사용자 결정: C4는 기존 스케줄러 + keyset 패턴만. Spring Batch는 Phase 3-2에서 측정 근거가 있을 때만 — §8-9).
- 안티 목표(실패로 보는 것): 같은 항목이 두 번 환불됨, 반품 배송비와 C2 무료배송 깨짐 차감이 겹쳐 구매자에게 6,000원 넘게 받음, 반품이 열린 판매자 주문이 자동 확정돼 정산에 들어감, 같은 판매자 주문이 두 정산에 들어감, 정산 생성 뒤 금액이 바뀜, 플랫폼 쿠폰·포인트·무료배송비가 판매자 정산에서 빠짐.

## 3. 모듈 구성 (Claude 결정)

- 반품은 **`order` 모듈 안**에 둔다. 반품은 주문 항목의 상태 전이(`ORDERED → … → RETURNED`)이고 환불 계산·PG 부분 취소·benefit 되돌림이 C2 취소와 같은 코드 경로를 탄다. 별도 모듈로 떼면 주문 행 락 아래에서 항목 상태를 바꾸는 트랜잭션을 모듈 경계 너머로 열어야 한다. 틀렸을 때 비용: `order`가 더 커진다. MSA 분리 시 반품은 주문 서비스의 일부로 간다(교환·분쟁이 생기면 그때 분리 검토).
- 정산은 **새 모듈 `settlement`**. 미래의 "정산 서비스" 경계이고, 주문을 읽기만 하고 쓰지 않는다. `settlement.application`에 포트 `ConfirmedSellerOrderReader`를 두고 `settlement.infrastructure`가 `order.application`의 조회 서비스를 호출해 구현한다(CLAUDE.md 모듈 규칙). 포트 DTO는 정산 계산에 필요한 숫자만 담는다(§8-2). 판매자 존재·상태 확인은 `seller.application` 조회 포트.
- 정산은 주문 테이블에 **쓰지 않는다**. "이 판매자 주문이 정산됐는가"는 `settlement_line.seller_order_id UNIQUE`로 정산 쪽에서만 안다.
- ADR: **ADR 0005**를 남긴다(§15). 내용은 (1) 반품 환불도 ADR 0003의 "tx1 → PG → tx2" 패턴과 ADR 0004 락 순서를 그대로 쓴다, (2) 정산은 생성 시점 스냅샷이며 불변, 보정은 조정 행으로만 한다, (3) 정산 생성이 `order` 데이터를 포트로 읽되 판매자별 한 트랜잭션으로 쓴다.

## 4. 반품 규칙 (사용자 결정)

- 반품은 **`DELIVERED` 판매자 주문의 `ORDERED` 항목**만, **배송 완료 후 7일 이내**, **구매 확정 전**에 요청할 수 있다. 항목 단위 부분 반품(C2 부분 취소와 같은 단위).
- 구매자가 직접 구매 확정하면 그 판매자 주문은 더 반품할 수 없다(확정 화면에서 경고). 자동 구매 확정은 배송 완료 7일 뒤.
- 반품 요청이 **열려 있는 판매자 주문은 자동 확정하지 않는다**. 반품이 끝나면(완료·거절) 다음 주기에 확정한다.
- 사유: `CHANGE_OF_MIND`(단순 변심), `DEFECTIVE`(불량), `WRONG_DELIVERY`(오배송).
- 반품 배송비:
  - 단순 변심 → **구매자 부담**. 편도 3,000원을 환불액에서 뺀다. 원래 주문이 **무료배송**이었으면 왕복 6,000원(발송 3,000 + 회수 3,000).
  - 불량·오배송 → **판매자 부담**. 환불액에서 빼지 않고, 판매자 정산에서 3,000원을 뺀다.
- 흐름: 구매자 요청 → 판매자 승인/거절 → 판매자 수거 확인 → 환불(PG 부분 취소는 트랜잭션 밖, C2 취소와 같은 2-트랜잭션 패턴. 현금 환불 0원이면 PG 호출 없음).
- 판매자가 **3일** 동안 승인·거절하지 않으면 자동 승인.
- 쿠폰·포인트는 C3 규칙으로 되돌린다: 상품 쿠폰은 만료 전이면 반환, 항목 배분 포인트 전액 반환, 장바구니 쿠폰은 주문의 마지막 항목일 때만 반환.

### 4-1. 반품 세부 (Claude 결정)

- **반품 기한**은 `kairos.order.return-window`(기본 7d)로 두고 자동 확정 기간 `auto-confirm-after`(현재 설정값 **이미 7d**, 변경 없음. 작업 주기 `jobs.auto-confirm-delay`는 1h)와 같게 유지한다. 기한 판정은 `deliveredAt + return-window ≥ now`(KST). 기한 안에 요청만 하면 승인·수거는 기한 뒤라도 진행된다. 틀렸을 때 비용: 두 값을 다르게 바꾸면 "확정 전 7일"과 "반품 가능 7일"이 어긋나 반품도 확정도 안 되는 틈이 생기므로, 기동 시 두 값이 다르면 경고 로그를 남긴다.
- **수동 확정도 반품이 열려 있으면 막는다**(`SELLER_ORDER_HAS_OPEN_RETURN`, 409). 반품을 걸어 둔 채 확정하면 환불과 적립이 동시에 일어난다. 틀렸을 때 비용: 구매자가 반품 요청을 철회해야 확정할 수 있다 → 구매자 철회 API를 둔다(§7).
- **거절은 그 항목에 대해 최종**이다. 같은 항목 재요청은 `RETURN_ALREADY_REQUESTED`. 분쟁은 관리자가 본다(관리자는 **판매자 주문이 `CONFIRMED`되기 전까지만** 거절된 반품을 다시 승인할 수 있다 — Claude 결정. 확정 뒤는 정산 조정 경로(§8-6). 틀렸을 때 비용: 확정 뒤 분쟁은 구매자 환불 API가 없어 PG 콘솔 수동 환불 + 조정). `return_request.order_item_id UNIQUE`가 마지막 방어선이 된다. 틀렸을 때 비용: 사유를 잘못 고른 구매자가 재요청하지 못하고 CS로 가야 한다. 거절 전까지는 구매자 철회 뒤 재요청이 가능하므로(철회는 행을 `WITHDRAWN`으로 두지 않고 **삭제가 아니라 `return_status = WITHDRAWN`**으로 남기고, 재요청은 같은 행을 `REQUESTED`로 되살린다) 거절만 최종이다.
- 판매자는 사유(부담 주체)를 바꿀 수 없다. "불량 아님"이면 거절하고 관리자가 판단한다. 틀렸을 때 비용: 판매자 귀책 판단이 구매자 신고에 의존해 판매자가 억울할 수 있다. 관리자 조정(§8-6)으로 보상한다.
- 수거 확인에는 기한을 두지 않는다. 승인 뒤 수거 확인이 안 되면 관리자가 대신 수거 확인할 수 있다(`POST /admin/returns/{id}/receive`).
- 재고·판매량(사용자 결정, 빌더 체크 반영): **`CHANGE_OF_MIND` 반품은 수거 확인 시(환불 tx2) SKU 재고를 복구하고 판매량을 줄인다**(정상 상품이 돌아온다). **`DEFECTIVE`/`WRONG_DELIVERY`는 재고를 복구하지 않고** 판매량만 줄인다(재판매 가능 여부는 판매자가 보고 C1 재고 수정으로 올린다). 락 순서는 ADR 0003/0004 그대로 `orders → member_point → coupon → sku → product`.
- 판매자 주문의 **남은 `ORDERED` 항목이 0**이 되면(전부 반품, 또는 반품 + 앞선 취소) 그 판매자 주문은 마지막 반품 확정 트랜잭션에서 **`CONFIRMED`로 닫는다**(`confirmedAt` = 그 시각). 구매자가 확정할 상품이 없고, 판매자는 이미 발송했으므로 배송비 정산 대상이다(사용자 결정 "발송한 판매자 주문의 배송비는 항상 지급"). 적립은 `confirmedPaidAmount = 0`이라 0P. 조회 응답에 `allItemsReturned`(계산값)를 내려 화면이 "구매 확정"과 "전부 반품"을 구분한다. 틀렸을 때 비용: 상태 하나를 두 뜻으로 쓴다. 대안(`SellerOrderStatus.RETURNED` 추가)은 정산 대상 조건·`Order.settle`·조회 필터가 모두 둘을 알아야 해서 더 비싸다.
- 구매 확정 뒤 반품(불량 뒤늦게 발견 등)은 C4 범위 밖이다. CS가 관리자 조정(§8-6)으로 판매자 정산에서 빼고, 구매자 환불은 관리자 환불 API 없이… **C4에서는 구매자에게 돈을 돌려주는 경로를 두지 않는다**(비목표). 틀렸을 때 비용: 확정 뒤 CS 환불은 PG 콘솔에서 수동으로 한다. 빈도가 보이면 C5+에서 관리자 환불 API를 만든다.

## 5. 반품 환불 금액 규칙 (Claude 결정, 사용자 규칙을 숫자로 고정)

계산은 order 도메인 순수 함수 **`ReturnRefundCalculator`** 한 곳에서만 한다(`RefundCalculator`와 같은 원칙, 별도 객체). 결과는 `ReturnRefundPlan(itemId, paidAmount, returnFee, outboundFeeRecovered, shippingFeeRefund, pointsRefund, productCouponReturn, sellerBorneFee)`.

- `refundAmount = paidAmount − returnFee − outboundFeeRecovered + shippingFeeRefund`. PG 부분 취소 금액·멱등 키(`refund-{itemId}-{금액}`, C2와 같은 함수 `refundKey`)는 이 값이다.
- **회수 구간 3,000**(`returnFee`): `CHANGE_OF_MIND`면 구매자 부담으로 환불에서 뺀다. `DEFECTIVE`/`WRONG_DELIVERY`면 0이고 대신 `sellerBorneFee = 3,000`을 기록한다(정산 차감).
- **발송 구간 3,000**(`outboundFeeRecovered`): `CHANGE_OF_MIND`이고 **주문이 무료배송(`freeShippingApplied`)** 이며 **이 주문에서 아직 발송 구간을 회수한 적이 없을 때만** 3,000. 회수한 적 = C2 취소의 `shippingFeeDeducted > 0`인 항목이 있거나, 앞선 반품의 `outboundFeeRecovered > 0`이 있을 때. 즉 **C2 무료배송 깨짐 차감과 반품 발송 구간 회수는 합쳐서 주문당 한 번**이다(사용자 규칙 "왕복 6,000"의 발송 구간은 C2의 3,000 차감과 같은 돈 — 플랫폼이 낸 발송비 회수 — 이므로 둘 다 받으면 이중 청구다). 틀렸을 때 비용: 판매자 주문이 여럿인 주문에서 플랫폼이 발송비를 최대 3,000원 덜 회수한다. 반대로 판매자 주문마다 회수하면 "주문 1건에 차감은 한 번"(C2 §5)과 어긋나고 구매자가 설명하기 어려운 금액을 본다.
- C2 `RefundCalculator`도 같은 판정을 쓰도록 바꾼다: 차감 조건의 `everDeducted == 0`을 `order.outboundFeeRecovered == 0`(취소 차감 + 반품 발송 회수 합)으로 넓힌다. 마지막 취소의 **차감 반환**(`shippingFeeReturned`)은 **취소에서 차감한 금액만** 돌려준다. 반품의 발송 구간 회수는 상품이 실제로 오갔으므로 어떤 경우에도 돌려주지 않는다.
- **구매자가 낸 발송 배송비 환불**(`shippingFeeRefund`): 이 반품 처리 뒤 판매자 주문에 `ORDERED` 항목도 열린 반품(요청·승인·환불 중)도 남지 않고, `shippingFeePayer == BUYER`이며, 사유가 **판매자 귀책**(불량·오배송)일 때만 그 판매자 주문의 `shippingFee`를 돌려준다. 열린 반품은 남은 항목으로 세므로, 같은 판매자 주문의 반품이 여러 건이면 **마지막으로 끝나는 반품**이 배송비를 환불한다(그 반품이 판매자 귀책일 때만). 단순 변심은 돌려주지 않는다(발송은 정상). 이때 그 판매자 주문의 "판매자에게 줄 배송비"는 0이 된다(§8-3). 틀렸을 때 비용: 불량 상품을 받고 발송비까지 물면 CS가 온다. 반대 방향 오류는 3,000원이 판매자 정산에서 빠지는 것인데, 귀책이 판매자이므로 받아들인다.
- 구매자 부담 반품비는 **현금 환불에서만** 뺀다. `returnFee + outboundFeeRecovered > paidAmount`이면 `paidAmount`까지만 받고 나머지는 플랫폼 손실로 둔다(포인트 반환액에서 빼지 않는다). 틀렸을 때 비용: 포인트·쿠폰으로 거의 다 낸 항목을 변심 반품하면 플랫폼이 최대 6,000원을 못 받는다. 포인트에서 빼면 원장·로트 되돌림 규칙(C3)이 복잡해지고 액수가 작다.
- 포인트·쿠폰: `pointsRefund = pointsAllocated` 전액, `productCouponReturn = productCouponId`(만료 전이면 반환), 장바구니 쿠폰은 **주문 전체가 취소·반품으로 비었을 때**(`allItemsClosed`: 모든 항목이 `CANCELED` 또는 `RETURNED`) 반환. 구매 확정 전이므로 **적립 포인트 회수는 없다**.
- 결제 상태(Claude 결정): 부분 반품 뒤 주문이 `COMPLETED`로 닫혀도 결제는 **`PARTIAL_CANCELED`로 남는다**(`CANCELED`는 누적 환불 = 결제 금액일 때만). 구매자 부담 반품비 때문에 전부 반품해도 누적 환불 < 결제 금액이면 `PARTIAL_CANCELED`다(예시 A). 틀렸을 때 비용: "전부 반품인데 결제가 취소 상태가 아님"이 화면에 설명돼야 한다.
- 불변식: `0 ≤ refundAmount`, `order.refundedAmount + refundAmount ≤ order.totalAmount`, 누적 포인트 반환 ≤ `pointsUsed`. C2·C3 돈 불변식을 확장한다: **(결제 금액 − 누적 환불액) = 남은(`ORDERED`·열린 반품) 항목 `paidAmount` 합 + 남은 구매자 배송비 + `order.shippingFeeRecovered`(취소 차감 − 취소 반환 + 반품 발송 회수) + Σ `return_request.return_fee`(완료된 반품의 회수 구간)**. 모든 항목이 닫힌 최종 상태에서는 남은 항목이 0이므로 **누적 환불 = 결제 금액 − Σ `return_fee` − `shippingFeeRecovered` − `remainingBuyerShippingFee`**다. 단순 변심 반품으로만 닫힌 판매자 주문의 구매자 배송비(`remainingBuyerShippingFee`)는 돌려주지 않으므로 이 항으로 남는다. 기존 불변식 테스트(C2 §5, C3 7-3)는 이 식으로 바꾼다. 주문당 발송 구간 회수 합(취소 차감 + 반품 회수 − 취소 반환) ≤ 3,000. `order.shippingFeeRecovered`(C2 컬럼)에 반품의 `outboundFeeRecovered`도 더해 "플랫폼 발송비 회수금"을 한 열로 본다. 반품 회수 구간(`returnFee`)은 다른 성격(플랫폼이 수거 택배비를 내고 회수)이므로 `return_request.return_fee`에만 둔다.

### 5-1. 예시 (숫자로 고정)

예시 A — 무료배송, 변심 반품 두 번. 판매자 S 항목 a1 40,000 + a2 20,000 = 60,000 ≥ 50,000 → 무료배송, 판매자 주문 배송비 3,000(PLATFORM). 쿠폰·포인트 없음, 결제 60,000.
1. a2 변심 반품: `returnFee` 3,000 + `outboundFeeRecovered` 3,000(무료배송, 첫 회수) → 환불 20,000 − 6,000 = **14,000**. `order.shippingFeeRecovered` = 3,000.
2. a1 변심 반품: `returnFee` 3,000, 발송 구간은 이미 회수 → 환불 40,000 − 3,000 = **37,000**. 누적 환불 51,000, 남은 항목 0 → 판매자 주문 `CONFIRMED`(전부 반품), 주문 `COMPLETED`, 적립 0P.
3. 결제는 `PARTIAL_CANCELED`(51,000 < 60,000). 돈: 구매자 60,000 냄, 51,000 받음, 9,000 부담(발송 3,000 + 회수 2회 6,000). 플랫폼은 발송비 3,000을 S에게 주고 3,000 회수, 수거 택배비 6,000을 내고 6,000 회수 → 순부담 0. S 정산: 매출 0, 배송비 3,000(§8-3).

예시 B — 유료배송, 불량 반품. 판매자 T 항목 b1 30,000, 배송비 3,000(BUYER), 결제 33,000.
- b1 불량 반품: `returnFee` 0, `sellerBorneFee` 3,000, 판매자 주문이 비고 구매자 부담 배송비 → `shippingFeeRefund` 3,000 → 환불 **33,000**(결제 전액, 결제 `CANCELED`). 판매자 주문 `CONFIRMED`(전부 반품). T 정산 명세: 매출 0, 배송비 0(구매자에게 돌려줌), 판매자 귀책 반품비 −3,000 → **−3,000**(§8-5 음수 처리).

예시 C — C2 취소 차감 뒤 변심 반품(이중 청구 방지). 판매자 B 30,000 + C 30,000 = 60,000 무료배송. C 항목을 배송 전 구매자 취소 → C2 규칙으로 27,000 환불(3,000 차감). 이후 B 배송 완료 뒤 B 변심 반품: 발송 구간은 이미 회수(`outboundFeeRecovered == 0` 아님) → `returnFee` 3,000만 → 환불 **27,000**. 누적 환불 54,000, 구매자 총부담 6,000(= 왕복). 반대 순서(반품이 먼저 6,000 회수, 뒤에 다른 판매자 주문 취소)도 C2 차감이 건너뛰어 결과는 같다.

예시 D — 포인트·판매자 쿠폰이 있는 항목의 불량 반품. 항목 d1 `lineAmount` 50,000, 판매자 상품 쿠폰 5,000, 장바구니 쿠폰 배분 4,000(플랫폼), 포인트 배분 3,000 → `paidAmount` 38,000. 불량 반품 → 현금 환불 38,000, 포인트 3,000 반환, 판매자 쿠폰 만료 전이면 반환, 장바구니 쿠폰은 마지막 항목일 때만. `sellerBorneFee` 3,000. 이 항목은 정산 매출에서 빠진다(§8-2: `ORDERED` 항목만).

## 6. 상태 (반품)

- `order_item_status`에 추가: `RETURN_REQUESTED`, `RETURN_APPROVED`, `RETURN_REFUNDING`, `RETURNED`. 전이:
  - `ORDERED → RETURN_REQUESTED`(구매자 요청) → `RETURN_APPROVED`(판매자 승인·자동 승인·관리자 승인) → `RETURN_REFUNDING`(수거 확인, 환불 계획 저장 = C2의 `CANCEL_REQUESTED`에 해당) → `RETURNED`(PG 환불 확인, tx2).
  - `RETURN_REQUESTED → ORDERED`(판매자 거절, 구매자 철회). `RETURN_APPROVED → ORDERED`는 없다(승인 뒤 철회·거절 불가. 틀렸을 때 비용: 구매자가 마음을 바꾸면 판매자가 수거 확인을 안 하고 관리자에게 알려야 한다. 관리자 `cancel-return`을 둔다).
  - `RETURN_REFUNDING → RETURN_APPROVED`(PG 실패로 되돌림, C2 `revertCancel`에 해당).
- `return_request.return_status`: `REQUESTED → APPROVED → REFUNDING → COMPLETED`, `REQUESTED → REJECTED`(최종), `REQUESTED → WITHDRAWN`(재요청 시 `REQUESTED`로 복귀), `APPROVED → CANCELED_BY_ADMIN`. 항목 상태와 1:1로 맞춘다(같은 트랜잭션).
- "열린 반품" = 항목이 `RETURN_REQUESTED | RETURN_APPROVED | RETURN_REFUNDING`. `SellerOrder.hasOpenReturn`.
- 판매자 주문: 상태 집합은 그대로(`DELIVERED`에 머문다). 전부 반품되면 `CONFIRMED`(§4-1). `Order.settle`은 그대로(`CONFIRMED`/`CANCELED`만 본다).
- 주문 상태: 변화 없음. 반품만으로 `CANCELED`가 되지는 않는다(판매자 주문이 `CONFIRMED`로 닫히므로 `COMPLETED`).
- 반품 중 다른 항목 취소: 같은 주문의 다른(`PAID`) 판매자 주문 항목 취소는 허용. **열린 반품(`RETURN_REQUESTED`/`RETURN_APPROVED`) 항목은 `RefundCalculator`의 '남은 항목'으로 센다**(Claude 결정: 아직 환불되지 않았고 거절·철회로 `ORDERED`로 돌아올 수 있다. 따라서 그 상태에서 다른 항목의 마지막 취소는 `remainingAfter > 0`이라 차감 반환이 일어나지 않고, 반품이 거절되면 상품이 남아 있어 맞다. 틀렸을 때 비용: 반품이 결국 완료되면 C2 차감 3,000이 돌아오지 않는데, 상품이 실제로 오갔으므로 §5 규칙과 일치한다). C2 `RefundCalculator`의 "다른 항목이 `CANCEL_REQUESTED`면 거절" 조건은 **`RETURN_REFUNDING`도 포함**한다(환불 계획이 서로 영향을 주지 않게 한 번에 하나). 틀렸을 때 비용: 드물게 동시 요청이 409를 받고 다시 시도한다.

## 7. 반품 API (SDD, `docs/api/openapi.yaml` 먼저)

구매자
- `POST /api/v1/orders/{orderId}/items/{orderItemId}/returns`(Idempotency-Key) 본문 `reason`(enum), `detail`(≤500자). 응답 `ReturnRequestResponse`.
- `DELETE /api/v1/returns/{returnId}`(철회, 멱등: 이미 `WITHDRAWN`이면 200). `REQUESTED`일 때만.
- `GET /api/v1/returns/me`, `GET /api/v1/returns/{returnId}`. 주문 상세의 항목에 `returnRequest`(요약) 포함.
- 주문 상세·판매자 주문 상세에 `returnableUntil`(계산값, `deliveredAt + return-window`)과 `allItemsReturned`.

판매자
- `GET /api/v1/seller/returns?status=`
- `PATCH /api/v1/seller/returns/{returnId}/approve`, `PATCH .../reject`(본문 `rejectReason` 필수), `PATCH .../receive`(수거 확인 → 환불 실행). 셋 다 멱등: 이미 목표 상태면 현재 상태를 돌려준다. `receive`는 PG 호출이 있으므로 응답이 `RETURNED` 또는 `RETURN_REFUNDING`(PG 불확실)일 수 있다.

관리자
- `GET /api/v1/admin/returns?status&sellerId&memberId`, `PATCH /api/v1/admin/returns/{returnId}/approve`(거절된 건 포함, 판매자 주문이 `CONFIRMED`면 `RETURN_NOT_ELIGIBLE`), `PATCH .../receive`(판매자 대신 수거 확인), `PATCH .../cancel`(승인 상태에서 구매자 요청으로 중단 → 항목 `ORDERED`, `CANCELED_BY_ADMIN`).

구매 확정(기존 `POST .../confirm`)은 열린 반품이 있으면 `SELLER_ORDER_HAS_OPEN_RETURN`.

## 8. 정산 (사용자 결정)

- 대상: **구매 확정(`CONFIRMED`)된 판매자 주문만**.
- 기간: **월~일 주간**, 확정 시각(`confirmedAt`, KST) 기준. 지급일은 **다음 주 수요일**(= `period_end` + 3일).
- 수수료: **판매자별 수수료율 스냅샷 `seller_order.commission_rate_bp`**(판매자 승인 시 관리자가 정함, 기본 1000bp = 10%, 판매자별로 관리자 수정 가능. 빌더 체크 뒤 사용자 결정 2026-11-10). 플랫폼 전체 설정값은 **두지 않는다**. 기본값이 필요하면 판매자 승인의 기본값(10%)이다. 적용 비율은 정산 라인마다 저장한다.
- 판매자 지급액 = 상품 매출 − 판매자 발급 쿠폰 할인 − 수수료 + 판매자에게 줄 배송비 − 판매자 귀책 반품 배송비 ± 관리자 조정.
- 플랫폼 부담(장바구니 쿠폰, 플랫폼 상품 쿠폰, 포인트, 무료배송비)은 판매자에게서 빼지 않는다.
- 실제 이체는 없다. 관리자가 **지급 완료로 표시**한다.
- 관리자 조정(금액 + 사유)은 **다음 정산**에 붙는다(예: 확정 뒤 CS가 처리한 예외 환불).
- 판매자 화면: 정산 목록, 정산 상세(계산 내역 + 포함된 판매자 주문). 관리자: 생성·확정, 지급 표시, 조정.

### 8-1. 기간·시간대 (Claude 결정)

- 모든 판정은 **KST**(서버 기본 시간대 Asia/Seoul, `LocalDateTime`). `period_start`는 월요일 `DATE`, 기간은 `[period_start 00:00:00, period_start + 7일 00:00:00)`. `payout_date = period_start + 9일`(다음 주 수요일).
- 정산 식별은 `(seller_id, period_start)` **UNIQUE**. 같은 기간을 두 번 생성해도 둘째는 UNIQUE 위반을 잡아 기존 정산을 돌려준다.

### 8-2. 정산 라인과 매출 정의 (Claude 결정)

정산 라인 1개 = 판매자 주문 1개(`settlement_line.seller_order_id` **UNIQUE**, 전체 정산을 통틀어 한 번만). 생성 시 다음 값을 **스냅샷**으로 저장한다(이후 주문 데이터가 바뀌어도 라인은 안 바뀐다).

- `sales_amount` = 그 판매자 주문의 **`ORDERED` 항목 `lineAmount` 합**(할인 전 상품 금액). 취소·반품된 항목은 0.
- `seller_coupon_discount` = `ORDERED` 항목 중 `productCouponIssuer == SELLER`인 `productCouponDiscount` 합.
- `commission_base` = `sales_amount − seller_coupon_discount`. **수수료는 판매자 쿠폰을 뺀 뒤의 금액에 매긴다**(판매자가 실제로 받는 상품 대금 기준. 판매자가 준 할인에 수수료까지 물리면 할인 비용이 110%가 된다). 틀렸을 때 비용: 플랫폼 수수료 수입이 판매자 쿠폰 할인액의 10%만큼 준다(판매자 쿠폰 1만 원당 1,000원). 라인에 `commission_rate_bp`를 저장하므로 기준을 바꿔도 과거 정산은 재현된다.
- `commission` = `floor(commission_base × rate_bp / 10,000)`, **라인마다 버림**. 예: 12,345 × 10% = 1,234.5 → 1,234. 틀렸을 때 비용: 정산 합계 기준으로 한 번 버리는 것과 라인 수만큼(최대 라인 수 − 1원) 차이. 라인별로 두면 "판매자 주문 하나의 정산액"이 그 라인만으로 설명되므로 택한다.
- `shipping_fee_owed` = 판매자 주문 `shippingFee`(발송했으므로 항상) **− 판매자 귀책 반품으로 구매자에게 돌려준 발송 배송비**(§5 `shippingFeeRefund`). 누가 냈는지(구매자/플랫폼)는 영향 없음(C2 §4).
- `return_fee_borne` = 그 판매자 주문 반품들의 `sellerBorneFee` 합(완료된 반품만).
- `line_amount` = `sales_amount − seller_coupon_discount − commission + shipping_fee_owed − return_fee_borne`.
- 수수료율(사용자 결정): 라인의 `commission_rate_bp` = 그 **판매자 주문의 `commission_rate_bp` 스냅샷**(주문 시점에 판매자에서 복사한 값, C2 §3). 판매자의 현재 비율을 다시 읽지 않는다(주문 뒤 관리자가 바꿔도 이미 생긴 주문은 주문 시점 비율). 한 정산 안에 비율이 다른 라인이 섞일 수 있으므로 정산 헤더에는 비율을 두지 않고 라인에만 둔다.

### 8-3. 정산 생성과 멱등 (Claude 결정)

- 작업 `SettlementGenerationService.generate(periodStart)`: `periodStart`는 월요일이어야 하고 `periodEnd ≤ now`여야 한다(`SETTLEMENT_PERIOD_INVALID`). 스케줄은 **매주 월요일 03:00 KST**(`kairos.jobs.settlement-generate-cron: "0 0 3 * * MON"`) 지난주를 생성. 관리자가 같은 API로 재실행할 수 있다(멱등).
- 대상 조회(포트 `ConfirmedSellerOrderReader.findConfirmedBetween(from, to, afterId, limit)`): `seller_order_status = CONFIRMED AND confirmed_at ∈ [from, periodEnd)`, `id > afterId` keyset. **`from = periodStart − late-confirm-lookback`**(`kairos.settlement.late-confirm-lookback: 28d`). 즉 이번 기간뿐 아니라 **지난 4주 안에 확정됐는데 아직 어느 정산에도 안 들어간 판매자 주문**도 이번 정산에 넣는다(늦게 들어온 확정 처리). "아직 안 들어감"은 `settlement_line.seller_order_id` IN 조회로 거른다(정산 쪽 지식). 틀렸을 때 비용: 정산 작업이 4주 넘게 멈췄다가 재개되면 그 전 확정분이 빠진다 → 관리자가 조정으로 넣는다. lookback을 무한으로 두면 확정 판매자 주문 전부를 매주 훑는다(`confirmed_at` 인덱스 없음, 3-2 측정 후보로 표시).
- **생성 순서 엄격화(Claude 결정)**: 생성은 항상 **아직 생성되지 않은 가장 오래된 기간**부터 한다. 작업은 `kairos.settlement.start-date`(§12, 정산 시작 월요일) 이후 기간을 오래된 순으로 훑어 끝난 기간 중 미생성분을 차례로 만든다("생성됨"은 `settlement_run(period_start UNIQUE, generated_at)` 행으로 기록 — 판매자 정산이 0개인 기간도 생성됐음을 남기기 위해). 관리자가 더 뒤 기간을 요청하는데 앞 기간이 비어 있으면 `SETTLEMENT_PERIOD_INVALID`(앞 기간을 먼저 생성). 이렇게 하면 lookback 포함 집합이 실행 순서와 무관하게 결정된다. 틀렸을 때 비용: 한 기간이 막히면 뒤 기간도 멈춘다(의도한 것: 건너뛴 기간의 확정분이 뒤 기간에 섞이는 것보다 낫다).
- **정산 시작 기준(Claude 결정)**: `kairos.settlement.start-date`(월요일 DATE) 이전에 확정된 판매자 주문은 정산 대상이 아니고, lookback 하한도 이 날짜보다 앞으로 가지 않는다(`from = max(periodStart − lookback, start-date)`). 틀렸을 때 비용: 시작일을 잘못 잡으면 그 전 확정분은 조정으로만 넣을 수 있다.
- 확정 시각은 서버 `now()`로 기록되고 생성은 기간이 끝난 뒤 하므로 정상 운영에서 "늦은 확정"은 생기지 않는다. 생기는 경우는 (1) 관리자가 기간이 끝나기 전에 생성하지 못하게 막았으므로 없음, (2) 작업 장애 뒤 재개, (3) 시계 어긋남. 모두 위 규칙으로 다음 정산에 들어간다.
- 판매자별 **한 트랜잭션**: 대상 판매자 주문을 판매자 ID로 묶은 뒤, 판매자마다 `tx.run { settlement INSERT(UNIQUE) → line INSERT(UNIQUE) → 조정 붙이기(§8-6) → 합계 계산 }`. UNIQUE 위반(동시 실행·재실행)은 그 판매자를 건너뛰고 다음 판매자로 간다. 스케줄러는 서비스를 감싸는 `@Transactional`을 붙이지 않는다(ADR 0003). keyset 순회는 `sweepByKeyset`을 `common`으로 옮기거나 settlement에 같은 함수를 둔다(빌더 선택).
- 생성 중 판매자 주문 수가 많으면 한 판매자 트랜잭션이 길어진다. 라인 INSERT는 배치(`saveAll`)로 하고, 3-2에서 측정한다. 대용량 집계·배치 최적화는 로드맵대로 3-2.
- 정지·탈퇴(소프트 딜리트) 판매자도 확정 판매자 주문이 있으면 정산을 **만든다**(Claude 결정: 받을 돈은 판매자 상태와 무관. 지급 여부는 관리자가 `PAID` 처리로 결정). 틀렸을 때 비용: 정지 판매자 정산이 목록에 섞인다(상세에 판매자 상태 표시).
- **정산이 없는 판매자**(이번 기간 확정 0건)는 정산을 만들지 않는다. 단 **붙일 조정이 있으면** 라인 0개짜리 정산을 만든다(조정이 다음 정산으로 미뤄지지 않게).

### 8-4. 불변성과 상태 (Claude 결정)

- `settlement_status`: `GENERATED → CONFIRMED → PAID`. 생성 뒤 라인·합계는 **바꾸지 않는다**. 삭제도 없다(소프트 딜리트 금지 대상에 포함). 틀린 정산은 **조정으로 다음 정산에서 보정**한다.
- `CONFIRMED`(관리자 검토 완료)와 `PAID`(`paid_at`, `paid_memo`) 전이는 PATCH 멱등. `GENERATED → PAID` 직행은 막는다(`SETTLEMENT_INVALID_STATE_TRANSITION`). 틀렸을 때 비용: 관리자가 두 번 눌러야 한다. 검토 단계를 명시적으로 남기는 값이 더 크다.
- 정산 생성 전에 판매자 주문 데이터가 바뀌는 경우(확정 뒤 반품은 없고, 확정 뒤 취소도 없음)는 C4 규칙상 없다. 확정 뒤 CS 예외는 조정으로만.

### 8-5. 음수 정산 (Claude 결정)

`total_amount < 0`(예: 매출 0에 판매자 귀책 반품비·조정만 있는 주)이면 정산은 그대로 만들고 `payable_amount = max(total_amount, 0)`, 부족분(`total_amount − payable_amount`, 음수)을 **생성 트랜잭션 안에서 시스템 조정 `CARRY_OVER`로 다음 정산에 넘긴다**(`settlement_adjustment` 행, `adjustment_type = CARRY_OVER`, `source_settlement_id`). 지급 표시는 `payable_amount` 기준. 틀렸을 때 비용: 판매자가 영영 매출이 없으면 받을 돈이 소멸한다(관리자가 조정 이력에서 본다). 대안(판매자에게 청구)은 이체가 없는 C4에서 구현할 수 없다.

### 8-6. 관리자 조정 (Claude 결정, 사용자 규칙 구체화)

- `POST /api/v1/admin/sellers/{sellerId}/settlement-adjustments`(Idempotency-Key) 본문 `amount`(0 아닌 부호 있는 정수, 원), `reason`(≤500자), 선택 `refSellerOrderId`(근거 주문). `adjustment_type = MANUAL`.
- 생성 시 `settlement_id NULL`. 정산 생성 트랜잭션이 `settlement_id IS NULL`인 조정을 **조건부 UPDATE**로 붙이고, 붙은 행만 합계에 더한다. `MANUAL` 조정은 `created_at < periodEnd`인 것만 붙인다(`SET settlement_id = ? WHERE seller_id = ? AND settlement_id IS NULL AND adjustment_type = 'MANUAL' AND created_at < ?`). `CARRY_OVER`는 `created_at`과 무관하게 다음으로 생성되는 정산에 붙인다(작업이 밀려 여러 기간을 한 번에 따라잡을 때 CARRY_OVER가 한 기간을 건너뛰는 것을 막기 위해서다). 붙은 조정은 바꾸거나 지울 수 없다(정산 불변). 아직 안 붙은 조정은 관리자가 **삭제**(소프트, `status = DELETED`)할 수 있다. 틀렸을 때 비용: 기간 종료 직후·생성 직전에 넣은 조정은 이번 정산에 들어간다(`MANUAL`은 `created_at < periodEnd` 조건으로 막는다. 즉 기간이 끝난 뒤 넣은 조정은 다음 정산).
- 정산 합계: `total_amount = Σ line_amount + Σ adjustment.amount`(MANUAL + CARRY_OVER).

### 8-7. 정산 예시 (숫자로 고정)

판매자 S, 기간 2026-11-16(월)~11-22(일), 지급일 2026-11-25(수).
- 판매자 S의 승인 수수료율 10%(1000bp). 판매자 주문 #1·#2·#3 모두 주문 시점 스냅샷 1000bp. (비교용: 비율 800bp인 판매자 주문이 섞이면 그 라인만 8%.)
- 라인 1 — 판매자 주문 #1(11-17 확정): 항목 50,000 + 30,000, 첫 항목에 **판매자** 쿠폰 5,000, 장바구니 쿠폰 배분 8,000(플랫폼), 포인트 2,000, 무료배송(배송비 3,000 PLATFORM). `sales` 80,000, `seller_coupon` 5,000, `base` 75,000, `commission` 7,500, `shipping_owed` 3,000, `return_borne` 0 → **70,500**. (구매자가 실제 낸 현금은 65,000이지만 정산과 무관.)
- 라인 2 — 판매자 주문 #2(11-20 확정): 항목 20,000은 불량 반품 완료, 항목 15,000만 남아 확정. 배송비 3,000 BUYER(판매자 주문이 비지 않아 환불 없음). `sales` 15,000, `commission` 1,500, `shipping_owed` 3,000, `return_borne` 3,000 → **13,500**.
- 라인 3 — 판매자 주문 #3(11-21, 전부 반품으로 자동 확정, 예시 B): `sales` 0, `shipping_owed` 0(구매자에게 환불), `return_borne` 3,000 → **−3,000**.
- 조정 — 11-19 등록, −10,000 "주문 1001 확정 후 파손 보상(CS 처리)".
- 합계 `total_amount` = 70,500 + 13,500 − 3,000 − 10,000 = **71,000**, `payable_amount` 71,000. 라인 3이 음수여도 합계가 양수이므로 CARRY_OVER 없음.
- 버림 예: `base` 12,345, 1000bp → 수수료 1,234. `base` 9 → 0. `base` 12,345, 850bp → 1,049(1,049.325).
- 비율 혼합 예: 같은 정산에 #4(주문 시점 800bp, `base` 10,000 → 800)가 있으면 라인마다 저장된 비율로 계산하고 헤더는 합만 가진다.

### 8-8. 정확성과 멱등성 (사용자 결정, 최우선)

정산은 속도보다 **정확성과 멱등성**이 먼저다. 기준: **작업이 중간에 멈추거나, 몇 번을 다시 실행하거나, 둘이 동시에 실행돼도 결과(정산 행·라인·금액)는 항상 같다.** 아래 다섯 가지로 보장한다.

1. **입력이 결정적이다.** 기간 `P`에 속하는 판매자 주문 = `seller_order_status = CONFIRMED AND confirmed_at ∈ [period_start 00:00:00 KST, period_start + 7일 00:00:00 KST)`(반열린 구간, 경계 시각 `00:00:00.000000`은 다음 기간). 여기에 §8-3의 "lookback 안에서 확정됐지만 아직 어느 라인에도 없는 판매자 주문"을 더한다. `confirmed_at`은 확정 트랜잭션이 한 번 쓰고 다시 쓰지 않는다(`SellerOrder.confirm`은 `CONFIRMED`면 no-op). 정산이 한 번 생성되면 그 정산의 입력 집합은 **동결**된다: 뒤늦게·소급해 확정된 판매자 주문은 **아직 열려 있는 다음 기간**에 들어가고, 이미 생성된 정산을 절대 바꾸지 않는다.
2. **판매자 단위 = 트랜잭션 단위.** 판매자 하나의 정산 헤더 + 라인 전부 + 조정 연결(`settlement_adjustment.settlement_id` 조건부 UPDATE) + 합계를 **한 트랜잭션**으로 커밋한다. 라인 INSERT가 곧 "이 판매자 주문은 정산됐다"는 포함 표시이며(별도 플래그 없음), `UNIQUE(seller_id, period_start)`와 `settlement_line.seller_order_id UNIQUE`가 재실행·동시 실행의 이중 집계를 DB 수준에서 막는다. 둘 중 하나라도 위반하면 그 판매자 트랜잭션 전체가 롤백된다 → 반쪽 정산은 커밋되지 않는다.
3. **부분 실행 뒤 재실행.** N번째 판매자에서 예외가 나면 그 판매자는 롤백, 앞 판매자들은 이미 커밋. 재실행은 앞 판매자들을 UNIQUE 위반(또는 생성 전 `existsBySellerIdAndPeriodStart` 조회)으로 건너뛰고 남은 판매자만 만든다. 결과는 처음부터 한 번에 끝난 실행과 **같다**. "생성 중" 상태를 두지 않으므로 외부에 반쪽 정산이 보이는 순간이 없다. 작업 전체를 감싸는 트랜잭션은 없다(ADR 0003).
4. **금액은 저장된 스냅샷에서만 계산한다.** 입력 = `seller_order.commission_rate_bp`(주문 시점 스냅샷), `order_item.line_amount(=단가×수량 스냅샷)`, `order_item_status`, `product_coupon_discount`·`product_coupon_issuer`, `seller_order.shipping_fee`·`shipping_fee_payer`, `return_request.seller_borne_fee`·`shipping_fee_refund`. 상품·쿠폰 정책·판매자 현재 값은 읽지 않는다. 모든 연산은 **원 단위 정수(Long)**, 나눗셈은 수수료 한 곳뿐이며 **라인별 버림**(§8-2). 계산은 settlement 도메인 순수 함수 `SettlementCalculator` 한 곳. 같은 입력이면 같은 출력(테스트에서 두 번 계산해 `==`).
5. **조정은 정확히 한 번.** 조정 행은 `settlement_id`로 소비한 정산에 묶이고, 묶는 UPDATE는 `WHERE settlement_id IS NULL`이라 두 정산이 같은 조정을 가져갈 수 없다(조건부 UPDATE 영향 행 수로 판정, 0행이면 이미 소비됨). `CARRY_OVER`도 `source_settlement_id`로 어느 정산이 만들었는지 남기고, 한 정산은 CARRY_OVER를 최대 하나만 만든다(`UNIQUE(source_settlement_id)` — NULL 허용 UNIQUE라 MANUAL 행은 걸리지 않는다).

판매자 간 순서(판매자 ID 오름차순)와 라인 순서(판매자 주문 ID 오름차순)를 고정해 생성 결과가 **바이트 단위로 같게** 한다(`created_at`·id 자동값 제외).

**불변식(테스트로 증명, §14)**
- `settlement.sales_amount/seller_coupon_discount/commission_amount/shipping_fee_owed/return_fee_borne` = 각 라인 합, `adjustment_amount` = 붙은 조정 합, `total_amount` = Σ `line_amount` + `adjustment_amount`, `payable_amount = max(total_amount, 0)`, `line_count` = 라인 수.
- 같은 기간 재실행 → 정산·라인·금액 동일(새 행 0개).
- 동시 2회 실행 → 판매자별 정산 1개.
- N번째 판매자 뒤 예외 → 재실행 → 한 번에 끝난 실행과 같은 결과.
- 한 판매자 주문은 전체 정산을 통틀어 라인 1개 이하.
- 확정 전 반품·취소는 라인에 반영(항목이 `ORDERED`가 아니면 매출 0, `return_fee_borne` 반영), 확정 뒤 변화는 조정으로만.

### 8-9. Spring Batch 보류 (사용자 결정)

C4의 정산 생성은 **기존 스케줄러 + keyset 순회 + 판매자별 트랜잭션**(C2 정리 작업과 같은 패턴)으로만 한다. Spring Batch(청크·재시작 메타테이블·JobRepository)는 넣지 않는다. Phase 3-2에서 다음을 **측정한 뒤** 근거가 있을 때만 도입한다:
- 판매자 N명(예: 1,000 / 10,000)·확정 판매자 주문 M건(예: 10만 / 100만)에서 한 번 생성에 걸리는 시간과 DB 부하(`confirmed_at` 범위 풀스캔 시간 포함).
- 중간 실패 뒤 재실행 비용: 이미 끝난 판매자를 건너뛰는 데 드는 시간(UNIQUE 위반/존재 조회 횟수)과 전체 재실행 시간.
- 작업 한 번의 메모리(판매자별 그룹핑을 메모리에서 하는 현재 방식의 한계).
위 수치가 주간 작업 창을 넘거나 재실행 비용이 문제로 측정되면 그때 Spring Batch(또는 분할 실행)를 ADR로 결정한다. 틀렸을 때 비용: 3-2 전까지는 큰 판매자 수에서 작업이 길어질 수 있으나, 주 1회·야간이고 멱등이라 다시 돌리면 된다.

## 9. 데이터 모델 (Flyway V21~, 빌더가 컬럼 세부 확정)

PK/UNIQUE 외 인덱스는 두지 않는다. 모든 테이블은 `BaseEntity` 컬럼을 가진다.

- `order_item`: `order_item_status` 값 추가(DDL 변경 없음, VARCHAR). 새 컬럼 없음(반품 금액은 `return_request`에).
- `orders`: `shipping_fee_recovered`(C2 기존)에 반품 발송 회수를 더해 쓴다. 새 컬럼 없음.
- `return_request`(order 모듈): `order_id`, `seller_order_id`, `order_item_id` **UNIQUE**, `member_id`, `seller_id`, `return_reason`(CHANGE_OF_MIND/DEFECTIVE/WRONG_DELIVERY), `detail`, `return_status`, `fee_bearer`(BUYER/SELLER, 사유에서 유도해 저장), `return_fee`(구매자 부담 회수 구간), `outbound_fee_recovered`, `shipping_fee_refund`, `seller_borne_fee`, `refund_amount`, `points_refund`, `reject_reason NULL`, `requested_at`, `decided_at NULL`, `received_at NULL`, `completed_at NULL`, `decided_by`(SELLER/SYSTEM/ADMIN, NULL). 환불 계획 값은 수거 확인(tx1) 때 채운다.
- `settlement`: `seller_id`, `period_start DATE`, `period_end DATE`, `payout_date DATE`, `settlement_status`, `sales_amount`, `seller_coupon_discount`, `commission_amount`, `shipping_fee_owed`, `return_fee_borne`, `adjustment_amount`, `total_amount`, `payable_amount`, `line_count`, `confirmed_at NULL`, `paid_at NULL`, `paid_memo NULL`. **`UNIQUE(seller_id, period_start)`**.
- `settlement_line`: `settlement_id`, `seller_order_id` **UNIQUE**, `order_id`, `order_no`, `confirmed_at`, `sales_amount`, `seller_coupon_discount`, `commission_base`, `commission_rate_bp`, `commission_amount`, `shipping_fee_owed`, `return_fee_borne`, `line_amount`. settlement ↔ line은 한 애그리거트(`@OneToMany` 허용, review와 같은 예외. 라인은 정산 안에서만 만들어지고 바뀌지 않는다).
- `settlement_run`: `period_start DATE` **UNIQUE**, `generated_at`, `seller_count`(기간 생성 완료 표시. 판매자별 트랜잭션이 모두 끝난 뒤 별도 트랜잭션으로 INSERT; 재실행은 이 행이 있으면 기간을 건너뛴다. 기간 중간에 멈추면 행이 없어 재실행이 남은 판매자를 채운다).
- `settlement_adjustment`: `seller_id`, `settlement_id NULL`, `adjustment_type`(MANUAL/CARRY_OVER), `amount`(부호), `reason`, `ref_seller_order_id NULL`, `source_settlement_id NULL`(**UNIQUE**, CARRY_OVER는 정산당 하나), `created_by_admin_id`. 조정은 정산과 다른 생명주기(정산보다 먼저 생기고 나중에 붙는다)라 별도 애그리거트, ID로만 참조.

인덱스 없이 받아들이는 조회(3-2 측정 후보): `seller_order(seller_order_status, confirmed_at)` 범위 조회(정산 생성), `return_request(seller_id, return_status)`(판매자 반품 목록), `return_request(return_status, requested_at)`(자동 승인 작업), `order_item(order_item_status)`(열린 반품·정체 환불 조회), `settlement(seller_id)`(판매자 정산 목록). 생성 작업이 주 1회라 풀스캔을 받아들인다. 반품 목록이 느리면 측정 뒤 결정.

## 10. 동시성·멱등성·락

- 반품의 모든 상태 변경은 **주문 행 락** 아래에서 한다(ADR 0003): 구매자 요청·철회는 `findByIdForUpdate(orderId)`, 판매자·관리자 엔드포인트는 트랜잭션 밖에서 `findOrderIdBySellerOrderId`/`findOrderIdByReturnId`(일반 조회)로 주문 ID를 찾고 안에서 잠근다. `return_request` 행 자체는 잠그지 않는다(주문 행 락이 직렬화).
- 수거 확인(환불): C2 `OrderCancelService`와 같은 구조의 **`OrderReturnService`**. tx1(락 → `ReturnRefundCalculator` → 항목 `RETURN_REFUNDING`, `return_request.refund_amount` 저장) → PG 부분 취소(트랜잭션 밖, `refundKey(itemId, amount)`) → tx2(락 → `RETURNED` 확정, `refundedAmount`·`pointsRefunded`·`shippingFeeRecovered` 갱신, `paymentGateway.recordRefund`, `BenefitReverter.revertItem(..., cartCouponId = allItemsClosed ? cartCouponId : null)`, `CHANGE_OF_MIND`면 `stockManager.restore` → `decreaseSales`(항상), 판매자 주문이 비면 `confirm` + `PointEarner`(0P), `settle`). 락 순서 `orders → member_point → coupon → sku → product`(ADR 0004). `refund_amount == 0` 또는 `paymentKey` 없음이면 PG 없이 tx2(C3 7-3). PG 실패 → `RETURN_APPROVED`로 되돌리고 `PAYMENT_CANCEL_FAILED`. 불확실 → `RETURN_REFUNDING` 유지, 대사가 맞춘다.
- 대사: `PaymentReconciliationService.reconcileCancels`의 대상에 `RETURN_REFUNDING`(`cancel-request-timeout` 경과)을 더한다. PG 조회 결과로 같은 tx2를 실행한다. `findCancelRequestedItems`를 상태 집합을 받는 형태로 넓힌다.
- 자동 확정과 반품 요청의 경합(배송 완료 6일 23시간에 요청, 7일에 자동 확정): 둘 다 주문 행 락 아래. 요청이 먼저 커밋되면 자동 확정은 `hasOpenReturn`으로 건너뛰고, 확정이 먼저면 요청은 `RETURN_NOT_ELIGIBLE`.
- 멱등: 반품 요청 POST는 C2 `Idempotency-Key` 필터 + `return_request.order_item_id UNIQUE`. 정산 생성 POST는 Idempotency-Key + `UNIQUE(seller_id, period_start)` + `settlement_line.seller_order_id UNIQUE`. 조정 POST는 Idempotency-Key(같은 금액·사유를 두 번 넣는 것은 정당할 수 있어 UNIQUE를 두지 않는다). PATCH(승인·거절·수거·정산 확정·지급)는 이미 목표 상태면 성공.
- 정산 생성 동시 실행(스케줄 + 관리자): 판매자 단위 UNIQUE 위반을 잡아 건너뛴다. 라인 UNIQUE가 "한 판매자 주문은 한 정산에만"을 보장한다. 정산 읽기는 락 없음.
- 정산 생성은 **판매자별 트랜잭션** 하나. 주문 행 락은 잡지 않는다(읽기만). 생성 중 판매자 주문이 바뀌는 경로는 없다(`CONFIRMED`는 종결 상태).

## 11. 작업(스케줄러)

| 작업 | 주기 설정 | 내용 |
|---|---|---|
| 반품 자동 승인 | `kairos.jobs.return-auto-approve-delay: 1h` | `RETURN_REQUESTED`이고 `requested_at < now − kairos.order.return-auto-approve-after(3d)`인 반품을 keyset으로 훑어 건별 트랜잭션에서 승인(`decided_by = SYSTEM`). |
| 자동 구매 확정(기존) | `auto-confirm-delay: 1h`, `auto-confirm-after: 7d`(변경 없음) | `findSellerOrderIdsDeliveredBefore`는 그대로 두고, `autoConfirm` 트랜잭션 안에서 `hasOpenReturn`이면 건너뛴다(쿼리로 거르지 않는다 — 락 뒤 현재 읽기로 판정). |
| 환불 대사(기존 확장) | `payment-reconcile-delay: 60s` | `RETURN_REFUNDING` 정체 항목 포함. |
| 정산 생성 | `kairos.jobs.settlement-generate-cron: 0 0 3 * * MON` | 지난주 정산 생성(§8-3). |

모두 ADR 0003대로 감싸는 `@Transactional` 없음, keyset 순회, 실패 건은 로그만 남기고 다음 건으로.

## 12. 설정값

`kairos.order.return-window: 7d`, `kairos.order.return-auto-approve-after: 3d`, `kairos.order.return-shipping-fee: 3000`(회수 구간 `returnFee`·`sellerBorneFee`에만 쓴다. 발송 구간 회수 `outboundFeeRecovered`는 C2 `base-shipping-fee`(3,000)를 쓴다 — 플랫폼이 낸 발송비를 되받는 것이므로. Claude 결정, 틀렸을 때 비용: 두 값을 다르게 바꾸면 "왕복 6,000"이 아니라 두 값의 합이 된다. 틀렸을 때 비용: 설정 하나 더), `kairos.settlement.late-confirm-lookback: 28d`, `kairos.settlement.start-date`(정산 시작 월요일, 필수), `kairos.jobs.return-auto-approve-delay: 1h`, `kairos.jobs.settlement-generate-cron`.

## 13. 에러 코드 (ErrorCode enum과 OpenAPI 일치)

| 코드 | HTTP | 뜻 |
|---|---|---|
| `RETURN_INVALID` | 400 | 사유 enum 아님, detail 길이, 거절 사유 없음 |
| `RETURN_NOT_FOUND` | 404 | 남의 반품 포함 |
| `RETURN_NOT_ELIGIBLE` | 409 | 판매자 주문이 `DELIVERED` 아님(확정·배송 중·취소), 항목이 `ORDERED` 아님 |
| `RETURN_WINDOW_EXPIRED` | 409 | 배송 완료 후 기한 경과 |
| `RETURN_ALREADY_REQUESTED` | 409 | 열린 반품 있음, 또는 거절된 항목 재요청 |
| `RETURN_INVALID_STATE_TRANSITION` | 409 | 승인 전 수거 확인, 승인 뒤 철회 등 |
| `SELLER_ORDER_HAS_OPEN_RETURN` | 409 | 열린 반품이 있는 판매자 주문 구매 확정 |
| `SETTLEMENT_NOT_FOUND` | 404 | 남의 정산 포함 |
| `SETTLEMENT_PERIOD_INVALID` | 400 | 월요일 아님, 기간이 아직 안 끝남 |
| `SETTLEMENT_INVALID_STATE_TRANSITION` | 409 | `GENERATED → PAID` 직행 등 |
| `SETTLEMENT_ADJUSTMENT_INVALID` | 400 | 금액 0, 사유 없음 |
| `SETTLEMENT_ADJUSTMENT_APPLIED` | 409 | 이미 정산에 붙은 조정 삭제 시도 |

`PAYMENT_CANCEL_FAILED`, `ORDER_ITEM_NOT_CANCELABLE`(반품 중 항목 취소 시도)은 재사용.

## 14. 증명할 테스트 (완료 기준)

- §5-1 예시 A·B·C·D 숫자 그대로(환불액, `shippingFeeRecovered`, 결제 상태, 판매자 주문 `CONFIRMED`, 적립 0P).
- 이중 청구 방지 양방향: C2 취소 차감 뒤 변심 반품은 3,000만; 변심 반품(6,000) 뒤 다른 판매자 주문 구매자 취소는 차감 없음. 주문당 발송 회수 합 ≤ 3,000(property 식: 취소·반품 요청·거절·철회·완료를 무작위 순서로 섞어도. 열린 반품 항목은 '남은 항목'으로 세어 마지막 취소 판정이 바뀌지 않음을 포함).
- 확장 돈 불변식(§5)이 취소·반품의 모든 중간 상태에서 성립. 부분 반품 뒤 주문 `COMPLETED` + 결제 `PARTIAL_CANCELED`; 누적 환불 = 결제 금액일 때만 `CANCELED`.
- 전부 반품·취소로 주문이 비면: 누적 환불 = 결제 금액 − 구매자 부담 반품비 합 − 발송 구간 회수금 − 환불되지 않은 구매자 배송비(변심 반품으로만 닫힌 판매자 주문의 것), 포인트 전액 반환, 장바구니 쿠폰 반환(만료 전), 결제 `CANCELED`는 누적 환불 = 결제 금액일 때만.
- 기한: 배송 완료 + 7일 − 1초 요청 성공, + 7일 + 1초 `RETURN_WINDOW_EXPIRED`. 확정된 판매자 주문 `RETURN_NOT_ELIGIBLE`.
- 열린 반품이 있는 판매자 주문은 자동 확정 작업이 건너뛰고, 반품 완료 뒤 다음 실행에서 확정. 수동 확정은 `SELLER_ORDER_HAS_OPEN_RETURN`.
- 3일 미응답 자동 승인, 2일 23시간은 미승인. 판매자 승인과 자동 승인 동시 → 한 번만 `decided_*` 기록.
- 수거 확인 PG 실패 → `RETURN_APPROVED` 복귀, 포인트·쿠폰·판매량 변화 없음. PG 불확실 → `RETURN_REFUNDING` 유지 → 대사가 PG 조회로 `RETURNED` 확정. 0원 환불·`paymentKey` 없음 → PG 없이 완료.
- 거절 뒤 재요청 `RETURN_ALREADY_REQUESTED`, 철회 뒤 재요청 성공(같은 행 재사용). 관리자가 거절 건 승인 가능.
- 변심 반품 완료 → SKU 재고 +수량, 판매량 −수량. 불량·오배송 반품 완료 → 재고 그대로, 판매량 −수량. PG 실패 되돌림에서는 둘 다 변화 없음.
- §8-7 숫자 그대로: 라인 70,500 / 13,500 / −3,000, 조정 −10,000, 합계 71,000. 버림 12,345·1000bp → 1,234, 850bp → 1,049. 비율 800bp 판매자 주문이 섞이면 그 라인만 8%, 판매자의 현재 비율을 바꿔도 라인 불변.
- 플랫폼 쿠폰·포인트·무료배송을 아무리 넣어도 `line_amount`가 안 바뀜(판매자 쿠폰만 바뀜).
- §8-8 불변식 전부: 헤더 각 금액 = 라인 합, `total = Σ line + adjustments`, `payable = max(total, 0)`, `line_count`.
- 같은 기간 생성 두 번(순차) → 두 번째 실행은 새 행 0개, 정산·라인 전부 동일(바이트 비교). 동시 2회 → 판매자별 정산 1개. 판매자 주문이 두 정산에 들어가지 않음(`seller_order_id UNIQUE`).
- 중간 실패: 판매자 3명 중 2번째 뒤에 예외를 던지는 Fake로 실행 → 1번째만 커밋, 2·3번째 없음(반쪽 정산 없음) → 재실행 → 3명 모두 생성, 한 번에 끝난 실행과 결과 동일.
- 저장 스냅샷만 쓰는지: 생성 전에 상품 가격·쿠폰 정책·판매자의 현재 수수료율(`seller.commission_rate_bp`)을 바꿔도(판매자 주문 스냅샷은 그대로) 라인 값 불변. 같은 입력으로 `SettlementCalculator`를 두 번 돌리면 같은 결과.
- 조정 한 번만: 같은 판매자 정산을 두 기간에 걸쳐 만들 때 조정 하나는 한 정산에만 붙음. CARRY_OVER는 정산당 최대 1개.
- 늦은 확정: 기간 A 생성 뒤 `confirmed_at`이 A 안인 판매자 주문이 생기면(테스트에서 시각 조작) 기간 B 정산에 들어간다. lookback 밖이면 안 들어간다. `start-date` 전 확정분은 어떤 정산에도 안 들어간다.
- 생성 순서: 기간 A·B가 모두 끝난 상태에서 (A→B)로 만들든 작업이 한 번에 만들든 결과가 같다. A 미생성 상태에서 B 요청은 `SETTLEMENT_PERIOD_INVALID`. 판매자 정산 0개 기간도 `settlement_run`에 남아 다음 실행이 건너뛴다.
- 조정: 기간 끝 전 등록 → 이번 정산, 기간 끝 뒤 등록 → 다음 정산. 붙은 조정 삭제 `SETTLEMENT_ADJUSTMENT_APPLIED`. 확정 0건 + 조정만 → 라인 0개 정산 생성.
- 음수 합계 → `payable_amount` 0, `CARRY_OVER` 조정이 다음 정산에 붙음.
- 상태 전이: `GENERATED → PAID` 직행 409, `CONFIRMED → PAID` 두 번 호출 멱등.
- 정산 생성 뒤 주문 데이터를 바꿔도(테스트에서 직접 UPDATE) 라인 값 불변.
- `ArchitectureTest`: `settlement`이 `order.domain`·`order.infrastructure`를 import하지 않고 포트로만 읽는다. `order`는 `settlement`를 모른다.
- 설정 `return-window ≠ auto-confirm-after`이면 기동 시 경고 로그.

## 15. 빌더에게 맡기는 것

`ReturnRefundCalculator`·`ReturnRefundPlan` 시그니처와 `RefundCalculator` 조건 확장 방식, `OrderReturnService`와 `OrderCancelService`의 공통부(tx2 마무리) 추출 여부, `return_request`를 `Order` 애그리거트 안에 둘지(항목과 1:1이고 주문 행 락 아래에서만 바뀌므로 `OrderItem`이 `returnRequest`를 갖는 쪽을 권장) 별도 Repository로 둘지, `ConfirmedSellerOrderReader` DTO 모양과 order 쪽 조회 서비스 이름, `sweepByKeyset` 공용화, 정산 금액 계산 순수 함수(`SettlementCalculator`, settlement 도메인) 시그니처, 테이블 컬럼 길이·NULL, OpenAPI 스키마 세부, ADR 0005 본문(§3), 설정 키 최종 이름, 판매자 정산 목록 정렬(`period_start DESC`).
