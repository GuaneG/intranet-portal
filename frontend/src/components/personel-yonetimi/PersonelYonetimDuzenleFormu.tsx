import {
    type FormEvent,
    useId,
    useRef,
    useState,
} from "react";
import type {
    AtanabilirYoneticiSecenegi,
    LookupSecenegi,
    PersonelYonetimDetayi,
    PersonelYonetimGuncellemeRequest,
} from "../../types/personelYonetim";
import {
    ApiError,
    personelGuncelle,
} from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";

type PersonelYonetimDuzenleFormuProps = {
    detay: PersonelYonetimDetayi;
    departmanlar: LookupSecenegi[];
    departmanlarYukleniyor: boolean;
    departmanHatasi: string | null;
    onDepartmanlariTekrarDene: () => void;
    yoneticiler: AtanabilirYoneticiSecenegi[];
    yoneticilerYukleniyor: boolean;
    yoneticiHatasi: string | null;
    onDepartmanDegisti: (departmanId: number) => void;
    onYoneticileriTekrarDene: (departmanId: number) => void;
    onIptal: () => void;
    onKaydedildi: () => void;
};

/**
 * HTML input alanları null ve sayı değerlerini metin olarak tuttuğu için
 * form state'i backend request tipinden ayrı tanımlanır.
 */
type FormDegerleri = {
    ad: string;
    soyad: string;
    dogumTarihi: string;
    ePosta: string;
    departmanId: string;
    yoneticiId: string;
    isGirisTarihi: string;
    telNo: string;
    mezunOkul: string;
    mezunBolum: string;
    mezuniyetYili: string;
};

const BELIRGIN_FORM_ALANI_SINIFI = "!bg-gray-50 dark:!bg-white/[0.05]";

function formDegerleriniOlustur(
    detay: PersonelYonetimDetayi,
): FormDegerleri {
    return {
        ad: detay.ad,
        soyad: detay.soyad,
        dogumTarihi: detay.dogumTarihi,
        ePosta: detay.ePosta,
        departmanId: String(detay.departmanId),
        yoneticiId: detay.yoneticiId ?? "",
        isGirisTarihi: detay.isGirisTarihi,
        telNo: detay.telNo ?? "",
        mezunOkul: detay.mezunOkul ?? "",
        mezunBolum: detay.mezunBolum ?? "",
        mezuniyetYili:
            detay.mezuniyetYili === null
                ? ""
                : String(detay.mezuniyetYili),
    };
}

function metniNormalizeEt(deger: string) {
    return deger.trim();
}

function nullableMetniNormalizeEt(deger: string) {
    const normalizeEdilmis = deger.trim();

    return normalizeEdilmis === ""
        ? null
        : normalizeEdilmis;
}

function hataMesajiniGetir(error: unknown) {
    return error instanceof ApiError
        ? error.message
        : "Personel bilgileri güncellenirken beklenmeyen bir hata oluştu.";
}

function bugununTarihi() {
    const bugun = new Date();
    const yil = String(bugun.getFullYear());
    const ay = String(bugun.getMonth() + 1).padStart(2, "0");
    const gun = String(bugun.getDate()).padStart(2, "0");

    return `${yil}-${ay}-${gun}`;
}

function rolEtiketiniGetir(rol: PersonelYonetimDetayi["rol"]) {
    const etiketler = {
        PERSONEL: "Personel",
        YONETICI: "Yönetici",
        HR: "İnsan Kaynakları",
        ADMIN: "Admin",
    } as const;

    return etiketler[rol];
}

function formDogrulamaHatasiniGetir(
    degerler: FormDegerleri,
): string | null {
    if (!metniNormalizeEt(degerler.ad)) {
        return "Ad boş olamaz.";
    }

    if (!metniNormalizeEt(degerler.soyad)) {
        return "Soyad boş olamaz.";
    }

    if (!degerler.dogumTarihi) {
        return "Doğum tarihi boş olamaz.";
    }

    if (!metniNormalizeEt(degerler.ePosta)) {
        return "E-posta boş olamaz.";
    }

    if (!degerler.departmanId || Number(degerler.departmanId) <= 0) {
        return "Geçerli bir departman seçmelisiniz.";
    }

    if (!degerler.isGirisTarihi) {
        return "İşe giriş tarihi boş olamaz.";
    }

    if (degerler.mezuniyetYili.trim()) {
        const mezuniyetYili = Number(degerler.mezuniyetYili);
        const mevcutYil = new Date().getFullYear();

        if (
            !Number.isInteger(mezuniyetYili) ||
            mezuniyetYili <= 0 ||
            mezuniyetYili > mevcutYil
        ) {
            return "Mezuniyet yılı geçerli bir yıl olmalıdır.";
        }
    }

    return null;
}

