package com.foodmemory.app.service;

import com.foodmemory.app.common.TooManyAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호를 틀린 횟수를 세어, 짧은 시간에 여러 번 틀리면 잠시 막는다.
 *
 * 왜 필요한가:
 *   아무 제한이 없으면 프로그램으로 초당 수백 번씩 비밀번호를 넣어볼 수 있다.
 *   사람들이 실제로 쓰는 비밀번호는 몇 개로 쏠려 있어서(생일, 이름+1234,
 *   다른 사이트에서 이미 새어 나간 것), 무제한으로 두드릴 수만 있으면
 *   '어려운 비밀번호' 도 별 의미가 없다. 맞히기 어렵게 만드는 대신
 *   두드리는 속도를 늦춘다. 5번 틀리면 1분을 막는 것만으로
 *   초당 100번이 분당 5번이 된다.
 *
 *   {@link JoinAttemptLimiter} 가 초대 코드에 대해 하는 일과 같다.
 *   왜 DB 가 아니라 메모리인지, 왜 ConcurrentHashMap 과 compute 를 쓰는지,
 *   왜 record 로 통째로 갈아끼우는지는 그쪽 주석에 적어두었다. 여기서는
 *   '로그인이라서 달라지는 것' 만 적는다.
 *
 * 무엇을 기준으로 세는가 — 회원번호가 아니라 이메일:
 *   초대 코드는 로그인한 사람만 넣을 수 있어서 시도하는 사람이 누구인지 안다.
 *   로그인은 그 반대다. 아직 누구인지 모르는 상태에서 세야 한다.
 *   남은 것은 입력받은 이메일뿐이다.
 *
 *   없는 이메일로 시도해도 똑같이 센다. 있는 계정만 세면
 *   '막히면 있는 계정' 이라는 신호를 줘서 계정을 찾는 데 쓰일 수 있다.
 *
 * 이 방식의 약점을 알고 쓴다 — 계정 잠금 공격:
 *   남의 이메일로 일부러 5번 틀리면 그 사람을 1분 동안 못 들어오게 만들 수 있다.
 *   막는 쪽이 오히려 괴롭히는 수단이 되는 것이다.
 *   그래도 이 방식을 쓰는 이유는, 아무 제한 없이 비밀번호를 무한정
 *   두드리게 두는 쪽이 훨씬 나쁘기 때문이다. 차단을 1분으로 짧게 둬서
 *   괴롭힘의 값어치를 낮췄다. 계정을 영영 잠그는 방식이었다면
 *   공격자가 한 번 두드려 두는 것만으로 사람을 영영 가둘 수 있다.
 *
 *   IP 로 세면 이 문제는 없지만 대신 같은 와이파이를 쓰는 가족이
 *   서로를 막고, IP 를 바꾸는 것만으로 쉽게 비켜 갈 수 있다.
 *   제대로 하려면 이메일과 IP 를 함께 봐야 하는데, 지금 규모에는 과하다.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    /** 이 횟수만큼 틀리면 막는다. 자기 비밀번호도 두세 번은 헷갈리므로 너무 낮추지 않는다. */
    private static final int MAX_FAILURES = 5;

    /** 막는 시간. 짧아 보이지만 초당 100번을 분당 5번으로 줄이는 효과가 크다. */
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(1);

    /** 이 시간 동안 틀린 적이 없으면 세던 것을 잊는다. */
    private static final Duration FORGET_AFTER = Duration.ofMinutes(10);

    /**
     * 기록이 이만큼 쌓이면 오래된 것을 치운다.
     *
     * 초대 코드 쪽보다 이 청소가 더 중요하다. 그쪽 열쇠는 실제로 가입한
     * 회원번호라 개수에 한계가 있지만, 여기 열쇠는 남이 적어 넣은 이메일이라
     * 얼마든지 새로 만들어낼 수 있다. 치우지 않으면 메모리가 계속 늘어난다.
     */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * @param blockedUntil 이 시각까지 막는다. null 이면 막힌 상태가 아니다
     */
    private record Attempt(int failures, Instant blockedUntil, Instant lastFailedAt) {}

    /**
     * 지금 시도해도 되는지 확인한다. 막힌 상태면 예외를 던진다.
     *
     * 비밀번호가 맞는지 보기 전에 이것부터 부른다.
     * 순서가 반대면 막아둔 사람이 계속 비밀번호를 두드릴 수 있어 막은 의미가 없다.
     */
    public void requireNotBlocked(String email) {
        Attempt attempt = attempts.get(email);
        if (attempt == null || attempt.blockedUntil() == null) {
            return;
        }

        Instant now = Instant.now();
        if (now.isBefore(attempt.blockedUntil())) {
            // 남은 시간을 올림한다. 0초 남았다고 알리면 눌러도 안 되어 더 헷갈린다
            long seconds = Duration.between(now, attempt.blockedUntil()).toSeconds() + 1;
            throw new TooManyAttemptsException(
                    "비밀번호를 여러 번 잘못 입력했습니다. " + seconds + "초 뒤에 다시 시도해주세요.");
        }
    }

    /**
     * 틀렸을 때 부른다. 정해진 횟수를 넘기면 그때부터 막는다.
     *
     * 반드시 {@link #requireNotBlocked(String)} 뒤에 부른다:
     *   이 메서드는 '다시 세기 시작' 하면서 blockedUntil 을 지운다.
     *   그래서 막혀 있는 동안 이것을 부르면 막아둔 것이 풀린다.
     *   실제 흐름은 막혔으면 requireNotBlocked 에서 예외가 나가 여기까지 오지 않으므로
     *   문제가 되지 않지만, 순서를 뒤집거나 '막혀도 일단 세두자' 고 고치면
     *   5번마다 한 번씩 풀려서 제한이 사실상 사라진다.
     *   LoginAttemptLimiterTest 가 이 순서를 굳혀두고 있다.
     */
    public void recordFailure(String email) {
        Instant now = Instant.now();

        attempts.compute(email, (key, previous) -> {
            // 한동안 조용했으면 처음부터 다시 센다
            boolean expired = previous == null
                    || previous.lastFailedAt().plus(FORGET_AFTER).isBefore(now);

            int failures = expired ? 1 : previous.failures() + 1;

            if (failures >= MAX_FAILURES) {
                // 이메일을 통째로 남기지 않는다. 로그는 대개 평문 파일이라
                // 거기에 계정 목록을 쌓아두는 셈이 된다. @ 앞만 보여도
                // "누가 막혔나" 를 따라가기에는 충분하다
                log.warn("로그인 반복 실패로 차단: email={}, 실패={}회", mask(key), failures);
                // 세던 것을 0으로 되돌린다. 막힘이 풀린 뒤 한 번만 틀려도 또 막히면 가혹하다
                return new Attempt(0, now.plus(BLOCK_DURATION), now);
            }
            return new Attempt(failures, null, now);
        });

        cleanupIfTooMany();
    }

    /**
     * 성공했을 때 부른다. 세던 것을 지운다.
     *
     * 지우지 않으면, 네 번 틀린 끝에 들어온 사람이 다음번에 한 번만 틀려도 막힌다.
     */
    public void recordSuccess(String email) {
        attempts.remove(email);
    }

    /**
     * 오래된 기록을 치운다. 평소에는 기록이 몇 개뿐이라 매번 돌 이유가 없다.
     */
    private void cleanupIfTooMany() {
        if (attempts.size() < CLEANUP_THRESHOLD) {
            return;
        }
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            boolean blocked = attempt.blockedUntil() != null
                    && now.isBefore(attempt.blockedUntil());
            boolean recent = attempt.lastFailedAt().plus(FORGET_AFTER).isAfter(now);
            return !blocked && !recent;
        });
        log.info("로그인 시도 기록 정리 후 크기: {}", attempts.size());
    }

    /** 로그에 남길 때 이메일의 뒷부분을 가린다. kim@gmail.com → kim@*** */
    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.substring(0, at) + "@***";
    }
}
