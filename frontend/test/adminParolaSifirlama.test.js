import assert from "node:assert/strict";
import test from "node:test";

import { adminParolaSifirla } from "../src/lib/api.js";

test("admin parola sıfırlama isteği hedefe geçici parola gövdesiyle gider", async () => {
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
        await adminParolaSifirla("hedef/personel", "Gecici123!");
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.equal(istekler.length, 1);
    assert.equal(
        istekler[0].url,
        "http://localhost:8080/api/admin/personel/hedef%2Fpersonel/parola-sifirla",
    );
    assert.equal(istekler[0].secenekler.method, "PATCH");
    assert.equal(
        istekler[0].secenekler.headers.Authorization,
        "Bearer test-access-token",
    );
    assert.deepEqual(
        JSON.parse(istekler[0].secenekler.body),
        { geciciParola: "Gecici123!" },
    );
});