function guncellemePayloadiniOlustur(
    ilkDegerler: FormDegerleri,
    guncelDegerler: FormDegerleri,
): PersonelYonetimGuncellemeRequest {
    const payload: PersonelYonetimGuncellemeRequest = {};

    const zorunluAlanlar = [
        "ad",
        "soyad",
        "dogumTarihi",
        "ePosta",
        "isGirisTarihi",
    ] as const;

    for (const alan of zorunluAlanlar) {
        const ilkDeger = metniNormalizeEt(ilkDegerler[alan]);
        const guncelDeger = metniNormalizeEt(
            guncelDegerler[alan],
        );

        if (ilkDeger !== guncelDeger) {
            payload[alan] = guncelDeger;
        }
    }

    const nullableAlanlar = [
        "telNo",
        "mezunOkul",
        "mezunBolum",
    ] as const;

    for (const alan of nullableAlanlar) {
        const ilkDeger = nullableMetniNormalizeEt(
            ilkDegerler[alan],
        );
        const guncelDeger = nullableMetniNormalizeEt(
            guncelDegerler[alan],
        );

        if (ilkDeger !== guncelDeger) {
            payload[alan] = guncelDeger;
        }
    }

    const ilkMezuniyetYili =
        ilkDegerler.mezuniyetYili === ""
            ? null
            : Number(ilkDegerler.mezuniyetYili);
    const guncelMezuniyetYili =
        guncelDegerler.mezuniyetYili === ""
            ? null
            : Number(guncelDegerler.mezuniyetYili);

    if (ilkMezuniyetYili !== guncelMezuniyetYili) {
        payload.mezuniyetYili = guncelMezuniyetYili;
    }

    const ilkDepartmanId = Number(ilkDegerler.departmanId);
    const guncelDepartmanId = Number(
        guncelDegerler.departmanId,
    );
    const departmanDegisti =
        ilkDepartmanId !== guncelDepartmanId;

    const ilkYoneticiId =
        ilkDegerler.yoneticiId === ""
            ? null
            : ilkDegerler.yoneticiId;
    const guncelYoneticiId =
        guncelDegerler.yoneticiId === ""
            ? null
            : guncelDegerler.yoneticiId;

    if (departmanDegisti) {
        // Backend, departman değişince yönetici alanını da zorunlu tutar.
        payload.departmanId = guncelDepartmanId;
        payload.yoneticiId = guncelYoneticiId;
    } else if (ilkYoneticiId !== guncelYoneticiId) {
        payload.yoneticiId = guncelYoneticiId;
    }

    return payload;
}

