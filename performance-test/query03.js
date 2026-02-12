import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        read_heavy_mix: {
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
    const params = {headers: {'Content-Type': 'application/json'}};
    const BASE_URL = 'http://localhost:8081/api/v1/search';

    const rand = Math.random();

    if (rand < 0.33) { // [Point Query]
        const userIdx = Math.floor(Math.random() * 100000);
        const res = http.post(`${BASE_URL}/point`, JSON.stringify({ userId: `user_${userIdx}` }), params);
        check(res, {'RediSearch Point OK': (r) => r.status === 200});
    } else if (rand < 0.66) { // [Range Search]
        const radius = (Math.random() * 4 + 1).toFixed(1);
        const payload = JSON.stringify({ latitude: parseFloat(record.rawlat), longitude: parseFloat(record.rawlng) });
        const res = http.post(`${BASE_URL}/range?radius=${radius}`, payload, params);
        check(res, {'RediSearch Range OK': (r) => r.status === 200});
    } else { // [KNN Search]
        const k = Math.floor(Math.random() * 41) + 10;
        const payload = JSON.stringify({ latitude: parseFloat(record.rawlat), longitude: parseFloat(record.rawlng) });
        const res = http.post(`${BASE_URL}/knn?n=${k}`, payload, params);
        check(res, {'RediSearch KNN OK': (r) => r.status === 200});
    }
    sleep(0.1);
}