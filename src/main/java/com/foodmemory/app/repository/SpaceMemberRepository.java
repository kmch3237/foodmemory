package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.SpaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    /**
     * 이 사람이 그 공간의 참여자인지.
     *
     * 이 한 줄이 곧 접근 권한이다.
     * 공간의 게시물을 보여주기 전에 반드시 확인한다.
     */
    boolean existsBySpaceSpaceIdAndMemberMemberId(Long spaceId, Long memberId);

    /** 공간 참여자 목록. 화면에 '멤버 3명' 을 보여줄 때 쓴다. */
    @Query("""
            select sm.member from SpaceMember sm
            where sm.space.spaceId = :spaceId
            order by sm.spaceMemberId
            """)
    List<Member> findMembersOf(@Param("spaceId") Long spaceId);

    long countBySpaceSpaceId(Long spaceId);
}