export default function PersonelYonetimDuzenleFormu({
                                                        detay,
                                                        departmanlar,
                                                        departmanlarYukleniyor,
                                                        departmanHatasi,
                                                        onDepartmanlariTekrarDene,
                                                        yoneticiler,
                                                        yoneticilerYukleniyor,
                                                        yoneticiHatasi,
                                                        onDepartmanDegisti,
                                                        onYoneticileriTekrarDene,
                                                        onIptal,
                                                        onKaydedildi,
                                                    }: PersonelYonetimDuzenleFormuProps) {
    const ilkDegerlerRef = useRef(
        formDegerleriniOlustur(detay),
    );

    const [degerler, setDegerler] = useState(
        ilkDegerlerRef.current,
    );
    const [hata, setHata] = useState("");
    const [bilgi, setBilgi] = useState("");
    const [gonderimSuruyor, setGonderimSuruyor] =
        useState(false);

    const gonderimSuruyorRef = useRef(false);

    const adId = useId();
    const soyadId = useId();
    const dogumTarihiId = useId();
    const ePostaId = useId();
    const departmanId = useId();
    const yoneticiId = useId();
    const isGirisTarihiId = useId();
    const telNoId = useId();
    const mezunOkulId = useId();
    const mezunBolumId = useId();
    const mezuniyetYiliId = useId();

    const bugun = bugununTarihi();

    const departmanDegisti =
        Number(degerler.departmanId) !==
        Number(ilkDegerlerRef.current.departmanId);

    const departmanSecimiDevreDisi =
        gonderimSuruyor ||
        departmanlarYukleniyor ||
        departmanHatasi !== null;

    const yoneticiSecimiDevreDisi =
        gonderimSuruyor ||
        yoneticilerYukleniyor ||
        yoneticiHatasi !== null;

    const kaydetmeDevreDisi =
        gonderimSuruyor ||
        (departmanDegisti &&
            (yoneticilerYukleniyor ||
                yoneticiHatasi !== null));

    function metinAlaniniGuncelle(
        alan:
            | "ad"
            | "soyad"
            | "dogumTarihi"
            | "ePosta"
            | "isGirisTarihi"
            | "telNo"
            | "mezunOkul"
            | "mezunBolum"
            | "mezuniyetYili",
        deger: string,
    ) {
        setDegerler((onceki) => ({
            ...onceki,
            [alan]: deger,
        }));
    }

    function departmanDegisiminiIsle(deger: string) {
        const yeniDepartmanId = Number(deger);
        const ilkDepartmanId = Number(ilkDegerlerRef.current.departmanId,);

        setDegerler((onceki) => ({
            ...onceki,
            departmanId: deger,

            // Kullanıcı ilk departmana geri dönerse, başlangıçtaki yöneticiyi
            // korur. Başka departman seçildiğinde yönetici bilinçli olarak temizlenir.
            yoneticiId:
                yeniDepartmanId === ilkDepartmanId
                    ? ilkDegerlerRef.current.yoneticiId
                    : "",
        }));

        if (yeniDepartmanId > 0) {
            onDepartmanDegisti(yeniDepartmanId);
        }
    }
    async function handleSubmit(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        if (gonderimSuruyorRef.current) {
            return;
        }

        setHata("");
        setBilgi("");

        const formHatasi = formDogrulamaHatasiniGetir(
            degerler,
        );

        if (formHatasi) {
            setHata(formHatasi);
            return;
        }

        if (departmanDegisti && yoneticilerYukleniyor) {
            setHata(
                "Departman değişikliğinin yönetici seçenekleri yükleniyor.",
            );
            return;
        }

        if (departmanDegisti && yoneticiHatasi) {
            setHata(
                "Departman değiştirirken yönetici bilgisi yüklenmelidir.",
            );
            return;
        }

        const payload = guncellemePayloadiniOlustur(
            ilkDegerlerRef.current,
            degerler,
        );

        if (Object.keys(payload).length === 0) {
            setBilgi("Değiştirilen bir alan bulunmuyor.");
            return;
        }

        gonderimSuruyorRef.current = true;
        setGonderimSuruyor(true);

        try {
            await personelGuncelle(detay.personelId, payload);
            onKaydedildi();
        } catch (error) {
            setHata(hataMesajiniGetir(error));
        } finally {
            gonderimSuruyorRef.current = false;
            setGonderimSuruyor(false);
        }
    }

    return (
        <form
            onSubmit={handleSubmit}
            aria-busy={gonderimSuruyor}
            className="space-y-6"
        >
            <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]">
                <h2 className="text-base font-semibold text-gray-800 dark:text-white/90">
                    Personel bilgisi
                </h2>

                <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                    <div>
                        <dt className="text-gray-500 dark:text-gray-400">
                            Kullanıcı adı
                        </dt>
                        <dd className="mt-1 font-medium text-gray-800 dark:text-white/90">
                            @{detay.kullaniciAdi}
                        </dd>
                    </div>

                    <div>
                        <dt className="text-gray-500 dark:text-gray-400">
                            Rol
                        </dt>
                        <dd className="mt-1 font-medium text-gray-800 dark:text-white/90">
                            {rolEtiketiniGetir(detay.rol)}
                        </dd>
                    </div>

                    <div>
                        <dt className="text-gray-500 dark:text-gray-400">
                            Durum
                        </dt>
                        <dd className="mt-1 font-medium text-gray-800 dark:text-white/90">
                            {detay.aktif ? "Aktif" : "Pasif"}
                        </dd>
                    </div>
                </dl>
            </section>

            <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]">
                <h2 className="text-base font-semibold text-gray-800 dark:text-white/90">
                    Kişisel bilgiler
                </h2>

                <div className="mt-4 grid gap-4 md:grid-cols-2">
                    <div>
                        <Label htmlFor={adId}>Ad</Label>
                        <Input
                            id={adId}
                            name="ad"
                            type="text"
                            value={degerler.ad}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "ad",
                                    event.target.value,
                                )
                            }
                            maxLength={50}
                            required
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>

                    <div>
                        <Label htmlFor={soyadId}>Soyad</Label>
                        <Input
                            id={soyadId}
                            name="soyad"
                            type="text"
                            value={degerler.soyad}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "soyad",
                                    event.target.value,
                                )
                            }
                            maxLength={50}
                            required
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>

                    <div>
                        <Label htmlFor={dogumTarihiId}>
                            Doğum tarihi
                        </Label>
                        <Input
                            id={dogumTarihiId}
                            name="dogumTarihi"
                            type="date"
                            value={degerler.dogumTarihi}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "dogumTarihi",
                                    event.target.value,
                                )
                            }
                            max={bugun}
                            required
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>

                    <div>
                        <Label htmlFor={ePostaId}>E-posta</Label>
                        <Input
                            id={ePostaId}
                            name="ePosta"
                            type="email"
                            value={degerler.ePosta}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "ePosta",
                                    event.target.value,
                                )
                            }
                            maxLength={100}
                            required
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>
                </div>
            </section>

            <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]">
                <h2 className="text-base font-semibold text-gray-800 dark:text-white/90">
                    İş bilgileri
                </h2>

                <div className="mt-4 grid gap-4 md:grid-cols-3">
                    <div>
                        <Label htmlFor={departmanId}>Departman</Label>
                        <select
                            id={departmanId}
                            name="departmanId"
                            value={degerler.departmanId}
                            disabled={departmanSecimiDevreDisi}
                            onChange={(event) =>
                                departmanDegisiminiIsle(
                                    event.target.value,
                                )
                            }
                            className="h-11 w-full rounded-lg border border-gray-300 bg-gray-50 px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-white/[0.05] dark:text-white/90"
                        >
                            {departmanHatasi && (
                                <option value={degerler.departmanId}>
                                    Mevcut departman bilgisi yüklenemedi
                                </option>
                            )}

                            {!departmanHatasi && (
                                <>
                                    <option value="" disabled>
                                        Departman seçin
                                    </option>

                                    {departmanlar.map((departman) => (
                                        <option
                                            key={departman.id}
                                            value={departman.id}
                                        >
                                            {departman.ad}
                                        </option>
                                    ))}
                                </>
                            )}
                        </select>

                        {departmanlarYukleniyor && (
                            <p
                                role="status"
                                aria-live="polite"
                                className="mt-2 text-sm text-gray-500 dark:text-gray-400"
                            >
                                Departmanlar yükleniyor…
                            </p>
                        )}

                        {departmanHatasi && (
                            <div
                                role="alert"
                                className="mt-2 flex flex-wrap items-center gap-3 text-sm text-error-500"
                            >
                                <span>{departmanHatasi}</span>

                                <button
                                    type="button"
                                    onClick={onDepartmanlariTekrarDene}
                                    disabled={gonderimSuruyor}
                                    className="font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    Departmanları tekrar dene
                                </button>
                            </div>
                        )}
                    </div>

                    <div>
                        <Label htmlFor={yoneticiId}>Yönetici</Label>
                        <select
                            id={yoneticiId}
                            name="yoneticiId"
                            value={degerler.yoneticiId}
                            disabled={yoneticiSecimiDevreDisi}
                            onChange={(event) =>
                                setDegerler((onceki) => ({
                                    ...onceki,
                                    yoneticiId: event.target.value,
                                }))
                            }
                            className="h-11 w-full rounded-lg border border-gray-300 bg-gray-50 px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-white/[0.05] dark:text-white/90"
                        >
                            <option value="">Yönetici yok</option>

                            {yoneticiHatasi &&
                                degerler.yoneticiId && (
                                    <option
                                        value={degerler.yoneticiId}
                                    >
                                        Mevcut yönetici bilgisi yüklenemedi
                                    </option>
                                )}

                            {!yoneticiHatasi &&
                                yoneticiler.map((yonetici) => (
                                    <option
                                        key={yonetici.personelId}
                                        value={yonetici.personelId}
                                    >
                                        {yonetici.ad} {yonetici.soyad}
                                        {" "}(@{yonetici.kullaniciAdi})
                                    </option>
                                ))}
                        </select>

                        {yoneticilerYukleniyor && (
                            <p
                                role="status"
                                aria-live="polite"
                                className="mt-2 text-sm text-gray-500 dark:text-gray-400"
                            >
                                Yönetici seçenekleri yükleniyor…
                            </p>
                        )}

                        {yoneticiHatasi && (
                            <div
                                role="alert"
                                className="mt-2 flex flex-wrap items-center gap-3 text-sm text-error-500"
                            >
                                <span>{yoneticiHatasi}</span>

                                <button
                                    type="button"
                                    onClick={() =>
                                        onYoneticileriTekrarDene(
                                            Number(
                                                degerler.departmanId,
                                            ),
                                        )
                                    }
                                    disabled={gonderimSuruyor}
                                    className="font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    Yöneticileri tekrar dene
                                </button>
                            </div>
                        )}
                    </div>

                    <div>
                        <Label htmlFor={isGirisTarihiId}>
                            İşe giriş tarihi
                        </Label>
                        <Input
                            id={isGirisTarihiId}
                            name="isGirisTarihi"
                            type="date"
                            value={degerler.isGirisTarihi}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "isGirisTarihi",
                                    event.target.value,
                                )
                            }
                            max={bugun}
                            required
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>
                </div>
            </section>

            <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]">
                <h2 className="text-base font-semibold text-gray-800 dark:text-white/90">
                    İletişim ve eğitim
                </h2>

                <div className="mt-4 grid gap-4 md:grid-cols-2">
                    <div>
                        <Label htmlFor={telNoId}>Telefon</Label>
                        <Input
                            id={telNoId}
                            name="telNo"
                            type="tel"
                            value={degerler.telNo}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "telNo",
                                    event.target.value,
                                )
                            }
                            maxLength={30}
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>

                    <div>
                        <Label htmlFor={mezuniyetYiliId}>
                            Mezuniyet yılı
                        </Label>
                        <Input
                            id={mezuniyetYiliId}
                            name="mezuniyetYili"
                            type="number"
                            value={degerler.mezuniyetYili}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "mezuniyetYili",
                                    event.target.value,
                                )
                            }
                            min="1"
                            max={String(new Date().getFullYear())}
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>

                    <div>
                        <Label htmlFor={mezunOkulId}>
                            Mezun olunan okul
                        </Label>
                        <Input
                            id={mezunOkulId}
                            name="mezunOkul"
                            type="text"
                            value={degerler.mezunOkul}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "mezunOkul",
                                    event.target.value,
                                )
                            }
                            maxLength={100}
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>

                    <div>
                        <Label htmlFor={mezunBolumId}>
                            Mezun olunan bölüm
                        </Label>
                        <Input
                            id={mezunBolumId}
                            name="mezunBolum"
                            type="text"
                            value={degerler.mezunBolum}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "mezunBolum",
                                    event.target.value,
                                )
                            }
                            maxLength={100}
                            className={BELIRGIN_FORM_ALANI_SINIFI}
                        />
                    </div>
                </div>
            </section>

            {hata && (
                <p
                    role="alert"
                    className="text-sm text-error-500"
                >
                    {hata}
                </p>
            )}

            {bilgi && (
                <p
                    role="status"
                    aria-live="polite"
                    className="text-sm text-success-600 dark:text-success-400"
                >
                    {bilgi}
                </p>
            )}

            <div className="flex flex-wrap gap-3">
                <button
                    type="submit"
                    disabled={kaydetmeDevreDisi}
                    className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {gonderimSuruyor
                        ? "Kaydediliyor…"
                        : "Kaydet"}
                </button>

                <button
                    type="button"
                    disabled={gonderimSuruyor}
                    onClick={onIptal}
                    className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                >
                    İptal
                </button>
            </div>
        </form>
    );
}
