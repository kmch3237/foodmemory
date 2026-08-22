package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.common.TooManyAttemptsException;
import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.service.PostService;
import com.foodmemory.app.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 공유 공간.
 *
 * 연인·친구·동호회처럼 여러 사람이 함께 사진을 올리고 서로의 기록을 보는 곳이다.
 * 초대는 코드로 한다. 코드를 아는 사람이 참여하고, 참여자만 그 공간의 기록을 본다.
 */
@Controller
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final PostService postService;

    @Value("${app.upload.url-prefix}")
    private String uploadUrlPrefix;

    /** 내가 참여 중인 공간 목록. */
    @GetMapping("/spaces")
    public String list(@Login LoginMember loginMember, Model model) {
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("spaces", spaceService.findMySpaces(loginMember.memberId()));
        return "space/list";
    }

    /** 공간을 만든다. 만든 사람은 자동으로 첫 참여자가 된다. */
    @PostMapping("/spaces")
    public String create(@RequestParam String name,
                         @Login LoginMember loginMember,
                         RedirectAttributes redirectAttributes) {
        try {
            Long spaceId = spaceService.create(name, loginMember.memberId());
            return "redirect:/spaces/" + spaceId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/spaces";
        }
    }

    /**
     * 초대 코드로 참여한다.
     *
     * GET 인 이유:
     *   초대 링크를 눌러서 들어오는 경로다. 링크는 GET 일 수밖에 없다.
     *   상태를 바꾸는 동작을 GET 으로 두는 것은 보통 피하지만,
     *   여기서는 "링크를 누른다" 는 것 자체가 사용자의 의사 표시다.
     *   그리고 여러 번 눌러도 결과가 같다(이미 참여 중이면 아무 일도 일어나지 않는다).
     */
    @GetMapping("/spaces/join")
    public String join(@RequestParam String code,
                       @RequestParam(required = false) String from,
                       @Login LoginMember loginMember,
                       RedirectAttributes redirectAttributes) {
        try {
            Long spaceId = spaceService.joinByCode(code, loginMember.memberId());
            redirectAttributes.addFlashAttribute("message", "방에 참여했습니다.");
            return "redirect:/spaces/" + spaceId;
        } catch (IllegalArgumentException | TooManyAttemptsException e) {
            /*
             * 오류 화면으로 보내지 않고 왔던 자리로 돌려보낸다.
             *
             * 코드를 잘못 옮겨 적는 것은 흔한 일이라 사고가 아니다.
             * 400 화면이 뜨면 뒤로 가기를 눌러야 다시 시도할 수 있는데,
             * 그러느니 원래 화면에 안내만 띄우고 그 자리에서 다시 넣게 한다.
             *
             * 두 예외를 함께 잡는 이유는 사용자가 할 일이 같기 때문이다.
             * '다시 입력한다' 로 끝나고, 다른 점은 문구뿐이다.
             */
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:" + safeReturnPath(from);
        }
    }

    /**
     * 실패했을 때 돌아갈 곳을 정한다.
     *
     * 화면이 보낸 값을 그대로 쓰지 않는 이유:
     *   from=https://남의사이트 처럼 넣으면 우리 서비스가 사용자를 그리로 보내준다.
     *   피싱 사이트를 우리 주소로 감싸서 퍼뜨릴 수 있어 열린 리다이렉트라 부른다.
     *   //남의사이트 도 브라우저는 바깥 주소로 읽으므로 함께 막는다.
     *   AuthController 의 로그인 후 복귀 처리와 같은 규칙이다.
     */
    private String safeReturnPath(String from) {
        if (from == null || from.isBlank()
                || !from.startsWith("/") || from.startsWith("//")) {
            return "/spaces";
        }
        return from;
    }

    /**
     * 공간 갤러리. 참여자 전원의 기록이 함께 보인다.
     *
     * 권한 확인은 서비스가 한다. spaceId 만 바꿔서 남의 공간을 열려고 하면 403 이 난다.
     */
    @GetMapping("/spaces/{spaceId}")
    public String detail(@PathVariable Long spaceId,
                         @Login LoginMember loginMember,
                         Model model) {

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("space", spaceService.getDetail(spaceId, loginMember.memberId()));

        GalleryPage page = postService.getSpaceGallery(spaceId, loginMember.memberId(), 0);
        model.addAttribute("posts", page.posts());
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("nextPage", page.nextPage());
        model.addAttribute("spaceId", spaceId);
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        return "space/detail";
    }

    /** 초대 코드를 새로 발급한다. 코드가 밖으로 샜을 때 쓴다. */
    @PostMapping("/spaces/{spaceId}/invite-code")
    public String renewInviteCode(@PathVariable Long spaceId,
                                  @Login LoginMember loginMember,
                                  RedirectAttributes redirectAttributes) {
        spaceService.renewInviteCode(spaceId, loginMember.memberId());
        redirectAttributes.addFlashAttribute("message",
                "초대 코드를 새로 만들었습니다. 이전 코드는 더 이상 쓸 수 없습니다.");
        return "redirect:/spaces/" + spaceId;
    }
}
