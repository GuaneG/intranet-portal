import { Navigate } from "react-router";

// Korumalı rota: token YOKSA login'e (/signin) yolla, VARSA içeriği göster.
// (Bu sadece UX içindir; gerçek yetki kontrolü backend'de her endpoint'te yapılır.)
function ProtectedRoute({ children }) {
  const token = localStorage.getItem("token"); // giriş yapılmış mı? (token var mı)
  if (!token) {
    return <Navigate to="/signin" replace />; // yoksa login'e at
  }
  return children; // varsa korumalı içeriği (AppLayout) göster
}

export default ProtectedRoute;
