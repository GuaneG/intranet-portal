package com.jforce.backend;

import com.jforce.backend.model.entity.Personel;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonelJpaEntityInformationTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void yeniPersonelNullVersionIleSpringDataTarafindanYeniSayilir() {
        Metamodel metamodel = mock(Metamodel.class);
        IdentifiableType<Personel> personelType = mock(IdentifiableType.class);
        Type<String> idType = mock(Type.class);
        SingularAttribute<Personel, String> idAttribute =
                mock(SingularAttribute.class);
        SingularAttribute<Personel, Long> versionAttribute =
                mock(SingularAttribute.class);
        PersistenceUnitUtil persistenceUnitUtil = mock(PersistenceUnitUtil.class);

        when(metamodel.managedType(Personel.class)).thenReturn(personelType);
        when(personelType.hasSingleIdAttribute()).thenReturn(true);
        when(personelType.getIdType()).thenReturn((Type) idType);
        when(idType.getJavaType()).thenReturn(String.class);
        when(personelType.getId(String.class)).thenReturn((SingularAttribute) idAttribute);
        when(personelType.getIdClassAttributes()).thenReturn(Set.of());
        when(personelType.getVersion(Object.class))
                .thenReturn((SingularAttribute) versionAttribute);
        when(versionAttribute.getJavaType()).thenReturn((Class) Long.class);
        when(versionAttribute.getName()).thenReturn("version");

        JpaMetamodelEntityInformation<Personel, String> entityInformation =
                new JpaMetamodelEntityInformation<>(
                        Personel.class,
                        metamodel,
                        persistenceUnitUtil
                );
        Personel personel = new Personel();

        assertAll(
                () -> assertNull(personel.getVersion()),
                () -> assertTrue(entityInformation.isNew(personel))
        );
    }
}
