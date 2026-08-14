package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpaceRepository extends JpaRepository<Space, Long> {

    /** 초대 코드로 공간을 찾는다. UNIQUE 라 결과는 0건 또는 1건이다. */
    Optional<Space> findByInviteCode(String inviteCode);

    /**
     * 내가 참여 중인 공간 목록.
     *
     * space 에서 시작해 space_member 를 조인한다.
     * 반대로 space_member 에서 시작하면 SpaceMember 객체가 나와서
     * 화면에 쓰려면 매번 getSpace() 를 한 번 더 거쳐야 한다.
     */
    @Query("""
            select s from Space s
            join SpaceMember sm on sm.space = s
            where sm.member.memberId = :memberId
            order by s.spaceId desc
            """)
    List<Space> findMySpaces(@Param("memberId") Long memberId);
}
