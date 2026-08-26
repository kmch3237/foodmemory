package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.dto.GallerySort;
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

        /*
         * 첫 화면은 사진을 다 보여주는 곳이 아니다.
         * 방 목록과 촬영 버튼이 함께 있는 자리라, 사진이 길게 이어지면 그것들이 밀린다.
         * 여기서는 최근 몇 장만 보여주고, 다 보려면 전체보기로 간다.
         */
        // 요약은 늘 올린 순이다. 방금 올린 것이 맨 앞에 보여야 잘 올라갔는지 알 수 있다.
        GalleryPage page = postService.getMyGallery(
                loginMember.memberId(), 0, PostService.PREVIEW_SIZE, GallerySort.UPLOADED);

        model.addAttribute("posts", page.posts());
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
                       @RequestParam(required = false) String sort,
                       @Login LoginMember loginMember,
                       Model model,
                       HttpServletResponse response) {

        /*
         * 보고 있던 순서를 그대로 이어야 한다.
         * 이 값을 빼먹으면 첫 화면은 먹은 날짜 순인데 스크롤로 붙는 것은 올린 순이 되어,
         * 이미 본 사진이 아래에 또 나오거나 중간이 통째로 빠진다.
         */
        GallerySort gallerySort = GallerySort.from(sort);

        // spaceId 가 있으면 공간 갤러리를, 없으면 내 갤러리를 이어붙인다.
        // 어느 쪽이든 서비스가 권한을 확인하므로 여기서 또 검사하지 않는다.
        GalleryPage galleryPage = (spaceId == null)
                ? postService.getMyGallery(loginMember.memberId(), page,
                                           PostService.FULL_PAGE_SIZE, gallerySort)
                : postService.getSpaceGallery(spaceId, loginMember.memberId(), page,
                                              PostService.FULL_PAGE_SIZE, gallerySort);

        model.addAttribute("posts", galleryPage.posts());
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        response.setHeader("X-Has-Next", String.valueOf(galleryPage.hasNext()));

        return "post/fragments/gallery-cards :: cards";
    }

    /**
     * 전체보기. 사진만 격자로 늘어놓고 스크롤에 따라 계속 이어붙인다.
     *
     * 요약 화면과 나누는 이유:
     *   첫 화면에는 방 목록·촬영 버튼처럼 사진 말고도 놓을 것이 있다.
     *   거기에 사진까지 무한히 이어붙이면 그 둘이 화면 밖으로 밀려 쓸 수 없게 된다.
     *   사진만 보고 싶을 때는 사진만 있는 자리로 오는 편이 낫다.
     *
     * 주소를 /posts/all?spaceId=3 으로 둔 이유:
     *   이미 /posts/more 가 같은 방식으로 갈린다. 내 갤러리냐 방이냐는
     *   spaceId 하나로 정해지므로 화면도 하나면 된다.
     *   /posts/{postId} 와 겹치지 않는 것은 Spring 이 고정된 경로를 먼저 보기 때문이다.
     */
    @GetMapping("/posts/all")
    public String all(@RequestParam(required = false) Long spaceId,
                      @RequestParam(required = false) String sort,
                      @Login LoginMember loginMember,
                      Model model) {

        // 모르는 값이 와도 기본값(올린 순)으로 돌아간다. 주소는 사용자가 고칠 수 있다.
        GallerySort gallerySort = GallerySort.from(sort);

        // 어느 쪽이든 서비스가 권한을 확인한다. 여기서 또 검사하지 않는다.
        GalleryPage page = (spaceId == null)
                ? postService.getMyGallery(loginMember.memberId(), 0,
                                           PostService.FULL_PAGE_SIZE, gallerySort)
                : postService.getSpaceGallery(spaceId, loginMember.memberId(), 0,
                                              PostService.FULL_PAGE_SIZE, gallerySort);

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("posts", page.posts());
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("nextPage", page.nextPage());
        model.addAttribute("spaceId", spaceId);
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);

        // 지금 어떤 순서로 보고 있는지. 고르는 칸이 이 값으로 어느 쪽을 켤지 정한다.
        model.addAttribute("sort", gallerySort.code());
        model.addAttribute("sortOptions", GallerySort.values());

        /*
         * 제목과 돌아갈 곳은 어디서 왔는지에 따라 다르다.
         * 방에서 왔는데 '내 갤러리' 로 돌려보내면 방을 다시 찾아 들어가야 한다.
         */
        if (spaceId == null) {
            model.addAttribute("title", "내 갤러리");
            model.addAttribute("backUrl", "/");
            model.addAttribute("backText", "내 갤러리");
        } else {
            model.addAttribute("title",
                    spaceService.getDetail(spaceId, loginMember.memberId()).name());
            model.addAttribute("backUrl", "/spaces/" + spaceId);
            model.addAttribute("backText", "방으로");
        }

        return "post/all";
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
    public String uploadForm(@RequestParam(required = false) Long spaceId,
                             @Login LoginMember loginMember,
                             Model model) {

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("spaceId", spaceId);

        /*
         * 어디에 올릴지는 '어느 화면에서 눌렀는가' 로 이미 정해진다.
         *
         *   내 갤러리에서 눌렀다 → spaceId 없음 → 개인 기록
         *   방에서 눌렀다       → spaceId 있음 → 그 방
         *
         * 고르는 칸을 없앤 이유:
         *   방에 들어가서 '기록 올리기' 를 누른 사람은 이미 그 방에 올릴 생각이다.
         *   그런데 목록의 기본값이 '나만 보기' 라, 그대로 두고 올리면 방이 아니라
         *   개인 기록으로 들어갔다. 방금 있던 자리와 다른 곳에 저장되는 셈이다.
         *   촬영 버튼도 같은 이유로 화면을 보고 정하게 해두었다. 규칙이 하나여야 한다.
         *
         * 화면에는 어디로 가는지만 알려준다. 고르게 하지 않아도 알 수는 있어야 한다.
         */
        if (spaceId == null) {
            model.addAttribute("targetName", "내 갤러리");
            model.addAttribute("backUrl", "/");
        } else {
            /*
             * 방 이름을 가져오면서 참여자인지도 함께 확인된다.
             * getDetail 은 참여자가 아니면 예외를 던지므로, 주소창에 남의 방 번호를
             * 넣어도 이 화면이 열리지 않는다. 저장할 때 서비스가 한 번 더 본다.
             */
            model.addAttribute("targetName",
                    spaceService.getDetail(spaceId, loginMember.memberId()).name());
            model.addAttribute("backUrl", "/spaces/" + spaceId);
        }

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
        try {
            postService.upload(photos, content, eatenDate, loginMember.memberId(), spaceId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            // 어디에 올리려던 것인지 잃지 않는다. 안 그러면 방에서 실패한 사람이
            // 개인 기록 화면으로 떨어져, 다시 방을 찾아 들어가야 한다.
            return spaceId == null
                    ? "redirect:/posts/new"
                    : "redirect:/posts/new?spaceId=" + spaceId;
        }

        /*
         * 방금 만든 기록이 아니라, 그 기록이 놓인 갤러리로 보낸다.
         *
         *   방에 올렸으면  → 그 방의 갤러리
         *   개인 기록이면  → 내 갤러리(메인)
         *
         * 한동안은 상세 화면으로 보냈다. 코멘트와 장소를 이어서 붙이라는 뜻이었는데,
         * 올리는 사람이 늘 그것을 바로 채우고 싶어하는 것은 아니었다.
         * 여러 장을 연달아 올릴 때는 매번 상세로 끌려 들어가 흐름이 끊긴다.
         *
         * 올린 것이 목록 맨 앞에 나타나므로 잘 올라갔다는 확인은 갤러리에서도 된다.
         * 채워 넣고 싶으면 그 자리에서 눌러 들어가면 되고, 그 길은 한 번만 더 누르면 된다.
         *
         * spaceId 로 판단해도 되는 이유:
         *   참여하지 않은 방이 넘어오면 서비스가 예외를 던져 위에서 이미 걸러진다.
         *   여기까지 왔다는 것은 그 방에 실제로 저장됐다는 뜻이다.
         */
        return spaceId == null
                ? "redirect:/"
                : "redirect:/spaces/" + spaceId;
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
