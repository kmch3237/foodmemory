# 개념 정리 — 이 프로젝트를 만들며 짚은 것들

코드를 짜면서 "왜 이렇게 했는가" 를 설명할 수 있어야 하는 것들만 모았다.
전부 이 저장소 안의 실제 코드에서 나온 이야기다.

---

## 1. GET 과 POST

### HTTP 는 그냥 글자다

브라우저와 서버가 주고받는 것은 텍스트 편지다.

브라우저가 보내는 요청:

```
GET /spaces HTTP/1.1
Host: mealmates.duckdns.org
Cookie: JSESSIONID=A1B2C3
```

서버가 보내는 응답:

```
HTTP/1.1 200 OK
Content-Type: text/html

<!DOCTYPE html>
<html>...
```

첫 줄의 `GET` 이 메서드다. 이 편지가 무슨 용건인지를 나타내는 딱지다.

### 표면적 차이 — 값이 어디에 담기나

GET 은 주소에 붙는다.

```
GET /spaces/join?code=A3K9PQ7M&from=/ HTTP/1.1
                 ^^^^^^^^^^^^^^^^^^^^
                 쿼리스트링. 주소의 일부다
```

POST 는 본문에 담긴다.

```
POST /posts HTTP/1.1
Content-Type: application/x-www-form-urlencoded

content=맛있었다&eatenDate=2026-08-22T19:30
```

값이 주소에 있냐 없냐가 나머지 차이를 전부 만든다.

| | GET | POST |
|---|---|---|
| 주소창에 보이나 | 보인다 | 안 보인다 |
| 북마크 | 된다 | 안 된다 |
| 새로고침 | 그냥 다시 감 | "재전송할까요?" 경고 |
| 브라우저 기록 | 남는다 | 안 남는다 |
| 길이 제한 | 있다 (약 2천자) | 사실상 없다 |
| 파일 전송 | 불가능 | 가능 |

### 본질적 기준

"중요한 데이터는 POST" 가 기준이 아니다. 진짜 기준은 둘이다.

**안전한가 (safe)** — 서버의 상태를 바꾸는가

```
GET   바꾸지 않는다. 몇 번을 불러도 서버는 그대로다
POST  바꾼다. 부를 때마다 저장되거나 지워진다
```

**멱등성 (idempotent)** — 여러 번 해도 결과가 같은가

```
GET  /posts/7    한 번 보나 열 번 보나 같다
POST /posts      열 번 하면 글이 열 개 생긴다
```

### 왜 로그아웃이 링크가 아닌가

`fragments/layout.html` 에서 로그아웃은 폼(POST)이다.

```html
<form th:action="@{/logout}" method="post" style="display:inline">
    <button type="submit" class="btn">로그아웃</button>
</form>
```

링크(GET)로 두면 이렇게 당한다.

```html
<img src="https://mealmates.duckdns.org/logout">
```

이 태그가 있는 페이지를 열기만 해도 브라우저가 그 주소를 GET 으로 부른다.
이미지를 불러오는 것만으로 로그아웃된다.

GET 은 브라우저가 사용자 의사와 무관하게 부를 수 있다.
미리 읽기, 이미지 로딩, 크롤러가 전부 GET 을 던진다.
그래서 상태를 바꾸는 동작을 GET 에 두면 안 된다.

### 예외 — GET 인데 상태를 바꾸는 곳

`SpaceController.join` 은 GET 인데 방에 참여시킨다.

```java
@GetMapping("/spaces/join")
public String join(@RequestParam String code, ...)
```

초대 링크를 눌러 들어오는 경로라 GET 일 수밖에 없다.
허용한 근거는 멱등성이다. 두 번 눌러도 방에 두 번 들어가지지 않는다.

원칙을 어긴 것이 아니라, 원칙이 왜 있는지 알고 판단한 것이다.

---

## 2. 리다이렉트와 PRG 패턴

### redirect 는 HTML 을 보내지 않는다

가장 흔한 오해다. redirect 가 보내는 것은 "저기로 가라" 는 쪽지 한 장이다.

**보통의 경우** — `return "post/detail";`

```
① 브라우저 → 서버   GET /posts/7
② 서버              detail.html 렌더링
③ 서버 → 브라우저   200 OK + <html>...</html>
④ 브라우저          화면에 그린다

요청 1번
```

