package com.foodmemory.app.service;

import com.foodmemory.app.common.ForbiddenException;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.dto.SpaceDetail;
import com.foodmemory.app.dto.SpaceSummary;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Space;
import com.foodmemory.app.entity.SpaceMember;
import com.foodmemory.app.repository.MemberRepository;
import com.foodmemory.app.repository.SpaceMemberRepository;
import com.foodmemory.app.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    /**
     * 초대 코드에 쓸 글자.
     *
     * 0/O, 1/I/l 을 뺐다. 코드를 눈으로 보고 옮겨 적거나 말로 불러주는 일이 생기는데,
     * 이 글자들은 서로 헷갈려서 "안 되는데요" 가 나온다.
     * 혼동되는 글자를 빼는 것만으로 그 문의가 사라진다.
     */
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /**
     * 코드 길이.
     *
     * 31글자 중 8자리면 31^8, 약 8,529억 가지다.
     * 짧으면 무작위로 넣어보다 남의 공간에 들어갈 수 있다. 코드 자체가 열쇠이므로
     * '맞히기 어려운 정도' 가 곧 보안 수준이다.
     *
     * 다만 이 크기만으로는 부족하다. 방이 늘어날수록 맞혀야 할 표적도 늘어,
     * 방 1,000개에 초당 100번을 두드리면 평균 99일이면 하나가 뚫린다.
     * 그래서 JoinAttemptLimiter 로 시도 속도 자체를 늦춘다.
     * 길이를 늘리는 것은 답이 아니다. 코드는 사람이 옮겨 적는 물건이다.
     */
    private static final int CODE_LENGTH = 8;

    /**
     * Random 이 아니라 SecureRandom 인 이유:
     *   Random 은 규칙적으로 만들어져 이전 값 몇 개를 보면 다음 값을 계산할 수 있다.
     *   초대 코드는 맞히지 못해야 의미가 있다.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final MemberRepository memberRepository;
    private final JoinAttemptLimiter joinAttemptLimiter;

    @Override
    @Transactional
    public Long create(String name, Long ownerId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("공간 이름을 입력해주세요.");
        }
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("공간 이름은 50자 이하로 입력해주세요.");
        }

        Member owner = memberRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다."));

        Space space = spaceRepository.save(Space.create(trimmed, owner, generateUniqueCode()));

        // 만든 사람도 참여자다. 따로 넣지 않으면 자기가 만든 공간을 못 본다.
        spaceMemberRepository.save(SpaceMember.join(space, owner));

        log.info("공간 생성: spaceId={}, ownerId={}", space.getSpaceId(), ownerId);
        return space.getSpaceId();
    }

    @Override
    @Transactional
    public Long joinByCode(String inviteCode, Long memberId) {
        /*
         * 코드를 확인하기 전에 막힌 사람인지부터 본다.
         *
         * 순서가 반대면 막아둔 사람도 계속 코드를 조회하게 되어 막은 의미가 없다.
         * 차단은 '조회를 못 하게 하는 것' 이지 '결과를 감추는 것' 이 아니다.
         */
        joinAttemptLimiter.requireNotBlocked(memberId);

        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        if (code.isEmpty()) {
            // 빈 입력은 실패로 세지 않는다. 맞히려는 시도가 아니라 그냥 잘못 누른 것이다
            throw new IllegalArgumentException("초대 코드를 입력해주세요.");
        }

        /*
         * 코드가 틀렸을 때 "없는 코드입니다" 라고만 알려준다.
         * 어떤 코드가 존재하는지 알아낼 수 있는 단서를 주지 않기 위해서다.
         * 로그인 실패 메시지를 하나로 통일한 것과 같은 이유다.
         */
        Space space = spaceRepository.findByInviteCode(code).orElseThrow(() -> {
            joinAttemptLimiter.recordFailure(memberId);
            return new IllegalArgumentException("올바르지 않은 초대 코드입니다.");
        });

        // 맞혔으니 세던 것을 지운다.
        // 안 지우면 오타 네 번 끝에 성공한 사람이 다음번 한 번의 오타로 막힌다.
        joinAttemptLimiter.recordSuccess(memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다."));

        // 이미 참여 중이면 아무것도 하지 않고 그 공간으로 보낸다.
        // 오류로 다루면 링크를 두 번 누른 사람에게 실패 화면을 보여주게 된다.
        if (spaceMemberRepository.existsBySpaceSpaceIdAndMemberMemberId(space.getSpaceId(), memberId)) {
            return space.getSpaceId();
        }

        spaceMemberRepository.save(SpaceMember.join(space, member));
        log.info("공간 참여: spaceId={}, memberId={}", space.getSpaceId(), memberId);
        return space.getSpaceId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceSummary> findMySpaces(Long memberId) {
        return spaceRepository.findMySpaces(memberId).stream()
                .map(space -> new SpaceSummary(
                        space.getSpaceId(),
                        space.getName(),
                        spaceMemberRepository.countBySpaceSpaceId(space.getSpaceId()),
                        space.isOwnedBy(memberId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceDetail getDetail(Long spaceId, Long memberId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 공간입니다."));

        requireMember(spaceId, memberId);

        List<String> nicknames = spaceMemberRepository.findMembersOf(spaceId).stream()
                .map(Member::getNickname)
                .toList();

        return new SpaceDetail(
                space.getSpaceId(),
                space.getName(),
                space.getInviteCode(),
                nicknames,
                space.isOwnedBy(memberId));
    }

    @Override
    @Transactional
    public void renewInviteCode(Long spaceId, Long memberId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 공간입니다."));

        // 참여자 아무나 바꿀 수 있으면, 들어온 사람이 코드를 갈아끼워
        // 만든 사람이 다른 사람을 못 부르게 만들 수 있다.
        if (!space.isOwnedBy(memberId)) {
            throw new ForbiddenException("공간을 만든 사람만 초대 코드를 바꿀 수 있습니다.");
        }

        space.renewInviteCode(generateUniqueCode());
        log.info("초대 코드 재발급: spaceId={}", spaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(Long spaceId, Long memberId) {
        return memberId != null
                && spaceMemberRepository.existsBySpaceSpaceIdAndMemberMemberId(spaceId, memberId);
    }

    /* ── 공통 ────────────────────────────────────────────────── */

    private void requireMember(Long spaceId, Long memberId) {
        if (!isMember(spaceId, memberId)) {
            throw new ForbiddenException("참여 중인 공간이 아닙니다.");
        }
    }

    /**
     * 겹치지 않는 초대 코드를 만든다.
     *
     * UNIQUE 제약이 있어도 여기서 확인하는 이유:
     *   제약만 믿으면 겹쳤을 때 DataIntegrityViolationException 이 올라와
     *   사용자에게는 알 수 없는 500 화면이 뜬다.
     *   확률은 낮지만 0 이 아니므로, 겹치면 조용히 다시 만들어 넘어간다.
     *
     * 그렇다고 제약을 빼면 안 된다. 두 요청이 동시에 같은 코드를 만들면
     * 둘 다 이 검사를 통과할 수 있다. 마지막 방어선은 DB 여야 한다.
     */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (spaceRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
            log.warn("초대 코드가 겹쳐 다시 만듭니다.");
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
