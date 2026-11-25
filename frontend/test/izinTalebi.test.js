import assert from "node:assert/strict";
import test from "node:test";

import * as api from "../src/lib/api.js";

function testOturumunuKur() {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];

    globalThis.localStorage = {
        getItem(anahtar) {
            return anahtar === "token" ? "test-access-token" : null;
        },
    };
    globalThis.fetch = async (url, secenekler = {}) => {
        istekler.push({ url, secenekler });
        const method = secenekler.method;
        return {
            ok: true,
            status: method === "POST" ? 201 : 200,
            json: async () => {
                if (method === "POST") {
                    return {
                        id: "8f198ffe-3334-4ec0-9dc6-a28c14ca966f",
                        izinTuru: "Yıllık İzin",
                        baslangicTarihi: "2026-09-01",
                        bitisTarihi: "2026-09-05",
                        isGunuSayisi: 4,
                        yoneticiyeNot: "Aile ziyareti",
                        durum: "ONAY_BEKLIYOR",
                        durumAdi: "Onay bekliyor",
                        talepTarihi: "2026-08-25T12:00:00",
                    };
                }

                if (url.includes("/api/leave-requests/my?")) {
                    return {
                        icerik: [{
                            id: "8f198ffe-3334-4ec0-9dc6-a28c14ca966f",
                            izinTuru: "Yıllık İzin",
                            baslangicTarihi: "2026-09-01",
                            bitisTarihi: "2026-09-05",
                            isGunuSayisi: 4,
                            yoneticiyeNot: "Aile ziyareti",
                            durum: "ONAY_BEKLIYOR",
                            durumAdi: "Onay bekliyor",
                            talepTarihi: "2026-08-25T12:00:00",
                        }],
                        sayfa: 0,
                        sayfaBoyutu: 10,
                        toplamKayit: 1,
                        toplamSayfa: 1,
                    };
                }

                return [{ id: 1, ad: "Yıllık İzin", yillikHaktanDuserMi: true }];
            },
        };
    };

    return {
        istekler,
        geriYukle() {
            globalThis.fetch = oncekiFetch;
            globalThis.localStorage = oncekiLocalStorage;
        },
    };
}

test("izin API istemcisi lookup, oluşturma ve durum filtreli kendi talep listesini sözleşmeye uygun gönderir", async () => {
    assert.equal(typeof api.izinTurleriniGetir, "function");
    assert.equal(typeof api.izinTalebiOlustur, "function");
    assert.equal(typeof api.izinTaleplerimiGetir, "function");

    const ortam = testOturumunuKur();
    const payload = {
        izinTuruId: 1,
        baslangicTarihi: "2026-09-01",
        bitisTarihi: "2026-09-05",
        yoneticiyeNot: "Aile ziyareti",
    };

    try {
        const izinTurleri = await api.izinTurleriniGetir();
        await api.izinTalebiOlustur(payload);
        const talepler = await api.izinTaleplerimiGetir({
            page: 0,
            size: 10,
            durum: "ONAY_BEKLIYOR",
        });

        assert.equal(izinTurleri[0].yillikHaktanDuserMi, true);
        assert.equal(talepler.icerik[0].durumAdi, "Onay bekliyor");
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            body: secenekler.body,
            authorization: secenekler.headers.Authorization,
        })),
        [
            {
                url: "http://localhost:8080/api/leave-types",
                method: undefined,
                body: undefined,
                authorization: "Bearer test-access-token",
            },
            {
                url: "http://localhost:8080/api/leave-requests",
                method: "POST",
                body: JSON.stringify(payload),
                authorization: "Bearer test-access-token",
            },
            {
                url: "http://localhost:8080/api/leave-requests/my?page=0&size=10&durum=ONAY_BEKLIYOR",
                method: undefined,
                body: undefined,
                authorization: "Bearer test-access-token",
            },
        ],
    );
});

