import { type FormEvent, useId, useRef, useState } from "react";
import type {
    ProfilBilgileri,
    ProfilGuncellemeRequest,
} from "../../types/profil";
import {
    ApiError,
    profilimiGetir,
    profilimiGuncelle,
} from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Button from "../ui/button/Button";

type DuzenlenebilirProfilAlanlari = Pick<
    ProfilBilgileri,
    "telNo" | "mezunOkul" | "mezunBolum"
>;

type ProfilDuzenleFormProps = {
    baslangicDegerleri: DuzenlenebilirProfilAlanlari;
    onIptal: () => void;
    onProfilYenilendi: (profil: ProfilBilgileri) => void;
};

function profilDegeriniNormalizeEt(deger: string | null): string | null {
    const temizlenmisDeger = deger?.trim() ?? "";
    return temizlenmisDeger.length > 0 ? temizlenmisDeger : null;
}

function profilGuncellemePayloadiOlustur(
    baslangicDegerleri: DuzenlenebilirProfilAlanlari,
    yeniDegerler: DuzenlenebilirProfilAlanlari,
): ProfilGuncellemeRequest {
    const payload: ProfilGuncellemeRequest = {};

    const eskiTelNo = profilDegeriniNormalizeEt(baslangicDegerleri.telNo);
    const yeniTelNo = profilDegeriniNormalizeEt(yeniDegerler.telNo);

    if (eskiTelNo !== yeniTelNo) {
        payload.telNo = yeniTelNo;
    }

    const eskiMezunOkul = profilDegeriniNormalizeEt(
        baslangicDegerleri.mezunOkul,
    );
    const yeniMezunOkul = profilDegeriniNormalizeEt(yeniDegerler.mezunOkul);

    if (eskiMezunOkul !== yeniMezunOkul) {
        payload.mezunOkul = yeniMezunOkul;
    }

    const eskiMezunBolum = profilDegeriniNormalizeEt(
        baslangicDegerleri.mezunBolum,
    );
    const yeniMezunBolum = profilDegeriniNormalizeEt(yeniDegerler.mezunBolum);

    if (eskiMezunBolum !== yeniMezunBolum) {
        payload.mezunBolum = yeniMezunBolum;
    }

    return payload;
}

function profilGuncellemeHataMesajiOlustur(error: unknown): string {
    return error instanceof ApiError
        ? error.message
        : "Profil bilgileri güncellenirken beklenmeyen bir hata oluştu.";
}

const PROFIL_YENILEME_HATA_MESAJI =
    "Bilgileriniz kaydedildi ancak güncel profil yüklenemedi. Lütfen tekrar deneyin.";

