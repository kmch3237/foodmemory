/*
 * 서비스 워커 — 브라우저가 백그라운드에서 돌려주는 작은 프로그램.
 *
 * 있어야 하는 이유:
 *   브라우저가 "이 웹을 앱으로 설치해도 되는가" 를 판단할 때
 *   요청을 가로챌 수 있는 서비스 워커가 등록돼 있는지를 본다.
 *   없으면 설치 버튼 자체가 안 뜬다.
 *
 * 지금은 캐싱을 거의 하지 않는다:
 *   사진과 댓글은 계속 바뀌는 데이터라, 잘못 캐싱하면
 *   지운 사진이 계속 보이거나 남의 기록이 남아 있는 사고가 난다.
 *   먼저 '설치되는 것' 까지만 하고, 캐싱 전략은 필요해지면 그때 정한다.
 */

// 아이콘과 CSS 처럼 잘 안 바뀌는 것만 미리 받아둔다
const CACHE = 'mealmate-v2';
const PRECACHE = [
    '/css/app.css',
    '/icons/icon-192.png',
    '/icons/icon-512.png'
];

self.addEventListener('install', (event) => {
    // 새 서비스 워커를 곧바로 활성화한다. 안 그러면 탭을 다 닫아야 바뀐다
    self.skipWaiting();
    event.waitUntil(
        caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)).catch(() => {})
    );
});

self.addEventListener('activate', (event) => {
    // 예전 버전 캐시를 지운다. 이름(mealmate-v1)이 바뀌면 옛것이 남기 때문이다
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(
                keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const req = event.request;

    // GET 이 아닌 것(등록·수정·삭제)은 절대 건드리지 않는다.
    // 캐시에서 꺼내주면 서버에 도달하지 못해 저장이 안 된다
    if (req.method !== 'GET') return;

    const url = new URL(req.url);

    // 우리 서버가 아닌 요청(카카오 등)도 그대로 둔다
    if (url.origin !== self.location.origin) return;

    // 정적 파일만 캐시에서 먼저 찾고, 없으면 네트워크로 간다.
    // 화면(HTML)은 항상 서버에서 받아야 최신 기록이 보인다
    const isStatic = url.pathname.startsWith('/css/')
                  || url.pathname.startsWith('/icons/')
                  || url.pathname.startsWith('/fonts/');

    if (!isStatic) return;

    /*
     * 네트워크를 먼저 보고, 실패하면 캐시로 넘어간다.
     *
     * 반대로 했다가 사고가 났다. 캐시를 먼저 보게 해뒀더니
     * CSS 를 고쳐 배포해도 폰에는 옛것이 계속 나왔다.
     * 화면이 안 바뀌는데 서버에는 새 파일이 있어서 원인을 찾기 어려웠다.
     *
     * 캐시는 '인터넷이 끊겼을 때를 위한 예비' 로만 둔다.
     * 받아온 것은 다음을 위해 캐시에 다시 넣어둔다.
     */
    event.respondWith(
        fetch(req)
            .then((res) => {
                const copy = res.clone();
                caches.open(CACHE).then((cache) => cache.put(req, copy)).catch(() => {});
                return res;
            })
            .catch(() => caches.match(req))
    );
});
