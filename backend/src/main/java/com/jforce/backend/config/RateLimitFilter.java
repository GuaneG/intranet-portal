package com.jforce.backend.config;

import com.jforce.backend.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

//  her IP+endpoint için bir kova saklayacağız bu kova'nın içinde ise bu ip+endpoint'in anlık kalan limiti göreceğiz
//  concurrenthashmap kullanıyoruz çünkü birden çok istek aynı anda gelebilir, bu yüzden threadsafe bir hashmap seçtik
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();


// çok fazla istek geldiğinde exception atabilmemiz için bu class'a ihtiyacımız var, normalde attığımız gibi atsak bu atılan exception tomcat'e gider
// çünkü şuan dispatcherservlet içinde/seviyesinde değiliz;bu seviyeden bi önceki seviye filter'lama seviyesindeyiz
// eğer dispatcherservlet içinde olsaydık @restcontrolleradvice anotasyonlu classımız bu exceptionları yakalardı ve bu class'a dependent olmaya gerek kalmazdı.
//
// bunu kullanıyoruz çünkü bizim global exception handler'ımız controller seviyesinde çalışıyor
// bu yüzdende bizim kendimiz bu exception handler'ı oluşturup kendimiz elimizle bu exception'u koymak zorunda
// kalıyoruz
    private final HandlerExceptionResolver resolver;
                            //resolver enjekte ettik
                            //@Qualifier("handlerExceptionResolver") neden gerekli: birden çok HandlerExceptionResolver bean'i var
                            //@ExceptionHandler mantığını içeren bileşik (composite) olanın adı bu. İsimle istemezsen Spring hangisini vereceğini bilemez.
    public RateLimitFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        //TODO: diğer endpointler için rate limiting durumunu düşün
        String requestURI = request.getRequestURI();
        boolean hedefEndpoint = request.getMethod().equals("POST") && (requestURI.equals("/api/auth/login") || requestURI.equals("/api/auth/refresh"));
        if (!hedefEndpoint) {
            filterChain.doFilter(request, response);
            return;
        }

        //ip + path key'ini oluşturduk
        String ip = request.getRemoteAddr();
        String path = request.getRequestURI();
        String key = ip + ":" + path;
        //bucket yoksa oluştur
        Bucket bucket = buckets.computeIfAbsent(key, k -> bucketOlustur(path));
        boolean isConsumed = bucket.tryConsume(1);

        if (isConsumed) {
            filterChain.doFilter(request, response);
            return;
        }else {
            //DispatcherServlet'in kullandığı motoru ödünç alıp filter'dan elle tetikledik.
            //Böylece filter MVC'nin dışında olsa da, cevap yine GlobalExceptionHandler'dan çıkıyor.
            //burda exception fırlatmıyoruz resolveException'a bir exception elle veriyoruz.
            resolver.resolveException(request, response, null, new RateLimitExceededException("Çok fazla istek, lütfen biraz sonra tekrar deneyin."));
        }
    }
    //bucket oluşturup limit belirleme için helper method
    public Bucket bucketOlustur(String path){
        if (path.equals("/api/auth/login")){
            //bu  capasity,refillgreedy birleşince dakikada max 5 tane istek atılabilir olur.
            Bandwidth limit = Bandwidth.builder()
                    .capacity(5)// 5 tokenlık bir kapasitesi olucak
                    .refillGreedy(5, Duration.ofMinutes(1)) //1 dakika içine 5 tane token doldurcak şekilde yavaş yavaş limit arttırır
                    .build();
            return Bucket.builder().addLimit(limit).build();
        }
        if (path.equals("/api/auth/refresh")){
            //bu  capasity,refillgreedy birleşince dakikada max 20 tane istek atılabilir olur.
            Bandwidth limit = Bandwidth.builder()
                    .capacity(20)// 5 tokenlık bir kapasitesi olucak
                    .refillGreedy(20, Duration.ofMinutes(1)) //her dakikada bucket sıfırlanıcak
                    .build();
            return Bucket.builder().addLimit(limit).build();
        }
        return null;
    }
}
