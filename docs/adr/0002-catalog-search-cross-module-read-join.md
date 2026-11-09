# ADR 0002. 상품 검색은 catalog 가 brand·seller 테이블을 읽기 전용 뷰 엔티티로 조인한다

- 상태: 채택 (MSA 분리 시 비정규화 + 이벤트 동기화로 대체 예정)
- 날짜: 2026-11-09

## 맥락
사용자 상품 목록·검색(`GET /api/v1/products`)의 노출 조건은 "상품 `ON_SALE` + 브랜드 `APPROVED` + 판매자 `APPROVED`"이고,
검색어는 상품명뿐 아니라 브랜드명에도 걸리며, 결과는 페이징·정렬된다.
브랜드와 판매자는 `seller` 모듈의 애그리거트라 catalog 는 ID 로만 참조한다.
모듈 간 규칙대로 `SellerReader` 포트로 브랜드·판매자 상태를 가져와 애플리케이션에서 거르면,
페이지 단위 필터링(DB 가 센 총 건수와 실제 노출 건수가 어긋남)과 브랜드명 검색을 올바르게 할 수 없다.
"노출 가능한 상품 N 건"을 한 번의 쿼리로 정확히 세고 정렬하려면 DB 조인이 필요하다.

## 결정
- `catalog.infrastructure` 가 `brand`·`seller` 테이블을 **읽기 전용 뷰 엔티티** `BrandView`(id, seller_id, name, brand_status),
  `SellerView`(id, seller_status) 로 직접 매핑하고, `ProductSearchRepositoryImpl`(QueryDSL) 에서 `product` 와 조인한다.
- 이것은 **테이블 의존**이지 코드 의존이 아니다. catalog 는 seller 모듈의 `domain`/`infrastructure` 클래스를 import 하지 않는다
  (ArchUnit 모듈 격리 규칙은 그대로 통과한다).
- 뷰 엔티티는 검색에 필요한 컬럼만 매핑하고, 쓰기 경로가 없다. 브랜드·판매자의 상태 변경은 계속 seller 모듈만 한다.
- 다른 조회(판매자 상품 상세의 브랜드명, 등록 시 브랜드 검증)는 기존처럼 `SellerReader` 포트를 쓴다. 뷰 엔티티는 검색 쿼리 전용이다.

## 결과
- 뷰 엔티티는 `@Immutable` 이고 `BaseEntity` 를 상속하지 않는다. 감사 컬럼·`status` 는 매핑하지 않지만
  소프트 딜리트는 `@SQLRestriction("status = 'ACTIVE'")` 로 동일하게 거른다.
  이것은 "모든 JPA 엔티티는 `BaseEntity` 를 상속한다" 규칙의 **명시적 예외**다(CLAUDE.md, 코드 주석에도 표시).
- `brand`·`seller` 테이블 스키마(특히 `brand_status`, `seller_status`, `name`) 가 바뀌면 catalog 의 뷰 엔티티도 함께 고쳐야 한다.
  `ddl-auto: validate` 가 불일치를 기동 시점에 잡는다.
- 노출 조건이 쿼리에 들어 있으므로 판매자 정지·브랜드 반려는 다음 요청부터 즉시 목록과 상세에 반영된다(캐시 없음).
- MSA 로 분리할 때는 조인이 불가능해지므로, 상품 테이블에 브랜드명과 노출 가능 여부를 비정규화해 저장하고
  seller 서비스의 상태 변경 이벤트로 동기화하는 방식으로 대체한다. 그때 이 ADR 을 대체(superseded)로 표시한다.
- 이 검색 쿼리(3-way 조인 + 품절·대표 이미지 서브쿼리 + LIKE)는 인덱스 없이 시작하며, 3-2 단계에서 측정 대상이다.
