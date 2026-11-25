import { type FormEvent, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../../context/AuthContext";
import { ApiError, parolamiDegistir } from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Button from "../ui/button/Button";

export default function ParolaDegistirForm() {
    const navigate = useNavigate();
    const { signOut } = useAuth();

    const [mevcutParola, setMevcutParola] = useState("");
    const [yeniParola, setYeniParola] = useState("");
    const [yeniParolaTekrar, setYeniParolaTekrar] = useState("");
    const [hata, setHata] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    const isSubmittingRef = useRef(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (isSubmittingRef.current) {
            return;
        }

        setHata("");

        if (yeniParola !== yeniParolaTekrar) {
            setHata("Yeni parola ve parola tekrarı eşleşmiyor.");
            return;
        }

        isSubmittingRef.current = true;
        setIsSubmitting(true);

        try {
            await parolamiDegistir({
                mevcutParola,
                yeniParola,
            });
        } catch (error) {
            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Parola değiştirilirken beklenmeyen bir hata oluştu.",
            );

            isSubmittingRef.current = false;
            setIsSubmitting(false);
            return;
        }

        setMevcutParola("");
        setYeniParola("");
        setYeniParolaTekrar("");

        try {
            sessionStorage.setItem(
                "authMesaji",
                "Parolanız değiştirildi. Yeni parolanızla giriş yapabilirsiniz.",
            );
        } catch {
            // Başarı mesajı yardımcı UX bilgisidir; saklanamaması güvenli çıkışı engellemez.
        }

        try {
            await signOut();
        } finally {
            navigate("/signin", { replace: true });
        }
    }

    return (
        <form
            onSubmit={handleSubmit}
            aria-busy={isSubmitting}
            className="space-y-5"
        >
            <div>
                <Label htmlFor="parola-degistir-mevcut">
                    Mevcut parola <span className="text-error-500">*</span>
                </Label>
                <Input
                    id="parola-degistir-mevcut"
                    name="mevcutParola"
                    type="password"
                    autoComplete="current-password"
                    value={mevcutParola}
                    onChange={(event) => setMevcutParola(event.target.value)}
                    disabled={isSubmitting}
                    required
                />
            </div>

            <div>
                <Label htmlFor="parola-degistir-yeni">
                    Yeni parola <span className="text-error-500">*</span>
                </Label>
                <Input
                    id="parola-degistir-yeni"
                    name="yeniParola"
                    type="password"
                    autoComplete="new-password"
                    ariaDescribedBy="parola-degistir-politika"
                    value={yeniParola}
                    onChange={(event) => setYeniParola(event.target.value)}
                    disabled={isSubmitting}
                    required
                />
            </div>

            <div>
                <Label htmlFor="parola-degistir-yeni-tekrar">
                    Yeni parola tekrarı <span className="text-error-500">*</span>
                </Label>
                <Input
                    id="parola-degistir-yeni-tekrar"
                    name="yeniParolaTekrar"
                    type="password"
                    autoComplete="new-password"
                    value={yeniParolaTekrar}
                    onChange={(event) => setYeniParolaTekrar(event.target.value)}
                    disabled={isSubmitting}
                    required
                />
            </div>

            <p id="parola-degistir-politika" className="text-sm leading-6 text-gray-500 dark:text-gray-400">
                Yeni parolanız 8–72 karakter olmalı; küçük harf, büyük harf, rakam
                ve özel karakter içermeli, boşluk içermemelidir.
            </p>

            {hata && (
                <p role="alert" className="break-words text-sm text-error-500">
                    {hata}
                </p>
            )}

            <Button
                className="w-full sm:w-auto"
                size="sm"
                disabled={isSubmitting}
            >
                {isSubmitting ? "Parola değiştiriliyor..." : "Parolayı değiştir"}
            </Button>
        </form>
    );
}