import type { ReactNode } from "react";
import { Navigate } from "react-router";
import { useAuth } from "../context/AuthContext";
import { ilkParolaKullaniciAdiniGetir } from "../lib/ilkParolaAkisi";

type IlkParolaRouteProps = {
  children: ReactNode;
};

// Bu yalnız UX kontrolüdür; gerçek yetkilendirme backend tarafında yapılır.
function IlkParolaRoute({ children }: IlkParolaRouteProps) {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  if (!ilkParolaKullaniciAdiniGetir()) {
    return <Navigate to="/signin" replace />;
  }

  return children;
}

export default IlkParolaRoute;
