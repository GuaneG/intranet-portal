import { type FormEvent,useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../../context/AuthContext";
import { EyeCloseIcon, EyeIcon } from "../../icons";
import { ApiError } from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Button from "../ui/button/Button";
import {ilkParolaAkisiniBaslat,ilkParolaAkisiniTemizle  } from "../../lib/ilkParolaAkisi.ts";

export default function SignInForm() {
    const [showPassword, setShowPassword] = useState(false);
    const [kullaniciAdi, setKullaniciAdi] = useState("");
    const [parola, setParola] = useState("");
    const [hata, setHata] = useState("");
    const [bilgi, setBilgi] = useState(() => {
        const mesaj = sessionStorage.getItem("authMesaji") || "";
        sessionStorage.removeItem("authMesaji");
        return mesaj;
    });
    const [isSubmitting, setIsSubmitting] = useState(false);

    const navigate = useNavigate();
    const { signIn } = useAuth();
    useEffect(() => {
        ilkParolaAkisiniTemizle()
    }, []);

    async function handleSubmit(e: FormEvent) {
        e.preventDefault();

        if (isSubmitting) {
            return;
        }

        setHata("");
        setBilgi("");
        setIsSubmitting(true);

        try {
            await signIn({ kullaniciAdi, parola });
            navigate("/", { replace: true });
        } catch (error) {
            if (error instanceof ApiError && error.status === 428) {
               ilkParolaAkisiniBaslat(kullaniciAdi);
               navigate("/ilk-parola", {replace: true});
                return;
            }

            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Giriş sırasında beklenmeyen bir hata oluştu.",
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
                            Giriş Yap
                        </h1>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Kullanıcı adı ve parolanızla giriş yapın
                        </p>
                    </div>

                    <div>
                        <form onSubmit={handleSubmit}>
                            <div className="space-y-6">
                                <div>
                                    <Label>
                                        Kullanıcı adı <span className="text-error-500">*</span>{" "}
                                    </Label>
                                    <Input
                                        placeholder="Kullanıcı adı"
                                        value={kullaniciAdi}
                                        onChange={(e) => setKullaniciAdi(e.target.value)}
                                        disabled={isSubmitting}
                                        required
                                    />
                                </div>

                                <div>
                                    <Label>
                                        Parola <span className="text-error-500">*</span>{" "}
                                    </Label>

                                    <div className="relative">
                                        <Input
                                            type={showPassword ? "text" : "password"}
                                            placeholder="Parola"
                                            value={parola}
                                            onChange={(e) => setParola(e.target.value)}
                                            disabled={isSubmitting}
                                            required
                                        />

                                        <span
                                            onClick={() => setShowPassword(!showPassword)}
                                            className="absolute z-30 -translate-y-1/2 cursor-pointer right-4 top-1/2"
                                        >
                      {showPassword ? (
                          <EyeIcon className="fill-gray-500 dark:fill-gray-400 size-5" />
                      ) : (
                          <EyeCloseIcon className="fill-gray-500 dark:fill-gray-400 size-5" />
                      )}
                    </span>
                                    </div>
                                </div>

                                {bilgi && (
                                    <p className="text-sm text-success-600">{bilgi}</p>
                                )}

                                {hata && <p className="text-sm text-error-500">{hata}</p>}

                                <div>
                                    <Button
                                        className="w-full"
                                        size="sm"
                                        disabled={isSubmitting}
                                    >
                                        {isSubmitting ? "Giriş yapılıyor..." : "Giriş yap"}
                                    </Button>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    );
}