package com.foodmemory.app.common;

import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

/**
 * 이미 쌓인 사진에서 위치를 걷어낸다. 좌표는 먼저 DB 로 옮긴다.
 *
 * ── 왜 필요한가 ──
 *
 * 업로드할 때 위치를 지우는 것은 '앞으로 올라올 사진' 에만 적용된다.
 * 그 전에 올라온 사진은 여전히 찍은 곳을 품은 채 방 참여자에게 내려간다.
 *
 * ── 순서가 전부다 ──
 *
 *   1. 파일에서 좌표를 읽어 photo 에 남기고 커밋한다
 *   2. 커밋이 끝난 뒤에 파일에서 지운다
 *
 * 반대로 하면, 파일은 이미 지웠는데 DB 저장이 실패했을 때 좌표를 되찾을 곳이 없다.
 * DB 는 되돌릴 수 있어도 파일은 되돌릴 수 없다. 그래서 되돌릴 수 없는 쪽을 뒤에 둔다.
 * 같은 이유로 @Transactional 을 메서드에 붙이지 않고 TransactionTemplate 으로
 * 1 번만 트랜잭션에 넣는다. 메서드 전체를 묶으면 커밋이 파일 삭제보다 늦어진다.
 *
 * ── 왜 앱이 뜰 때 하나 ──
 *
 * ThumbnailBackfill 과 같은 이유다. 한 번 쓰고 말 주소를 만들지 않는다.
 * 매번 전체를 훑지만 파일 앞쪽의 부가 정보만 읽으므로(needsStripping) 가볍고,
 * 이미 걷어낸 사진은 건너뛴다. 할 일이 없으면 로그도 남기지 않는다.
 * 운영에서 한 번 돌고 나면 할 일이 없어지므로, 확인 뒤에 이 클래스를 지워도 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationScrubBackfill {

    /** 한 번에 불러올 사진 수. 전체를 이만큼씩 끊어 훑는다. */
    private static final int BATCH_SIZE = 100;

    private final PhotoRepository photoRepository;
    private final FileStorage fileStorage;
    private final ExifReader exifReader;
    private final ExifStripper exifStripper;
    private final TransactionTemplate transactionTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void scrubStoredPhotos() {
        long lastId = 0;
        int located = 0;
        int stripped = 0;

        while (true) {
            List<Photo> batch = photoRepository.findByPhotoIdGreaterThanOrderByPhotoIdAsc(
                    lastId, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            lastId = batch.get(batch.size() - 1).getPhotoId();

            List<Photo> targets = batch.stream()
                    .filter(photo -> exifStripper.needsStripping(fileStorage.resolve(photo.getFilePath())))
                    .toList();
            if (targets.isEmpty()) {
                continue;
            }

            // 1. 좌표를 먼저 DB 에 남기고 커밋한다
            located += recordLocations(targets);

            // 2. 커밋이 끝났으니 파일에서 지운다
            for (Photo photo : targets) {
                if (exifStripper.strip(fileStorage.resolve(photo.getFilePath()))) {
                    stripped++;
                }
            }
        }

        if (stripped > 0 || located > 0) {
            log.info("쌓인 사진의 위치 정리 완료: 좌표 {}장 옮김, 파일 {}장에서 지움", located, stripped);
        }
    }

    /** 파일의 좌표를 photo 에 옮기고 커밋한다. 옮긴 장수를 돌려준다. */
    private int recordLocations(List<Photo> targets) {
        Integer count = transactionTemplate.execute(status -> {
            int n = 0;
            for (Photo target : targets) {
                // 위에서 불러온 것은 트랜잭션 밖의 엔티티라 고쳐도 저장되지 않는다. 다시 불러온다.
                Photo photo = photoRepository.findById(target.getPhotoId()).orElse(null);
                if (photo == null || photo.hasLocation()) {
                    continue;
                }
                Path file = fileStorage.resolve(photo.getFilePath());
                PhotoMetadata metadata = exifReader.read(file);
                if (metadata.hasLocation()) {
                    photo.recordLocation(metadata.latitude(), metadata.longitude());   // 변경 감지로 UPDATE 된다
                    n++;
                }
            }
            return n;
        });
        return count == null ? 0 : count;
    }
}
