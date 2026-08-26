package com.foodmemory.app.dto;

import org.springframework.data.domain.Sort;

/**
 * 갤러리를 어떤 순서로 볼지.
 *
 * 문자열("uploaded")을 그대로 들고 다니지 않고 값으로 묶은 이유:
 *   화면에서 온 문자열을 쿼리까지 흘려보내면, 오타 하나가 컴파일을 통과해
 *   실행 중에야 드러난다. 정렬 기준을 바꿀 때 고쳐야 할 곳도 여기저기 흩어진다.
 *   여기 하나로 모으면 컴파일러가 대신 확인해준다.
 */
public enum GallerySort {

    /**
     * 올린 순. 방금 올린 것이 맨 앞에 온다.
     *
     * createdAt 이 아니라 postId 로 정렬하는 이유:
     *   둘 다 '만들어진 순서' 를 나타내지만 createdAt 은 같은 값이 나올 수 있다.
     *   사진 여러 장을 한 번에 올리면 같은 시각으로 찍힌다.
     *   그러면 DB 가 그 사이의 순서를 보장하지 않아, 페이지를 넘길 때마다 순서가 달라져
     *   1페이지에 나온 것이 2페이지에 또 나오거나 아예 빠진다.
     *   postId 는 절대 겹치지 않으므로 순서가 고정된다.
     */
    UPLOADED("uploaded", "올린 순", Sort.by(Sort.Direction.DESC, "postId")),

    /**
     * 먹은 날짜 순. 예전에 먹은 사진을 나중에 올려도 그날 자리에 놓인다.
     *
     * postId 를 뒤에 덧붙이는 이유는 위와 같다.
     * 먹은 날짜가 똑같은 기록이 여럿이면 그 사이의 순서를 DB 가 정해주지 않는다.
     * 겹치지 않는 값을 뒤에 붙여 순서를 고정한다.
     */
    EATEN("eaten", "먹은 날짜 순", Sort.by(Sort.Direction.DESC, "eatenDate", "postId"));

    /** 주소에 실려 다니는 값. ?sort=uploaded */
    private final String code;

    /** 화면에 보이는 이름 */
    private final String label;

    private final Sort sort;

    GallerySort(String code, String label, Sort sort) {
        this.code = code;
        this.label = label;
        this.sort = sort;
    }

    public String code()  { return code; }
    public String label() { return label; }
    public Sort   sort()  { return sort; }

    /**
     * 주소에서 온 값을 정렬 기준으로 바꾼다.
     *
     * valueOf 를 쓰지 않는 이유:
     *   주소는 사용자가 얼마든지 고칠 수 있다. ?sort=xxx 를 넣으면 valueOf 는 예외를 던지고
     *   화면이 오류로 끝난다. 순서를 못 알아들었을 뿐인데 화면 전체가 죽을 일은 아니다.
     *   모르는 값이면 조용히 기본값으로 돌린다.
     */
    public static GallerySort from(String code) {
        for (GallerySort candidate : values()) {
            if (candidate.code.equalsIgnoreCase(code)) {
                return candidate;
            }
        }
        return UPLOADED;
    }
}