**redirect** — `return "redirect:/spaces/7";`

```
① 브라우저 → 서버   POST /spaces/join
② 서버              DB 에 저장
③ 서버 → 브라우저   302 Found
                    Location: /spaces/7
                    (본문 없음)
④ 브라우저          "302네? 저기로 가라는 거구나"
                    주소창을 /spaces/7 로 바꿈
⑤ 브라우저 → 서버   GET /spaces/7      ← 브라우저가 스스로
⑥ 서버              space/detail.html 렌더링
⑦ 서버 → 브라우저   200 OK + <html>...
⑧ 브라우저          화면에 그린다

요청 2번
```

302 는 상태 코드다. 200(성공), 404(없음), 403(권한없음) 과 같은 자리의 숫자로,
"옮겨졌다" 는 뜻이다.

### 주소창이 바뀌는 이유

브라우저는 자기가 **마지막으로 요청한 주소** 를 주소창에 띄운다. 그게 전부다.

```
redirect 없음:  마지막 요청 = POST /spaces/join  →  주소창 /spaces/join
redirect 있음:  마지막 요청 = GET  /spaces/7     →  주소창 /spaces/7
```

⑤ 에서 브라우저가 스스로 요청했으므로 주소창도 거기로 바뀐다.

### 왜 새로고침 문제를 막나

F5 는 "주소창의 그 요청을 똑같이 다시 보내라" 는 뜻이다.
주소만이 아니라 **메서드와 본문까지 그대로** 다시 간다.

redirect 없이:

```
① POST /posts (사진 3장)
② 글 7번 생성
③ HTML 직접 반환 → 주소창에 POST 가 남음

   F5

④ 브라우저: "POST 를 다시 보낼까요?" → 확인
⑤ POST /posts (같은 내용)
⑥ 글 8번 생성          ← 같은 글이 또
```

redirect 있으면:

```
① POST /posts
② 글 7번 생성
③ 302 Location: /
④ 브라우저가 GET / 요청 → 주소창에 GET 이 남음

   F5

⑤ GET /                 ← 조회만 다시
⑥ 아무것도 저장 안 됨
```

핵심은 **저장하는 요청을 주소창에 남기지 않는 것** 이다.
남는 것은 안전한 GET 뿐이라 몇 번을 새로고침해도 안전하다.

이 패턴을 PRG 라고 부른다. **P**ost → **R**edirect → **G**et.

`PostController.upload` 가 이 형태다.

```java
@PostMapping("/posts")
public String upload(...) {
    postService.upload(...);            // 저장
    return spaceId == null
            ? "redirect:/"              // 저장 후 GET 으로 넘긴다
            : "redirect:/spaces/" + spaceId;
}
```

### flash 메시지는 어떻게 살아남나

요청이 2번인데 메시지가 넘어가는 이유.

```java
redirectAttributes.addFlashAttribute("error", e.getMessage());
return "redirect:/spaces";
```

```
① POST 처리 중 → "error" 를 세션에 잠깐 넣는다
② 302 응답
③ 브라우저가 GET 요청
④ 서버가 세션에서 꺼내 model 에 넣고, 세션에서는 지운다
⑤ 화면에 표시
```

한 번 쓰고 버려서 flash(반짝)다. F5 를 눌러도 두 번째엔 안 나온다.

### forward 와의 차이

```
forward    서버 안에서 다른 코드로 넘김. 요청 1번. 주소 안 바뀜
redirect   브라우저에게 다시 요청시킴. 요청 2번. 주소 바뀜
```

---

## 3. 스레드와 동시성

### 스레드는 "일하는 손"

코드를 위에서 아래로 실행해나가는 흐름 하나가 스레드다.

웹 서버는 손이 여러 개다. 톰캣 기본값이 200개다.

```
철수 요청  →  1번 손이 처리
영희 요청  →  2번 손이 처리   ← 동시에
민수 요청  →  3번 손이 처리   ← 동시에
```

손이 하나면 철수가 사진 10장 올리는 30초 동안 영희가 멍하니 기다린다.

### 그래서 공유 객체가 위험해진다

`@Component` 를 붙인 스프링 빈은 **하나만 만들어져서 돌려쓴다**(싱글톤).

```
손 1번 ┐
손 2번 ├→  JoinAttemptLimiter 하나  →  attempts Map 하나
손 3번 ┘
```

