import { useState, FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { ChevronLeftIcon, EyeCloseIcon, EyeIcon } from "../../icons";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Checkbox from "../form/input/Checkbox";
import Button from "../ui/button/Button";

export default function SignInForm() {
  const [showPassword, setShowPassword] = useState(false);
  const [isChecked, setIsChecked] = useState(false);

  // --- bizim login state'imiz ---
  const [kullaniciAdi, setKullaniciAdi] = useState(""); // kullanıcı adı input'u
  const [parola, setParola] = useState(""); // parola input'u
  const [hata, setHata] = useState(""); // giriş hata mesajı
  const navigate = useNavigate(); // login sonrası yönlendirme

  // form gönderilince: backend'e login isteği at, token'ı sakla, dashboard'a git
  async function handleSubmit(e: FormEvent) {
    e.preventDefault(); // formun sayfayı yenilemesini engelle
    setHata("");
    const cevap = await fetch("http://localhost:8080/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include", // refresh token cookie'sini tarayıcı kabul etsin diye
      body: JSON.stringify({ kullaniciAdi, parola }),
    });
    if (cevap.ok) {
      const data = await cevap.json();
      localStorage.setItem("token", data.token); // guard'ların baktığı anahtar
      localStorage.setItem(
        "kullanici",
        JSON.stringify({ ad: data.ad, soyad: data.soyad, rol: data.rol }),
      );
      navigate("/", { replace: true }); // dashboard = "/"
    } else {
      const err = await cevap.json().catch(() => ({})); // backend ErrorResponse'u oku
      setHata(err.message || "Giriş başarısız.");
    }
  }

  return (
    <div className="flex flex-col flex-1">
        {/*alttaki div'i silersem sıkıntı çıkarmı?*/}
      <div className="w-full max-w-md pt-10 mx-auto">
        <Link
          to="/"
          className="inline-flex items-center text-sm text-gray-500 transition-colors hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
        >
          <ChevronLeftIcon className="size-5" />
          Dashboard'a dön
        </Link>
      </div>
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
            {/* onSubmit → handleSubmit; içteki Button type belirtmediği için form'u submit eder */}
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
                  {/*bu div'i silsem sıkıntı olur mu kalsın mı kalmasın mı*/}
                <div className="flex items-center gap-3">
                  <Checkbox checked={isChecked} onChange={setIsChecked} />
                  <span className="block font-normal text-gray-700 text-theme-sm dark:text-gray-400">
                    Beni hatırla
                  </span>
                </div>
                {/* giriş hatası varsa göster */}
                {hata && <p className="text-sm text-error-500">{hata}</p>}
                <div>
                  <Button className="w-full" size="sm">
                    Giriş yap
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
