package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 댓글.
 *
 * 세 동작이 전부 POST 인 이유:
 *   등록·수정·삭제는 모두 서버의 상태를 바꾼다. 조회만 GET 이다.
 *   상태를 바꾸는 동작을 링크(GET)로 두면 브라우저의 미리 불러오기나
 *   검색엔진 크롤러가 링크를 따라가며 실행시킬 수 있다.
 *
 * 화면이 따로 없는 이유:
 *   댓글은 기록 상세 화면 안에서만 쓰인다. 목록도 폼도 그 화면에 들어 있어서
 *   여기서는 처리만 하고 다시 그 화면으로 돌려보낸다.
 */
@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 댓글 등록.
     *
     * 처리 뒤 redirect 로 상세 화면에 돌려보낸다.
     * 화면을 그대로 반환하면 새로고침할 때 브라우저가 POST 를 다시 보내
     * 같은 댓글이 두 번 등록된다.
     *
     * 실패 메시지는 addFlashAttribute 로 넘긴다.
     * 리다이렉트는 새 요청이라 Model 에 담은 값이 사라지는데,
     * 이 방식은 세션에 잠깐 넣어뒀다가 다음 화면에서 한 번 꺼내 쓰고 지운다.
     */
    @PostMapping("/posts/{postId}/comments")
    public String create(@PathVariable Long postId,
                         @RequestParam String content,
                         @Login LoginMember loginMember,
                         RedirectAttributes redirectAttributes) {
        try {
            commentService.create(postId, content, loginMember.memberId());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("commentError", e.getMessage());
        }

        /*
         * 방금 쓴 댓글이 보이는 쪽으로 돌려보낸다.
         *
         * 댓글은 오래된 순이라 새 댓글은 언제나 맨 뒤에 붙는다.
         * 첫 쪽으로 돌아가면 사용자는 자기 댓글을 못 보고 등록이 안 됐다고 생각한다.
         * 등록에 실패한 경우에도 마지막 쪽이 나오는데, 그 쪽 위에 실패 이유가 함께 보인다.
         */
        return redirectToPost(postId, commentService.lastPage(postId));
    }

    /**
     * 댓글 수정.
     *
     * 주소에 postId 가 함께 들어 있는 이유:
     *   commentId 만으로도 댓글은 찾을 수 있지만, 처리 후 어느 화면으로
     *   돌려보낼지 알아야 한다. 숨은 필드로 받을 수도 있으나
     *   주소에 두면 이 요청이 어느 기록에 속한 일인지 로그에도 남는다.
     *
     * 자격 확인은 서비스가 한다. 쓴 사람만 고칠 수 있다.
     */
    @PostMapping("/posts/{postId}/comments/{commentId}/edit")
    public String update(@PathVariable Long postId,
                         @PathVariable Long commentId,
                         @RequestParam String content,
                         @RequestParam(name = "commentPage", defaultValue = "0") int commentPage,
                         @Login LoginMember loginMember,
                         RedirectAttributes redirectAttributes) {
        try {
            commentService.update(commentId, content, loginMember.memberId());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("commentError", e.getMessage());
        }

        // 보고 있던 쪽으로 그대로 돌아간다.
        // 3쪽에서 고쳤는데 1쪽으로 튕기면 방금 뭘 했는지 확인할 수 없다.
        return redirectToPost(postId, commentPage);
    }

    /** 댓글 삭제. 쓴 사람과 기록 주인이 지울 수 있다. */
    @PostMapping("/posts/{postId}/comments/{commentId}/delete")
    public String delete(@PathVariable Long postId,
                         @PathVariable Long commentId,
                         @RequestParam(name = "commentPage", defaultValue = "0") int commentPage,
                         @Login LoginMember loginMember) {
        commentService.delete(commentId, loginMember.memberId());

        /*
         * 보고 있던 쪽으로 돌아가되, 그 쪽이 사라졌으면 마지막 쪽으로 보낸다.
         *
         * 마지막 쪽에 댓글이 하나만 남아 있을 때 그것을 지우면 그 쪽 자체가 없어진다.
         * 없는 쪽을 그대로 요청하면 빈 화면이 나와, 사용자는 댓글이 전부 사라진 줄 안다.
         */
        return redirectToPost(postId, Math.min(commentPage, commentService.lastPage(postId)));
    }

    /**
     * 상세 화면의 특정 댓글 쪽으로 돌려보낸다.
     *
     * 첫 쪽이면 ?commentPage=0 을 붙이지 않는다.
     * 기본값과 같은 값을 주소에 달아두면 주소만 길어지고 얻는 것이 없다.
     */
    private String redirectToPost(Long postId, int commentPage) {
        if (commentPage <= 0) {
            return "redirect:/posts/" + postId;
        }
        return "redirect:/posts/" + postId + "?commentPage=" + commentPage;
    }
}
