package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.service.PostService;
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

    /**
     * 사진 주소의 앞부분. DB 에는 저장하지 않고 화면에서 붙인다.
     * 나중에 S3 로 바꾸면 설정 파일의 이 값만 바꾸면 된다.
     */
    @Value("${app.upload.url-prefix}")
    private String uploadUrlPrefix;

    /**
     * 갤러리 화면. 첫 페이지만 그리고, 나머지는 스크롤에 따라 이어붙인다.
     *
     * 이 화면은 로그인하지 않아도 볼 수 있다. 그래서 loginMember 가 null 일 수 있고,
     * 화면에서는 그때 "로그인" 버튼을 보여준다.
     */
    @GetMapping("/")
    public String gallery(@Login LoginMember loginMember, Model model) {
        model.addAttribute("loginMember", loginMember);

        GalleryPage page = postService.getGallery(0);
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
                       Model model,
                       HttpServletResponse response) {
        GalleryPage galleryPage = postService.getGallery(page);

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
                         @Login LoginMember loginMember,
                         Model model) {
        model.addAttribute("post", postService.getDetail(postId));
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        return "post/detail";
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
     * 좌표만으로는 건물 안 어느 가게인지 확정할 수 없다. 같은 건물에 식당이 다섯 곳이면
     * GPS 로는 전부 같은 위치다. 그래서 자동으로 정하지 않고 사용자가 고르게 한다.
     */
    @GetMapping("/posts/{postId}/restaurants")
    public String selectRestaurant(@PathVariable Long postId, Model model) {
        model.addAttribute("postId", postId);
        model.addAttribute("places", postService.findNearbyPlaces(postId));
        return "post/restaurant-select";
    }

    /** 고른 장소를 게시물의 식당으로 저장한다. 본인 기록에만 지정할 수 있다. */
    @PostMapping("/posts/{postId}/restaurant")
    public String assignRestaurant(@PathVariable Long postId,
                                   @RequestParam("placeId") String placeId,
                                   @Login LoginMember loginMember) {
        postService.assignRestaurant(postId, placeId, loginMember.memberId());
        return "redirect:/posts/" + postId;
    }

    /** 업로드 폼 화면 */
    @GetMapping("/posts/new")
    public String uploadForm() {
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
                         @Login LoginMember loginMember,
                         RedirectAttributes redirectAttributes) {
        try {
            postService.upload(photos, content, eatenDate, loginMember.memberId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/posts/new";
        }
        return "redirect:/";
    }
}
