# mealmates

먹은 것을 사진으로 남기고, 방(space)을 만들어 함께 모으는 서비스.

**운영 중** → https://mealmates.duckdns.org

Spring Boot · Thymeleaf · MySQL · Redis · AWS EC2

---

## 무엇을 푸는가

사진첩에는 음식 사진이 계속 쌓이는데, 시간이 지나면 **언제 어디서 누구와 먹었는지가 사라진다.**
사진만 남고 기억은 안 남는다.

그래서 두 가지를 한다.

- **사진에 이미 들어 있는 정보를 꺼내 쓴다.** 사진 파일에는 촬영 시각과 GPS 좌표가 들어 있다(EXIF).
  그걸 읽어 날짜를 채우고, 좌표 주변의 식당을 찾아 후보로 보여준다. 사람이 적을 것을 줄인다.
- **혼자 쌓지 않는다.** 방을 만들어 초대 코드를 건네면, 같이 먹은 사람들의 기록이 한자리에 모인다.

---

## 기능

**기록**
- 사진 여러 장을 한 번에 올리고, 올리기 전에 미리보기로 확인한다
- EXIF 에서 촬영 시각을 꺼내 먹은 날짜를 자동으로 채운다
- GPS 좌표 주변의 식당을 카카오 로컬 API 로 찾아 후보를 보여준다
- 코멘트와 댓글

**함께 보기**
- 방을 만들고 초대 코드로 참여한다. 참여자 전원의 기록이 함께 쌓인다
- 코드를 반복해서 잘못 넣으면 잠시 막는다(무차별 대입 방지)
- 코드가 밖으로 샜으면 방장이 새로 발급할 수 있다

**보기**
- 요약 화면은 최근 네 장만, 전체보기는 무한 스크롤
- 올린 순 / 먹은 날짜 순 정렬
- 화면 폭에 따라 사진 크기가 자동으로 맞춰진다

**계정**
- 자체 로그인(BCrypt) 과 소셜 로그인(카카오 · 구글)
- 한 계정에 여러 소셜을 연결할 수 있다

**그 밖**
- PWA — 홈 화면에 설치된다. 촬영 버튼을 누르면 뒷카메라가 바로 열린다

---

## 기술

| | |
|---|---|
| 언어 · 프레임워크 | Java 21, Spring Boot 3.5 |
| 화면 | Thymeleaf (서버 렌더링) |
| 데이터 | MySQL 8, Spring Data JPA |
| 세션 | Redis (Spring Session) |
| 외부 API | 카카오 로컬(장소 검색), 카카오 · 구글 OAuth |
| 배포 | AWS EC2(Ubuntu) · systemd · GitHub Actions |

라이브러리를 넣기 전에 **없이도 되는지 먼저 본다.** 그래서 Spring Security 대신
`spring-security-crypto`(BCrypt)만, 사진 넘겨보기는 자바스크립트 없이 CSS `scroll-snap` 으로 만들었다.

---

## 로컬에서 실행하기

**필요한 것** — JDK 21, MySQL 8, Redis

```bash
# 1. DB 와 Redis 를 띄운다
brew services start mysql
brew services start redis

# 2. DB 와 테이블을 만든다 (JPA 가 만들지 않는다. 아래 '왜' 참고)
#    schema.sql 은 테이블만 만들므로 DB 는 먼저 만들어 두어야 한다
mysql -u root -e "CREATE DATABASE IF NOT EXISTS foodmemory CHARACTER SET utf8mb4"
mysql -u root foodmemory < src/main/resources/sql/schema.sql

# 3. 키를 환경변수로 넣는다 (없으면 그 기능만 건너뛴다)
export KAKAO_REST_API_KEY=...
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...

# 4. 실행
./gradlew bootRun
```

http://localhost:8080

> 키가 없어도 앱은 뜬다. 장소 검색과 소셜 로그인만 동작하지 않는다.
> 기본값을 빈 문자열로 두어, 키가 없다고 실행 자체가 막히지는 않게 했다.

---

## 로컬과 운영은 완전히 다른 환경이다

