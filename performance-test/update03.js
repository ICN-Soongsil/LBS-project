import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

// 2. 부하 시나리오 설정 (200 VU 유지)
export const options = {
    scenarios: {
        update_stress: {
            executor: 'ramping-vus',
            stages: [
                {duration: '30s', target: 200},
                {duration: '2m', target: 200},
                {duration: '30s', target: 0},
            ],
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    const userIdx = Math.floor(Math.random() * 100000);
    const virtualTrjId = `user_${userIdx}`;

    // 3. 수정된 페이로드 (Redis 전용 serviceType 설정)
    const payload = JSON.stringify({
        userId: virtualTrjId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        speed: 0.0,
        accuracy: 0.0,
        serviceType: "REDIS",         // 💥 핵심: 유효성 검사 통과 및 데이터 구분용
        timestamp: new Date().toISOString()
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 4. API 호출 (로컬 포트 8080 확인 필요)
    const res = http.post('http://localhost:8080/api/v1/locations', payload, params);

    // 5. 결과 검증
    check(res, {
        'Redis Streams Update OK': (r) => r.status === 200,
    });

    sleep(0.1);
}