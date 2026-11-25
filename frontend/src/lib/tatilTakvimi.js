const TATIL_KATEGORI_ETIKETLERI = {
    RESMI_SABIT: "Sabit resmî tatil",
    RESMI_DEGISKEN: "Değişken resmî tatil",
    SIRKET: "Şirket günü",
};

const TATIL_SINIFLARI = {
    RESMI_SABIT: "primary",
    RESMI_DEGISKEN: "warning",
    SIRKET: "success",
};

export const TAKVIM_EVENT_TURLERI = {
    TATIL: "TATIL",
    DOGUM_GUNU: "DOGUM_GUNU",
};

export function tarihiYerelIsoBicimlendir(tarih) {
    const yil = tarih.getFullYear();
    const ay = String(tarih.getMonth() + 1).padStart(2, "0");
    const gun = String(tarih.getDate()).padStart(2, "0");

    return `${yil}-${ay}-${gun}`;
}

/** FullCalendar bitişini exclusive, backend bitişini inclusive kabul eder. */
export function gorunenTakvimAraliginiOlustur(baslangic, exclusiveBitis) {
    const bitis = new Date(exclusiveBitis);
    bitis.setDate(bitis.getDate() - 1);

    return {
        baslangicTarihi: tarihiYerelIsoBicimlendir(baslangic),
        bitisTarihi: tarihiYerelIsoBicimlendir(bitis),
    };
}

export function tatilKategoriEtiketi(kategori) {
    return TATIL_KATEGORI_ETIKETLERI[kategori] ?? "Tatil";
}

export function tatilGunSuresiEtiketi(gunSuresi) {
    return gunSuresi === "YARIM_GUN" ? "Yarım gün" : "Tam gün";
}

export function tatilSinifiniGetir(kategori) {
    return TATIL_SINIFLARI[kategori] ?? "primary";
}

/** TailAdmin takvim teması event'i şeffaflaştırdığı için renkli kart sınıfı zorunludur. */
export function tatilEventSiniflariniGetir(kategori) {
    return ["event-fc-color", `fc-bg-${tatilSinifiniGetir(kategori)}`];
}

function isoTarihiParcala(tarih) {
    return tarih.split("-").map(Number);
}

/** Backend bitişini dahil, FullCalendar bitişini hariç kabul eder. */
export function fullCalendarBitisiniGetir(dahilBitisTarihi) {
    const [yil, ay, gun] = isoTarihiParcala(dahilBitisTarihi);
    const sonrakiGun = new Date(yil, ay - 1, gun);
    sonrakiGun.setDate(sonrakiGun.getDate() + 1);
    return tarihiYerelIsoBicimlendir(sonrakiGun);
}

export function tatiliTakvimOlayinaDonustur(tatil) {
    return {
        id: String(tatil.tatilId),
        title: tatil.ad,
        start: tatil.tarih,
        end: fullCalendarBitisiniGetir(tatil.bitisTarihi),
        allDay: true,
        classNames: tatilEventSiniflariniGetir(tatil.kategori),
        extendedProps: {
            tur: TAKVIM_EVENT_TURLERI.TATIL,
            kategori: tatil.kategori,
            gunSuresi: tatil.gunSuresi,
        },
    };
}

/**
 * Aynı ad/tarih/departmana sahip kayıtlar gerçek hayatta ayrı kişiler olabilir.
 * Backend kimlik göndermediği için sıra yalnız FullCalendar event anahtarını ayırır.
 */
export function dogumGununuTakvimOlayinaDonustur(dogumGunu, sira) {
    return {
        id: `dogum-gunu-${dogumGunu.tarih}-${sira}`,
        title: dogumGunu.adSoyad,
        start: dogumGunu.tarih,
        end: fullCalendarBitisiniGetir(dogumGunu.tarih),
        allDay: true,
        editable: false,
        startEditable: false,
        durationEditable: false,
        classNames: ["event-fc-color", "fc-bg-birthday"],
        extendedProps: {
            tur: TAKVIM_EVENT_TURLERI.DOGUM_GUNU,
            departman: dogumGunu.departman,
        },
    };
}

export function tatilAraliginiDogrula(tarih, bitisTarihi) {
    if (!tarih || !bitisTarihi) return "Başlangıç ve bitiş tarihi zorunludur.";
    if (bitisTarihi < tarih) return "Bitiş tarihi başlangıç tarihinden önce olamaz.";

    const [baslangicYil, baslangicAy, baslangicGun] = isoTarihiParcala(tarih);
    const [bitisYil, bitisAy, bitisGun] = isoTarihiParcala(bitisTarihi);
    const baslangic = Date.UTC(baslangicYil, baslangicAy - 1, baslangicGun);
    const bitis = Date.UTC(bitisYil, bitisAy - 1, bitisGun);
    const dahilGunSayisi = ((bitis - baslangic) / 86_400_000) + 1;

    if (dahilGunSayisi > 366) return "Tatil aralığı en fazla 366 gün olabilir.";
    return null;
}

export function rolTatilYonetebilirMi(rol) {
    return rol === "HR" || rol === "ADMIN";
}

/** Tatil grubu kapsadığı herhangi bir gün üzerinden düzenlenir. */
export function tarihteTatilBul(tatiller, tarih) {
    return tatiller.find((tatil) => (
        tatil.tarih <= tarih && (tatil.bitisTarihi ?? tatil.tarih) >= tarih
    ));
}

/** JSON number'ı yuvarlamadan Türkçe ondalık ayıracıyla gösterir. */
export function gunDegeriniTurkceBicimlendir(deger) {
    return String(deger).replace(".", ",");
}
