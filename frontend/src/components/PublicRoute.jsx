import { Navigate } from "react-router";
import { useAuth } from "../context/AuthContext";

// ProtectedRoute'un tersi: giriş YAPILMIŞSA (token varsa) login sayfasını gösterme,
// dashboard'a (/) yolla. Böylece login'li kullanıcı /signin'i göremez.
function PublicRoute({ children }) {
    const { isAuthenticated  } = useAuth();
    if (isAuthenticated) {
        return <Navigate to="/" replace />; // giriş yapmışsa ana sayfaya yönlendir
    }
  return children; // giriş yapılmamışsa normal login sayfasını göster
}

export default PublicRoute;
