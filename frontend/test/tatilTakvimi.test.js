import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
    TAKVIM_EVENT_TURLERI,
    dogumGununuTakvimOlayinaDonustur,
    gorunenTakvimAraliginiOlustur,
    gunDegeriniTurkceBicimlendir,
    rolTatilYonetebilirMi,
    tatilAraliginiDogrula,
    tatilEventSiniflariniGetir,
    tatilKategoriEtiketi,
    tatilSinifiniGetir,
    tatiliTakvimOlayinaDonustur,
    tarihteTatilBul,
} from "../src/lib/tatilTakvimi.js";

test("FullCalendar exclusive bitişini backend'in inclusive tarih aralığına çevirir", () => {
    const aralik = gorunenTakvimAraliginiOlustur(
        new Date(2026, 7, 1),
        new Date(2026, 8, 1),
    );

    assert.deepEqual(aralik, {
        baslangicTarihi: "2026-08-01",
        bitisTarihi: "2026-08-31",
    });
});

test("tatil enumlarını kullanıcıya Türkçe metin ve sınıf olarak sunar", () => {
    assert.equal(tatilKategoriEtiketi("RESMI_SABIT"), "Sabit resmî tatil");
    assert.equal(tatilKategoriEtiketi("RESMI_DEGISKEN"), "Değişken resmî tatil");
    assert.equal(tatilKategoriEtiketi("SIRKET"), "Şirket günü");
    assert.equal(tatilSinifiniGetir("SIRKET"), "success");
    assert.deepEqual(tatilEventSiniflariniGetir("SIRKET"), [
        "event-fc-color",
        "fc-bg-success",
    ]);
});

test("ondalıklı izin değeri ve sıfır kayıpsız Türkçe gösterilir", () => {
    assert.equal(gunDegeriniTurkceBicimlendir(16.5), "16,5");
    assert.equal(gunDegeriniTurkceBicimlendir(0), "0");
});

test("gün hücresine tıklanınca o tarihteki tatil bulunur", () => {
    const tatiller = [
        { tatilId: 14, tarih: "2026-08-24", bitisTarihi: "2026-08-28", ad: "Yarım gün" },
        { tatilId: 15, tarih: "2026-08-29", bitisTarihi: "2026-08-29", ad: "Tatil" },
    ];

    assert.deepEqual(tarihteTatilBul(tatiller, "2026-08-26"), tatiller[0]);
    assert.equal(tarihteTatilBul(tatiller, "2026-08-30"), undefined);
});

test("çok günlük tatil grubu FullCalendar'a tek ve hariç bitişli event olarak dönüşür", () => {
    const grup = {
        tatilId: 42,
        ad: "Kurban Bayramı",
        tarih: "2026-08-24",
        bitisTarihi: "2026-08-28",
        kategori: "RESMI_DEGISKEN",
        gunSuresi: "YARIM_GUN",
    };

    assert.deepEqual(tatiliTakvimOlayinaDonustur(grup), {
        id: "42",
        title: "Kurban Bayramı",
        start: "2026-08-24",
        end: "2026-08-29",
        allDay: true,
        classNames: ["event-fc-color", "fc-bg-warning"],
        extendedProps: {
            tur: "TATIL",
            kategori: "RESMI_DEGISKEN",
            gunSuresi: "YARIM_GUN",
        },
    });
});

test("aynı gün, ad ve departmandaki doğum günleri ayrı ve salt-okunur eventlere dönüşür", () => {
    const dogumGunu = {
        adSoyad: "Ayşe Yılmaz",
        tarih: "2026-08-14",
        departman: "Bilgi Teknolojileri",
    };

    const ilkEvent = dogumGununuTakvimOlayinaDonustur(dogumGunu, 0);
    const ikinciEvent = dogumGununuTakvimOlayinaDonustur(dogumGunu, 1);

    assert.notEqual(ilkEvent.id, ikinciEvent.id);
    assert.deepEqual(ilkEvent, {
        id: "dogum-gunu-2026-08-14-0",
        title: "Ayşe Yılmaz",
        start: "2026-08-14",
        end: "2026-08-15",
        allDay: true,
        editable: false,
        startEditable: false,
        durationEditable: false,
        classNames: ["event-fc-color", "fc-bg-birthday"],
        extendedProps: {
            tur: TAKVIM_EVENT_TURLERI.DOGUM_GUNU,
            departman: "Bilgi Teknolojileri",
        },
    });
});

test("tatil aralığı başlangıç-bitiş sırasını ve 366 günlük limiti doğrular", () => {
    assert.equal(tatilAraliginiDogrula("", "2026-08-24"), "Başlangıç ve bitiş tarihi zorunludur.");
    assert.equal(tatilAraliginiDogrula("2026-08-25", "2026-08-24"), "Bitiş tarihi başlangıç tarihinden önce olamaz.");
    assert.equal(tatilAraliginiDogrula("2026-01-01", "2027-01-02"), "Tatil aralığı en fazla 366 gün olabilir.");
    assert.equal(tatilAraliginiDogrula("2026-01-01", "2027-01-01"), null);
});

test("tatil yönetim eylemleri yalnız HR ve ADMIN için görünür", () => {
    assert.equal(rolTatilYonetebilirMi("HR"), true);
    assert.equal(rolTatilYonetebilirMi("ADMIN"), true);
    assert.equal(rolTatilYonetebilirMi("PERSONEL"), false);
    assert.equal(rolTatilYonetebilirMi("YONETICI"), false);
});

test("rezervasyonla çakışan tatil kaydı form açıkken belirgin iş kuralı uyarısı verir", async () => {
    const calendar = await readFile(new URL("../src/pages/Calendar.tsx", import.meta.url), "utf8");

    assert.match(calendar, /error instanceof ApiError && error\.status === 409/);
    assert.match(calendar, /rezervasyonCakismaHatasi/);
    assert.match(calendar, /Çakışan rezervasyonları iptal ettikten sonra bu formu yeniden gönderebilirsiniz\./);
    assert.match(calendar, /role="alert"/);
});

test("takvim gün hücresi hover sırasında görünür geri bildirim verir", async () => {
    const css = await readFile(new URL("../src/index.css", import.meta.url), "utf8");

    assert.match(
        css,
        /\.custom-calendar \.fc-daygrid-day:hover\s*\{\s*@apply[^}]*bg-gray-50/,
    );
});
