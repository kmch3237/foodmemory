package com.foodmemory.app.common;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 업로드된 파일을 어딘가에 저장하는 역할.
 *
 * 인터페이스로 둔 이유:
 *   지금은 서버 폴더에 저장하지만(LocalFileStorage), 배포할 때는 S3 로 바꿀 예정이다.
 *   그때 이 인터페이스를 구현한 S3FileStorage 를 만들어 갈아끼우면
 *   Service 코드는 한 줄도 고치지 않아도 된다.
 */
public interface FileStorage {

    /**
     * 파일을 저장하고, DB 에 넣을 상대 경로를 돌려준다.
     * 예) 2026/08/3f8c1e9a4b2d.jpg
     *
     * 도메인이나 폴더 위치는 포함하지 않는다. 그건 환경마다 다르기 때문이다.
     */
    String store(MultipartFile file);

    /**
     * 이미 저장된 원본에서 작은 사본(썸네일)을 만들어 저장하고, 그 상대 경로를 돌려준다.
     * 예) 2026/08/3f8c1e9a4b2d.jpg  →  2026/08/3f8c1e9a4b2d_thumb.jpg
     *
     * 원본을 받아 만드는 이유:
     *   업로드 순간뿐 아니라 나중에 이미 쌓인 사진에도 똑같이 쓸 수 있다.
     *   업로드 경로와 보정 경로가 같은 코드를 지나므로 결과가 어긋나지 않는다.
     *
     * 못 만들었으면 null 을 돌려준다. 예외를 던지지 않는 이유:
     *   썸네일은 없으면 원본을 대신 보여주면 되는 '있으면 좋은 것' 이다.
     *   자바가 못 읽는 형식(HEIC)이라는 이유로 사용자의 기록을 막을 수는 없다.
     *
     * @return 썸네일의 상대 경로. 만들지 못했으면 null
     */
    String storeThumbnail(String originalRelativePath);

    /**
     * DB 에 저장된 상대 경로로 실제 파일의 위치를 알려준다.
     * 저장된 사진에서 EXIF 를 다시 읽을 때 쓴다.
     */
    Path resolve(String relativePath);

    /**
     * 저장된 파일을 지운다.
     *
     * 실패해도 예외를 던지지 않고 false 를 돌려준다.
     * 파일 삭제는 게시물 삭제가 DB 에 반영된 뒤에 하는 뒷정리라서,
     * 여기서 예외가 터지면 이미 지워진 게시물을 되살릴 수도 없으면서
     * 사용자에게는 실패했다고 알리게 된다.
     * 남은 파일은 아무도 참조하지 않으므로 나중에 정리하면 된다.
     *
     * @return 실제로 지워졌으면 true
     */
    boolean delete(String relativePath);
}
