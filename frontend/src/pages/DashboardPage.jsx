import { useNavigate } from "react-router-dom";

function DashboardPage() {
  const navigate = useNavigate();
  const kullanici = JSON.parse(localStorage.getItem("kullanici"));

  function cikisYap() {
    localStorage.removeItem("token");
    localStorage.removeItem("kullanici");
    navigate("/login");
  }

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
      </div>
      {/*kullanici?.rol burdaki soru işareti soru işaretinin solundaki değer null/undefined ise patlamak yerine undefined dön demek*/}
      <p className="mt-2 text-gray-600">Rol: {kullanici?.rol}</p>
    </div>
  );
}

export default DashboardPage;
