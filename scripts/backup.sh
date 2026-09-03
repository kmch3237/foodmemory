#!/bin/bash
#
# DB 와 사진을 하루 한 번 백업한다.
#
# 서버(EC2)의 cron 이 매일 새벽에 부른다. 사람이 직접 부를 일은 거의 없다.
#
# ── 왜 필요한가 ──
#
# 백업이 없으면 되돌릴 방법이 없다. 서버가 날아가는 것만 문제가 아니다.
# 실수로 지운 것, 잘못된 쿼리 한 줄, 업데이트 중 사고 — 전부 되돌릴 수 없다.
# 내 기록만 있을 때는 아깝고 마는 일이지만, 남의 기록을 받기 시작하면 사고다.
#
# ── 이 스크립트의 한계 ──
#
# 백업 파일이 원본과 같은 서버에 있다. 서버가 통째로 사라지면 백업도 같이 사라진다.
# 그래서 이것만으로는 절반이다. "실수로 지웠다" 는 막지만 "서버가 죽었다" 는 못 막는다.
# 나머지 절반(서버 밖으로 내보내기)은 따로 붙인다.

set -euo pipefail
# -e : 명령 하나라도 실패하면 즉시 멈춘다
#      이게 없으면 DB 백업이 실패해도 다음 줄로 넘어가, 사진만 든 반쪽 백업이 남는다
# -u : 값이 없는 변수를 쓰면 멈춘다. 오타로 빈 경로가 만들어지는 사고를 막는다
# -o pipefail : 파이프(|) 중간이 실패해도 알아챈다
#      mysqldump | gzip 에서 mysqldump 가 실패해도 gzip 은 성공하므로,
#      이게 없으면 '빈 파일을 잘 압축했다' 를 성공으로 착각한다

BACKUP_DIR=/home/ubuntu/backups
UPLOAD_DIR=/home/ubuntu/app/uploads
DB_NAME=foodmemory
KEEP_DAYS=7          # 며칠치를 남길지

STAMP=$(date +%Y%m%d-%H%M%S)

mkdir -p "$BACKUP_DIR"

# ── 1. DB ────────────────────────────────────────────────────
#
# --single-transaction 이 중요하다.
#   이게 없으면 mysqldump 가 테이블에 자물쇠를 건다. 그동안 사이트가 멈춘다.
#   InnoDB 는 '그 시점의 사진' 을 따로 떠서 읽을 수 있어서, 자물쇠 없이도
#   앞뒤가 맞는 백업을 만들 수 있다. 이 옵션이 그걸 시킨다.
#
# 비밀번호를 적지 않는 이유:
#   이 서버의 MySQL 은 리눅스 계정을 그대로 믿는 방식(auth_socket)이라
#   sudo 로 부르면 비밀번호를 묻지 않는다. 스크립트에 비밀번호를 적어두면
#   그 파일을 읽을 수 있는 사람은 누구나 DB 를 통째로 가져갈 수 있다.
sudo mysqldump --single-transaction --routines --events "$DB_NAME" \
    | gzip > "$BACKUP_DIR/db-$STAMP.sql.gz"

# ── 2. 사진 ──────────────────────────────────────────────────
#
# DB 에는 사진의 '경로' 만 들어 있다. 실제 파일은 디스크에 있으므로 따로 받아야 한다.
# 둘 중 하나만 있으면 복구가 안 된다. DB 만 있으면 깨진 이미지 뿐이고,
# 사진만 있으면 누가 언제 올린 것인지 알 수 없다.
#
# -C 로 위치를 옮겨서 묶는 이유:
#   그냥 묶으면 압축 파일 안에 home/ubuntu/app/uploads/... 처럼 전체 경로가 들어간다.
#   나중에 다른 자리에 풀 때 번거롭다. uploads 부터 시작하게 만든다.
tar -czf "$BACKUP_DIR/uploads-$STAMP.tar.gz" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"

# ── 3. 오래된 백업 지우기 ────────────────────────────────────
#
# 안 지우면 디스크가 찬다. 디스크가 차면 앱이 아니라 서버 전체가 멈춘다.
# 백업하려다 서비스를 죽이는 셈이 된다.
#
# -mtime +N 은 'N 일보다 오래된 것'. 7 이면 오늘부터 7일치가 남는다.
find "$BACKUP_DIR" -name 'db-*.sql.gz'      -mtime +$KEEP_DAYS -delete
find "$BACKUP_DIR" -name 'uploads-*.tar.gz' -mtime +$KEEP_DAYS -delete

# ── 4. 결과를 남긴다 ─────────────────────────────────────────
#
# 백업이 조용히 실패하는 것이 제일 위험하다.
# 정작 필요할 때 열어보니 지난달 것뿐이더라, 가 실제로 흔한 사고다.
echo "$(date '+%F %T') 백업 완료  db=$(du -h "$BACKUP_DIR/db-$STAMP.sql.gz" | cut -f1)  사진=$(du -h "$BACKUP_DIR/uploads-$STAMP.tar.gz" | cut -f1)  보관=$(ls "$BACKUP_DIR" | wc -l)개"
