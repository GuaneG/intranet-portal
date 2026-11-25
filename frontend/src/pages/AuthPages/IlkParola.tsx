import IlkParolaForm from "../../components/auth/IlkParolaForm";
import PageMeta from "../../components/common/PageMeta";
import AuthLayout from "./AuthPageLayout";

export default function IlkParola() {
  return (
    <>
      <PageMeta
        title="İlk Parola Değişikliği"
        description="İlk giriş parolanızı güvenli biçimde değiştirin."
      />
      <AuthLayout>
        <IlkParolaForm />
      </AuthLayout>
    </>
  );
}
