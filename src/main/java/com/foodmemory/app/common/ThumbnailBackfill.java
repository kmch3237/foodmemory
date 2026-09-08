package com.foodmemory.app.common;

import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 썸네일이 없는 사진에 뒤늦게 사본을 만들어 붙인다.
 *
 * ── 왜 필요한가 ──
 *
 * 썸네일 기능은 '앞으로 올라올 사진' 에만 적용된다.
 * 그런데 지금 목록에 보이는 사진은 전부 그 전에 올라온 것들이라,
 * 이게 없으면 정작 화면은 하나도 안 바뀐다. 기능을 만들고도 효과가 0 이 된다.
 *
 * 업로드할 때 일시적으로 실패한 사진도 여기서 함께 구제된다.
 *
 * ── 왜 앱이 뜰 때 하나 ──
 *
 * 이 일을 시킬 화면이나 주소를 따로 만들면, 한 번 쓰고 말 것이 계속 남는다.
 * 그 주소를 누가 부를 수 있는지도 같이 지켜야 한다.
 * 앱이 뜰 때 알아서 하고, 할 일이 없으면 조용히 지나가는 편이 관리할 것이 적다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailBackfill {

    /**
     * 한 번 뜰 때 처리할 최대 장수.
     *
     * 제한을 두는 이유:
     *   사진이 수천 장 쌓인 뒤에 이 일이 한꺼번에 돌면 서버가 오래 붙잡힌다.
     *   운영 서버는 램이 1GB 뿐이라 더 조심해야 한다.
     *   남은 것은 다음에 뜰 때 이어서 한다. 급한 일이 아니다.
     */
    private static final int BATCH_SIZE = 100;

    private final PhotoRepository photoRepository;
    private final FileStorage fileStorage;

    /**
     * ApplicationReadyEvent — 앱이 완전히 뜬 뒤에 불린다.
     *
     * 이 시점에는 웹 서버가 이미 요청을 받고 있다.
     * 그래서 여기서 시간을 좀 써도 사이트가 멈추지는 않는다.
     * 별도 흐름(@Async)으로 빼지 않은 이유가 그것이다.
     * 그러자고 앱 전체에 비동기 장치를 켜면, 이 일 하나를 위해
     * 다른 모든 곳의 동작 방식까지 건드리게 된다.
     *
     * 대신 한 번에 처리할 양을 BATCH_SIZE 로 묶어둔다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void fillMissingThumbnails() {
        List<Photo> targets = photoRepository.findByThumbPathIsNull(PageRequest.of(0, BATCH_SIZE));
        if (targets.isEmpty()) {
            return;   // 평소에는 여기서 끝난다. 로그도 남기지 않는다
        }

        log.info("썸네일이 없는 사진 {}장을 확인합니다.", targets.size());

        int made = 0;
        int skipped = 0;
        for (Photo photo : targets) {
            String thumbPath = fileStorage.storeThumbnail(photo.getFilePath());
            if (thumbPath == null) {
                // HEIC 처럼 못 만드는 형식이다. 다음에 떠도 또 시도하겠지만,
                // 실패해도 원본을 보여주므로 화면은 멀쩡하다.
                skipped++;
                continue;
            }
            // 변경 감지로 UPDATE 된다. 트랜잭션 안에서 조회한 엔티티라 save() 를 부르지 않아도 된다.
            photo.attachThumbnail(thumbPath);
            made++;
        }

        log.info("썸네일 보정 완료: {}장 생성, {}장 건너뜀", made, skipped);
    }
}
