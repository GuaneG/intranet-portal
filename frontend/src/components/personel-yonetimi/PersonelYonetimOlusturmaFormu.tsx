import {
    type FormEvent,
    useId,
    useRef,
    useState,
} from "react";
import type {
    AtanabilirYoneticiSecenegi,
    LookupSecenegi,
    PersonelOlusturmaRequest,
    PersonelOlusturmaResponse,
} from "../../types/personelYonetim";
import {
    ApiError,
    personelOlustur,
} from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";

type PersonelYonetimOlusturmaFormuProps = {
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
    onOlusturuldu: (
        sonuc: PersonelOlusturmaResponse,
    ) => void;
};

type FormDegerleri = {
    kullaniciAdi: string;
    ad: string;
    soyad: string;
    dogumTarihi: string;
    ePosta: string;
    departmanId: string;
    yoneticiId: string;
    isGirisTarihi: string;
};

const baslangicDegerleri: FormDegerleri = {
    kullaniciAdi: "",
    ad: "",
    soyad: "",
    dogumTarihi: "",
    ePosta: "",
    departmanId: "",
    yoneticiId: "",
    isGirisTarihi: "",
};

function bugununTarihi() {
    const bugun = new Date();
    const yil = String(bugun.getFullYear());
    const ay = String(bugun.getMonth() + 1).padStart(2, "0");
    const gun = String(bugun.getDate()).padStart(2, "0");

    return `${yil}-${ay}-${gun}`;
}

function dununTarihi() {
    const dun = new Date();
    dun.setDate(dun.getDate() - 1);

    const yil = String(dun.getFullYear());
    const ay = String(dun.getMonth() + 1).padStart(2, "0");
    const gun = String(dun.getDate()).padStart(2, "0");

    return `${yil}-${ay}-${gun}`;
}

function hataMesajiniGetir(error: unknown) {
    return error instanceof ApiError
        ? error.message
        : "Personel oluşturulurken beklenmeyen bir hata oluştu.";
}

function formHatasiniGetir(
    degerler: FormDegerleri,
): string | null {
    const kullaniciAdi = degerler.kullaniciAdi.trim();
    const ad = degerler.ad.trim();
    const soyad = degerler.soyad.trim();
    const ePosta = degerler.ePosta.trim();

    if (kullaniciAdi.length < 3 || kullaniciAdi.length > 50) {
        return "Kullanıcı adı 3 ile 50 karakter arasında olmalıdır.";
    }

    if (ad.length < 1 || ad.length > 50) {
        return "Ad 1 ile 50 karakter arasında olmalıdır.";
    }

    if (soyad.length < 3 || soyad.length > 50) {
        return "Soyad 3 ile 50 karakter arasında olmalıdır.";
    }

    if (!degerler.dogumTarihi) {
        return "Doğum tarihi boş olamaz.";
    }

    if (degerler.dogumTarihi >= bugununTarihi()) {
        return "Doğum tarihi geçmişte olmalıdır.";
    }

    if (!ePosta) {
        return "E-posta boş olamaz.";
    }

    if (!degerler.departmanId || Number(degerler.departmanId) <= 0) {
        return "Geçerli bir departman seçmelisiniz.";
    }

    if (!degerler.isGirisTarihi) {
        return "İşe giriş tarihi boş olamaz.";
    }

    if (degerler.isGirisTarihi > bugununTarihi()) {
        return "İşe giriş tarihi gelecekte olamaz.";
    }

    return null;
}

function requestOlustur(
    degerler: FormDegerleri,
): PersonelOlusturmaRequest {
    const request: PersonelOlusturmaRequest = {
        kullaniciAdi: degerler.kullaniciAdi.trim(),
        ad: degerler.ad.trim(),
        soyad: degerler.soyad.trim(),
        dogumTarihi: degerler.dogumTarihi,
        ePosta: degerler.ePosta.trim(),
        departmanId: Number(degerler.departmanId),
        isGirisTarihi: degerler.isGirisTarihi,
    };

    // Boş seçim backend'e boş string değil, hiç alan göndermemek demektir.
    if (degerler.yoneticiId) {
        request.yoneticiId = degerler.yoneticiId;
    }

    return request;
}

/**
 * Yeni personel için yalnız backend'in kabul ettiği alanları toplar.
 * Departman-yönetici uyumluluğu yine backend tarafından doğrulanır.
 */
