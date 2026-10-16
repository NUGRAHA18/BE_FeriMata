package com.smartmelon.backend.security.jwt;

import com.smartmelon.backend.security.auth.AppUserDetails;
import com.smartmelon.backend.security.config.JwtProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Menerbitkan token akses yang ditandatangani.
 *
 * <p>Proses validasi sengaja tidak diimplementasikan di sini: dukungan <i>resource server</i> pada 
 * Spring Security telah memverifikasi tanda tangan, pembuat (<i>issuer</i>), dan masa berlaku (<i>expiry</i>) 
 * dari setiap token yang masuk sebelum permintaan mencapai pengontrol (<i>controller</i>). Kelas ini 
 * hanya bertugas untuk mencetak (<i>mint</i>) token tersebut.
 */

@Service
public class JwtTokenService {

    /** Claim carrying the account id, so auditing does not need an extra database round-trip. */
    public static final String CLAIM_USER_ID = "uid";

    /** Claim carrying role names; {@code JwtRoleConverter} turns these into authorities. */
    public static final String CLAIM_ROLES = "roles";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedToken issue(AppUserDetails user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim(CLAIM_USER_ID, user.userId())
                .claim(CLAIM_ROLES, List.of(user.role().name()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    /** A freshly minted access token. The raw value is returned to the client exactly once. */
    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {}
}
