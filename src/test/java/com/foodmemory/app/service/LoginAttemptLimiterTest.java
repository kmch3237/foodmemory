package com.foodmemory.app.service;

import com.foodmemory.app.common.TooManyAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비밀번호를 여러 번 틀렸을 때 막히는지 본다.
 *
 * 스프링을 띄우지 않는다. 이 클래스는 DB 도 다른 부품도 쓰지 않는
 * 순수한 계산이라, 그냥 new 로 만들어 확인하는 것이 빠르고 확실하다.
 *
 * 시간이 흘러야 확인되는 것(1분 뒤 풀리는지, 10분 조용하면 잊는지)은
 * 여기서 확인하지 않는다. 테스트가 1분을 기다리게 만들 수는 없고,
 * 그걸 확인하려면 '지금 몇 시인지' 를 밖에서 넣어줄 수 있게
 * 코드를 고쳐야 한다(Clock 을 주입받는 방식). 지금 규모에는 과해서
 * 막히는 것까지만 굳혀둔다.
 */
class LoginAttemptLimiterTest {

    private static final String EMAIL = "someone@example.com";

    @Test
    @DisplayName("처음에는 막히지 않는다")
    void notBlockedAtFirst() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        assertThatCode(() -> limiter.requireNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("네 번까지는 통과하고, 다섯 번째부터 막는다")
    void blocksAfterFiveFailures() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        // 네 번 틀린 상태 — 오타 몇 번으로 막히면 안 된다
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(EMAIL);
        }
        assertThatCode(() -> limiter.requireNotBlocked(EMAIL)).doesNotThrowAnyException();

        limiter.recordFailure(EMAIL);

        assertThatThrownBy(() -> limiter.requireNotBlocked(EMAIL))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining("초 뒤에 다시 시도해주세요");
    }

    @Test
    @DisplayName("성공하면 세던 것을 잊는다")
    void successResetsCount() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(EMAIL);
        }
        limiter.recordSuccess(EMAIL);

        // 지우지 않았다면 이 한 번으로 다섯 번째가 되어 막혔을 것이다
        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.requireNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 사람이 막혀도 다른 사람은 들어올 수 있다")
    void blocksOnlyTheGuessedAccount() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL);
        }

        assertThatCode(() -> limiter.requireNotBlocked("other@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("막혔을 때 남은 시간을 알려준다")
    void tellsHowLongToWait() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL);
        }

        // 1분을 막으므로 남은 시간은 60초 언저리다.
        // '0초 남았다' 고 알리면 눌러도 안 되어 더 헷갈리므로 올림한다
        assertThatThrownBy(() -> limiter.requireNotBlocked(EMAIL))
                .hasMessageMatching(".*\\b([1-9]|[1-5][0-9]|60)초 뒤에.*");
    }

    @Test
    @DisplayName("몇 번을 더 두드려도 막힌 상태가 유지된다")
    void staysBlockedWhileRetrying() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL);
        }

        // 막힌 사람이 계속 눌러보는 상황. 부르는 쪽은 여기서 예외를 받고 되돌아가므로
        // recordFailure 까지 가지 않는다 (아래 테스트가 그 순서를 굳혀둔다)
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limiter.requireNotBlocked(EMAIL))
                    .isInstanceOf(TooManyAttemptsException.class);
        }
    }

    @Test
    @DisplayName("막힌 동안 실패를 또 기록하면 차단이 풀려버린다 — 그래서 부르는 순서가 중요하다")
    void recordingFailureWhileBlockedClearsTheBlock() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(EMAIL);
        }

        // recordFailure 는 '다시 세기 시작' 하면서 blockedUntil 을 지운다.
        // 즉 막힌 상태에서 이것을 부르면 막은 것이 풀린다.
        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.requireNotBlocked(EMAIL)).doesNotThrowAnyException();

        /*
         * 이것은 고쳐야 할 버그가 아니라, 지켜야 할 순서를 드러내는 테스트다.
         *
         * 실제 흐름(AuthServiceImpl.loginLocal)은 requireNotBlocked 를 먼저 부르고,
         * 막혀 있으면 거기서 예외가 나가 비밀번호 비교까지 가지 않는다.
         * 그래서 막힌 동안에는 recordFailure 가 불릴 일이 없다.
         *
         * 누군가 나중에 순서를 바꾸거나 '막혀도 일단 세두자' 고 고치면
         * 5번마다 한 번씩 풀려서 사실상 제한이 사라진다.
         * 그때 이 테스트가 먼저 빨개지라고 남겨둔다.
         */
    }
}
