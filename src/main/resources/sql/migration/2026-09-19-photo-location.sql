-- ============================================================
--  photo 에 latitude, longitude 를 추가한다
--  2026-09-19
-- ============================================================
--
-- ── 무엇을 하나 ──
--
-- 사진 파일 속 EXIF 에 찍힌 위치(GPS)가 방 참여자에게 그대로 건너가고 있었다.
-- 원본을 받으면 찍은 곳을 알 수 있고, 집에서 찍은 사진이면 집 위치다.
-- 그래서 파일에서는 위치를 지우고, 근처 가게를 찾는 데 필요한 좌표는 이 칸으로 옮긴다.
--
-- ── 안전한가 ──
--
-- NULL 을 허용하는 칸을 뒤에 붙이는 것이라 기존 행은 건드리지 않는다(INSTANT).
-- 이미 쌓인 사진의 좌표는 앱이 뜰 때 LocationScrubBackfill 이 파일에서 읽어 채우고,
-- 그다음에 파일에서 지운다. 순서가 반대면 좌표가 영영 사라진다.
--
-- ── 순서가 중요하다 ──
--
-- 이 SQL 을 '먼저' 돌리고 배포한다. (ddl-auto: validate — 배포를 먼저 하면 앱이 못 뜬다)
--
--   운영:  ssh 로 들어가  sudo mysql foodmemory < 이 파일
--   로컬:  mysql -u root -p foodmemory < 이 파일

ALTER TABLE photo
    ADD COLUMN latitude  DECIMAL(10, 7) NULL COMMENT '촬영 위치 위도 (파일에서는 지운다)' AFTER thumb_path,
    ADD COLUMN longitude DECIMAL(10, 7) NULL COMMENT '촬영 위치 경도 (파일에서는 지운다)' AFTER latitude;
