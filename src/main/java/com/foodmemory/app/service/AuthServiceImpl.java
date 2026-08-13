package com.foodmemory.app.service;

import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.auth.OAuthAuthentication;
import com.foodmemory.app.auth.PendingSignUp;
import com.foodmemory.app.auth.oauth.OAuthClients;
import com.foodmemory.app.auth.oauth.OAuthUserInfo;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.dto.LinkedIdentity;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.MemberIdentity;
import com.foodmemory.app.entity.Provider;
import com.foodmemory.app.repository.MemberIdentityRepository;
import com.foodmemory.app.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 비밀번호 최소 길이. 짧으면 대입으로 뚫린다. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final MemberRepository memberRepository;
    private final MemberIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuthClients oAuthClients;

    /* ── 자체 가입 / 로그인 ───────────────────────────────────── */

    @Override
    @Transactional
    public LoginMember signUpLocal(String email, String password, String nickname,
                                   boolean agreedToTerms) {
        // 동의 여부를 가장 먼저 본다. 동의하지 않았다면 나머지 검사는 의미가 없다.
        if (!agreedToTerms) {
            throw new IllegalArgumentException("약관에 동의해야 가입할 수 있습니다.");
        }

        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);

        String trimmedNickname = nickname == null ? "" : nickname.trim();
        if (trimmedNickname.isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해주세요.");
        }

        /*
         * 이미 가입한 이메일인지 먼저 확인한다.
         *
         * DB 의 UNIQUE 제약이 있는데도 여기서 또 확인하는 이유:
         *   제약만 믿으면 중복 가입 시 DataIntegrityViolationException 이 올라온다.
         *   사용자에게는 무슨 말인지 알 수 없는 500 화면이 뜬다.
         *   미리 확인해서 "이미 가입된 이메일입니다" 라고 알려주는 것이 화면의 몫이다.
         *
         * 그렇다고 DB 제약을 빼면 안 된다. 두 사람이 같은 이메일로 동시에 가입 버튼을
         * 누르면 둘 다 이 검사를 통과할 수 있다. 마지막 방어선은 DB 여야 한다.
         */
        identityRepository.findWithMember(Provider.LOCAL, normalizedEmail)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("이미 가입된 이메일입니다.");
                });

        // 회원과 로그인 수단을 함께 만든다.
        // 같은 트랜잭션이므로 중간에 실패하면 둘 다 저장되지 않는다.
        // 회원만 남고 로그인 수단이 없으면 아무도 들어갈 수 없는 유령 계정이 된다.
        Member member = memberRepository.save(Member.signUp(trimmedNickname, normalizedEmail));
        identityRepository.save(MemberIdentity.ofLocal(
                member, normalizedEmail, passwordEncoder.encode(password)));

        log.info("자체 가입 완료: memberId={}", member.getMemberId());
        return LoginMember.from(member);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginMember loginLocal(String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        MemberIdentity identity = identityRepository
                .findWithMember(Provider.LOCAL, normalizedEmail)
                .orElseThrow(AuthServiceImpl::loginFailed);

        // 소셜 수단에는 비밀번호가 없다. LOCAL 로 조회했으므로 여기 올 일은 없지만,
        // null 을 그대로 비교에 넘기지 않도록 막아둔다.
        if (identity.getPasswordHash() == null) {
            throw loginFailed();
        }

        // 저장된 해시와 입력값을 비교한다. 해시를 되돌리는 것이 아니라,
        // 입력값을 저장된 해시 속 솔트로 다시 계산해 같은지 본다.
        if (!passwordEncoder.matches(password, identity.getPasswordHash())) {
            throw loginFailed();
        }

        return LoginMember.from(identity.getMember());
    }

    /* ── 소셜 로그인 ─────────────────────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public OAuthAuthentication authenticateWithOAuth(Provider provider, String code) {
        OAuthUserInfo userInfo = oAuthClients.get(provider).fetchUserInfo(code);

        /*
         * 찾는 기준이 이메일이 아니라 (제공자, 제공자가 매긴 번호) 인 점이 중요하다.
         *   - 이메일은 바뀔 수 있고, 제공자가 아예 주지 않을 수도 있다
         *   - 이메일로 찾으면 남의 이메일로 만든 소셜 계정이 기존 계정을 삼킬 수 있다
         *
         * 계정을 합치는 것은 이미 로그인한 사람이 직접 '연결' 을 눌렀을 때만 한다.
         */
        return identityRepository.findWithMember(provider, userInfo.providerUserId())
                .<OAuthAuthentication>map(identity ->
                        new OAuthAuthentication.Registered(LoginMember.from(identity.getMember())))
                .orElseGet(() -> new OAuthAuthentication.NotRegistered(
                        new PendingSignUp(
                                provider,
                                userInfo.providerUserId(),
                                userInfo.nickname(),
                                userInfo.email())));
    }

    @Override
    @Transactional
    public LoginMember completeSocialSignUp(PendingSignUp pending, String nickname,
                                            boolean agreedToTerms) {
        if (!agreedToTerms) {
            throw new IllegalArgumentException("약관에 동의해야 가입할 수 있습니다.");
        }

        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) {
            trimmed = fallbackNickname(pending);
        }
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("닉네임은 50자 이하로 입력해주세요.");
        }

        /*
         * 저장 직전에 다시 확인한다.
         *
         * 인증 단계에서 이미 "없는 수단" 임을 확인했는데도 또 보는 이유:
         *   그 사이에 다른 탭에서 같은 계정으로 가입을 끝냈을 수 있다.
         *   사용자가 가입 화면을 열어둔 채 시간을 보내는 일은 흔하다.
         */
        var existing = identityRepository.findWithMember(
                pending.provider(), pending.providerUserId());
        if (existing.isPresent()) {
            return LoginMember.from(existing.get().getMember());
        }

        Member member = memberRepository.save(Member.signUp(trimmed, pending.email()));
        identityRepository.save(MemberIdentity.ofSocial(
                member, pending.provider(), pending.providerUserId()));

        log.info("{} 소셜 가입 완료: memberId={}", pending.provider(), member.getMemberId());
        return LoginMember.from(member);
    }

    /* ── 계정 연결 ───────────────────────────────────────────── */

    @Override
    @Transactional
    public void linkIdentity(Long memberId, PendingSignUp pending) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다."));

        /*
         * 이 소셜 계정이 이미 누군가에게 붙어 있는지 본다.
         *
         * 남의 회원에게 붙어 있으면 옮겨오지 않는다. 옮기면 그 사람은 로그인 수단을
         * 하나 잃게 되고, 최악의 경우 계정에 아예 못 들어가게 된다.
         * 그건 우리가 대신 결정할 일이 아니라, 그쪽에서 먼저 연결을 해제해야 하는 일이다.
         */
        var existing = identityRepository.findWithMember(
                pending.provider(), pending.providerUserId());

        if (existing.isPresent()) {
            Long ownerId = existing.get().getMember().getMemberId();
            if (ownerId.equals(memberId)) {
                return;   // 이미 내 계정에 붙어 있다. 아무것도 하지 않는다
            }
            throw new IllegalArgumentException(
                    "이 계정은 이미 다른 회원에 연결되어 있습니다.");
        }

        if (identityRepository.existsByMemberMemberIdAndProvider(memberId, pending.provider())) {
            throw new IllegalArgumentException(
                    pending.provider() + " 계정은 이미 연결되어 있습니다.");
        }

        identityRepository.save(MemberIdentity.ofSocial(
                member, pending.provider(), pending.providerUserId()));

        // 카카오로 먼저 가입해 이메일이 비어 있던 사람이 구글을 연결하면 여기서 채워진다
        member.fillEmailIfMissing(pending.email());

        log.info("{} 계정 연결 완료: memberId={}", pending.provider(), memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkedIdentity> findLinkedIdentities(Long memberId) {
        return identityRepository.findByMemberMemberId(memberId).stream()
                .map(identity -> new LinkedIdentity(
                        identity.getProvider(),
                        describe(identity),
                        identity.getCreatedAt()))
                .toList();
    }

    /** 화면에 보여줄 설명. 자체 로그인만 아이디(이메일)를 함께 보여준다. */
    private String describe(MemberIdentity identity) {
        return switch (identity.getProvider()) {
            case KAKAO -> "카카오 계정";
            case GOOGLE -> "구글 계정";
            case LOCAL -> "이메일 로그인 (" + identity.getProviderUserId() + ")";
        };
    }

    /* ── 공통 ────────────────────────────────────────────────── */

    /**
     * 사용자가 닉네임을 비워둔 경우에 쓸 이름을 지어준다.
     *
     * 제공자가 닉네임을 주지 않는 경우가 있어(프로필 제공에 동의하지 않은 경우)
     * 가입 화면의 기본값이 비어 있을 수 있다. nickname 은 NOT NULL 이라
     * 빈 값으로는 저장할 수 없으므로 여기서 채운다.
     */
    private String fallbackNickname(PendingSignUp pending) {
        // 제공자가 매긴 번호는 길 수 있으므로 뒤 4자리만 붙여 구분한다.
        String suffix = pending.providerUserId();
        if (suffix.length() > 4) {
            suffix = suffix.substring(suffix.length() - 4);
        }

        String name = pending.provider().name();   // KAKAO
        return name.charAt(0) + name.substring(1).toLowerCase() + "회원" + suffix;   // Kakao회원8413
    }

    /**
     * 로그인 실패는 이유를 구분해서 알려주지 않는다.
     *
     * "없는 이메일입니다" 와 "비밀번호가 틀렸습니다" 를 나누면, 공격자가 이메일만 바꿔가며
     * 어떤 이메일이 이 사이트에 가입돼 있는지 알아낼 수 있다.
     * 그것만으로도 가치 있는 정보이고, 이후 표적 공격의 재료가 된다.
     */
    private static IllegalArgumentException loginFailed() {
        return new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    /**
     * 이메일을 소문자로 통일한다.
     *
     * 이메일 주소의 도메인 부분은 대소문자를 구분하지 않는다.
     * 통일하지 않으면 Kim@a.com 과 kim@a.com 이 서로 다른 계정이 되어,
     * 본인이 가입한 줄도 모르고 다시 가입하게 된다.
     */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        }
        String normalized = email.trim().toLowerCase();

        // 형식 검사는 최소한만 한다. 이메일 형식을 정규식으로 완벽하게 거르는 것은
        // 사실상 불가능하고, 무리하게 만들면 정상적인 주소를 막아버린다.
        // 진짜 확인은 인증 메일을 보내는 것이지, 문자열 검사가 아니다.
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
    }
}
