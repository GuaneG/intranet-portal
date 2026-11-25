import assert from "node:assert/strict";
import test from "node:test";

import {
    departmanFiltresiSecilebilirMi,
    yoneticiEtiketiGorunurMu,
} from "../src/lib/ekipDiziniGorunurluk.js";
import { hiyerarsiTablosuGosterilmeli } from "../src/lib/ekipDiziniGorunurluk.js";

test("yöneticin etiketi personel, yönetici ve HR için görünür", () => {
    assert.equal(yoneticiEtiketiGorunurMu("PERSONEL"), true);
    assert.equal(yoneticiEtiketiGorunurMu("YONETICI"), true);
    assert.equal(yoneticiEtiketiGorunurMu("HR"), true);
    assert.equal(yoneticiEtiketiGorunurMu("ADMIN"), false);
});

test("yönetici kendi departmanı seçili başlasa da departman filtresini değiştirebilir", () => {
    assert.equal(departmanFiltresiSecilebilirMi("PERSONEL"), false);
    assert.equal(departmanFiltresiSecilebilirMi("YONETICI"), true);
    assert.equal(departmanFiltresiSecilebilirMi("HR"), true);
    assert.equal(departmanFiltresiSecilebilirMi("ADMIN"), true);
});

test("her rol filtre uygulansa da kendi hiyerarşi görünümünü tabloda korur", () => {
    assert.equal(hiyerarsiTablosuGosterilmeli("PERSONEL", true), true);
    assert.equal(hiyerarsiTablosuGosterilmeli("YONETICI", true), true);
    assert.equal(hiyerarsiTablosuGosterilmeli("HR", true), true);
    assert.equal(hiyerarsiTablosuGosterilmeli("ADMIN", true), true);
    assert.equal(hiyerarsiTablosuGosterilmeli("ADMIN", false), true);
});
