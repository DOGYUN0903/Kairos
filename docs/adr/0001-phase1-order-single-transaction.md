# ADR 0001. Phase 1 주문 생성은 재고·주문·결제를 한 트랜잭션에서 처리한다

- 상태: 채택 (Phase 4에서 Saga로 대체 예정)
- 날짜: 2026-11-08

## 맥락
설계 원칙은 "유스케이스 하나에서 한 애그리거트만 변경"이다. 그러나 주문 생성은 재고(Stock), 주문(Order), 결제(Payment) 세 애그리거트를 바꾼다.
Phase 1은 모놀리식 기본 구현 단계이며, Phase 2에서 동시성 문제와 PG 지연의 영향을 측정하는 것이 목적이다.

## 결정
- `OrderService.place`가 하나의 DB 트랜잭션 안에서 재고 차감, 주문 저장, 결제를 모두 처리한다.
- 결제는 Mock PG를 동기 호출해 결과를 기다린다. 실패나 타임아웃(`kairos.mock-pg.timeout`)은 예외가 아니라 주문을 `CANCELLED`로 커밋하고 재고를 같은 트랜잭션에서 복구한다.
- 재고 차감에는 락을 쓰지 않는다(조회 후 도메인 검증, 저장).
- 다른 모듈 호출은 order.application의 인터페이스와 order.infrastructure 구현을 통해서만 한다.

## 결과
- 단순하고 일관성이 쉽게 보장된다(중간 상태가 커밋되지 않는다).
- 락이 없으므로 동시 주문에서 lost update와 초과 판매(oversell)가 발생할 수 있다. 이는 Phase 2에서 재현·측정하기 위한 의도된 결과다.
- PG 지연 동안 요청 스레드와 DB 커넥션을 붙잡아 전체 처리량이 떨어진다. 이는 MSA 전환 근거로 측정한다.
- Phase 4에서 주문 생성 이벤트에서 시작하는 Saga로 대체하며, 그때 이 ADR을 대체(superseded)로 표시한다.
- oversell 이후에는 `remaining`이 `total`을 넘을 수 있다. Phase 1에서 재고 복구(`restore`)는 `total`로 cap하지 않으며(주문 취소가 영구 500이 되는 것을 막기 위함), 복구 후 `remaining > total`이면 `StockService`가 WARN 로그("oversell detected")를 남긴다. 상한 검증은 Phase 3 이후에 다룬다.
- 계획 변경: 스케줄러 기반 자동 취소는 Phase 1에서 Mock PG 타임아웃 처리(타임아웃 시 즉시 `CANCELLED`)로 대체했다.