export default function ProfilDuzenleForm({
                                              baslangicDegerleri,
                                              onIptal,
                                              onProfilYenilendi,
                                          }: ProfilDuzenleFormProps) {
    const benzersizId = useId();

    const telNoId = `${benzersizId}-tel-no`;
    const mezunOkulId = `${benzersizId}-mezun-okul`;
    const mezunBolumId = `${benzersizId}-mezun-bolum`;

    const ilkDegerlerRef = useRef<DuzenlenebilirProfilAlanlari>({
        telNo: profilDegeriniNormalizeEt(baslangicDegerleri.telNo),
        mezunOkul: profilDegeriniNormalizeEt(baslangicDegerleri.mezunOkul),
        mezunBolum: profilDegeriniNormalizeEt(baslangicDegerleri.mezunBolum),
    });

    const [telNo, setTelNo] = useState(
        ilkDegerlerRef.current.telNo ?? "",
    );
    const [mezunOkul, setMezunOkul] = useState(
        ilkDegerlerRef.current.mezunOkul ?? "",
    );
    const [mezunBolum, setMezunBolum] = useState(
        ilkDegerlerRef.current.mezunBolum ?? "",
    );

    const [hata, setHata] = useState("");
    const [bilgi, setBilgi] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [yenilemeBekliyor, setYenilemeBekliyor] = useState(false);
    const [yenilemeYapiliyor, setYenilemeYapiliyor] = useState(false);

    const isSubmittingRef = useRef(false);
    const yenilemeYapiliyorRef = useRef(false);

    const formKilitli =
        isSubmitting || yenilemeBekliyor || yenilemeYapiliyor;

    function mesajlariTemizle() {
        setHata("");
        setBilgi("");
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (
            isSubmittingRef.current ||
            yenilemeYapiliyorRef.current ||
            yenilemeBekliyor
        ) {
            return;
        }

        mesajlariTemizle();

        const payload = profilGuncellemePayloadiOlustur(
            ilkDegerlerRef.current,
            {
                telNo,
                mezunOkul,
                mezunBolum,
            },
        );

        if (Object.keys(payload).length === 0) {
            setBilgi("Kaydedilecek bir değişiklik yok.");
            return;
        }

        isSubmittingRef.current = true;
        setIsSubmitting(true);

        try {
            await profilimiGuncelle(payload);
        } catch (error) {
            setHata(profilGuncellemeHataMesajiOlustur(error));
            isSubmittingRef.current = false;
            setIsSubmitting(false);
            return;
        }

        setBilgi("Bilgileriniz kaydedildi. Güncel profil yükleniyor...");

        let guncelProfil: ProfilBilgileri;

        try {
            guncelProfil = (await profilimiGetir()) as ProfilBilgileri;
        } catch {
            setYenilemeBekliyor(true);
            setBilgi("Değişiklikleriniz kaydedildi.");
            setHata(PROFIL_YENILEME_HATA_MESAJI);
            isSubmittingRef.current = false;
            setIsSubmitting(false);
            return;
        }

        setBilgi("");
        isSubmittingRef.current = false;
        setIsSubmitting(false);
        onProfilYenilendi(guncelProfil);
    }

    async function handleGuncelProfiliYukle() {
        if (
            yenilemeYapiliyorRef.current ||
            isSubmittingRef.current ||
            !yenilemeBekliyor
        ) {
            return;
        }

        yenilemeYapiliyorRef.current = true;
        setYenilemeYapiliyor(true);
        setHata("");
        setBilgi("Güncel profil yükleniyor...");

        let guncelProfil: ProfilBilgileri;

        try {
            guncelProfil = (await profilimiGetir()) as ProfilBilgileri;
        } catch {
            setBilgi("Değişiklikleriniz kaydedildi.");
            setHata(PROFIL_YENILEME_HATA_MESAJI);
            yenilemeYapiliyorRef.current = false;
            setYenilemeYapiliyor(false);
            return;
        }

        setYenilemeBekliyor(false);
        setBilgi("");
        yenilemeYapiliyorRef.current = false;
        setYenilemeYapiliyor(false);
        onProfilYenilendi(guncelProfil);
    }

    function handleIptal() {
        if (
            isSubmittingRef.current ||
            yenilemeYapiliyorRef.current ||
            yenilemeBekliyor
        ) {
            return;
        }

        onIptal();
    }

    return (
        <form
            onSubmit={handleSubmit}
            aria-busy={isSubmitting || yenilemeYapiliyor}
            className="rounded-2xl border border-gray-200 p-5 dark:border-gray-800 lg:p-6"
        >
            <div className="mb-6">
                <h4 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                    Profil Bilgilerini Düzenle
                </h4>

                <p className="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
                    Telefon ve eğitim bilgilerinizi güncelleyebilirsiniz. Bir alanı
                    temizlemek için içeriğini tamamen silin.
                </p>
            </div>

            <fieldset disabled={formKilitli} className="space-y-5">
                <div>
                    <Label htmlFor={telNoId}>Telefon</Label>
                    <Input
                        id={telNoId}
                        name="telNo"
                        type="tel"
                        autoComplete="tel"
                        maxLength={30}
                        value={telNo}
                        onChange={(event) => {
                            setTelNo(event.target.value);
                            mesajlariTemizle();
                        }}
                        disabled={formKilitli}
                    />
                </div>

                <div>
                    <Label htmlFor={mezunOkulId}>Mezun Olunan Okul</Label>
                    <Input
                        id={mezunOkulId}
                        name="mezunOkul"
                        type="text"
                        maxLength={100}
                        value={mezunOkul}
                        onChange={(event) => {
                            setMezunOkul(event.target.value);
                            mesajlariTemizle();
                        }}
                        disabled={formKilitli}
                    />
                </div>

                <div>
                    <Label htmlFor={mezunBolumId}>Mezun Olunan Bölüm</Label>
                    <Input
                        id={mezunBolumId}
                        name="mezunBolum"
                        type="text"
                        maxLength={100}
                        value={mezunBolum}
                        onChange={(event) => {
                            setMezunBolum(event.target.value);
                            mesajlariTemizle();
                        }}
                        disabled={formKilitli}
                    />
                </div>
            </fieldset>

            {bilgi && (
                <p
                    role="status"
                    aria-live="polite"
                    className="mt-5 break-words text-sm text-gray-600 dark:text-gray-300"
                >
                    {bilgi}
                </p>
            )}

            {hata && (
                <p
                    role="alert"
                    className="mt-5 break-words text-sm text-error-500"
                >
                    {hata}
                </p>
            )}

            {yenilemeBekliyor ? (
                <div className="mt-6">
                    <button
                        type="button"
                        onClick={handleGuncelProfiliYukle}
                        disabled={yenilemeYapiliyor}
                        className="inline-flex w-full items-center justify-center rounded-lg bg-brand-500 px-4 py-3 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:bg-brand-300 disabled:opacity-50 sm:w-auto"
                    >
                        {yenilemeYapiliyor
                            ? "Güncel profil yükleniyor..."
                            : "Güncel Profili Yükle"}
                    </button>
                </div>
            ) : (
                <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                    <button
                        type="button"
                        onClick={handleIptal}
                        disabled={isSubmitting}
                        className="inline-flex w-full items-center justify-center rounded-lg border border-gray-300 bg-white px-4 py-3 text-sm font-medium text-gray-700 shadow-theme-xs transition hover:bg-gray-50 hover:text-gray-800 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-white/[0.03] dark:hover:text-gray-200 sm:w-auto"
                    >
                        İptal
                    </button>

                    <Button
                        className="w-full sm:w-auto"
                        size="sm"
                        disabled={isSubmitting}
                    >
                        {isSubmitting ? "Kaydediliyor..." : "Değişiklikleri Kaydet"}
                    </Button>
                </div>
            )}
        </form>
    );
}