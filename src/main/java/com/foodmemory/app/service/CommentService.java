package com.foodmemory.app.service;

import com.foodmemory.app.dto.CommentPage;

/**
 * 댓글.
 *
 * 공개 범위는 게시물을 따른다.
 *   개인 기록 → 작성자만 읽고 쓸 수 있다
 *   공간 기록 → 그 공간의 참여자 전원이 읽고 쓸 수 있다
 * 그 판단은 PostService.requireCanView 에 맡긴다. 규칙이 한 곳에 있어야 어긋나지 않는다.
 */
public interface CommentService {

    /**
     * 한 게시물의 댓글을 오래된 순으로 한 쪽씩 가져온다.
     * 그 기록을 볼 수 있는 사람만 읽을 수 있다.
     *
     * @param page 0 부터 시작하는 쪽 번호
     */
    CommentPage findByPost(Long postId, Long loginMemberId, int page);

    /**
     * 마지막 쪽 번호. 댓글을 단 직후 그 댓글이 보이는 쪽으로 보내는 데 쓴다.
     *
     * 이게 없으면 댓글을 달고 첫 쪽으로 돌아가는데, 댓글이 오래된 순이라
     * 방금 쓴 댓글은 맨 뒤에 있다. 사용자는 자기 댓글이 안 달렸다고 생각한다.
     */
    int lastPage(Long postId);

    /** 댓글을 단다. 그 기록을 볼 수 있는 사람만 달 수 있다. */
    void create(Long postId, String content, Long loginMemberId);

    /**
     * 댓글을 고친다. 쓴 사람만 고칠 수 있다.
     *
     * 게시물 작성자에게도 열어주지 않는다. 남이 한 말을 바꾸는 것은
     * 그 사람이 하지 않은 말을 하게 만드는 일이다. 지우는 것과 성격이 다르다.
     */
    void update(Long commentId, String content, Long loginMemberId);

    /**
     * 댓글을 지운다. 쓴 사람과 게시물 작성자가 지울 수 있다.
     *
     * 게시물 작성자에게도 허용하는 이유:
     *   내 기록에 달린 불쾌한 말을 내가 치울 수 없으면, 방에 사람을 부르기 어려워진다.
     *   지우는 것은 말을 없애는 것이지 바꾸는 것이 아니라서 위의 수정과 다르게 본다.
     */
    void delete(Long commentId, Long loginMemberId);
}
