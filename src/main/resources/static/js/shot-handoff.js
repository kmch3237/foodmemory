/*
 * 촬영 버튼으로 찍은 사진을 '기록 올리기' 폼까지 들고 간다.
 *
 * ── 왜 이런 게 필요한가 ──
 *
 * 파일 입력칸(input type=file)의 내용은 그 페이지에만 있다.
 * 다른 주소로 넘어가는 순간 비워진다. 보안 때문에 그렇게 만들어져 있다.
 * 주소에 실어 보낼 수도 없다. 사진은 수 MB 라 주소에 들어갈 크기가 아니다.
 *
 * 그래서 넘어가기 직전에 브라우저 안에 잠깐 맡겨두고, 넘어간 뒤 다시 꺼낸다.
 *
 * ── 왜 IndexedDB 인가 ──
 *
 * sessionStorage 는 문자열만 담는다. 사진을 넣으려면 글자로 바꿔야 하는데(base64)
 * 그 과정에서 크기가 1.3배로 늘고, 보관 한도(보통 5MB)를 사진 두 장이면 넘긴다.
 * IndexedDB 는 파일을 그 모양 그대로 담을 수 있고 한도도 훨씬 크다.
 *
 * ── 실패해도 화면은 살아 있어야 한다 ──
 *
 * 시크릿 모드나 저장을 막아둔 브라우저에서는 이게 통째로 안 될 수 있다.
 * 그때는 사진 없이 폼만 열린다. 사용자는 '지금 찍기' 를 한 번 더 누르면 되고,
 * 화면이 오류로 죽는 것보다 낫다. 그래서 모든 실패를 조용히 삼킨다.
 */
(function (global) {
    'use strict';

    var DB_NAME = 'mealmate-shot';
    var STORE = 'files';
    var KEY = 'pending';

    function openDb() {
        return new Promise(function (resolve, reject) {
            var request = indexedDB.open(DB_NAME, 1);

            // 저장소가 없을 때(처음 쓸 때) 한 번만 불린다
            request.onupgradeneeded = function () {
                request.result.createObjectStore(STORE);
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
        });
    }

    function run(db, mode, work) {
        return new Promise(function (resolve, reject) {
            var request = work(db.transaction(STORE, mode).objectStore(STORE));
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
        });
    }

    global.ShotHandoff = {

        /** 찍은 사진을 맡겨둔다. 실패하면 false 를 돌려준다 */
        save: function (files) {
            return openDb()
                .then(function (db) {
                    // FileList 는 그대로 담기지 않으므로 배열로 바꾼다
                    return run(db, 'readwrite', function (store) {
                        return store.put(Array.prototype.slice.call(files), KEY);
                    }).then(function () { db.close(); return true; });
                })
                .catch(function () { return false; });
        },

        /**
         * 맡겨둔 사진을 꺼내고 지운다.
         *
         * 꺼내면서 지우는 이유:
         *   지우지 않으면 다음에 그냥 '기록 올리기' 로 들어왔을 때 예전 사진이 따라 들어온다.
         *   한 번 쓰고 버리는 값이다.
         */
        take: function () {
            return openDb()
                .then(function (db) {
                    return run(db, 'readonly', function (store) { return store.get(KEY); })
                        .then(function (files) {
                            return run(db, 'readwrite', function (store) {
                                return store.delete(KEY);
                            }).then(function () { db.close(); return files || null; });
                        });
                })
                .catch(function () { return null; });
        }
    };
})(window);
