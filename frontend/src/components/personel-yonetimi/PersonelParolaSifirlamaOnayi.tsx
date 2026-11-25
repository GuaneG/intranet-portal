import { type FormEvent, useRef, useState } from "react";
import { ApiError, adminParolaSifirla } from "../../lib/api";
import type { PersonelYonetimDetayi } from "../../types/personelYonetim";
import { useModal } from "../../hooks/useModal";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import { Modal } from "../ui/modal";

type PersonelParolaSifirlamaOnayiProps = {
    detay: PersonelYonetimDetayi;
    onBasarili: () => void;
};

/**
 * Admin'in hedef kullanıcıya geçici parola atamasını açık onayla başlatır.
 * Geçici parola yalnız form state'inde tutulur ve işlem sonrası temizlenir.
 */
export default function PersonelParolaSifirlamaOnayi({
    detay,
    onBasarili,
}: PersonelParolaSifirlamaOnayiProps) {
    const { isOpen, openModal, closeModal } = useModal();
    const [geciciParola, setGeciciParola] = useState("");
    const [geciciParolaTekrari, setGeciciParolaTekrari] = useState("");
    const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
    const [hata, setHata] = useState<string | null>(null);
    const gonderimKilidi = useRef(false);

    if (detay.rol === "ADMIN") {
        return null;
    }

    function parolalariTemizle() {
        setGeciciParola("");
        setGeciciParolaTekrari("");
    }

    function modaliAc() {
        parolalariTemizle();
        setHata(null);
        openModal();
    }

    function modaliKapat() {
        if (gonderimKilidi.current || gonderimSuruyor) {
            return;
        }

        parolalariTemizle();
        setHata(null);
        closeModal();
    }

    async function formuGonder(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (gonderimKilidi.current) {
            return;
        }

        setHata(null);

        if (geciciParola !== geciciParolaTekrari) {
            setHata("Geçici parola ve tekrar alanı eşleşmiyor.");
            return;
        }

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);

        try {
            await adminParolaSifirla(detay.personelId, geciciParola);
            parolalariTemizle();
            closeModal();
            onBasarili();
        } catch (error) {
            parolalariTemizle();
            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Parola sıfırlanırken beklenmeyen bir hata oluştu.",
            );
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    return (
        <>
            <button
                type="button"
                onClick={modaliAc}
                className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03]"
            >
                Parolayı sıfırla
            </button>

            <Modal
                isOpen={isOpen}
                onClose={modaliKapat}
                showCloseButton={!gonderimSuruyor}
                className="max-w-[520px] p-6"
            >
                <form onSubmit={formuGonder} className="px-2">
                    <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Parolayı sıfırla
                    </h2>

                    <p className="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
                        <strong>{detay.ad} {detay.soyad}</strong> için geçici
                        parola belirleyin. Kullanıcı sonraki girişinde kendi
                        parolasını değiştirmek zorunda kalır.
                    </p>

                    <div className="mt-5 space-y-4">
                        <div>
                            <Label htmlFor="personel-gecici-parola">
                                Geçici parola <span className="text-error-500">*</span>
                            </Label>
                            <Input
                                id="personel-gecici-parola"
                                name="geciciParola"
                                type="password"
                                autoComplete="new-password"
                                value={geciciParola}
                                onChange={(event) => setGeciciParola(event.target.value)}
                                disabled={gonderimSuruyor}
                                required
                            />
                        </div>

                        <div>
                            <Label htmlFor="personel-gecici-parola-tekrar">
                                Geçici parola tekrar <span className="text-error-500">*</span>
                            </Label>
                            <Input
                                id="personel-gecici-parola-tekrar"
                                name="geciciParolaTekrari"
                                type="password"
                                autoComplete="new-password"
                                value={geciciParolaTekrari}
                                onChange={(event) => setGeciciParolaTekrari(event.target.value)}
                                disabled={gonderimSuruyor}
                                required
                            />
                        </div>
                    </div>

                    <p className="mt-4 text-sm leading-6 text-gray-500 dark:text-gray-400">
                        Parola 8–72 karakter olmalı; küçük harf, büyük harf,
                        rakam ve özel karakter içermeli, boşluk içermemelidir.
                    </p>

                    {hata && (
                        <p role="alert" className="mt-4 break-words text-sm text-error-500">
                            {hata}
                        </p>
                    )}

                    <div className="mt-6 flex flex-wrap justify-end gap-3">
                        <button
                            type="button"
                            onClick={modaliKapat}
                            disabled={gonderimSuruyor}
                            className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                            Vazgeç
                        </button>

                        <button
                            type="submit"
                            disabled={gonderimSuruyor}
                            className="rounded-lg bg-error-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-error-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {gonderimSuruyor
                                ? "Parola sıfırlanıyor…"
                                : "Parolayı sıfırla"}
                        </button>
                    </div>
                </form>
            </Modal>
        </>
    );
}
