-- ============================================================
--  FoodMemory - 음식 추억 기록 서비스
--  DDL 스크립트 (MySQL 8.0 기준)
--
--  실행 순서가 중요하다.
--    DROP   : 자식 → 부모   (참조하는 쪽을 먼저 지운다)
--    CREATE : 부모 → 자식   (참조당하는 쪽을 먼저 만든다)
--  순서를 어기면 FK 때문에 실행이 거부된다.
-- ============================================================


-- ============================================================
--  1. DROP  (자식 → 부모 역순)
-- ============================================================
-- IF EXISTS 를 붙이면 테이블이 없어도 에러가 나지 않는다.
-- 개발 중에 이 스크립트를 반복 실행하기 위한 장치다.

DROP TABLE IF EXISTS photo;            -- post 를 참조하므로 가장 먼저
DROP TABLE IF EXISTS post;             -- member, restaurant 를 참조
DROP TABLE IF EXISTS member_identity;  -- member 를 참조
DROP TABLE IF EXISTS restaurant;       -- 아무것도 참조하지 않음
DROP TABLE IF EXISTS member;           -- 아무것도 참조하지 않음


-- ============================================================
--  2. member  (회원 = 사람 한 명)
-- ============================================================
--  이 테이블에는 '로그인하는 방법' 이 없다. 그건 member_identity 가 맡는다.
--
--  나눈 이유:
--    한 사람이 카카오로도 들어오고 구글로도 들어올 수 있다.
--    로그인 수단을 이 테이블에 두면 그때마다 다른 회원이 되어 사진첩이 쪼개진다.
--    "사람" 과 "그 사람이 들어오는 문" 은 개수가 다르므로 테이블도 나눈다.
--
--  post 는 계속 member_id 만 바라본다.
--  로그인 수단이 늘어나도 게시물의 주인은 흔들리지 않는다.
-- ============================================================
CREATE TABLE member (

    -- 인조키(surrogate key). 의미가 없으므로 바뀔 이유가 없다.
    -- 회원이 이메일을 바꾸든 소셜을 갈아타든 이 번호는 그대로다.
    -- BIGINT 인 이유: PK 타입을 나중에 늘리는 것이 더 비싸서 처음부터 크게 잡는다.
    member_id         BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '회원 식별자',

    -- 화면 표시용 이름. 중복을 허용한다.
    -- UNIQUE 를 걸면 소셜이 던져준 닉네임이 겹칠 때 가입이 실패한다.
    -- 고유 식별자가 필요해지면(공유 기능) handle 컬럼을 따로 추가한다.
    nickname          VARCHAR(50)   NOT NULL                 COMMENT '표시용 닉네임',

    -- NULL 을 허용하는 이유: 제공자가 이메일을 주지 않을 수 있다.
    -- NOT NULL 로 걸면 '이메일 동의 안 한 사람은 가입 불가' 라는 정책 선언이 된다.
    email             VARCHAR(255)  NULL                     COMMENT '대표 이메일',

    -- 이용약관·개인정보 처리방침에 동의한 시각.
    --
    -- BOOLEAN 이 아니라 DATETIME 인 이유:
    --   "동의했다" 만 남기면 언제 동의했는지 알 수 없다.
    --   약관을 개정하면 "새 약관 이후에 동의한 사람" 과 "다시 받아야 할 사람" 을
    --   갈라내야 하는데, 시각이 없으면 전원에게 다시 받는 수밖에 없다.
    --   동의 기록은 분쟁이 생겼을 때 근거가 되므로 시점이 중요하다.
    --
    -- 사람 단위의 동의라 member 에 둔다. 로그인 수단을 하나 더 연결한다고
    -- 약관에 다시 동의해야 하는 것은 아니다.
    terms_agreed_at   DATETIME      NULL                     COMMENT '약관 동의 시각',

    created_at        DATETIME      NOT NULL                 COMMENT '가입 시각',

    -- NULL 을 허용하지 않는 이유: INSERT 시 created_at 과 같은 값을 넣는다.
    -- NULL 이 섞이면 '최근 수정순 정렬' 같은 조회에서 매번 NULL 처리를 해야 한다.
    updated_at        DATETIME      NOT NULL                 COMMENT '마지막 수정 시각',

    PRIMARY KEY (member_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4   -- utf8 은 3바이트라 이모지가 저장되지 않는다
  COMMENT='회원';


-- ============================================================
--  2-1. member_identity  (로그인 수단)
-- ============================================================
--  한 회원(member) 밑에 여러 줄이 달린다.
--
--    member 17 (강명철)
--      ├─ KAKAO  5037008413
--      ├─ GOOGLE 109628478545752180304
--      └─ LOCAL  kmch@example.com  + 비밀번호 해시
--
--  어느 줄로 들어와도 도착하는 곳은 같은 member_id 이므로 사진첩이 하나다.
-- ============================================================
CREATE TABLE member_identity (

    identity_id       BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '로그인 수단 식별자',

    member_id         BIGINT        NOT NULL                 COMMENT '이 수단이 속한 회원',

    -- KAKAO / GOOGLE / LOCAL
    -- 자바 enum 이름을 그대로 저장하므로 대문자다.
    -- 순서 번호(0,1,2)로 저장하지 않는 이유는 Member 엔티티 주석에 적어두었다.
    -- 코드 테이블로 빼지 않고 컬럼 + 자바 enum 으로 관리한다.
    -- 제공자 추가는 연동 코드 작성이 동반되므로 '데이터만 추가' 이점이 없기 때문.
    provider          VARCHAR(20)   NOT NULL                 COMMENT 'KAKAO/GOOGLE/LOCAL',

    -- 그 세계에서의 신분증.
    --   KAKAO  → 카카오가 매긴 회원 번호
    --   GOOGLE → 구글이 준 sub 값
    --   LOCAL  → 이메일. 자체 가입은 이메일이 곧 로그인 아이디다
    --
    -- VARCHAR 인 이유: 카카오는 숫자지만 구글(21자리)과 애플은 문자열을 준다.
    --                 계산하지 않는 숫자는 문자열로 저장한다.
    provider_user_id  VARCHAR(100)  NOT NULL                 COMMENT '제공자가 매긴 ID (LOCAL 은 이메일)',

    -- 자체 가입의 비밀번호. 소셜은 비밀번호가 없으므로 NULL 이다.
    --
    -- 길이가 60 인 이유: BCrypt 결과는 알고리즘·비용·솔트·해시가 합쳐져 항상 60자다.
    -- 원문을 저장하지 않는 이유: DB 가 유출되면 그대로 남의 계정이 된다.
    --   사람들은 다른 사이트에서도 같은 비밀번호를 쓰기 때문에 피해가 여기서 끝나지 않는다.
    -- 해시는 단방향이라 저장된 값에서 원문을 되돌릴 수 없다.
    --   로그인할 때는 입력값을 같은 방식으로 해싱해 저장된 값과 비교한다.
    password_hash     VARCHAR(60)   NULL                     COMMENT 'BCrypt 해시 (LOCAL 만)',

    created_at        DATETIME      NOT NULL                 COMMENT '연결 시각',
    updated_at        DATETIME      NOT NULL                 COMMENT '마지막 수정 시각',

    PRIMARY KEY (identity_id),

    -- 같은 소셜 계정이 두 회원에게 붙는 것을 막는다.
    -- 둘 중 하나만으로는 특정되지 않는다.
    -- (카카오 12345 와 구글 12345 는 서로 다른 사람이다)
    --
    -- 자체 가입에도 그대로 쓸모가 있다. provider='LOCAL' 이고 provider_user_id 가
    -- 이메일이므로, 같은 이메일로 두 번 가입하는 것이 이 제약 하나로 막힌다.
    UNIQUE KEY uk_identity_provider (provider, provider_user_id),

    -- 한 회원이 같은 제공자를 두 번 연결하지 못하게 한다.
    -- 카카오 계정을 두 개 붙이면 어느 쪽이 대표인지 정할 수 없고,
    -- 하나를 해제할 때 어느 것을 지울지도 애매해진다.
    UNIQUE KEY uk_identity_member_provider (member_id, provider),

    CONSTRAINT fk_identity_member
        FOREIGN KEY (member_id) REFERENCES member (member_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='회원의 로그인 수단';


-- ============================================================
--  3. restaurant  (식당)
-- ============================================================
--  이 테이블만 성격이 다르다.
--  원본은 지도 API 에 있고, 우리 DB 는 사본(캐시)이다.
--  그래도 저장하는 이유:
--    (1) 갤러리 20개마다 API 20번 호출은 불가능하다
--    (2) '내가 가본 식당 목록' 은 지도 API 가 모른다. 우리만 안다
--    (3) 폐업하면 원본이 사라진다. 추억 서비스에서 추억이 사라지면 안 된다
-- ============================================================
CREATE TABLE restaurant (

    restaurant_id  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '식당 식별자',

    -- 지도 API 가 매긴 장소 ID. 같은 식당인지 판별하는 유일한 기준이다.
    -- 이름+주소로 비교하면 '역전할머니맥주' 와 '역전 할머니 맥주' 가
    -- 다른 가게로 인식되어 중복 행이 쌓이고, 식당별 모아보기가 망가진다.
    place_id       VARCHAR(50)     NOT NULL                 COMMENT '지도 API 장소 ID',

    -- MVP 에서는 갱신하지 않으므로 이 값이 곧 '방문했던 그 시절 이름' 이다.
    -- 나중에 갱신 기능을 넣을 때는 반드시 순서를 지켜야 한다.
    --   (1) 게시물에 이름 스냅샷 컬럼을 먼저 추가하고 현재 값으로 채운다
    --   (2) 그다음 갱신을 켠다
    -- 순서를 뒤집으면 '그때 이름' 을 영원히 잃는다.
    name           VARCHAR(100)    NOT NULL                 COMMENT '가게 이름',

    address        VARCHAR(255)    NOT NULL                 COMMENT '주소',

    -- DECIMAL(10, 7) = 전체 10자리, 소수점 아래 7자리
    -- 위도는 -90~90, 경도는 -180~180 이라 정수부는 최대 3자리.
    -- 소수 7자리면 약 1cm 정밀도로 식당 위치에는 충분하다.
    -- FLOAT / DOUBLE 을 쓰지 않는 이유: 부동소수점 오차가 생긴다.
    latitude       DECIMAL(10, 7)  NOT NULL                 COMMENT '위도',
    longitude      DECIMAL(10, 7)  NOT NULL                 COMMENT '경도',

    created_at     DATETIME        NOT NULL                 COMMENT '우리 DB 에 처음 저장된 시각',
    updated_at     DATETIME        NOT NULL                 COMMENT '마지막 수정 시각',

    PRIMARY KEY (restaurant_id),

    -- 겹치면 같은 가게가 두 줄로 쪼개져 '식당별 모아보기' 가 깨진다.
    -- 겹치면 데이터가 망가지는 값이므로 UNIQUE 를 건다.
    UNIQUE KEY uk_restaurant_place (place_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='식당 (지도 API 사본)';


-- ============================================================
--  4. post  (게시물 = 한 번의 식사 기록)
-- ============================================================
CREATE TABLE post (

    post_id        BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '게시물 식별자',

    -- 작성자. 주인 없는 게시물은 존재할 수 없으므로 NOT NULL.
    -- FK 는 참조 대상과 타입이 같아야 한다 (member.member_id 가 BIGINT).
    member_id      BIGINT        NOT NULL                 COMMENT '작성자',

    -- NULL 을 허용하는 이유:
    --   집에서 해먹은 음식, 캠핑장, EXIF 가 지워져 좌표를 못 찾는 사진,
    --   폐업해서 지도에서 검색되지 않는 가게.
    -- NOT NULL 로 걸면 집밥 사진을 아예 올릴 수 없게 되어
    -- '갤러리에 쌓인 음식 사진을 모아둔다' 는 서비스 취지가 반쯤 깨진다.
    restaurant_id  BIGINT        NULL                     COMMENT '먹은 장소 (없을 수 있음)',

    -- 컬럼명을 comment 가 아니라 content 로 둔 이유:
    --   MySQL 에서 COMMENT 는 DDL 문법에 쓰이는 키워드라 읽을 때 헷갈리고,
    --   Oracle 에서는 아예 예약어다.
    -- NULL 허용: 이 서비스는 억지 리뷰 문제에서 출발했다.
    --   글쓰기를 강요하면 그 문제를 그대로 반복하는 셈이라
    --   사진만으로도 하나의 완결된 기록이 되도록 한다.
    content        VARCHAR(500)  NULL                     COMMENT '짧은 코멘트',

    -- created_at 과 절대 같지 않다.
    --   created_at  = DB 에 저장된 시각 (오늘)
    --   eaten_date  = 실제로 먹은 시각 (3년 전일 수 있다)
    -- 갤러리에 쌓아둔 예전 사진을 몰아서 올리는 것이 핵심 시나리오라,
    -- created_at 으로 정렬하면 100장이 전부 오늘 자리에 뭉쳐서 뜬다.
    -- DATE 가 아니라 DATETIME 인 이유: 같은 날 아침·점심·저녁의 순서를 지킨다.
    eaten_date     DATETIME      NOT NULL                 COMMENT '먹은 시각 (갤러리 정렬 기준)',

    -- MySQL 의 BOOLEAN 은 TINYINT(1) 의 별칭이며 0/1 로 저장된다.
    -- 자바 boolean 필드와 JPA 가 바로 매핑되어 변환기가 필요 없다.
    -- DEFAULT FALSE : 명시하지 않고 등록된 게시물은 비공개가 된다.
    --   공유 기능이 없는 MVP 단계에서 실수로 공개되는 것을 막는 안전한 기본값.
    is_public      BOOLEAN       NOT NULL DEFAULT FALSE   COMMENT '공개 여부',

    created_at     DATETIME      NOT NULL                 COMMENT '저장 시각',
    updated_at     DATETIME      NOT NULL                 COMMENT '마지막 수정 시각',

    PRIMARY KEY (post_id),

    -- 제약 이름 규칙: fk_자기테이블_대상테이블
    -- 이렇게 두면 에러 메시지만 보고도 어디가 문제인지 알 수 있다.
    -- ON DELETE 를 지정하지 않았으므로 기본값 RESTRICT 가 적용된다.
    --   = 게시물이 남아 있는 회원은 삭제할 수 없다.
    -- 회원 탈퇴 기능을 만들 때 이 정책을 다시 검토한다.
    CONSTRAINT fk_post_member
        FOREIGN KEY (member_id)     REFERENCES member (member_id),

    CONSTRAINT fk_post_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='게시물 (한 번의 식사 기록)';


-- ============================================================
--  5. photo  (사진)
-- ============================================================
CREATE TABLE photo (

    -- PK 이면서 표시 순서 정렬 기준도 겸한다.
    -- 한 게시물의 사진들은 같은 트랜잭션에서 연달아 INSERT 되므로
    -- photo_id 오름차순 = 업로드 순서가 된다.
    -- 사용자가 사진 순서를 바꾸는 기능이 생기면 이 가정이 깨지고,
    -- 그때 sort_order 컬럼을 추가한다.
    photo_id    BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '사진 식별자',

    -- 게시물 없는 사진은 존재할 수 없다.
    post_id     BIGINT        NOT NULL                 COMMENT '속한 게시물',

    -- 도메인과 버킷을 제외한 상대 경로만 저장한다. 예) 2026/07/abc123.jpg
    -- 전체 URL 을 저장하면 버킷 변경·리전 이전·CDN 도입 시
    -- 사진 수만 장의 URL 을 전부 UPDATE 해야 한다.
    -- 앞부분은 설정 파일에 한 줄로 두고 화면에서 합친다.
    -- 파일명은 서버가 UUID 기반으로 새로 만든다. 사용자가 준 이름을 그대로 쓰면
    --   (1) 같은 이름이 올라오면 앞 사람 사진을 덮어쓴다
    --   (2) 한글·공백·특수문자로 URL 인코딩 문제가 생긴다
    --   (3) 경로 조작(../../)이나 실행 가능한 확장자 업로드가 가능해진다
    file_path   VARCHAR(500)  NOT NULL                 COMMENT '저장 경로 (도메인 제외 상대 경로)',

    created_at  DATETIME      NOT NULL                 COMMENT '업로드 시각',
    updated_at  DATETIME      NOT NULL                 COMMENT '마지막 수정 시각',

    PRIMARY KEY (photo_id),

    CONSTRAINT fk_photo_post
        FOREIGN KEY (post_id) REFERENCES post (post_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='사진';
