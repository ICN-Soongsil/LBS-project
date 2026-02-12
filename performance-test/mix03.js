import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        mixed_complex_streams: {
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
    const params = {headers: {'Content-Type': 'application/json'}};

    const mainRand = Math.random();

    if (mainRand < 0.9) { // [Write 90%]
        const payload = JSON.stringify({ userId: virtualTrjId, latitude: parseFloat(record.rawlat), longitude: parseFloat(record.rawlng), timestamp: Date.now() });
        const res = http.post('http://localhost:8080/api/v1/locations', payload, params);
        check(res, {'Streams Write OK': (r) => r.status === 200});
    } else { // [Read 10%]
        const queryRand = Math.random();
        const searchPayload = JSON.stringify({ latitude: parseFloat(record.rawlat), longitude: parseFloat(record.rawlng) });
        const SEARCH_URL = 'http://localhost:8081/api/v1/search';

        if (queryRand < 0.33) {
            const res = http.post(`${SEARCH_URL}/point`, JSON.stringify({ userId: virtualTrjId }), params);
            check(res, {'Streams Point OK': (r) => r.status === 200});
        } else if (queryRand < 0.66) {
            const radius = (Math.random() * 4 + 1).toFixed(1);
            const res = http.post(`${SEARCH_URL}/range?radius=${radius}`, searchPayload, params);
            check(res, {'Streams Range OK': (r) => r.status === 200});
        } else {
            const k = Math.floor(Math.random() * 41) + 10;
            const res = http.post(`${SEARCH_URL}/knn?n=${k}`, searchPayload, params);
            check(res, {'Streams KNN OK': (r) => r.status === 200});
        }
    }
    sleep(0.1);
}