package com.foodmemory.app.common;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

/**
 * 사진 파일에서 촬영 시각과 GPS 좌표를 읽는다.
 *
 * 스마트폰으로 찍은 사진에는 EXIF 라는 메타데이터가 함께 저장된다.
 * 여기에 촬영 시각과 위도·경도가 들어 있어, 사용자가 직접 입력하지 않아도
 * '언제 어디서 먹었는지' 를 알아낼 수 있다.
 *
 * 웹으로 업로드해도 EXIF 는 그대로 따라온다. 앱을 만들지 않아도 되는 이유다.
 */
@Slf4j
@Component
public class ExifReader {

    /**
     * 좌표를 소수점 7자리로 맞춘다. 식당 테이블의 DECIMAL(10, 7) 과 같은 정밀도다.
     */
    private static final int COORD_SCALE = 7;

    public PhotoMetadata read(MultipartFile file) {
        // EXIF 가 없거나 형식이 달라 읽기에 실패해도 업로드 자체는 성공해야 한다.
        // 부가 정보를 못 읽었다고 사용자의 기록을 막을 이유가 없다.
        try (InputStream in = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);
            return new PhotoMetadata(
                    extractTakenAt(metadata),
                    extractLatitude(metadata),
                    extractLongitude(metadata)
            );
        } catch (Exception e) {
            log.debug("EXIF 를 읽지 못했습니다: {}", file.getOriginalFilename());
            return PhotoMetadata.empty();
        }
    }

    /**
     * 이미 저장된 파일에서 읽는다.
     * 업로드 시점에 좌표를 어딘가에 보관해두지 않아도, 사진 자체가 원본이므로 다시 읽으면 된다.
     */
    public PhotoMetadata read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);
            return new PhotoMetadata(
                    extractTakenAt(metadata),
                    extractLatitude(metadata),
                    extractLongitude(metadata)
            );
        } catch (Exception e) {
            log.debug("EXIF 를 읽지 못했습니다: {}", path);
            return PhotoMetadata.empty();
        }
    }

    /** 촬영 시각. 파일이 만들어진 시각이 아니라 셔터를 누른 시각이다. */
    private LocalDateTime extractTakenAt(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) {
            return null;
        }
        // EXIF 의 촬영 시각에는 시간대 정보가 없다. 찍은 지역의 벽시계 시각만 들어 있다.
        // 그래서 서버의 기본 시간대를 기준으로 해석한다.
        Date date = dir.getDateOriginal(TimeZone.getDefault());
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private BigDecimal extractLatitude(Metadata metadata) {
        GeoLocation location = geoLocation(metadata);
        return location == null ? null : toDecimal(location.getLatitude());
    }

    private BigDecimal extractLongitude(Metadata metadata) {
        GeoLocation location = geoLocation(metadata);
        return location == null ? null : toDecimal(location.getLongitude());
    }

    private GeoLocation geoLocation(Metadata metadata) {
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gps == null) {
            return null;
        }
        GeoLocation location = gps.getGeoLocation();
        // 좌표가 0,0 이면 바다 한가운데다. 값이 없을 때 이렇게 채워지는 경우가 있어 걸러낸다.
        if (location == null || location.isZero()) {
            return null;
        }
        return location;
    }

    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(COORD_SCALE, RoundingMode.HALF_UP);
    }
}
