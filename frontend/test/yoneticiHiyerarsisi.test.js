import assert from "node:assert/strict";
import test from "node:test";

import {
    ekipHiyerarsisiniGetir,
    yoneticiHiyerarsimiGetir,
    yoneticiZincirimiGetir,
} from "../src/lib/api.js";

test("yönetici hiyerarşisi istekleri doğru yetkili endpointlere gider", async () => {
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
        return {
            ok: true,
            status: 200,
            json: async () => ({}),
        };
    };

    try {
        await ekipHiyerarsisiniGetir({
            arama: " Ayşe ",
            departmanId: 10,
            yetenekId: 3,
        });
        await yoneticiHiyerarsimiGetir({ departmanId: 10 });
        await yoneticiZincirimiGetir();
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
                url: "http://localhost:8080/api/ekip-dizini/hiyerarsi?arama=Ay%C5%9Fe&departmanId=10&yetenekId=3",
                method: undefined,
                authorization: "Bearer test-access-token",
            },
            {
                url: "http://localhost:8080/api/ekip-dizini/yonetici-hiyerarsim?departmanId=10",
                method: undefined,
                authorization: "Bearer test-access-token",
            },
            {
                url: "http://localhost:8080/api/ekip-dizini/yonetici-zincirim",
                method: undefined,
                authorization: "Bearer test-access-token",
            },
        ],
    );
});

test("aynı hiyerarşi isteği devam ederken tek ağ çağrısı paylaşılır", async () => {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];

    globalThis.localStorage = {
        getItem(anahtar) {
            return anahtar === "token" ? "test-access-token" : null;
        },
    };
    globalThis.fetch = async (url) => {
        istekler.push(url);
        return {
            ok: true,
            status: 200,
            json: async () => ({}),
        };
    };

    try {
        await Promise.all([
            ekipHiyerarsisiniGetir({ departmanId: 10 }),
            ekipHiyerarsisiniGetir({ departmanId: 10 }),
            yoneticiHiyerarsimiGetir({ departmanId: 10 }),
            yoneticiHiyerarsimiGetir({ departmanId: 10 }),
            yoneticiZincirimiGetir(),
            yoneticiZincirimiGetir(),
        ]);
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(istekler, [
        "http://localhost:8080/api/ekip-dizini/hiyerarsi?departmanId=10",
        "http://localhost:8080/api/ekip-dizini/yonetici-hiyerarsim?departmanId=10",
        "http://localhost:8080/api/ekip-dizini/yonetici-zincirim",
    ]);
});

test("farklı hiyerarşi filtreleri birbirinin devam eden isteğini paylaşmaz", async () => {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];

    globalThis.localStorage = {
        getItem(anahtar) {
            return anahtar === "token" ? "test-access-token" : null;
        },
    };
    globalThis.fetch = async (url) => {
        istekler.push(url);
        return {
            ok: true,
            status: 200,
            json: async () => ({}),
        };
    };

    try {
        await Promise.all([
            ekipHiyerarsisiniGetir({ departmanId: 10 }),
            ekipHiyerarsisiniGetir({ departmanId: 11 }),
        ]);
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(istekler, [
        "http://localhost:8080/api/ekip-dizini/hiyerarsi?departmanId=10",
        "http://localhost:8080/api/ekip-dizini/hiyerarsi?departmanId=11",
    ]);
});

test("tamamlanan hiyerarşi isteğinden sonra yeni veri için yeniden çağrı yapılır", async () => {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];

    globalThis.localStorage = {
        getItem(anahtar) {
            return anahtar === "token" ? "test-access-token" : null;
        },
    };
    globalThis.fetch = async (url) => {
        istekler.push(url);
        return {
            ok: true,
            status: 200,
            json: async () => ({}),
        };
    };

    try {
        await ekipHiyerarsisiniGetir();
        await ekipHiyerarsisiniGetir();
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(istekler, [
        "http://localhost:8080/api/ekip-dizini/hiyerarsi",
        "http://localhost:8080/api/ekip-dizini/hiyerarsi",
    ]);
});
