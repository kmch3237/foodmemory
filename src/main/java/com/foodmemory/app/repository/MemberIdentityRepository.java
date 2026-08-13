package com.foodmemory.app.repository;

import com.foodmemory.app.entity.MemberIdentity;
import com.foodmemory.app.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberIdentityRepository extends JpaRepository<MemberIdentity, Long> {

    /**
     * 로그인 수단으로 회원을 찾는다.
     *
     * join fetch 로 회원까지 함께 가져오는 이유:
     *   찾은 다음 곧바로 member.getNickname() 을 꺼내 세션에 담는다.
     *   fetch 없이 두면 LAZY 프록시라 그 순간 쿼리가 한 번 더 나가고,
     *   트랜잭션 밖이면 아예 예외가 난다.
     *
     * (provider, provider_user_id) 에 UNIQUE 를 걸어두었으므로 결과는 0건 또는 1건이다.
     */
    @Query("""
            select mi from MemberIdentity mi
            join fetch mi.member
            where mi.provider = :provider
              and mi.providerUserId = :providerUserId
            """)
    Optional<MemberIdentity> findWithMember(@Param("provider") Provider provider,
                                            @Param("providerUserId") String providerUserId);

    /** 한 회원에게 연결된 로그인 수단 전체. '연결된 계정' 화면에서 쓴다. */
    List<MemberIdentity> findByMemberMemberId(Long memberId);

    /** 이 회원이 해당 제공자를 이미 연결했는지. */
    boolean existsByMemberMemberIdAndProvider(Long memberId, Provider provider);
}