test("izin formu tarih sırasını ve not sınırını backend'e gitmeden doğrular", async () => {
    const izinTalebi = await import("../src/lib/izinTalebi.js").catch(() => null);

    assert.notEqual(izinTalebi, null);
    assert.equal(
        izinTalebi.izinTalepFormunuDogrula({
            izinTuruId: "",
            baslangicTarihi: "2026-09-01",
            bitisTarihi: "2026-09-05",
            yoneticiyeNot: "",
        }),
        "İzin türü seçmelisiniz.",
    );
    assert.equal(
        izinTalebi.izinTalepFormunuDogrula({
            izinTuruId: "1",
            baslangicTarihi: "2026-09-05",
            bitisTarihi: "2026-09-01",
            yoneticiyeNot: "",
        }),
        "Bitiş tarihi başlangıç tarihinden önce olamaz.",
    );
    assert.equal(
        izinTalebi.izinTalepFormunuDogrula({
            izinTuruId: "1",
            baslangicTarihi: "2026-09-01",
            bitisTarihi: "2026-09-05",
            yoneticiyeNot: "a".repeat(1001),
        }),
        "Yönetici notu en fazla 1000 karakter olabilir.",
    );
    assert.equal(
        izinTalebi.izinTalepFormunuDogrula({
            izinTuruId: "1",
            baslangicTarihi: "2026-09-01",
            bitisTarihi: "2026-09-05",
            yoneticiyeNot: "Aile ziyareti",
        }),
        null,
    );
});

test("izin karar API istemcisi ekip ve admin kapsamını ayrı endpointlere gönderir", async () => {
    assert.equal(typeof api.ekipIzinTalepleriniGetir, "function");
    assert.equal(typeof api.adminIzinTalepleriniGetir, "function");
    assert.equal(typeof api.izinTalebiniOnayla, "function");
    assert.equal(typeof api.izinTalebiniReddet, "function");

    const ortam = testOturumunuKur();
    const talepId = "8f198ffe-3334-4ec0-9dc6-a28c14ca966f";

    try {
        await api.ekipIzinTalepleriniGetir({ page: 0, size: 10 });
        await api.adminIzinTalepleriniGetir({
            page: 1,
            size: 10,
            durum: "REDDEDILDI",
        });
        await api.izinTalebiniOnayla(talepId);
        await api.izinTalebiniReddet(talepId, "Ekip planlaması uygun değil.");
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            body: secenekler.body,
        })),
        [
            {
                url: "http://localhost:8080/api/leave-requests/team?page=0&size=10",
                method: undefined,
                body: undefined,
            },
            {
                url: "http://localhost:8080/api/admin/leave-requests?page=1&size=10&durum=REDDEDILDI",
                method: undefined,
                body: undefined,
            },
            {
                url: `http://localhost:8080/api/leave-requests/${talepId}/approve`,
                method: "PATCH",
                body: undefined,
            },
            {
                url: `http://localhost:8080/api/leave-requests/${talepId}/reject`,
                method: "PATCH",
                body: JSON.stringify({ kararNotu: "Ekip planlaması uygun değil." }),
            },
        ],
    );
});

test("kendi bekleyen izin talebi body olmadan iptal edilir", async () => {
    assert.equal(typeof api.izinTalebiniIptalEt, "function");

    const ortam = testOturumunuKur();
    const talepId = "8f198ffe-3334-4ec0-9dc6-a28c14ca966f";

    try {
        await api.izinTalebiniIptalEt(talepId);
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            body: secenekler.body,
        })),
        [{
            url: `http://localhost:8080/api/leave-requests/${talepId}/cancel`,
            method: "PATCH",
            body: undefined,
        }],
    );
});

test("red karar notu boş ve 500 karakterden uzun olamaz", async () => {
    const izinTalebi = await import("../src/lib/izinTalebi.js");

    assert.equal(typeof izinTalebi.kararNotunuDogrula, "function");
    assert.equal(izinTalebi.kararNotunuDogrula("   "), "Red notu zorunludur.");
    assert.equal(
        izinTalebi.kararNotunuDogrula("a".repeat(501)),
        "Red notu en fazla 500 karakter olabilir.",
    );
    assert.equal(izinTalebi.kararNotunuDogrula("  Ekip planlaması uygun değil.  "), null);
});