같은 코드가 돌지만 **DB 도 사진도 서로 분리돼 있다.** 로컬에서 올린 사진은 운영에 없고, 그 반대도 마찬가지다.

| | 로컬 | 운영 |
|---|---|---|
| 주소 | localhost:8080 (http) | mealmates.duckdns.org (https) |
| DB | 내 노트북의 MySQL | EC2 안의 MySQL — **별개** |
| 사진 | `./uploads` | EC2 디스크 — **별개** |
| 프로파일 | 없음 | `prod` |
| HTML 캐시 | 끔 (고치면 바로 보이게) | 켬 |
| 쿠키 `secure` | 끔 (http 라 켜면 로그인 불가) | 켬 |
| SQL 로그 | `debug` (배우는 중) | `warn` |

운영에서만 다른 값은 `application-prod.yml` 에 모아 두고,
서버에서 `SPRING_PROFILES_ACTIVE=prod` 로 켠다. 프로파일은 덮어쓰는 게 아니라 올려놓는 방식이라,
운영에서 바꿀 것만 적으면 나머지는 `application.yml` 그대로다.

---

## 배포

**`main` 에 push 하면 그것이 곧 배포다.**

```
push → GitHub Actions
         ├ bootJar 로 빌드
         ├ scp 로 EC2 에 전송
         ├ systemctl restart foodmemory
         └ /login 이 200 을 줄 때까지 확인 (아니면 실패로 표시)
```

`build` 가 아니라 `bootJar` 로 빌드한다. `build` 는 테스트까지 도는데 그 테스트에는 MySQL 이 필요하고,
GitHub 이 빌려주는 컴퓨터에는 DB 가 없어서 반드시 실패한다.

재시작 후 실제로 응답하는지까지 확인한다. "재시작했다" 와 "살아났다" 는 다른 말이다.

---

## 만들면서 고민한 것

### Spring Security 를 넣지 않았다

인증을 직접 만들었다. `LoginCheckInterceptor` 로 출입을 한곳에서 막고,
`@Login` 애노테이션과 `ArgumentResolver` 로 컨트롤러에 로그인 정보를 넣어준다.

`spring-boot-starter-security` 를 넣으면 필터 체인·인증 매니저·기본 로그인 화면이 통째로 딸려 와
모든 요청이 프레임워크의 보안 필터를 먼저 지난다. 직접 만든 인터셉터와 서로 부딪힌다.
비밀번호 해싱만 필요했으므로 `spring-security-crypto` 만 넣었다. 이 모듈은 아무것도 가로채지 않는다.

대신 프레임워크가 대신 해주던 것을 직접 챙겨야 했다.

- **로그인 성공 시 세션 재발급** — 세션 고정 공격 방어.
  공격자가 미리 아는 세션 ID 를 심어두고 로그인을 유도하면, ID 가 그대로일 때 그 계정에 그대로 들어갈 수 있다
- **로그아웃은 `invalidate()`** — `removeAttribute` 로 로그인 정보만 지우면 세션은 살아 있어 다른 값이 남는다
- **로그아웃은 POST** — GET 이면 `<img src="/logout">` 한 줄이 박힌 다른 사이트를 보기만 해도 로그아웃된다
- **돌아갈 주소 검사** — `?redirectUrl=` 를 그대로 믿으면 로그인 직후 남의 사이트로 보낼 수 있다.
  `/` 로 시작하는 우리 경로만 허용한다

### 세션을 Redis 로 옮겼다

세션은 톰캣의 메모리 안에 있었다. 그런데 배포할 때마다 `systemctl restart` 로 프로세스가 새로 뜨고,
**그 순간 모든 사람이 한꺼번에 로그아웃됐다.**

Redis 는 앱과 따로 도는 저장소라 앱이 죽었다 살아나도 세션이 남는다.
`HttpSession` 을 쓰는 코드는 한 줄도 바뀌지 않았다. 보관 장소만 바뀐다.

쿠키에 `max-age` 를 함께 줬다. **로그인 유지에는 시계가 둘**이고 짧은 쪽이 실제 수명이 된다.
서버 세션만 늘리고 쿠키를 그대로 두면, 브라우저를 닫는 순간 번호표를 잃어 세션을 찾아갈 수 없다.

