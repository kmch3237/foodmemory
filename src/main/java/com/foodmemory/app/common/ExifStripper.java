package com.foodmemory.app.common;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * 저장된 JPEG 에서 EXIF 를 걷어낸다. 사진 방향만 남긴다.
 *
 * ── 왜 필요한가 ──
 *
 * 폰으로 찍은 사진에는 찍은 곳의 위도·경도가 들어 있다.
 * 원본 파일은 방 참여자에게 그대로 내려가므로, 받은 사람이 파일 정보를 열면
 * 찍은 곳을 알 수 있다. 집에서 찍은 사진이면 집 위치다.
 * 근처 가게를 찾는 데 필요한 좌표는 저장하기 전에 읽어 DB(photo.latitude)에 따로 둔다.
 *
 * ── GPS 만 골라 지우지 않고 통째로 걷어내는 이유 ──
 *
 * EXIF 안에는 기종·일련번호·제조사 전용 영역까지 섞여 있고, 제조사마다 구조가 다르다.
 * 그 안을 따라 들어가 GPS 만 지우는 코드는 길고, 처음 보는 폰에서 틀리기 쉽다.
 * 이 서비스가 파일에서 필요로 하는 것은 '어느 쪽으로 돌려 보여줄지' 하나뿐이라,
 * 전부 걷어내고 그것만 새로 적는 편이 짧고 확실하다.
 *
 * ── 화질은 그대로다 ──
 *
 * 그림 데이터는 다시 압축하지 않고 바이트 그대로 옮긴다. 앞쪽의 부가 정보 조각만 바뀐다.
 *
 * ── JPEG 만 다룬다 ──
 *
 * 폰 카메라 사진은 대개 JPEG 으로 올라온다(아이폰의 HEIC 도 사파리가 올릴 때 보통 JPEG 으로 바꾼다).
 * PNG 는 대개 화면 캡처라 위치가 없다. 그 밖의 형식은 손대지 않고 그대로 둔다.
 */
@Slf4j
@Component
public class ExifStripper {

    /** JPEG 의 조각 표시. 모두 0xFF 뒤에 한 바이트가 붙는다. */
    private static final int SOI = 0xD8;    // 파일의 시작
    private static final int SOS = 0xDA;    // 여기서부터 그림 데이터
    private static final int EOI = 0xD9;    // 파일의 끝
    private static final int APP1 = 0xE1;   // EXIF 와 XMP 가 들어가는 조각

    /**
     * 파일의 EXIF 를 걷어내고 방향만 남긴다. 같은 자리에 덮어쓴다.
     *
     * 실패해도 예외를 던지지 않는다. 썸네일과 같은 판단이다.
     * 형식이 조금 어긋난 사진 때문에 사용자의 기록이 막히면 안 된다.
     * 대신 경고를 남긴다. 위치가 남은 채 저장됐다는 뜻이기 때문이다.
     *
     * @return 파일을 실제로 고쳤으면 true. JPEG 이 아니거나 걷어낼 것이 없으면 false
     */
    public boolean strip(Path file) {
        try {
            byte[] original = Files.readAllBytes(file);
            if (!isJpeg(original)) {
                return false;
            }

            byte[] stripped = rewrite(original, readOrientation(original));
            if (stripped == null) {
                return false;   // 부가 정보가 없거나, 이미 방향만 남은 사진이다
            }

            // 임시 파일에 다 쓴 뒤 한 번에 바꿔 끼운다.
            // 그대로 덮어쓰다 중간에 멈추면 원본도 새 파일도 아닌 깨진 사진이 남는다.
            Path temp = Files.createTempFile(file.getParent(), ".strip-", ".tmp");
            try {
                Files.write(temp, stripped);
                copyPermissions(file, temp);   // 바꿔 끼우기 전에. 뒤에 하면 이미 늦다
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;

        } catch (Exception e) {
            log.warn("EXIF 를 걷어내지 못했습니다. 위치가 남아 있을 수 있습니다: {}", file.getFileName(), e);
            return false;
        }
    }

    /**
     * 원본의 읽기·쓰기 권한을 새 파일에 옮긴다. 바꿔 끼우기 '전에' 불러야 한다.
     *
     * ── 왜 필요한가 ──
     *
     * Files.createTempFile 이 만드는 파일은 주인만 읽을 수 있다(600).
     * 업로드된 사진은 누구나 읽을 수 있다(644). 권한을 옮기지 않고 바꿔 끼우면
     * 원본이 임시 파일의 600 을 물려받는다. 파일 내용이 아니라 권한만 조용히 바뀐다.
     *
     * 로컬에서는 앱이 사진을 직접 내보내므로 자기 파일이라 드러나지 않는다.
     * 운영은 nginx 가 직접 내보내는데, nginx 는 앱과 다른 사용자라 600 파일을
     * 읽지 못하고 403 을 낸다. 사진 자리가 빈 칸으로 뜬다.
     * 2026-09-20 에 실제로 그렇게 됐고, 이미 쌓인 10장이 그렇게 잠겼다.
     *
     * 못 옮겨도 지우기 자체는 막지 않는다. 위치가 남는 편이 더 나쁘다.
     */
    private void copyPermissions(Path from, Path to) {
        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("파일 권한을 옮기지 못했습니다: {}", to.getFileName());
        }
    }

