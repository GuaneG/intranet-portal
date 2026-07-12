package com.jforce.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;


//composite key, nasıl hallolcak???

//Serializable'ı implemente etme nedeni: Serialization bir nesneyi byte dizisine çevirip saklanabilir/taşınabilir hale getirmektir.
//diske yazmak için,ağ üzerinden göndermek,cache'e koymak için kullanılabilir.
//JPA spec gereğide composite key sınıfı Serializable olması lazım."Her ihtimale karşı bu kimliği güvenle bir yerden bir yere taşıyabilsin"

//embeddable anotasyonu ise bize şunu söyler "Bu sınıfın kendi tablosu ve kendi kimliği yok başka bir entity'nin içine gömülmek için var."
@Embeddable
public class PersonelYetenekId implements java.io.Serializable {

    @Column(length = 36)
    private String personelId;
    @Column(length = 36)
    private Integer yetenekId;


    //no arg constructor her entity için var, bunu istemesinin nedeni'de hibernate'in önceden boş bir kabuk oluşturması "Personel = new Personel()"
    //sonra bu kabuğu(nesneyi) setter'ları çağırarak içini doldurması
    public PersonelYetenekId() {
    }
    public PersonelYetenekId(String personelId, Integer yetenekId) {
        this.personelId = personelId;
        this.yetenekId = yetenekId;
    }

    public String getPersonelId() {
        return personelId;
    }

    public void setPersonelId(String personelId) {
        this.personelId = personelId;
    }

    public Integer getYetenekId() {
        return yetenekId;
    }

    public void setYetenekId(Integer yetenekId) {
        this.yetenekId = yetenekId;
    }


    //hibernate aslında bir map olduğundan'da bizim bu equals,hashcode metodunu override etmemiz lazım.

    //Object.equals() sadece objelerin bellekteki adreslerini karşılaştırır ve eşitse true döndürür,
    //ama bir k,v pair durumunda bu karşılaştırma işe yaramaz çünkü her k,v pair bellekte ayrı adreste tutulur.
    //bu yüzden equals override edilir ve adrese değil içinede bak denir.
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PersonelYetenekId that = (PersonelYetenekId) o;
        return Objects.equals(personelId, that.personelId) && Objects.equals(yetenekId, that.yetenekId);
    }
    //hashmap gibi map tabanlı koleksiyonlar hızlı olmak için bir numara kullanır, bir eleman ararken
    //bütün elemanları tek tek gezip equals() çağırmaz, bu yavaş olurdu.Onun yerine iki adımlı çalışıyor
    //ilk adım:önce hashcode() hesaplar -> bu bir sayıdır objeleri belli raf gibi ayırır, aramayı anında
    //küçük bir gruba indirger.
    //sonra o grubun içinde equals() çalıştırarak aradığım tam olarak bu mu diye bulur.
    @Override
    public int hashCode() {
        return Objects.hash(personelId, yetenekId);
    }
}