### 무한 스크롤은 `Page` 가 아니라 `Slice`

`Page` 는 전체 건수를 세려고 count 쿼리를 한 번 더 보낸다.
무한 스크롤에 필요한 것은 전체 건수가 아니라 "다음이 있느냐" 뿐이다.
`Slice` 는 요청한 개수보다 한 건 더 읽어 그것으로 판단한다. 쿼리가 하나 줄어든다.

정렬에는 `postId` 를 뒤에 덧붙인다. 같은 날짜가 여럿이면 DB 가 그 사이 순서를 보장하지 않아,
페이지마다 순서가 달라지면 **1페이지에 나온 게 2페이지에 또 나오거나 아예 빠진다.**

다음 페이지는 JSON 이 아니라 **HTML 조각**으로 준다. JSON 으로 주면 사진 칸을 만드는 코드를
자바스크립트에도 똑같이 써야 한다. 화면을 만드는 곳이 두 군데가 되면 한쪽만 고치는 실수가 생긴다.

### `ddl-auto: validate`

JPA 가 테이블을 건드리지 않게 한다. 테이블은 `schema.sql` 로 직접 만들고,
JPA 는 엔티티와 실제 테이블이 맞는지 **검사만** 한다. 다르면 실행이 실패한다.

`update` 는 JPA 가 테이블을 마음대로 고치고, `create` 는 실행할 때마다 지우고 새로 만든다.
운영 DB 에서 그런 일이 일어나면 되돌릴 수 없다.

### 사진을 고쳐 배포해도 폰에 옛 화면이 남았다

주소가 늘 `/css/app.css` 로 같아서, 브라우저가 "같은 주소면 같은 파일" 이라고 보고
갖고 있던 것을 다시 썼다. 서버에 새 파일이 있어도 물어보지 않는다.

파일 내용으로 이름을 짓게 했다. 내용이 바뀌면 `/css/app-<해시>.css` 로 주소가 달라져
브라우저가 처음 보는 파일로 여긴다. 안 바뀐 파일은 이름도 그대로라 계속 캐시를 쓴다.

### 어디에 올릴지는 고르게 하지 않는다

폼에 "어디에 올릴까요?" 목록이 있었는데, 기본값이 '나만 보기' 였다.
방에 들어가 올리기를 누른 사람은 이미 그 방에 올릴 생각인데, 그대로 두고 올리면 개인 기록으로 들어갔다.
**방금 있던 자리와 다른 곳에 저장되는 셈이다.**

목록을 없애고 **어느 화면에서 눌렀는지로** 정한다. 촬영 버튼도 같은 규칙으로 돈다.
사진을 올리는 길이 둘인데 규칙이 서로 다르면 결과를 예측할 수 없다.

---

## 구조

```
src/main/java/com/foodmemory/app/
├── auth/          인증 — 인터셉터, @Login, 세션 상수, OAuth 클라이언트
├── common/        EXIF 읽기, 파일 저장, 카카오 로컬 API, 예외 처리
├── config/        WebConfig(인터셉터 등록), JPA, PasswordEncoder
├── controller/    화면과 요청
├── dto/           화면에 넘기는 값
├── entity/        member · post · photo · place · space · comment
├── repository/
└── service/

src/main/resources/
├── sql/schema.sql          테이블 정의 (JPA 가 만들지 않는다)
├── static/                 css · js · icons · sw.js · manifest
└── templates/              Thymeleaf
```

**엔티티를 세션에 담지 않는다.** `LoginMember` 라는 별도 값으로 옮겨 담는다.
엔티티를 넣으면 영속성 컨텍스트 밖으로 나가 LAZY 로딩에서 터지고,
비밀번호 해시까지 통째로 세션에 들어간다. 화면에 필요한 것은 이름 정도다.

---

## 앞으로

- 자동 로그인(Remember-Me) — 토큰 회전으로 탈취를 감지하는 방식
- 방 인원 제한 해제 구독 결제
