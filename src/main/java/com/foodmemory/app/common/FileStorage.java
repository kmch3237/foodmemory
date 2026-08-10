package com.foodmemory.app.common;

import org.springframework.web.multipart.MultipartFile;

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
}