export default function PersonelYonetimOlusturmaFormu({
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
                                                          onOlusturuldu,
                                                      }: PersonelYonetimOlusturmaFormuProps) {
    const [degerler, setDegerler] =
        useState<FormDegerleri>(baslangicDegerleri);
    const [hata, setHata] = useState("");
    const [gonderimSuruyor, setGonderimSuruyor] =
        useState(false);

    // Render gerçekleşmeden gelen hızlı ikinci form submit'ini engeller.
    const gonderimKilidi = useRef(false);

    const kullaniciAdiId = useId();
    const adId = useId();
    const soyadId = useId();
    const dogumTarihiId = useId();
    const ePostaId = useId();
    const departmanId = useId();
    const yoneticiId = useId();
    const isGirisTarihiId = useId();

    const departmanSecimiDevreDisi =
        gonderimSuruyor ||
        departmanlarYukleniyor ||
        departmanHatasi !== null;

    const yoneticiSecimiDevreDisi =
        gonderimSuruyor ||
        !degerler.departmanId ||
        yoneticilerYukleniyor ||
        yoneticiHatasi !== null;

    function metinAlaniniGuncelle(
        alan:
            | "kullaniciAdi"
            | "ad"
            | "soyad"
            | "dogumTarihi"
            | "ePosta"
            | "isGirisTarihi",
        deger: string,
    ) {
        setDegerler((onceki) => ({
            ...onceki,
            [alan]: deger,
        }));
    }

    function departmanDegisiminiIsle(deger: string) {
        setDegerler((onceki) => ({
            ...onceki,
            departmanId: deger,
            // Yeni departmandaki yöneticiler henüz bilinmediği için seçim temizlenir.
            yoneticiId: "",
        }));

        const yeniDepartmanId = Number(deger);

        if (yeniDepartmanId > 0) {
            onDepartmanDegisti(yeniDepartmanId);
        }
    }

    async function formuGonder(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        if (gonderimKilidi.current) {
            return;
        }

        setHata("");

        const formHatasi = formHatasiniGetir(degerler);

        if (formHatasi) {
            setHata(formHatasi);
            return;
        }

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);

        try {
            const sonuc = await personelOlustur(
                requestOlustur(degerler),
            );

            onOlusturuldu(sonuc);
        } catch (error) {
            setHata(hataMesajiniGetir(error));
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    return (
        <form
            onSubmit={formuGonder}
            aria-busy={gonderimSuruyor}
            className="space-y-6"
        >
            <section className="rounded-xl border border-gray-200 p-5 dark:border-white/[0.05]">
                <h2 className="text-base font-semibold text-gray-800 dark:text-white/90">
                    Hesap bilgileri
                </h2>

                <div className="mt-4">
                    <Label htmlFor={kullaniciAdiId}>
                        Kullanıcı adı
                    </Label>
                    <Input
                        id={kullaniciAdiId}
                        name="kullaniciAdi"
                        type="text"
                        value={degerler.kullaniciAdi}
                        disabled={gonderimSuruyor}
                        onChange={(event) =>
                            metinAlaniniGuncelle(
                                "kullaniciAdi",
                                event.target.value,
                            )
                        }
                        maxLength={50}
                        required
                    />
                </div>
            </section>

            <section className="rounded-xl border border-gray-200 p-5 dark:border-white/[0.05]">
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
                            disabled={gonderimSuruyor}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "ad",
                                    event.target.value,
                                )
                            }
                            maxLength={50}
                            required
                        />
                    </div>

                    <div>
                        <Label htmlFor={soyadId}>Soyad</Label>
                        <Input
                            id={soyadId}
                            name="soyad"
                            type="text"
                            value={degerler.soyad}
                            disabled={gonderimSuruyor}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "soyad",
                                    event.target.value,
                                )
                            }
                            maxLength={50}
                            required
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
                            disabled={gonderimSuruyor}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "dogumTarihi",
                                    event.target.value,
                                )
                            }
                            max={dununTarihi()}
                            required
                        />
                    </div>

                    <div>
                        <Label htmlFor={ePostaId}>E-posta</Label>
                        <Input
                            id={ePostaId}
                            name="ePosta"
                            type="email"
                            value={degerler.ePosta}
                            disabled={gonderimSuruyor}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "ePosta",
                                    event.target.value,
                                )
                            }
                            maxLength={100}
                            required
                        />
                    </div>
                </div>
            </section>

            <section className="rounded-xl border border-gray-200 p-5 dark:border-white/[0.05]">
                <h2 className="text-base font-semibold text-gray-800 dark:text-white/90">
                    İş bilgileri
                </h2>

                <div className="mt-4 grid gap-4 md:grid-cols-2">
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
                            className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            required
                        >
                            <option value="">
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
                        </select>

                        {departmanHatasi && (
                            <button
                                type="button"
                                disabled={gonderimSuruyor}
                                onClick={onDepartmanlariTekrarDene}
                                className="mt-2 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Departmanları tekrar dene
                            </button>
                        )}
                    </div>

                    <div>
                        <Label htmlFor={yoneticiId}>
                            Yönetici
                        </Label>
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
                            className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                        >
                            <option value="">Yönetici yok</option>
                            {yoneticiler.map((yonetici) => (
                                <option
                                    key={yonetici.personelId}
                                    value={yonetici.personelId}
                                >
                                    {yonetici.ad} {yonetici.soyad} (
                                    @{yonetici.kullaniciAdi})
                                </option>
                            ))}
                        </select>

                        {yoneticilerYukleniyor && (
                            <p
                                role="status"
                                className="mt-2 text-sm text-gray-500 dark:text-gray-400"
                            >
                                Yönetici seçenekleri yükleniyor…
                            </p>
                        )}

                        {yoneticiHatasi && (
                            <button
                                type="button"
                                disabled={
                                    gonderimSuruyor ||
                                    !degerler.departmanId
                                }
                                onClick={() =>
                                    onYoneticileriTekrarDene(
                                        Number(
                                            degerler.departmanId,
                                        ),
                                    )
                                }
                                className="mt-2 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Yöneticileri tekrar dene
                            </button>
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
                            disabled={gonderimSuruyor}
                            onChange={(event) =>
                                metinAlaniniGuncelle(
                                    "isGirisTarihi",
                                    event.target.value,
                                )
                            }
                            max={bugununTarihi()}
                            required
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

            <div className="flex flex-wrap gap-3">
                <button
                    type="submit"
                    disabled={gonderimSuruyor}
                    className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {gonderimSuruyor
                        ? "Oluşturuluyor…"
                        : "Personel oluştur"}
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