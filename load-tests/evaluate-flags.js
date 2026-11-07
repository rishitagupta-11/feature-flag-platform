
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 10,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export default function () {
    const payload = JSON.stringify({
        flagKey: 'new_checkout',
        userId: `load_user_${__VU}_${__ITER}`,
    });

    const response = http.post(
        `${BASE_URL}/api/v1/flags/evaluate`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
        'flag evaluation is enabled': (r) =>
            r.status === 200 && r.json('enabled') === true,
    });
}
