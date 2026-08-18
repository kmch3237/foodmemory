package com.foodmemory.app.service;

import com.foodmemory.app.common.ForbiddenException;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.dto.CommentPage;
import com.foodmemory.app.dto.CommentResponse;
import com.foodmemory.app.entity.Comment;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Post;
import com.foodmemory.app.repository.CommentRepository;
import com.foodmemory.app.repository.MemberRepository;
import com.foodmemory.app.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostService postService;

    /** 댓글 길이 제한. DDL 의 VARCHAR(300) 과 같은 값이다. */
    private static final int MAX_LENGTH = 300;

    /**
     * 한 쪽에 보여줄 댓글 수.
     *
     * 상수로 빼둔 이유:
     *   조회할 때와 '마지막 쪽이 몇 쪽인가' 를 계산할 때 두 곳에서 쓴다.
     *   숫자를 따로 적어두면 나중에 한쪽만 고쳐서, 마지막 쪽으로 보냈는데
     *   거기에 그 댓글이 없는 일이 생긴다.
     */
    private static final int PAGE_SIZE = 5;

    @Override
    @Transactional(readOnly = true)
    public CommentPage findByPost(Long postId, Long loginMemberId, int page) {
        // 볼 수 없는 기록의 댓글은 읽을 수 없다.
        // 이 검사가 없으면 기록은 감췄는데 그 밑의 대화는 새어 나간다.
        postService.requireCanView(postId, loginMemberId);

        // 주소로 ?commentPage=-1 처럼 넘어올 수 있다.
        // 음수를 그대로 PageRequest 에 넣으면 예외가 나므로 여기서 0 으로 끌어올린다.
        int safePage = Math.max(page, 0);

        Page<Comment> found =
                commentRepository.findPageByPostId(postId, PageRequest.of(safePage, PAGE_SIZE));

        List<CommentResponse> comments = found.getContent().stream()
                .map(CommentResponse::from)
                .toList();

        return new CommentPage(
                comments,
                safePage,
                found.getTotalPages(),
                found.getTotalElements());
    }

    /**
     * 마지막 쪽 번호를 계산한다.
     *
     * (개수 - 1) / 쪽당개수 로 구한다.
     *   댓글 5개 → (5-1)/5 = 0 쪽  (한 쪽에 딱 들어간다)
     *   댓글 6개 → (6-1)/5 = 1 쪽  (두 번째 쪽으로 넘어간다)
     * 댓글이 하나도 없으면 음수가 되므로 0 으로 막는다.
     */
    @Override
    @Transactional(readOnly = true)
    public int lastPage(Long postId) {
        long count = commentRepository.countByPostPostId(postId);
        if (count == 0) {
            return 0;
        }
        return (int) ((count - 1) / PAGE_SIZE);
    }

    @Override
    @Transactional
    public void create(Long postId, String content, Long loginMemberId) {
        String trimmed = requireValidContent(content);

        postService.requireCanView(postId, loginMemberId);

        /*
         * getReferenceById 는 DB 를 조회하지 않고 껍데기 객체(프록시)만 만들어 준다.
         *
         * 여기서 필요한 것은 comment 테이블에 넣을 post_id, member_id 값뿐이고
         * 게시물의 코멘트나 회원의 닉네임은 쓰지 않는다.
         * findById 를 쓰면 쓰지도 않을 값을 읽으려고 SELECT 가 두 번 더 나간다.
         *
         * 없는 번호를 넣으면 여기서는 통과하고 저장할 때 예외가 난다.
         * postId 는 바로 위에서 requireCanView 가 확인했고,
         * memberId 는 로그인 세션에서 온 값이라 실재하는 회원이다.
         */
        Post post = postRepository.getReferenceById(postId);
        Member writer = memberRepository.getReferenceById(loginMemberId);

        Comment comment = commentRepository.save(Comment.create(post, writer, trimmed));

        log.info("댓글 등록: commentId={}, postId={}", comment.getCommentId(), postId);
    }

    @Override
    @Transactional
    public void update(Long commentId, String content, Long loginMemberId) {
        String trimmed = requireValidContent(content);

        Comment comment = findComment(commentId);

        // 쓴 사람만 고칠 수 있다. 게시물 작성자에게도 열어주지 않는다.
        if (!comment.isWrittenBy(loginMemberId)) {
            throw new ForbiddenException("본인이 쓴 댓글만 고칠 수 있습니다.");
        }

        // save() 를 부르지 않는다. 트랜잭션이 끝날 때 변경 감지가 UPDATE 를 만들어 보낸다.
        comment.updateContent(trimmed);
    }

    @Override
    @Transactional
    public void delete(Long commentId, Long loginMemberId) {
        Comment comment = findComment(commentId);

        // 댓글을 쓴 사람이거나, 그 기록의 주인이면 지울 수 있다.
        boolean isCommentWriter = comment.isWrittenBy(loginMemberId);
        boolean isPostOwner = comment.getPost().getMember().getMemberId().equals(loginMemberId);

        if (!isCommentWriter && !isPostOwner) {
            throw new ForbiddenException("이 댓글을 지울 수 없습니다.");
        }

        commentRepository.delete(comment);

        log.info("댓글 삭제: commentId={}", commentId);
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 댓글입니다."));
    }

    /**
     * 댓글 내용을 검사하고 다듬는다.
     *
     * 빈 댓글을 막는 이유는 DDL 의 NOT NULL 과 같은 이유다. 내용이 댓글의 존재 이유다.
     * 공백만 넣은 댓글도 막는다. DB 입장에서는 NOT NULL 을 통과하지만
     * 화면에서는 빈 줄로 보여서 사용자에게는 같은 것이다.
     *
     * 길이를 여기서도 검사하는 이유:
     *   화면의 maxlength 는 브라우저가 지키는 약속일 뿐이라 요청을 직접 만들면 무시된다.
     *   DB 까지 가면 300 자를 넘긴 값은 잘려 들어가거나 오류가 난다.
     *   어느 쪽이든 사용자에게 이유를 설명할 수 없어서 서버가 먼저 걸러낸다.
     */
    private String requireValidContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }

        String trimmed = content.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + MAX_LENGTH + "자까지 쓸 수 있습니다.");
        }

        return trimmed;
    }
}
