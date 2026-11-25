import { BrowserRouter as Router, Routes, Route } from "react-router";
import SignIn from "./pages/AuthPages/SignIn";
import IlkParola from "./pages/AuthPages/IlkParola";
import NotFound from "./pages/OtherPage/NotFound";
import UserProfiles from "./pages/UserProfiles";
import Calendar from "./pages/Calendar";
import AppLayout from "./layout/AppLayout";
import { ScrollToTop } from "./components/common/ScrollToTop";
import Home from "./pages/Dashboard/Home";
import ProtectedRoute from "./components/ProtectedRoute";
import PublicRoute from "./components/PublicRoute";
import IlkParolaRoute from "./components/IlkParolaRoute.tsx";
import ProfilParola from "./pages/ProfilParola";
import RoleRoute from "./components/RoleRoute";
import PersonelYonetimi from "./pages/PersonelYonetimi";
import PersonelYonetimDetayi from "./pages/PersonelYonetimDetayi";
import PersonelYonetimDuzenle from "./pages/PersonelYonetimDuzenle";
import PersonelYonetimOlustur from "./pages/PersonelYonetimOlustur";
import EkipDizini from "./pages/EkipDizini";
import KatalogYonetimi from "./pages/KatalogYonetimi";
import IzinTaleplerim from "./pages/IzinTaleplerim";
import IzinKararListesi from "./pages/IzinKararListesi";
import AuditLoglari from "./pages/AuditLoglari";
import RezervasyonTaleplerim from "./pages/RezervasyonTaleplerim";
import EkipmanTaleplerim from "./pages/EkipmanTaleplerim";
import EnvanterZimmetYonetimi from "./pages/EnvanterZimmetYonetimi";

export default function App() {
  return (
    <>
      <Router>
        <ScrollToTop />
        <Routes>
          {/* Korumalı dashboard katmanı: AppLayout grubu ProtectedRoute ile sarılı.
              Token yoksa hepsi /signin'e yönlenir; varsa AppLayout render olur (Outlet alt sayfaları doldurur). */}
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route index path="/" element={<Home />} />

              {/*personel yönetimi*/}
              <Route path="/yonetim/personel" element={
                      <RoleRoute roller={["HR", "ADMIN"]}>
                          <PersonelYonetimi />
                      </RoleRoute>
                  }
              />
              <Route
                  path="/yonetim/personel/yeni"
                  element={
                      <RoleRoute roller={["HR", "ADMIN"]}>
                          <PersonelYonetimOlustur />
                      </RoleRoute>
                  }
              />
              <Route
                  path="/yonetim/personel/:personelId"
                  element={
                      <RoleRoute roller={["HR", "ADMIN"]}>
                          <PersonelYonetimDetayi />
                      </RoleRoute>
                  }
              />
              <Route
                  path="/yonetim/personel/:personelId/duzenle"
                  element={
                      <RoleRoute roller={["HR", "ADMIN"]}>
                          <PersonelYonetimDuzenle />
                      </RoleRoute>
                  }
              />
              <Route
                  path="/yonetim/katalog"
                  element={
                      <RoleRoute roller={["HR", "ADMIN"]}>
                          <KatalogYonetimi />
                      </RoleRoute>
                  }
              />

            {/* Others Page */}
            <Route path="/profile" element={<UserProfiles />} />
            <Route path="/profil/parola" element={<ProfilParola />} />
            <Route path="/ekip-dizini" element={<EkipDizini />} />
            <Route path="/calendar" element={<Calendar />} />
            <Route path="/talepler/izinlerim" element={
              <RoleRoute roller={["PERSONEL", "YONETICI", "HR"]}>
                <IzinTaleplerim />
              </RoleRoute>
            } />
            <Route path="/talepler/ekipman" element={
              <RoleRoute roller={["PERSONEL", "YONETICI", "HR", "ADMIN"]}>
                <EkipmanTaleplerim />
              </RoleRoute>
            } />
            <Route path="/talepler/rezervasyonlar" element={<RezervasyonTaleplerim />} />
            <Route path="/talepler/ekip-izinleri" element={
              <RoleRoute roller={["YONETICI"]}>
                <IzinKararListesi kapsam="EKIP" />
              </RoleRoute>
            } />
            <Route path="/talepler/tum-izinler" element={
              <RoleRoute roller={["ADMIN"]}>
                <IzinKararListesi kapsam="SIRKET" />
              </RoleRoute>
            } />
            <Route path="/yonetim/audit-log" element={
              <RoleRoute roller={["ADMIN"]}>
                <AuditLoglari />
              </RoleRoute>
            } />
            <Route path="/yonetim/envanter-zimmet" element={
              <RoleRoute roller={["ADMIN"]}>
                <EnvanterZimmetYonetimi />
              </RoleRoute>
            } />
          </Route>

          {/* Public auth: giriş yapılmışsa PublicRoute kullanıcıyı "/"'a yollar */}
          <Route
            path="/signin"
            element={
              <PublicRoute>
                <SignIn />
              </PublicRoute>
            }
          />
            <Route
                path="/ilk-parola"
                element={
                    <IlkParolaRoute>
                        <IlkParola />
                    </IlkParolaRoute>
                }
            />

          {/* Fallback */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Router>
    </>
  );
}
