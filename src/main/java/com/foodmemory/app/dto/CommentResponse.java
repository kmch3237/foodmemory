package com.foodmemory.app.dto;

import com.foodmemory.app.entity.Comment;

import java.time.LocalDateTime;

/**
 * 화면에 내보낼 댓글 한 건.
 *
 * 엔티티(Comment)를 그대로 넘기지 않는 이유는 다른 DTO 와 같다.
 * 화면에서 comment.getMember().getEmail() 같은 것까지 꺼낼 수 있게 되면
 * 실수 한 번에 남의 이메일이 화면에 찍힌다. 내보낼 값만 골라 담는다.
 */
public record CommentResponse(
        Long commentId,
        String content,
        LocalDateTime createdAt,

        /**
         * 작성자 회원 번호. 화면에서 "내가 쓴 댓글인가" 를 비교하는 데 쓴다.
         * 닉네임으로 비교하면 안 된다. 닉네임은 중복될 수 있어서
         * 같은 이름을 쓰는 남의 댓글에 수정 버튼이 뜬다.
         */
        Long writerId,

        String writerNickname,

        /**
         * 수정된 적이 있는가.
         *
         * 화면에 '(수정됨)' 을 붙이기 위한 값이다. 사람의 말이 소리 없이 바뀌면
         * 읽는 쪽이 자기 기억을 의심하게 된다. 바뀌었다는 사실 자체를 알려준다.
         *
         * 저장할 때 created_at 과 updated_at 에 같은 값이 들어가므로,
         * 둘이 다르면 그 뒤에 고쳐졌다는 뜻이다. 컬럼을 따로 두지 않아도 판단할 수 있다.
         */
        boolean edited
) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getMember().getMemberId(),
                comment.getMember().getNickname(),
                !comment.getCreatedAt().equals(comment.getUpdatedAt())
        );
    }
}
