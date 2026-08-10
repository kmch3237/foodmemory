package com.foodmemory.app.controller;

import com.foodmemory.app.dto.PostListResponse;
import com.foodmemory.app.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** 갤러리 화면 */
    @GetMapping("/")
    public String gallery(Model model) {
        List<PostListResponse> posts = postService.getGallery();
        model.addAttribute("posts", posts);
        model.addAttribute("uploadUrlPrefix", uploadUrlPrefix);
        return "post/list";
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
                         @RequestParam("eatenDate")
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime eatenDate,
                         RedirectAttributes redirectAttributes) {
        try {
            postService.upload(photos, content, eatenDate);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/posts/new";
        }
        return "redirect:/";
    }
}
