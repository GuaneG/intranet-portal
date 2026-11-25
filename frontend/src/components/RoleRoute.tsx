import type { ReactNode } from "react";
import { Navigate } from "react-router";
import { useAuth, type AuthRole } from "../context/AuthContext";

// Component'ın alacağı bilgiler:
// children: Korunacak sayfa/component.
// roller: Bu sayfayı görmesine izin verilen roller.
type RoleRouteProps = {
    children: ReactNode;
    roller: readonly AuthRole[];
};

function RoleRoute({ children, roller }: RoleRouteProps) {
    // Kullanıcının oturum ve rol bilgilerini ortak AuthContext'ten alıyoruz.
    const { isAuthenticated, kullanici } = useAuth();

    // Kullanıcı giriş yapmamışsa giriş sayfasına gönderilir.
    // replace, yetkisiz route'un tarayıcı geçmişinde kalmasını engeller.
    if (!isAuthenticated) {
        return <Navigate to="/signin" replace />;
    }

    // Kullanıcı bilgisi bulunmuyorsa veya kullanıcının rolü
    // bu route için izin verilen roller arasında değilse
    // kullanıcı dashboard'a yönlendirilir.
    if (!kullanici || !roller.includes(kullanici.rol)) {
        return <Navigate to="/" replace />;
    }

    // Kullanıcı giriş yapmış ve rolü izinliyse korunan sayfa gösterilir.
    return children;
}

export default RoleRoute;