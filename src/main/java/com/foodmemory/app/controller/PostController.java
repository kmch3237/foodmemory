package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.service.CommentService;
import com.foodmemory.app.service.PostService;
import com.foodmemory.app.service.SpaceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final SpaceService spaceService;
    private final CommentService commentService;

    /**
     * 사진 주소의 앞부분. DB 에는 저장하지 않고 화면에서 붙인다.
     * 나중에 S3 로 바꾸면 설정 파일의 이 값만 바꾸면 된다.
     */
    @Value("${app.upload.url-prefix}")
    private String uploadUrlPrefix;

    /**
     * 내 갤러리. 첫 페이지만 그리고, 나머지는 스크롤에 따라 이어붙인다.
     *
     * 로그인하지 않으면 로그인 화면으로 보낸다.
     * 예전에는 누구나 볼 수 있었고 남의 기록까지 다 보였다.
     * 이제 기록에 주인이 생겼으므로 로그인이 전제가 된다.
     */
    @GetMapping("/")
    public String gallery(@Login LoginMember loginMember, Model model) {
        if (loginMember == null) {
            return "redirect:/login";
        }

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("spaces", spaceService.findMySpaces(loginMember.memberId()));

        GalleryPage page = postService.getMyGallery(loginMember.memberId(), 0);
        model.addAttribute("posts", page.posts());
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("nextPage", page.nextPage());
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        return "post/list";
    }

    /**
     * 무한 스크롤이 이어서 요청하는 다음 페이지.
     *
     * 화면 전체가 아니라 사진 칸들만 돌려준다.
     * 반환값의 "템플릿 :: 조각이름" 형식이 그 조각만 그리라는 뜻이다.
     *
     * JSON 이 아니라 HTML 조각을 주는 이유:
     *   JSON 으로 주면 사진 칸을 만드는 코드를 자바스크립트에도 똑같이 써야 한다.
     *   같은 화면을 만드는 코드가 두 벌이 되면 한쪽만 고치는 실수가 생긴다.
     *   조각을 그대로 받아 붙이면 화면을 만드는 곳은 계속 한 군데다.
     *
     * 다음 페이지가 남았는지는 본문이 아니라 응답 헤더로 알린다.
     * 본문은 화면에 그대로 붙일 HTML 이므로, 거기에 "다음 있음" 같은 표시를 섞으면
     * 붙일 내용과 판단용 값이 뒤엉킨다. 헤더에 두면 둘이 깔끔하게 나뉜다.
     */
    @GetMapping("/posts/more")
    public String more(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Long spaceId,
                       @Login LoginMember loginMember,
                       Model model,
                       HttpServletResponse response) {

        // spaceId 가 있으면 공간 갤러리를, 없으면 내 갤러리를 이어붙인다.
        // 어느 쪽이든 서비스가 권한을 확인하므로 여기서 또 검사하지 않는다.
        GalleryPage galleryPage = (spaceId == null)
                ? postService.getMyGallery(loginMember.memberId(), page)
                : postService.getSpaceGallery(spaceId, loginMember.memberId(), page);

        model.addAttribute("posts", galleryPage.posts());
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        response.setHeader("X-Has-Next", String.valueOf(galleryPage.hasNext()));

        return "post/fragments/gallery-cards :: cards";
    }

    /**
     * 상세 화면.
     *
     * @PathVariable 은 주소에 들어 있는 값을 꺼낸다.
     *   /posts/12  →  postId = 12
     *
     * 주소로 값을 넘기는 이유:
     *   이 주소 자체가 "12번 게시물"이라는 자원을 가리키므로, 링크를 공유하거나
     *   북마크할 수 있다. 검색 조건처럼 부가적인 값은 ?key=value 로 넘긴다.
     *
     * /posts/new 와 /posts/{postId} 가 겹치지 않는 이유는
     * Spring 이 고정된 경로를 변수 경로보다 먼저 확인하기 때문이다.
     */
    @GetMapping("/posts/{postId}")
    public String detail(@PathVariable Long postId,
                         @RequestParam(name = "commentPage", defaultValue = "0") int commentPage,
                         @Login LoginMember loginMember,
                         Model model) {
        // 볼 수 있는 사람인지는 서비스가 판단한다.
        // 개인 기록이면 작성자만, 공간 기록이면 그 공간의 참여자만 통과한다.
        model.addAttribute("post", postService.getDetail(postId, loginMemberId(loginMember)));

        /*
         * 댓글은 상세 화면에서만 필요하므로 여기서 따로 조회한다.
         * 게시물 엔티티에 댓글 목록을 매달아두면 갤러리처럼 댓글이 필요 없는 화면에서도
         * 조회가 일어나기 쉽다. 필요한 화면에서 필요한 만큼만 가져온다.
         *
         * 쪽 번호를 주소(?commentPage=1)로 받는 이유:
         *   '지금 몇 쪽을 보고 있는가' 는 기록에 저장될 성질이 아니라 그 순간의 상태다.
         *   주소에 담아두면 그 쪽을 그대로 링크로 주고받을 수 있고,
         *   댓글을 단 뒤 그 댓글이 있는 쪽으로 돌려보내기도 쉽다.
         */
        model.addAttribute("comments",
                commentService.findByPost(postId, loginMemberId(loginMember), commentPage));

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        return "post/detail";
    }

    /**
     * 수정 폼 화면.
     *
     * 등록 폼(post/form.html)을 재사용하지 않고 따로 만들었다.
     * 등록 폼에는 파일 선택 칸이 필수로 들어 있는데 수정에는 그 칸이 없어야 한다.
     * 한 템플릿에 "등록일 때만 보여줘" 같은 분기를 넣기 시작하면
     * 파일 하나가 두 가지 일을 하게 되어 읽기 어려워진다.
     *
     * 내가 속한 공간 목록을 함께 넘긴다. 다른 방으로 옮길 수 있어야 하기 때문이다.
     */
    @GetMapping("/posts/{postId}/edit")
    public String editForm(@PathVariable Long postId,
                           @Login LoginMember loginMember,
                           Model model) {
        // 남의 기록이면 서비스가 여기서 막는다.
        // 폼을 다 채우고 저장을 눌렀을 때 거부하는 것보다, 열리지 않는 편이 낫다.
        model.addAttribute("form", postService.getEditForm(postId, loginMember.memberId()));
        model.addAttribute("spaces", spaceService.findMySpaces(loginMember.memberId()));
        model.addAttribute("loginMember", loginMember);
        return "post/edit";
    }

    /**
     * 수정 저장.
     *
     * PUT 이 아니라 POST 인 이유:
     *   HTML 의 form 태그는 GET 과 POST 만 보낼 수 있다. 브라우저가 PUT 을 지원하지 않는다.
     *   숨은 필드로 흉내내는 방법이 있지만, 그러려고 설정을 하나 더 켜야 한다.
     *   지금은 얻는 것이 없어서 POST 로 둔다.
     *
     * 저장 뒤 redirect 로 상세 화면에 보내는 이유는 등록과 같다.
     * 화면을 그대로 반환하면 새로고침할 때 브라우저가 POST 를 다시 보내 같은 저장이 반복된다.
     */
    @PostMapping("/posts/{postId}/edit")
    public String edit(@PathVariable Long postId,
                       @RequestParam(value = "content", required = false) String content,
                       @RequestParam(value = "eatenDate", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime eatenDate,
                       @RequestParam(value = "spaceId", required = false) Long spaceId,
                       @Login LoginMember loginMember) {

        postService.update(postId, content, eatenDate, spaceId, loginMember.memberId());
        return "redirect:/posts/" + postId;
    }

    /**
     * 게시물 삭제.
     *
     * GET 이 아니라 POST 인 이유:
     *   GET 은 브라우저가 마음대로 미리 불러오기도 하고, 검색엔진이 링크를 따라가기도 한다.
     *   삭제 링크를 GET 으로 두면 크롤러가 지나가며 글을 전부 지울 수 있다.
     *   실제로 있었던 사고다. 상태를 바꾸는 동작은 GET 으로 두지 않는다.
     *
     * 자격 확인은 여기서 하지 않고 서비스에 맡긴다. 판단을 한 곳에 모아두기 위해서다.
     */
    @PostMapping("/posts/{postId}/delete")
    public String delete(@PathVariable Long postId, @Login LoginMember loginMember) {
        postService.delete(postId, loginMember.memberId());
        return "redirect:/";
    }

    /**
     * 사진 좌표로 찾은 주변 음식점 후보를 보여준다.
     *
     * 좌표만으로는 건물 안 어느 가게인지 확정할 수 없다. 같은 건물에 장소가 다섯 곳이면
     * GPS 로는 전부 같은 위치다. 그래서 자동으로 정하지 않고 사용자가 고르게 한다.
     */
    @GetMapping("/posts/{postId}/places")
    public String selectPlace(@PathVariable Long postId,
                                   @RequestParam(required = false) String keyword,
                                   @Login LoginMember loginMember,
                                   Model model) {
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("postId", postId);
        model.addAttribute("result", postService.findPlaceCandidates(postId, keyword));
        return "post/place-select";
    }

    /**
     * 고른 장소를 게시물의 장소로 저장한다. 본인 기록에만 지정할 수 있다.
     *
     * keyword 를 함께 넘기는 이유는 PostService 에 적어두었다.
     * 요약하면 서버가 같은 검색을 한 번 더 돌려 이 ID 가 실제 결과에 있었는지 확인하기 위해서다.
     */
    @PostMapping("/posts/{postId}/place")
    public String assignPlace(@PathVariable Long postId,
                                   @RequestParam("kakaoPlaceId") String kakaoPlaceId,
                                   @RequestParam(required = false) String keyword,
                                   @Login LoginMember loginMember) {
        postService.assignPlace(postId, kakaoPlaceId, keyword, loginMember.memberId());
        return "redirect:/posts/" + postId;
    }

    /**
     * 업로드 폼 화면.
     *
     * 내가 속한 공간 목록을 함께 넘긴다. 어디에 올릴지 고를 수 있어야 하기 때문이다.
     * 고르지 않으면 개인 기록이 된다.
     */
    @GetMapping("/posts/new")
    public String uploadForm(@Login LoginMember loginMember, Model model) {
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("spaces", spaceService.findMySpaces(loginMember.memberId()));
        return "post/form";
    }

    /**
     * 업로드 처리.
     *
     * MultipartFile 은 브라우저가 올린 파일을 자바에서 받는 타입이다.
     * 사진을 여러 장 올릴 수 있으므로 List 로 받는다.
     *
     * @DateTimeFormat 은 화면에서 넘어온 "2026-08-04T13:00" 같은 문자열을
     * LocalDateTime 으로 바꿔준다. 없으면 타입 변환에 실패한다.
     *
     * 저장 후 redirect 로 돌려보내는 이유:
     *   그냥 화면을 반환하면 사용자가 새로고침할 때 브라우저가 POST 를 다시 보내
     *   같은 글이 두 번 등록된다. 저장 뒤에는 GET 으로 넘겨 이를 막는다.
     */
    @PostMapping("/posts")
    public String upload(@RequestParam("photos") List<MultipartFile> photos,
                         @RequestParam(value = "content", required = false) String content,
                         @RequestParam(value = "eatenDate", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime eatenDate,
                         @RequestParam(required = false) Long spaceId,
                         @Login LoginMember loginMember,
                         RedirectAttributes redirectAttributes) {
        Long postId;
        try {
            postId = postService.upload(photos, content, eatenDate, loginMember.memberId(), spaceId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/posts/new";
        }

        /*
         * 갤러리가 아니라 방금 만든 기록으로 보낸다.
         *
         * 촬영 버튼으로 올리면 코멘트도 장소도 없는 기록이 만들어진다.
         * 갤러리로 돌려보내면 그것들을 붙이려고 다시 찾아 들어가야 한다.
         * 상세 화면에는 '기록 수정' 과 '장소 연결하기' 가 이미 있으므로,
         * 거기로 보내면 이어서 채워 넣을 수 있다.
         *
         * 올린 것이 실제로 어떻게 저장됐는지 바로 보여주는 효과도 있다.
         */
        return "redirect:/posts/" + postId;
    }

    /**
     * 로그인하지 않았으면 null 을 돌려준다.
     *
     * 상세 화면은 인터셉터가 막지 않는다. 그래서 loginMember 가 null 일 수 있고,
     * 그대로 memberId() 를 부르면 NullPointerException 이 난다.
     * 권한 판단은 서비스가 하므로, 여기서는 "로그인 안 함" 을 null 로 전달만 한다.
     */
    private Long loginMemberId(LoginMember loginMember) {
        return loginMember == null ? null : loginMember.memberId();
    }
}
