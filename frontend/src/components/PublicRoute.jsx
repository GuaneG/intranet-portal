import { Navigate } from "react-router";

// ProtectedRoute'un tersi: giriş YAPILMIŞSA (token varsa) login sayfasını gösterme,
// dashboard'a (/) yolla. Böylece login'li kullanıcı /signin'i göremez.
function PublicRoute({ children }) {
  const token = localStorage.getItem("token");
  if (token) {
    return <Navigate to="/" replace />; // TailAdmin'de dashboard "/"
  }
  return children; // giriş yapılmamışsa normal login sayfasını göster
}

export default PublicRoute;