200개의 손이 Map 하나를 동시에 만진다.

### HashMap 이 깨지는 방식

HashMap 안은 칸이 여러 개인 서랍장이다.
같은 칸에 여러 개가 들어가면 줄로 연결해 매단다.

```
0번 칸: (비어있음)
1번 칸: 36번 → 41번 → 58번      ← 연결 리스트
2번 칸: (비어있음)
```

칸이 꽉 차면 더 큰 서랍장으로 바꾸고 내용을 전부 옮긴다(리사이징).
그 도중에 다른 손이 끼어들어 같이 옮기면 줄이 꼬인다.

```
정상:  A → B → C → 끝

꼬임:  A → B
            ↑ ↓
            C          ← B와 C가 서로를 가리킨다
```

이 줄을 따라가는 코드는 B → C → B → C 를 영원히 돈다.
**무한 루프다.** CPU 하나가 100% 로 붙잡히고 로그에는 아무 에러도 안 남는다.
가장 찾기 어려운 종류의 장애다.

### ConcurrentHashMap

같은 칸을 두 손이 동시에 못 만지게 막는다.

```
손 1번이 3번 칸을 만지는 중
  → 손 2번도 3번 칸을 원하면 잠깐 기다림
  → 손 2번이 5번 칸을 원하면 그냥 바로 함
```

칸 단위로만 잠그므로 거의 안 느려진다.
서랍장 전체를 잠그는 `Collections.synchronizedMap` 보다 훨씬 빠르다.

```java
private final Map<Long, Attempt> attempts = new ConcurrentHashMap<>();
```

타입은 `Map`, 실제 물건은 `ConcurrentHashMap`.
나중에 다른 구현으로 바꿔도 이 줄만 고치면 된다.

### 중간 상태가 보이는 문제

만약 값을 고칠 수 있는 클래스로 만들었다면:

```java
attempt.failures = 5;                     // A줄
attempt.blockedUntil = now.plus(1분);     // B줄
```

```
시각   1번 손                        2번 손
─────────────────────────────────────────────────────
t1     A줄 실행 (failures = 5)

t2                                   attempt 를 읽음
                                     failures = 5
                                     blockedUntil = null
                                     "안 막힌 사람이네" → 통과  ✗

t3     B줄 실행 (blockedUntil 설정)
```

t2 의 상태는 **"5번 틀렸는데 차단은 안 됨"** 이다.
설계할 때 생각조차 안 한 조합인데, A줄과 B줄 사이의 짧은 틈에 실제로 존재한다.

### compute 를 쓰는 이유

ConcurrentHashMap 을 써도 이렇게 짜면 여전히 틀린다.

```java
// 틀린 방법
Attempt a = attempts.get(memberId);            // ① 읽고
int n = a.failures() + 1;                      // ② 더하고
attempts.put(memberId, new Attempt(n, ...));   // ③ 쓰기
```

get 도 안전하고 put 도 안전한데, 셋을 묶으면 안전하지 않다.

```
손1: ① 읽음 → 3
손2: ① 읽음 → 3        ← 손1이 아직 안 썼다
손1: ③ 4를 씀
손2: ③ 4를 씀          ← 5가 되어야 하는데 4
```

두 번 틀렸는데 한 번으로 세어진다. 공격자는 이걸로 제한을 우회할 수 있다.

`compute` 는 그 키에 대해 읽기-계산-쓰기를 한 덩어리로 처리한다.

```java
attempts.compute(memberId, (id, previous) -> {
    ...
    return new Attempt(failures, null, now);
});
```

---

## 4. record

### 문법이지 역할이 아니다

```
record  =  문법 (자바가 제공하는 도구)
DTO     =  역할 (그 자리에서 하는 일)
```

망치와 못 박는 일의 관계다. 망치로 못을 박지만 망치가 곧 못 박기는 아니다.

### 한 줄이 30줄을 대신한다

```java
private record Attempt(int failures, Instant blockedUntil, Instant lastFailedAt) {}
```

이 한 줄이 아래 전부와 같다.

```java
private static final class Attempt {
    private final int failures;
    private final Instant blockedUntil;
    private final Instant lastFailedAt;

    Attempt(int failures, Instant blockedUntil, Instant lastFailedAt) {
        this.failures = failures;
        this.blockedUntil = blockedUntil;
        this.lastFailedAt = lastFailedAt;
    }

    public int failures() { return failures; }
    public Instant blockedUntil() { return blockedUntil; }
    public Instant lastFailedAt() { return lastFailedAt; }

    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
    @Override public String toString() { ... }
}
```

