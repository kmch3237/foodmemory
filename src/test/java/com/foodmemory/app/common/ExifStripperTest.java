package com.foodmemory.app.common;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 파일에서 위치가 지워지는지 본다.
 *
 * 진짜 사진을 테스트 자료로 넣지 않는다. 폰 사진에는 찍은 곳이 들어 있어서,
 * 저장소에 올리는 순간 그 위치가 공개된다. 그래서 GPS 가 든 JPEG 을 여기서 직접 만든다.
 */
class ExifStripperTest {

    @TempDir
    Path dir;

    private final ExifStripper stripper = new ExifStripper();

    @Test
    @DisplayName("GPS 와 기종은 지워지고, 사진 방향은 남는다")
    void stripsGpsKeepsOrientation() throws Exception {
        Path file = write("a.jpg", jpegWithExif(6));
        assertThat(metadata(file).containsDirectoryOfType(GpsDirectory.class)).isTrue();   // 정말 GPS 가 든 파일인지 먼저

        assertThat(stripper.strip(file)).isTrue();

        Metadata after = metadata(file);
        assertThat(after.containsDirectoryOfType(GpsDirectory.class)).isFalse();
        ExifIFD0Directory ifd0 = after.getFirstDirectoryOfType(ExifIFD0Directory.class);
        assertThat(ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION)).isEqualTo(6);
        assertThat(ifd0.containsTag(ExifIFD0Directory.TAG_MAKE)).isFalse();
    }

    @Test
    @DisplayName("그림 데이터는 한 바이트도 바뀌지 않는다 (다시 압축하지 않는다)")
    void imageDataUntouched() throws Exception {
        byte[] original = jpegWithExif(6);
        Path file = write("a.jpg", original);

        stripper.strip(file);

        byte[] after = Files.readAllBytes(file);
        assertThat(tailFromSos(after)).isEqualTo(tailFromSos(original));
        assertThat(ImageIO.read(file.toFile())).isNotNull();   // 여전히 열리는 JPEG 이다
    }

    @Test
    @DisplayName("방향이 기본값이면 EXIF 를 아예 남기지 않는다")
    void noOrientationLeavesNoExif() throws Exception {
        Path file = write("a.jpg", jpegWithExif(1));

        stripper.strip(file);

        assertThat(metadata(file).containsDirectoryOfType(ExifIFD0Directory.class)).isFalse();
    }

    @Test
    @DisplayName("이미 걷어낸 파일은 다시 쓰지 않는다")
    void secondStripIsNoOp() throws Exception {
        Path file = write("a.jpg", jpegWithExif(6));
        assertThat(stripper.needsStripping(file)).isTrue();

        stripper.strip(file);
        byte[] once = Files.readAllBytes(file);

        assertThat(stripper.needsStripping(file)).isFalse();
        assertThat(stripper.strip(file)).isFalse();
        assertThat(Files.readAllBytes(file)).isEqualTo(once);
    }

    /**
     * 운영에서는 nginx 가 사진을 직접 내보낸다. nginx 는 앱과 다른 사용자라,
     * 파일이 주인만 읽을 수 있는 상태(600)가 되면 403 을 내고 사진이 빈 칸으로 뜬다.
     * 실제로 그렇게 됐다(2026-09-20). 임시 파일이 600 으로 태어나는 것이 원인이었다.
     */
    @Test
    @DisplayName("위치를 지워도 파일 권한은 그대로다 (nginx 가 읽을 수 있어야 한다)")
    void keepsFilePermissions() throws Exception {
        Path file = write("a.jpg", jpegWithExif(6));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        assertThat(stripper.strip(file)).isTrue();

        assertThat(Files.getPosixFilePermissions(file))
                .isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));
    }

    @Test
    @DisplayName("JPEG 이 아니면 손대지 않는다")
    void leavesNonJpegAlone() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB), "png", png);
        Path file = write("a.png", png.toByteArray());

        assertThat(stripper.strip(file)).isFalse();
        assertThat(Files.readAllBytes(file)).isEqualTo(png.toByteArray());
    }

    @Test
    @DisplayName("업로드로 저장된 파일에는 위치가 없다")
    void storeStripsLocation() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), new ThumbnailMaker(), stripper);
        MockMultipartFile upload = new MockMultipartFile("photos", "lunch.jpg", "image/jpeg", jpegWithExif(6));

        String stored = storage.store(upload);

        assertThat(metadata(storage.resolve(stored)).containsDirectoryOfType(GpsDirectory.class)).isFalse();
    }

    /* ── 테스트용 사진 만들기 ─────────────────────────────────── */

    /**
     * 작은 JPEG 에 EXIF(기종·방향·GPS)와 XMP 조각을 끼워 넣는다.
     * 폰 사진의 앞부분을 흉내 낸 것이다.
     */
    private byte[] jpegWithExif(int orientation) throws Exception {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB), "jpeg", plain);
        byte[] jpeg = plain.toByteArray();

        byte[] exif = concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff(orientation));
        byte[] xmp = ("http://ns.adobe.com/xap/1.0/\0"
                + "<x:xmpmeta xmlns:x='adobe:ns:meta/'/>").getBytes(StandardCharsets.ISO_8859_1);

        // SOI(2바이트) 바로 뒤에 두 조각을 넣는다
        return concat(Arrays.copyOfRange(jpeg, 0, 2), segment(exif), segment(xmp),
                Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    /** 빅엔디언 TIFF. IFD0(기종·방향·GPS 위치) → GPS IFD(서울 어딘가) → 좌표 값. */
    private byte[] tiff(int orientation) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(152);
        b.put(new byte[]{'M', 'M', 0x00, 0x2A}).putInt(8);

        // IFD0 — 8 에서 시작, 항목 3개, 50 에서 끝
        b.putShort((short) 3);
        b.putShort((short) 0x010F).putShort((short) 2).putInt(4).put(new byte[]{'a', 'b', 'c', 0});   // 기종
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        b.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(50);                            // GPS IFD 위치
        b.putInt(0);

        // GPS IFD — 50 에서 시작, 항목 4개, 104 에서 끝. 좌표 값은 104, 128 에
        b.putShort((short) 4);
        b.putShort((short) 1).putShort((short) 2).putInt(2).put(new byte[]{'N', 0, 0, 0});
        b.putShort((short) 2).putShort((short) 5).putInt(3).putInt(104);
        b.putShort((short) 3).putShort((short) 2).putInt(2).put(new byte[]{'E', 0, 0, 0});
        b.putShort((short) 4).putShort((short) 5).putInt(3).putInt(128);
        b.putInt(0);

        b.putInt(37).putInt(1).putInt(33).putInt(1).putInt(0).putInt(1);    // 북위 37도 33분
        b.putInt(126).putInt(1).putInt(58).putInt(1).putInt(0).putInt(1);   // 동경 126도 58분
        return b.array();
    }

    private byte[] segment(byte[] payload) {
        int length = payload.length + 2;
        return concat(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length}, payload);
    }

    private byte[] tailFromSos(byte[] jpeg) {
        for (int i = 0; i < jpeg.length - 1; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xDA) {
                return Arrays.copyOfRange(jpeg, i, jpeg.length);
            }
        }
        throw new AssertionError("SOS 가 없는 JPEG");
    }

    private byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private Path write(String name, byte[] bytes) throws Exception {
        return Files.write(dir.resolve(name), bytes);
    }

    private Metadata metadata(Path file) throws Exception {
        return ImageMetadataReader.readMetadata(new ByteArrayInputStream(Files.readAllBytes(file)));
    }
}
