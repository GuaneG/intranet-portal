export function bugununTarihi() {
    const tarih = new Date();
    return `${tarih.getFullYear()}-${String(tarih.getMonth() + 1).padStart(2, "0")}-${String(tarih.getDate()).padStart(2, "0")}`;
}

export function enIleriRezervasyonTarihi(bugun = bugununTarihi()) {
    const [yil, ay, gun] = bugun.split("-").map(Number);
    const tarih = new Date(yil, ay - 1, gun);
    tarih.setDate(tarih.getDate() + 90);
    return `${tarih.getFullYear()}-${String(tarih.getMonth() + 1).padStart(2, "0")}-${String(tarih.getDate()).padStart(2, "0")}`;
}

export function rezervasyonFormunuDogrula(form, { bugun, mevcutSaat, odaKapasitesi, tatilGunSuresi }) {
    const odaId = Number(form.odaId);
    const baslangicSaat = Number(form.baslangicSaat);
    const bitisSaat = Number(form.bitisSaat);
    const katilimciSayisi = Number(form.katilimciSayisi);
    const baslik = form.baslik.trim();

    if (!Number.isInteger(odaId) || odaId <= 0) return "Oda seçmelisiniz.";
    if (!form.tarih) return "Rezervasyon tarihi zorunludur.";
    if (form.tarih < bugun) return "Geçmiş tarih için rezervasyon oluşturulamaz.";
    if (form.tarih > enIleriRezervasyonTarihi(bugun)) return "Rezervasyon en fazla 90 gün ileri tarih için oluşturulabilir.";
    if (new Date(`${form.tarih}T00:00:00`).getDay() === 0 || new Date(`${form.tarih}T00:00:00`).getDay() === 6) return "Hafta sonu için rezervasyon oluşturulamaz.";
    if (tatilGunSuresi === "TAM_GUN") return "Tam gün tatilde rezervasyon oluşturulamaz.";
    if (!Number.isInteger(baslangicSaat) || !Number.isInteger(bitisSaat) || baslangicSaat < 8 || bitisSaat > 18 || bitisSaat <= baslangicSaat) return "Başlangıç ve bitiş saati çalışma aralığında olmalıdır.";
    if (tatilGunSuresi === "YARIM_GUN" && bitisSaat > 13) return "Yarım gün tatilde rezervasyon en geç 13:00'te bitmelidir.";
    if (bitisSaat - baslangicSaat > 4) return "Rezervasyon en fazla 4 saat sürebilir.";
    if (form.tarih === bugun && baslangicSaat <= mevcutSaat) return "Bugün için yalnız sonraki tam saatten başlayan rezervasyon oluşturulabilir.";
    if (!baslik) return "Rezervasyon başlığı zorunludur.";
    if (baslik.length > 50) return "Rezervasyon başlığı en fazla 50 karakter olabilir.";
    if (!Number.isInteger(katilimciSayisi) || katilimciSayisi < 1) return "Katılımcı sayısı en az 1 olmalıdır.";
    if (odaKapasitesi !== null && katilimciSayisi > odaKapasitesi) return "Katılımcı sayısı oda kapasitesini aşamaz.";
    return null;
}

export function rezervasyonIstekGövdesiniOlustur(form) {
    return {
        odaId: Number(form.odaId),
        tarih: form.tarih,
        baslangicSaat: Number(form.baslangicSaat),
        bitisSaat: Number(form.bitisSaat),
        baslik: form.baslik.trim(),
        katilimciSayisi: Number(form.katilimciSayisi),
    };
}

export function iptalNedeniniDogrula(iptalNedeni) {
    const temizlenmisNeden = iptalNedeni.trim();
    if (!temizlenmisNeden) return "İptal nedeni zorunludur.";
    if (temizlenmisNeden.length > 500) return "İptal nedeni en fazla 500 karakter olabilir.";
    return null;
}

/** Günlük oda grid'inin gösterdiği çalışma saatleri; her hücre bir saattir. */
export const GUNLUK_REZERVASYON_SAATLERI = Array.from({ length: 10 }, (_, index) => index + 8);

function haftaSonuMu(tarih) {
    const gun = new Date(`${tarih}T00:00:00`).getDay();
    return gun === 0 || gun === 6;
}

function hucredeRezervasyonBul(rezervasyonlar, odaId, tarih, saat) {
    return rezervasyonlar.find((rezervasyon) => (
        rezervasyon.oda.id === odaId
        && rezervasyon.tarih === tarih
        && rezervasyon.baslangicSaat <= saat
        && rezervasyon.bitisSaat > saat
    )) ?? null;
}

/**
 * Seçili gün için her oda/saat hücresinin kullanılabilirlik durumunu üretir.
 * Tatil, hafta sonu ve bugün başlamış saatler yalnız arayüz seçimini kapatır;
 * rezervasyon oluşturma çağrısının nihai denetimi backend'dedir.
 */
export function gunlukOdaGridiniOlustur(odalar, rezervasyonlar, { tarih, tatilGunSuresi, bugun, mevcutSaat }) {
    return odalar.map((oda) => ({
        oda,
        hucreler: GUNLUK_REZERVASYON_SAATLERI.map((baslangicSaat) => {
            const rezervasyon = hucredeRezervasyonBul(rezervasyonlar, oda.id, tarih, baslangicSaat);
            const kapaliMi = tatilGunSuresi === "TAM_GUN"
                || tatilGunSuresi === "YARIM_GUN" && baslangicSaat >= 13
                || haftaSonuMu(tarih)
                || tarih === bugun && baslangicSaat <= mevcutSaat;

            return {
                baslangicSaat,
                durum: rezervasyon ? "DOLU" : kapaliMi ? "KAPALI" : "MUSAIT",
                rezervasyon,
            };
        }),
    }));
}

/** Bir veya birden fazla bitişik boş saat seçimini form alanlarına çevirir. */
export function gridSecimindenRezervasyonAraligiOlustur(odaId, ilkSaat, sonSaat, hucreler) {
    if (ilkSaat > sonSaat || sonSaat - ilkSaat + 1 > 4) return null;

    for (let saat = ilkSaat; saat <= sonSaat; saat += 1) {
        const hucre = hucreler.find((kayit) => kayit.baslangicSaat === saat);
        if (!hucre || hucre.durum !== "MUSAIT") return null;
    }

    return {
        odaId: String(odaId),
        baslangicSaat: String(ilkSaat),
        bitisSaat: String(sonSaat + 1),
    };
}

/** Formdaki geçerli saat aralığını, ekranda görünen günlük grid seçimine çevirir. */
export function formdanGridSecimiOlustur(form, seciliTarih, satirlar) {
    const odaId = Number(form.odaId);
    const ilkSaat = Number(form.baslangicSaat);
    const bitisSaat = Number(form.bitisSaat);

    if (
        form.tarih !== seciliTarih
        || !Number.isInteger(odaId)
        || !Number.isInteger(ilkSaat)
        || !Number.isInteger(bitisSaat)
        || ilkSaat < 8
        || bitisSaat > 18
        || bitisSaat <= ilkSaat
        || bitisSaat - ilkSaat > 4
    ) return null;

    const satir = satirlar.find((kayit) => kayit.oda.id === odaId);
    if (!satir) return null;

    const aralik = gridSecimindenRezervasyonAraligiOlustur(odaId, ilkSaat, bitisSaat - 1, satir.hucreler);
    return aralik ? { odaId, ilkSaat, sonSaat: bitisSaat - 1 } : null;
}