    /**
     * 걷어낼 것이 남아 있는지 본다. 이미 쌓인 사진을 훑을 때 쓴다(LocationScrubBackfill).
     *
     * 파일을 통째로 읽지 않고 앞쪽의 부가 정보만 읽는다. 그림 데이터 앞에서 멈추므로,
     * 앱이 뜰 때마다 모든 사진을 훑어도 부담이 작다.
     * 방향만 남긴 파일은 IFD0 에 항목이 하나뿐이고 나머지 영역이 없다.
     */
    public boolean needsStripping(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            return metadata.containsDirectoryOfType(GpsDirectory.class)
                    || metadata.containsDirectoryOfType(ExifSubIFDDirectory.class)
                    || metadata.containsDirectoryOfType(XmpDirectory.class)
                    || (ifd0 != null && ifd0.getTagCount() > 1);
        } catch (Exception e) {
            return false;   // 못 읽는 형식이면 strip() 도 손대지 않는다
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length > 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == SOI && (bytes[2] & 0xFF) == 0xFF;
    }

    /**
     * APP1 조각을 빼고 다시 이어붙인다. 첫 APP1 이 있던 자리에 방향만 담은 EXIF 를 넣는다.
     *
     * JPEG 의 앞부분은 [FF 표시][길이 2바이트][내용] 조각이 줄지어 있고,
     * SOS 조각부터 파일 끝까지가 그림이다. 그림 쪽은 조각 단위로 읽을 수 없으므로
     * SOS 를 만나면 그 뒤는 통째로 복사한다.
     *
     * @return 새 파일 내용. 고칠 것이 없으면(APP1 이 없거나, 이미 방향만 남은 파일) null
     */
    byte[] rewrite(byte[] in, int orientation) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 2);   // FF D8

        byte[] replacement = orientationOnlyExif(orientation);
        boolean removed = false;
        boolean changed = false;   // 이미 걷어낸 파일을 또 쓰지 않으려고 따로 센다
        int pos = 2;
        while (true) {
            if (pos + 1 >= in.length || (in[pos] & 0xFF) != 0xFF) {
                throw new IOException("JPEG 조각 표시가 있어야 할 자리가 아닙니다: " + pos);
            }
            int marker = in[pos + 1] & 0xFF;

            if (marker == 0xFF) {   // 채움 바이트. 건너뛴다
                pos++;
                continue;
            }
            if (marker == SOS || marker == EOI) {
                if (!changed) {
                    return null;
                }
                out.write(in, pos, in.length - pos);
                return out.toByteArray();
            }

            if (pos + 3 >= in.length) {
                throw new IOException("JPEG 조각의 길이를 읽을 수 없습니다: " + pos);
            }
            int length = ((in[pos + 2] & 0xFF) << 8) | (in[pos + 3] & 0xFF);   // 길이 칸 2바이트를 포함한 길이
            int end = pos + 2 + length;
            if (length < 2 || end > in.length) {
                throw new IOException("JPEG 조각의 길이가 파일을 벗어납니다: " + pos);
            }

            if (marker == APP1) {
                if (!removed) {
                    out.write(replacement);
                    removed = true;
                    if (!Arrays.equals(in, pos, end, replacement, 0, replacement.length)) {
                        changed = true;
                    }
                } else {
                    changed = true;   // 두 번째 APP1(XMP 등)은 언제나 빼야 할 것이다
                }
            } else {
                out.write(in, pos, end - pos);
            }
            pos = end;
        }
    }

    /**
     * 방향만 담은 가장 작은 EXIF 조각을 만든다. 방향이 기본값(1)이면 빈 배열이다.
     *
     * 방향을 남기는 이유:
     *   폰은 세로로 찍어도 가로로 저장하고 '90도 돌려 보여줘라' 는 값을 EXIF 에 적는다.
     *   이것까지 지우면 상세 화면과 원본에서 세로 사진이 옆으로 눕는다.
     *
     * 구조(모두 빅엔디언, 이 조각 전체 34바이트 + 표시 2바이트):
     *   FF E1 | 길이 | "Exif\0\0" | "MM" 002A 00000008 | 항목 1개 | 0112 SHORT 1 값 | 다음 IFD 없음
     */
    private byte[] orientationOnlyExif(int orientation) {
        if (orientation < 2 || orientation > 8) {
            return new byte[0];
        }
        return new byte[]{
                (byte) 0xFF, (byte) APP1,
                0x00, 0x22,                                     // 길이 34 (길이 칸 포함)
                'E', 'x', 'i', 'f', 0x00, 0x00,                 // EXIF 라는 표시
                'M', 'M', 0x00, 0x2A,                           // 빅엔디언 TIFF
                0x00, 0x00, 0x00, 0x08,                         // 첫 IFD 는 8바이트 뒤
                0x00, 0x01,                                     // 항목 1개
                0x01, 0x12,                                     // 태그: 방향(Orientation)
                0x00, 0x03,                                     // 형식: SHORT
                0x00, 0x00, 0x00, 0x01,                         // 개수 1
                0x00, (byte) orientation, 0x00, 0x00,           // 값 (4바이트 칸의 앞 2바이트)
                0x00, 0x00, 0x00, 0x00                          // 다음 IFD 없음
        };
    }

    /** 걷어내기 전에 방향을 읽어둔다. 없거나 못 읽으면 1(그대로). */
    private int readOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("사진 방향을 읽지 못했습니다");
        }
        return 1;
    }
}
