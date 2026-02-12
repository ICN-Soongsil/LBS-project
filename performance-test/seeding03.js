import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        seeding_100k: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100000,
            maxDuration: '5m',
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    const virtualTrjId = `user_${__ITER}`; 
    
    const payload = JSON.stringify({
        userId: virtualTrjId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        timestamp: Date.now()
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    // Producer 쪽으로 직접 전송
    const res = http.post('http://localhost:8080/api/v1/locations', payload, params);
    
    check(res, {'Streams Seeded': (r) => r.status === 200});
}