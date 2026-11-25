import assert from "node:assert/strict";
import test from "node:test";

import {
    ApiError,
    dogumGunleriniGetir,
    isGunuOnizle,
    tatilGuncelle,
    tatilOlustur,
    tatilSil,
    tatilleriGetir,
} from "../src/lib/api.js";

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
        const durum = secenekler.method === "POST"
            ? 201
            : secenekler.method
                ? 204
                : 200;
        return {
            ok: true,
            status: durum,
            json: async () => {
                if (durum === 204) {
                    throw new Error("204 response gövde taşımaz.");
                }

            return [{
                    tatilId: 7,
                    ad: "Kurban Bayramı",
                    tarih: "2026-08-24",
                    bitisTarihi: "2026-08-28",
                    kategori: "RESMI_DEGISKEN",
                    gunSuresi: "TAM_GUN",
            }];
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

test("tatil listesi ve iş günü önizlemesi ISO tarih aralığını doğru endpointlere gönderir", async () => {
    const ortam = testOturumunuKur();

    try {
        const tatiller = await tatilleriGetir("2026-08-01", "2026-08-31");
        await isGunuOnizle("2026-08-24", "2026-08-28");

        assert.equal(tatiller[0].bitisTarihi, "2026-08-28");
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            authorization: secenekler.headers.Authorization,
        })),
        [
            {
                url: "http://localhost:8080/api/holidays?baslangicTarihi=2026-08-01&bitisTarihi=2026-08-31",
                method: undefined,
                authorization: "Bearer test-access-token",
            },
            {
                url: "http://localhost:8080/api/leave-requests/workday-preview?baslangicTarihi=2026-08-24&bitisTarihi=2026-08-28",
                method: undefined,
                authorization: "Bearer test-access-token",
            },
        ],
    );
});

test("doğum günü listesi görünür tarih aralığını endpoint'e gönderir ve aynı kayıtları korur", async () => {
    const ortam = testOturumunuKur();
    const ayniDogumGunleri = [
        { adSoyad: "Ayşe Yılmaz", tarih: "2026-08-14", departman: "Bilgi Teknolojileri" },
        { adSoyad: "Ayşe Yılmaz", tarih: "2026-08-14", departman: "Bilgi Teknolojileri" },
    ];
    globalThis.fetch = async (url, secenekler = {}) => {
        ortam.istekler.push({ url, secenekler });
        return { ok: true, status: 200, json: async () => ayniDogumGunleri };
    };

    try {
        const dogumGunleri = await dogumGunleriniGetir("2026-08-01", "2026-08-31");
        assert.deepEqual(dogumGunleri, ayniDogumGunleri);
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(ortam.istekler, [{
        url: "http://localhost:8080/api/birthdays?baslangicTarihi=2026-08-01&bitisTarihi=2026-08-31",
        secenekler: {
            headers: {
                Authorization: "Bearer test-access-token",
            },
        },
    }]);
});

test("tatil mutationları enum body ile doğru methodu kullanır ve 204 cevabı parse etmez", async () => {
    const ortam = testOturumunuKur();
    const payload = {
        ad: "Zafer Bayramı",
        tarih: "2026-08-30",
        bitisTarihi: "2026-08-30",
        kategori: "RESMI_SABIT",
        gunSuresi: "TAM_GUN",
    };

    try {
        await tatilOlustur(payload);
        await tatilGuncelle(7, payload);
        await tatilSil(7);
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
                url: "http://localhost:8080/api/holidays",
                method: "POST",
                body: JSON.stringify(payload),
            },
            {
                url: "http://localhost:8080/api/holidays/7",
                method: "PATCH",
                body: JSON.stringify(payload),
            },
            {
                url: "http://localhost:8080/api/holidays/7",
                method: "DELETE",
                body: undefined,
            },
        ],
    );
});

test("rezervasyonla çakışan tatil oluşturma ve güncelleme güvenli 409 mesajını taşır", async () => {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    globalThis.localStorage = { getItem: () => "test-access-token" };
    globalThis.fetch = async () => ({
        ok: false,
        status: 409,
        json: async () => ({
            message: "Seçilen tarih aralığında tatil saatleriyle çakışan aktif rezervasyonlar bulunuyor. Tatili kaydetmek için önce bu rezervasyonları iptal edin.",
        }),
    });

    const payload = {
        ad: "Kurban Bayramı",
        tarih: "2026-08-24",
        bitisTarihi: "2026-08-28",
        kategori: "RESMI_DEGISKEN",
        gunSuresi: "TAM_GUN",
    };
    const cakismaMesaji = "Seçilen tarih aralığında tatil saatleriyle çakışan aktif rezervasyonlar bulunuyor. Tatili kaydetmek için önce bu rezervasyonları iptal edin.";

    try {
        await assert.rejects(
            () => tatilOlustur(payload),
            (error) => error instanceof ApiError
                && error.status === 409
                && error.message === cakismaMesaji,
        );
        await assert.rejects(
            () => tatilGuncelle(7, payload),
            (error) => error instanceof ApiError
                && error.status === 409
                && error.message === cakismaMesaji,
        );
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }
});
