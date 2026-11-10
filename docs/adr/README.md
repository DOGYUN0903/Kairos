# ADR 목록

- [0001. Phase 1 주문 생성은 재고·주문·결제를 한 트랜잭션에서 처리한다](0001-phase1-order-single-transaction.md)
- [0002. 상품 검색은 catalog 가 brand·seller 테이블을 읽기 전용 뷰 엔티티로 조인한다](0002-catalog-search-cross-module-read-join.md)
- [0003. C2 주문 트랜잭션과 외부 호출 경계](0003-c2-order-transaction-and-external-calls.md)
- [0004. C3 혜택 트랜잭션과 락 순서](0004-c3-benefit-transaction-and-lock-order.md)
- [0005. C4 반품 환불 트랜잭션과 정산 불변성](0005-c4-return-refund-and-settlement-immutability.md)