게터 이름이 `getFailures()` 가 아니라 `failures()` 인 것이 특징이다.

### 핵심은 못 고친다는 것

record 의 필드는 전부 final 이다.

```java
Attempt a = new Attempt(3, null, now);
a.failures = 4;        // 컴파일 에러
```

값을 바꾸려면 새로 만들어 통째로 갈아끼우는 수밖에 없다.
그래서 위에서 본 "중간 상태" 가 존재할 수 없다.

```
옛것 아니면 새것. 둘 다 완성된 값이라 어느 쪽을 봐도 말이 된다.
```

**고칠 수 없으면 고치는 중간도 없다.**

### DTO 인지 아닌지는 따로 본다

| 코드 | record | DTO | 하는 일 |
|---|---|---|---|
| `LoginMember` | O | O | 세션에 담겨 계층 사이를 오간다 |
| `GalleryPage` | O | O | 서비스 → 컨트롤러로 결과 전달 |
| `Attempt` | O | **X** | private. 클래스 밖으로 안 나간다 |

DTO 는 Data Transfer Object, **옮기는** 물건이다.
`Attempt` 는 아무 데도 안 건너간다. 한 클래스 안에서 값 셋을 묶었을 뿐이다.

반대로 record 를 안 써도 DTO 일 수 있다.
자바 16 이전에는 전부 일반 클래스로 썼다.

record 를 보면 "DTO 구나" 가 아니라 **"이게 어디로 옮겨 다니나"** 를 봐야 한다.

---

## 5. 열린 리다이렉트 (Open Redirect)

### 문제가 되는 구조

폼이 "실패하면 어디로 돌아갈지" 를 서버에 보낸다.

```html
<input type="hidden" name="from" value="/">
```

서버가 그 값을 그대로 믿으면:

```java
return "redirect:" + from;      // 위험
```

### 공격 방법

`from` 은 화면이 보내는 값이라 누구나 바꿀 수 있다.

```
https://mealmates.duckdns.org/spaces/join?code=X&from=https://가짜사이트.com
```

이 링크를 뿌린다. 받은 사람이 보는 도메인은 우리 것이다.

```
① 사용자: 아는 주소네 → 클릭 (안심)
② 우리 서버: 302 Location: https://가짜사이트.com
③ 브라우저: 가짜사이트로 이동
④ 사용자: "로그아웃됐네" 하고 비밀번호 입력
⑤ 공격자: 비밀번호 획득
```

**우리 서버가 공격자의 심부름을 해준 것이다.** 우리 도메인의 신뢰를 빌려줬다.

### 막는 코드

```java
private String safeReturnPath(String from) {
    if (from == null || from.isBlank()
            || !from.startsWith("/") || from.startsWith("//")) {
        return "/spaces";
    }
    return from;
}
```

| 입력 | 결과 | 이유 |
|---|---|---|
| `/` | 통과 | 우리 사이트 안 |
| `/spaces` | 통과 | 우리 사이트 안 |
| `https://가짜.com` | 차단 | `/` 로 시작 안 함 |
| `//가짜.com` | 차단 | 아래 설명 |

### `//` 를 따로 막는 이유

가장 놓치기 쉬운 부분이다.

```
//가짜사이트.com
```

`/` 로 시작하니 첫 검사는 통과한다. 그런데 브라우저는 이것을 바깥 주소로 읽는다.

```
//가짜.com  →  브라우저가 https://가짜.com 으로 해석
```

**프로토콜 상대 URL** 이라고 한다.
"지금 쓰는 프로토콜 그대로, 저 사이트로 가라" 는 뜻이다. 그래서 따로 막아야 한다.

`AuthController` 의 로그인 후 복귀 처리에도 같은 검사가 있다.

---

## 6. 초대 코드 무차별 대입 방어

### 코드 공간의 크기

```java
private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";  // 31글자
private static final int CODE_LENGTH = 8;
```

```
31^8 = 852,891,037,441  =  약 8,529억 가지
```

0/O, 1/I/l 을 뺐다. 눈으로 옮겨 적거나 말로 불러줄 때 헷갈리는 글자다.

