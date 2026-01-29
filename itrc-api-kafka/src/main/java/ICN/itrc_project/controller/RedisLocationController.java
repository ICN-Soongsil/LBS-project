package ICN.itrc_project.controller;

import ICN.itrc_project.dto.LocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.geom.Path2D;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/search/redis")
@RequiredArgsConstructor
public class RedisLocationController {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String GEO_KEY = "mobility:locations";

    /**
     * [Range Query]
     * 핵심 질문: "내 주변 1km 원 안에 누가 있어?"
     * 판단 기준: 거리 중심
     */
    @GetMapping("/range")
    public ResponseEntity<List<LocationResponse>> searchByRange(
            @RequestParam double lat, @RequestParam double lng, @RequestParam double radiusMeter
    ) {
        long startTime = System.currentTimeMillis();
        log.info(">>> [🔎 공간 검색] 반경 내 검색 실행 | 위도: {}, 경도: {}) | 반경: {}m", lat, lng, (int) radiusMeter);

        Circle circle = new Circle(new Point(lng, lat), new Distance(radiusMeter, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        List<LocationResponse> response = results.getContent().stream()
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName().toString())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toUnmodifiableList());

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(">>> [✅ 검색 결과] 주변 차량 {}대 발견 (소요시간: {}ms \n)", response.size(), elapsedTime);

        return ResponseEntity.ok(response);
    }

    /**
     * [KNN Query]
     * 핵심 질문: "나랑 제일 가까운 3명이 누구야?"
     * 판단 기준: 순위 중심
     */
    @GetMapping("/knn")
    public ResponseEntity<List<LocationResponse>> searchByKnn(
            @RequestParam double lat, @RequestParam double lng, @RequestParam int n
    ) {
        long startTime = System.currentTimeMillis();
        log.info(">>> [🔎 공간 검색] 최근접 N명 탐색 실행 | 위도: {}, 경도: {} | 목표: 상위 {}명", lat, lng, n);

        Circle circle = new Circle(new Point(lng, lat), new Distance(5000, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending().limit(n);

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        List<LocationResponse> response = results.getContent().stream()
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName().toString())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toUnmodifiableList());

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(">>> [✅ 검색 결과] 최접점 차량 {}대 발견 (소요시간: {}ms \n)", response.size(), elapsedTime);

        return ResponseEntity.ok(response);
    }

    /**
     * [PIP Query]
     * 핵심 질문: "이 차가 강남구(영역) 안에 있어?"
     * 판단 기준: 경계 중심
     */
    @GetMapping("/pip")
    public ResponseEntity<List<LocationResponse>> searchByPolygon(
            @RequestParam List<Double> lats, @RequestParam List<Double> lngs
    ) {
        long startTime = System.currentTimeMillis();
        log.info(">>> [🔎 공간 검색] 다각형 구역 필터링 실행 | 꼭짓점 수: {}개", lats.size());

        if (lats.size() != lngs.size() || lats.size() < 3) {
            return ResponseEntity.badRequest().build();
        }

        // 1. 다각형 형태 정의
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(lngs.get(0), lats.get(0));
        for (int i = 1; i < lats.size(); i++) {
            polygon.lineTo(lngs.get(i), lats.get(i));
        }
        polygon.closePath();

        // 2. Filter: 1차 후보군 추출
        Circle filterArea = new Circle(new Point(lngs.get(0), lats.get(0)), new Distance(3000, Metrics.METERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo().radius(GEO_KEY, filterArea, args);

        // 3. Refine: 2차 수학적 판정
        List<LocationResponse> response = results.getContent().stream()
                .filter(result -> {
                    Point p = result.getContent().getPoint();
                    return p != null && polygon.contains(p.getX(), p.getY());
                })
                .map(result -> LocationResponse.builder()
                        .userId(result.getContent().getName().toString())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceMeter(result.getDistance().getValue())
                        .build())
                .collect(Collectors.toUnmodifiableList());

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(">>> [✅ 검색 결과] 구역 내 차량 {}대 발견 (소요시간: {}ms \n)", response.size(), elapsedTime);

        return ResponseEntity.ok(response);
    }
}