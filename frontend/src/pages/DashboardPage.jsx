import { useNavigate } from "react-router-dom";
import { logout } from "../lib/api";

function DashboardPage() {
  const navigate = useNavigate();
  const kullanici = JSON.parse(localStorage.getItem("kullanici"));

  //artık async: önce sunucuya haber ver (DB satırı silinir + cookie imha edilir), sonra login'e git
  async function cikisYap() {
    await logout(); //localStorage temizliği de logout() içinde yapılıyor
    navigate("/login");
  }
  //for debug async function testApiFetch() {
  //   const cevap = await apiFetch("/api/ping"); // endpoint yok, 404 döner ama önemi yok  401→refresh zincirini izliyoruz
  //   setSonuc("status: " + cevap.status);
  // }

  return (
    <div className="min-h-screen bg-gray-100 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-800">
          Hoş geldin, {kullanici?.ad} {kullanici?.soyad}
        </h1>
        <button
          onClick={cikisYap}
          className="rounded bg-gray-800 px-4 py-2 text-sm text-white hover:bg-gray-700"
        >
          Çıkış yap
        </button>

        {/*for debug<button*/}
        {/*  onClick={testApiFetch}*/}
        {/*  className="rounded bg-blue-600 px-4 py-2 text-sm text-white"*/}
        {/*>*/}
        {/*  Test apiFetch*/}
        {/*</button>*/}
      </div>
      {/*kullanici?.rol burdaki soru işareti soru işaretinin solundaki değer null/undefined ise patlamak yerine undefined dön demek*/}
      <p className="mt-2 text-gray-600">Rol: {kullanici?.rol}</p>
    </div>
  );
}

export default DashboardPage;
