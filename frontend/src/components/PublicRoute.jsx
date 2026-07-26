import { Navigate } from "react-router-dom";

// ProtectedRoute'un tersi: token VARSA (giriş yapılmışsa) login'i gösterme, dashboard'a at
function PublicRoute({ children }) {
  const token = localStorage.getItem("token"); // giriş yapılmış mı? (token var mı)
  if (token) {
    return <Navigate to="/dashboard" replace />; // giriş yapmış kullanıcıyı login'den uzaklaştır
  }
  return children; // giriş yapılmamışsa normal login sayfasını göster
}

export default PublicRoute;
