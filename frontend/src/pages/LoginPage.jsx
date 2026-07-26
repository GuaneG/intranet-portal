import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

function LoginPage() {
  const [kullaniciAdi, setKullaniciAdi] = useState("");
  const [parola, setParola] = useState("");
  const [hata, setHata] = useState(
    () => sessionStorage.getItem("authMesaji") || "",
  );
  const navigate = useNavigate();

  useEffect(() => {
    sessionStorage.removeItem("authMesaji");
  }, []); //[] = sadece sayfa ilk açıldığında çalış

  async function handleSubmit(e) {
    e.preventDefault();
    setHata("");

    const cevap = await fetch("http://localhost:8080/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      //cevaptaki Set-Cookie'yi tarayıcının KABUL etmesi için şart; olmazsa cookie sessizce çöpe gider
      credentials: "include",
      body: JSON.stringify({ kullaniciAdi, parola }),
    });

    if (cevap.ok) {
      const data = await cevap.json();
      localStorage.setItem("token", data.token);
      localStorage.setItem(
        "kullanici",
        JSON.stringify({ ad: data.ad, soyad: data.soyad, rol: data.rol }),
      );
      navigate("/dashboard");
    } else {
      const err = await cevap.json();
      setHata(err.message);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-lg bg-white p-8 shadow-md"
      >
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-800">
          Intranet Portal
        </h1>
        <input
          required
          value={kullaniciAdi}
          onChange={(e) => setKullaniciAdi(e.target.value)}
          placeholder="Kullanıcı adı"
          className="mb-4 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
        />
        {/* e.target = olayı tetikleyen DOM elementini verir (inputun kendisi), e.target.value inputun o anki metni */}
        <input
          required
          type="password"
          value={parola}
          onChange={(e) => setParola(e.target.value)}
          placeholder="Parola"
          className="mb-6 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
        />
        <button
          type="submit"
          className="w-full rounded bg-blue-600 py-2 font-semibold text-white hover:bg-blue-700"
        >
          Giriş yap
        </button>
        {hata && <p className="mt-4 text-sm text-red-600">{hata}</p>}
      </form>
    </div>
  );
}

export default LoginPage;