한 번 찍어 맞을 확률은 1/853억. 로또 1등(1/814만)보다 만 배쯤 어렵다.
**사람이 손으로 맞히는 것은 불가능하다.**

### 그런데 안전하지 않다

문제는 자동화다. 그리고 방이 늘수록 표적도 는다.

| 공격 속도 | 방 개수 | 평균 소요 |
|---|---|---|
| 초당 100회 | 10개 | 27년 |
| 초당 100회 | 1,000개 | **99일** |
| 초당 1,000회 | 1,000개 | **10일** |

99일이면 뚫린다. "거의 불가능" 이 아니라 "석 달 기다리면 됨" 이다.

### 코드를 길게 하는 것은 답이 아니다

초대 코드는 카톡으로 보내고 말로 불러주고 손으로 옮겨 적는 물건이다.
32자리로 늘리면 안전해지지만 사람이 쓸 수 없다.

**보안만 생각하면 못 쓰는 물건이 된다.**

### 답 — 속도를 늦춘다

도어락이 하는 방식 그대로다. 5번 틀리면 1분간 차단.

```
전:  초당 100번  =  분당 6,000번
후:            분당 5번        ← 1200배 느려짐
```

99일이 32만 년이 된다. **코드 길이는 그대로인데 안전해진다.**

추측을 어렵게 만드는 대신 추측하는 속도를 늦춘 것이다.

### 설계에서 신경 쓴 것

**차단 확인이 코드 조회보다 먼저다**

```java
joinAttemptLimiter.requireNotBlocked(memberId);          // 먼저
Space space = spaceRepository.findByInviteCode(code)     // 나중
```

순서가 반대면 막아둔 사람도 계속 DB 를 조회한다.
차단은 결과를 감추는 것이 아니라 조회 자체를 막는 것이다.

**성공하면 카운트를 지운다**

```java
joinAttemptLimiter.recordSuccess(memberId);
```

안 지우면 오타 네 번 끝에 들어간 사람이 다음번 한 번의 오타로 막힌다.
공격자가 아니라 손가락 굵은 사용자를 막게 된다.

**빈 입력은 실패로 세지 않는다**

맞히려는 시도가 아니라 잘못 누른 것이다.
막아야 할 것과 아닌 것을 구분해야 애먼 사람이 안 걸린다.

### 메모리에 둔 이유와 그 한계

DB 에 쓰면 막으려는 공격이 오히려 DB 부하가 된다.
지금은 서버가 한 대라 메모리로 충분하다.

다만 두 가지를 감수한 것이다.

```
서버를 재시작하면 기록이 사라진다
서버가 여러 대가 되면 각자 세므로 실질 제한이 (5번 × 서버 수) 가 된다
```

서버를 늘리게 되면 Redis 처럼 서버 밖의 저장소로 옮겨야 한다.

### IP 가 아니라 회원 단위로 센 이유

참여는 로그인해야 하므로 시도하는 사람이 누구인지 항상 안다.

```
IP 기준  →  같은 와이파이를 쓰는 사람들이 서로 막는다
            VPN 으로 쉽게 우회된다
```

---

## 부록 — 이 프로젝트에서 세 번 겪은 함정

> **고쳤는데 안 바뀌면, 내가 고친 곳이 그 값을 정하는 곳이 맞는지부터 확인한다**

| 겪은 일 | 진짜 원인 |
|---|---|
| HTML 을 고쳤는데 화면이 그대로 | 서버는 `src/` 가 아니라 `build/resources/` 를 본다 |
| nginx 설정을 고쳤는데 그대로 | 그 헤더를 정하는 것은 nginx 가 아니라 Spring 이었다 |
| CSS 를 배포했는데 폰에서 그대로 | 서비스 워커가 옛 캐시를 주고 있었다 |
| 코드를 고쳤는데 사이트가 그대로 | 커밋·배포를 안 했다. 보고 있던 곳이 운영 서버였다 |

겉모습은 매번 달랐지만 원인은 하나다.
**바꾼 곳과 값을 정하는 곳이 달랐다.**

확인 순서:

```
1. 지금 보고 있는 곳이 어디인가? (로컬인가 운영인가)
2. 서버가 실제로 주는 파일에 그것이 들어 있나? (curl 로 확인)
3. 중간에 캐시가 끼어 있나? (브라우저, 서비스 워커, nginx)
```
