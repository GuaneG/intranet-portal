import assert from "node:assert/strict";
import test from "node:test";

import { personelYetenekleriniGuncelle } from "../src/lib/api.js";

test("admin hedef personelin yeteneklerini tam küme ile günceller", async () => {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];

    globalThis.localStorage = {
        getItem(anahtar) {
            return anahtar === "token" ? "test-access-token" : null;
        },
    };
    globalThis.fetch = async (url, secenekler) => {
        istekler.push({ url, secenekler });
        return { ok: true, status: 204 };
    };

    try {
        await personelYetenekleriniGuncelle("hedef/personel", [3, 11]);
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.equal(istekler.length, 1);
    assert.equal(
        istekler[0].url,
        "http://localhost:8080/api/hr/personel/hedef%2Fpersonel/yetenekler",
    );
    assert.equal(istekler[0].secenekler.method, "PUT");
    assert.equal(
        istekler[0].secenekler.headers.Authorization,
        "Bearer test-access-token",
    );
    assert.deepEqual(
        JSON.parse(istekler[0].secenekler.body),
        { yetenekIdleri: [3, 11] },
    );
});
