package top.ilovemyhome.zora.muserver.security.utils;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;

import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


public class JwtTest {

    @Test
    void testCreateAndParseJwt() {
        String subject = "Joe";
        SecretKey key = Jwts.SIG.HS256.key()
            .build();
        String jws = Jwts.builder()
            .header()
            .keyId("123456")
            .and()
            .subject(subject)
            .signWith(key)
            .compact();
        LOGGER.info(jws);
        JwtHelper.printJwtInfo(jws);

        String decodedSubject = Jwts.parser()
            .verifyWith(key).build()
            .parseSignedClaims(jws)
            .getPayload()
            .getSubject();
        assertThat(decodedSubject).isEqualTo(subject);
    }

    @Test
    void testJwtWithByteContent() {
        SecretKey signingKey = Jwts.SIG.HS256.key().build();

        Date now = Calendar.getInstance(TimeZone.getDefault()).getTime();

        //a week later
        Calendar aWeekLater = Calendar.getInstance();
        aWeekLater.add(Calendar.DATE, 7);

        //Keep 14 days
        Calendar twoWeeksLater = Calendar.getInstance();
        twoWeeksLater.add(Calendar.DATE, 14);

        String jwt = Jwts.builder()                     // (1)
            .header()                                   // (2) optional
            .keyId("123456")
            .add("foo", "bar")
            .add(Map.of("name","jack"))
            .and()
            .issuer("ilovemyhome.top")
            .subject("jack")                             // (3) JSON Claims, or
            .audience().add("GuangZhou").add("BeiJin").add("ShenZhen")
            .and()
            .expiration(twoWeeksLater.getTime())
            .issuedAt(now)
            .notBefore(aWeekLater.getTime())
            .id(UUID.randomUUID().toString())
//            .content(content.getBytes(StandardCharsets.UTF_8), "text/plain")   //  or any byte[] content, with media type
            .signWith(signingKey)                       // (4) if signing, or
            .compact();                                 // (5)
        LOGGER.info(jwt);
        JwtHelper.printJwtInfo(jwt);

//        Jws<Claims> claimsJws = Jwts.parser()
//            .verifyWith(signingKey).build()
//            .parseSignedClaims(jwt);



    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTest.class);
}
