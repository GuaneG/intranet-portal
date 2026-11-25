import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router";
import { ApiError, ilkParolayiDegistir } from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Button from "../ui/button/Button";
import {ilkParolaAkisiniTemizle,ilkParolaKullaniciAdiniGetir} from "../../lib/ilkParolaAkisi.ts";

export default function IlkParolaForm() {
    const navigate = useNavigate();

    const [kullaniciAdi, setKullaniciAdi] = useState(
        () => ilkParolaKullaniciAdiniGetir() || "",
    );
    const [mevcutParola, setMevcutParola] = useState("");
    const [yeniParola, setYeniParola] = useState("");
    const [yeniParolaTekrar, setYeniParolaTekrar] = useState("");
    const [hata, setHata] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(e: FormEvent) {
        e.preventDefault();

        if (isSubmitting) {
            return;
        }

        setHata("");

        if (yeniParola !== yeniParolaTekrar) {
            setHata("Yeni parola ve parola tekrarı eşleşmiyor.");
            return;
        }

        setIsSubmitting(true);

        try {
            await ilkParolayiDegistir({
                kullaniciAdi,
                mevcutParola,
                yeniParola,
            });

            sessionStorage.setItem(
                "authMesaji",
                "Parolanız değiştirildi. Yeni parolanızla giriş yapabilirsiniz.",
            );

            ilkParolaAkisiniTemizle()
            navigate("/signin", { replace: true });
        } catch (error) {
            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Parola değiştirilirken beklenmeyen bir hata oluştu.",
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <div className="flex flex-col flex-1">
            <div className="flex flex-col justify-center flex-1 w-full max-w-md mx-auto">
                <div>
                    <div className="mb-5 sm:mb-8">
                        <h1 className="mb-2 font-semibold text-gray-800 text-title-sm dark:text-white/90 sm:text-title-md">
                            İlk Parolanızı Değiştirin
                        </h1>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Devam etmek için geçici parolanızı yenileyin.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit}>
                        <div className="space-y-6">
                            <div>
                                <Label htmlFor="ilk-parola-kullanici-adi">
                                    Kullanıcı adı <span className="text-error-500">*</span>
                                </Label>
                                <Input
                                    id="ilk-parola-kullanici-adi"
                                    value={kullaniciAdi}
                                    onChange={(e) => setKullaniciAdi(e.target.value)}
                                    disabled={isSubmitting}
                                    required
                                />
                            </div>

                            <div>
                                <Label htmlFor="ilk-parola-mevcut-parola">
                                    Mevcut parola <span className="text-error-500">*</span>
                                </Label>
                                <Input
                                    id="ilk-parola-mevcut-parola"
                                    type="password"
                                    value={mevcutParola}
                                    onChange={(e) => setMevcutParola(e.target.value)}
                                    disabled={isSubmitting}
                                    required
                                />
                            </div>

                            <div>
                                <Label htmlFor="ilk-parola-yeni-parola">
                                    Yeni parola <span className="text-error-500">*</span>
                                </Label>
                                <Input
                                    id="ilk-parola-yeni-parola"
                                    type="password"
                                    value={yeniParola}
                                    onChange={(e) => setYeniParola(e.target.value)}
                                    disabled={isSubmitting}
                                    required
                                />
                            </div>

                            <div>
                                <Label htmlFor="ilk-parola-yeni-parola-tekrar">
                                    Yeni parola tekrarı{" "}
                                    <span className="text-error-500">*</span>
                                </Label>
                                <Input
                                    id="ilk-parola-yeni-parola-tekrar"
                                    type="password"
                                    value={yeniParolaTekrar}
                                    onChange={(e) => setYeniParolaTekrar(e.target.value)}
                                    disabled={isSubmitting}
                                    required
                                />
                            </div>

                            {hata && <p className="text-sm text-error-500">{hata}</p>}

                            <Button
                                className="w-full"
                                size="sm"
                                disabled={isSubmitting}
                            >
                                {isSubmitting
                                    ? "Parola değiştiriliyor..."
                                    : "Parolayı değiştir"}
                            </Button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}