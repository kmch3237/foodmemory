package com.foodmemory.app.common;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * 원본 사진에서 작은 사본(썸네일)을 만든다.
 *
 * ── 왜 필요한가 ──
 *
 * 목록 화면은 사진을 손톱만 하게 그리면서도 원본 파일을 통째로 내려받고 있었다.
 * 한 장이 평균 2.5MB 라 한 페이지(12장)에 약 31MB 가 오갔다.
 * 화면에서 작게 그리는 것(CSS)과 작은 파일을 보내는 것은 전혀 다른 이야기다.
 *
 * ── 라이브러리를 새로 받지 않은 이유 ──
 *
 * 자바에 ImageIO 가 들어 있어 크기를 줄이는 데 부족하지 않다.
 * 의존성이 하나 늘면 서버에도 그만큼 부담이고, 여기서 필요한 기능은 그리 많지 않다.
 * EXIF 를 읽는 metadata-extractor 는 촬영 시각·좌표 때문에 이미 쓰고 있어 그대로 빌려 쓴다.
 */
@Slf4j
@Component
public class ThumbnailMaker {

    /**
     * 사본의 긴 변 길이.
     *
     * 목록 칸은 CSS 로 200~400px 사이다(app.css 의 .gallery).
     * 요즘 화면은 점을 두 배로 촘촘히 찍으므로 그보다 커야 흐릿하지 않다.
     * 500 은 '또렷하게 보이는 선'과 '파일이 작아지는 이득' 이 만나는 지점이다.
     * 더 키우면 용량이 빠르게 늘고, 더 줄이면 넓은 화면에서 뿌옇게 보인다.
     */
    private static final int MAX_EDGE = 500;

    /** JPEG 압축 품질. 0.82 아래로 내려가면 음식 사진에서 얼룩이 눈에 띄기 시작한다. */
    private static final float QUALITY = 0.82f;

    /**
     * 원본 파일에서 작은 JPEG 을 만들어 바이트로 돌려준다.
     *
     * 실패해도 예외를 던지지 않고 null 을 돌려준다.
     * 썸네일은 없으면 원본을 대신 보여주면 그만인 '있으면 좋은 것' 이라,
     * 이것 때문에 사용자의 업로드가 실패하면 안 된다.
     * HEIC 처럼 자바가 못 읽는 형식도 여기서 조용히 null 이 된다.
     *
     * @return JPEG 바이트. 만들지 못했으면 null
     */
    public byte[] shrink(Path source) {
        try {
            BufferedImage small = readScaled(source);
            if (small == null) {
                log.debug("이미지를 읽지 못해 썸네일을 건너뜁니다: {}", source.getFileName());
                return null;
            }

            small = applyOrientation(small, readOrientation(source));
            return encodeJpeg(small);

        } catch (Exception e) {
            // OutOfMemoryError 까지 잡지는 않는다. 그건 서버가 알아야 할 문제다.
            log.warn("썸네일을 만들지 못했습니다: {}", source.getFileName(), e);
            return null;
        }
    }

    /**
     * 원본을 줄여서 읽는다.
     *
     * ── 통째로 읽지 않는 이유 ──
     *
     * 4000×3000 사진을 그대로 펼치면 메모리에서만 약 48MB 를 차지한다.
     * 운영 서버는 램이 1GB 뿐이고 앱이 이미 절반을 쓰고 있어서,
     * 몇 명이 동시에 올리면 서버가 통째로 죽는다.
     *
     * setSourceSubsampling 은 '몇 픽셀 건너 하나씩만 읽어라' 는 지시다.
     * 4로 주면 1000×750 만 메모리에 올라온다. 어차피 500px 로 줄일 것이라
     * 버리는 정보가 결과에 거의 드러나지 않는다.
     *
     * 2의 거듭제곱만 쓰는 이유는 격자가 규칙적으로 맞아떨어져
     * 무늬가 생기는(모아레) 현상이 덜하기 때문이다.
     */
    private BufferedImage readScaled(Path source) throws Exception {
        try (ImageInputStream in = ImageIO.createImageInputStream(Files.newInputStream(source))) {
            if (in == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;   // 자바가 다룰 줄 모르는 형식 (예: HEIC)
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(in);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int longEdge = Math.max(width, height);

                // 줄여 읽어도 목표 크기보다는 커야 한다. 작아지면 늘려 그리게 되어 뭉개진다.
                int step = 1;
                while (longEdge / (step * 2) >= MAX_EDGE) {
                    step *= 2;
                }

                ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                BufferedImage rough = reader.read(0, param);

                return scaleToFit(rough);

            } finally {
                reader.dispose();   // 안 부르면 네이티브 자원이 쌓인다
            }
        }
    }

    /** 긴 변이 MAX_EDGE 가 되도록 맞춘다. 이미 작으면 그대로 둔다(늘리지 않는다). */
    private BufferedImage scaleToFit(BufferedImage src) {
        int longEdge = Math.max(src.getWidth(), src.getHeight());
        if (longEdge <= MAX_EDGE) {
            return src;
        }

        double ratio = (double) MAX_EDGE / longEdge;
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));

        // TYPE_INT_RGB — JPEG 은 투명을 담지 못한다.
        // PNG 의 투명한 부분을 그대로 두면 검게 나오므로 흰 바탕을 먼저 깐다.
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * EXIF 에 적힌 사진 방향을 읽는다.
     *
     * ── 이게 없으면 생기는 일 ──
     *
     * 폰은 세로로 찍어도 사진을 가로로 저장하고, '보여줄 때 90도 돌려라' 는 쪽지를
     * EXIF 에 끼워 넣는다. 브라우저는 그 쪽지를 읽고 돌려 그린다.
     * 그런데 썸네일을 새로 만들면 쪽지가 사라지므로, 우리가 직접 돌려두지 않으면
     * 목록에서만 사진이 옆으로 누워 보인다. 원본은 멀쩡한데 목록만 이상해진다.
     *
     * @return EXIF 방향 값(1~8). 없으면 1(그대로)
     */
    private int readOrientation(Path source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(source.toFile());
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("사진 방향을 읽지 못했습니다: {}", source.getFileName());
        }
        return 1;
    }

    /**
     * 방향 쪽지대로 실제로 돌린다.
     *
     * 값의 뜻은 EXIF 표준이 정해둔 것이다. 자주 나오는 것은 1·3·6·8 이고
     * 2·4·5·7 은 좌우가 뒤집힌 경우라 드물지만 함께 처리한다.
     */
    private BufferedImage applyOrientation(BufferedImage img, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return img;
        }

        int w = img.getWidth();
        int h = img.getHeight();

        // 90도·270도로 돌면 가로세로가 뒤바뀐다. 새 그림판의 크기가 달라진다.
        boolean swapped = orientation >= 5;
        int newW = swapped ? h : w;
        int newH = swapped ? w : h;

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }                    // 좌우 뒤집기
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }                  // 180도
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }                    // 상하 뒤집기
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }                // 90도 + 뒤집기
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }              // 시계 90도 (제일 흔함)
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0);
                        t.translate(0, w); t.rotate(3 * Math.PI / 2); }          // 270도 + 뒤집기
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }          // 반시계 90도
            default -> { return img; }
        }

        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 품질을 정해 JPEG 으로 압축한다. 기본 ImageIO.write 는 품질을 고를 수 없다. */
    private byte[] encodeJpeg(BufferedImage img) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {

            writer.setOutput(out);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);

            writer.write(null, new IIOImage(img, null, null), param);
            out.flush();
            return bytes.toByteArray();

        } finally {
            writer.dispose();
        }
    }
}
