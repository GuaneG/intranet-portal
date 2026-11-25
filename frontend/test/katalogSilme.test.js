import assert from "node:assert/strict";
import test from "node:test";

import { departmanSil, yetenekSil } from "../src/lib/api.js";

test("katalog silme istekleri doğru DELETE endpointlerine gider", async () => {
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
        await departmanSil(17);
        await yetenekSil(23);
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(
        istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            authorization: secenekler.headers.Authorization,
        })),
        [
            {
                url: "http://localhost:8080/api/departments/17",
                method: "DELETE",
                authorization: "Bearer test-access-token",
            },
            {
                url: "http://localhost:8080/api/skills/23",
                method: "DELETE",
                authorization: "Bearer test-access-token",
            },
        ],
    );
});
