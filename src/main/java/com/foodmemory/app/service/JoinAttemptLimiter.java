package com.foodmemory.app.service;

import com.foodmemory.app.common.TooManyAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 초대 코드를 틀린 횟수를 세어, 짧은 시간에 여러 번 틀리면 잠시 막는다.
 *
 * 왜 필요한가:
 *   초대 코드는 8자리(31^8, 약 8,529억 가지)라 사람이 손으로 맞히는 것은 불가능하다.
 *   문제는 프로그램으로 두드릴 때다. 아무 제한이 없으면 초당 수백 번을 시도할 수 있고,
 *   방이 늘어날수록 표적이 많아져 맞을 확률이 올라간다.
 *   방 1,000개에 초당 100번이면 평균 99일이면 뚫린다. 불가능한 수가 아니다.
 *
 *   코드를 더 길게 만드는 것은 답이 아니다. 초대 코드는 카톡으로 보내고
 *   말로 불러주는 물건이라, 32자리가 되면 사람이 쓸 수 없다.
 *   추측을 어렵게 만드는 대신 추측하는 속도를 늦춘다.
 *   5번 틀리면 1분을 막는 것만으로 초당 100번이 분당 5번이 된다.
 *
 * 왜 DB 가 아니라 메모리인가:
 *   시도할 때마다 DB 를 읽고 쓰면, 막으려는 공격이 오히려 DB 부하가 된다.
 *   지금은 서버가 한 대라 메모리로 충분하다.
 *   서버를 여러 대로 늘리면 각 서버가 따로 세게 되므로(5번씩 × 서버 수),
 *   그때는 Redis 처럼 서버 밖에 두는 저장소로 옮겨야 한다.
 *   재시작하면 기록이 사라지는 것도 같은 이유로 감수한다.
 *
 * 회원 단위로 세는 이유:
 *   참여는 로그인해야 할 수 있으므로 시도하는 사람이 누구인지 항상 안다.
 *   IP 로 세면 같은 와이파이를 쓰는 사람들이 서로 막고, VPN 으로 쉽게 우회된다.
 */
@Slf4j
@Component
public class JoinAttemptLimiter {

    /** 이 횟수만큼 틀리면 막는다. 오타 두세 번은 봐줘야 하므로 너무 낮추지 않는다. */
    private static final int MAX_FAILURES = 5;

    /** 막는 시간. 짧아 보이지만 초당 100번을 분당 5번으로 줄이는 효과가 크다. */
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(1);

    /** 이 시간 동안 틀린 적이 없으면 세던 것을 잊는다. 어제 두 번 틀린 것까지 셀 이유는 없다. */
    private static final Duration FORGET_AFTER = Duration.ofMinutes(10);

    /** 기록이 이만큼 쌓이면 오래된 것을 치운다. 지우지 않으면 메모리가 계속 늘어난다. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    /**
     * HashMap 이 아니라 ConcurrentHashMap 인 이유:
     *   웹 서버는 요청마다 다른 스레드에서 돈다. 두 사람이 동시에 시도하면
     *   같은 Map 을 동시에 건드리게 되는데, HashMap 은 그때 내부가 깨질 수 있다.
     *   값이 틀리는 정도가 아니라 무한 루프에 빠지는 사례가 알려져 있다.
     */
    private final Map<Long, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 한 사람의 시도 기록.
     *
     * record(불변)로 둔 이유:
     *   값을 바꾸는 대신 새로 만들어 갈아끼운다.
     *   필드를 고치는 방식이면 한 스레드가 고치는 도중의 어중간한 상태를
     *   다른 스레드가 볼 수 있다. 통째로 바꾸면 그런 중간이 없다.
     *
     * @param blockedUntil 이 시각까지 막는다. null 이면 막힌 상태가 아니다
     */
    private record Attempt(int failures, Instant blockedUntil, Instant lastFailedAt) {}

    /**
     * 지금 시도해도 되는지 확인한다. 막힌 상태면 예외를 던진다.
     *
     * 코드가 맞는지 보기 전에 이것부터 부른다.
     * 순서가 반대면, 막아둔 사람이 계속 코드를 조회할 수 있어 막은 의미가 없다.
     */
    public void requireNotBlocked(Long memberId) {
        Attempt attempt = attempts.get(memberId);
        if (attempt == null || attempt.blockedUntil() == null) {
            return;
        }

        Instant now = Instant.now();
        if (now.isBefore(attempt.blockedUntil())) {
            // 남은 시간을 올림한다. 0초 남았다고 알리면 눌러도 안 되어 더 헷갈린다
            long seconds = Duration.between(now, attempt.blockedUntil()).toSeconds() + 1;
            throw new TooManyAttemptsException(
                    "초대 코드를 여러 번 잘못 입력했습니다. " + seconds + "초 뒤에 다시 시도해주세요.");
        }
    }

    /**
     * 틀렸을 때 부른다. 정해진 횟수를 넘기면 그때부터 막는다.
     *
     * compute 를 쓰는 이유:
     *   읽고-더하고-쓰는 세 동작 사이에 다른 스레드가 끼어들면 세다가 놓친다.
     *   compute 는 그 키에 대해 한 번에 처리되므로 중간에 끼어들 수 없다.
     */
    public void recordFailure(Long memberId) {
        Instant now = Instant.now();

        attempts.compute(memberId, (id, previous) -> {
            // 한동안 조용했으면 처음부터 다시 센다
            boolean expired = previous == null
                    || previous.lastFailedAt().plus(FORGET_AFTER).isBefore(now);

            int failures = expired ? 1 : previous.failures() + 1;

            if (failures >= MAX_FAILURES) {
                log.warn("초대 코드 반복 실패로 차단: memberId={}, 실패={}회", id, failures);
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
     * 지우지 않으면, 오타 네 번 끝에 성공한 사람이 다음번에 한 번만 틀려도 막힌다.
     */
    public void recordSuccess(Long memberId) {
        attempts.remove(memberId);
    }

    /**
     * 오래된 기록을 치운다.
     *
     * 매번 돌리지 않고 일정 크기를 넘었을 때만 도는 이유:
     *   평소에는 기록이 몇 개뿐이라 청소할 것이 없다.
     *   그 상태에서 매 요청마다 전체를 훑으면 시도가 느려지기만 한다.
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
        log.info("초대 코드 시도 기록 정리 후 크기: {}", attempts.size());
    }
}
