import assert from "node:assert/strict";
import test from "node:test";

test("izin bakiyesi yüzdesi ve gün değerleri ondalık hassasiyetini korur", async () => {
    const yardimcilar = await import("../src/lib/dashboardIzinBakiyesi.js");

    assert.equal(yardimcilar.izinBakiyesiYuzdesi({ toplamHak: 20, kalan: 12.5 }), 62.5);
    assert.equal(yardimcilar.izinBakiyesiYuzdesi({ toplamHak: 0, kalan: 4 }), 0);
    assert.equal(yardimcilar.izinGununuBicimlendir(12.5), "12,5");
    assert.equal(yardimcilar.izinGununuBicimlendir(0), "0");
});

test("bugünkü izin enumu yalnız bilinen rozet metinlerine dönüşür", async () => {
    const yardimcilar = await import("../src/lib/dashboardIzinBakiyesi.js");

    assert.equal(yardimcilar.bugunIzinEtiketi("IZINLI"), "İzinli");
    assert.equal(yardimcilar.bugunIzinEtiketi("YARIM_GUN_IZINLI"), "Yarım gün izinli");
    assert.equal(yardimcilar.bugunIzinEtiketi(null), null);
});
