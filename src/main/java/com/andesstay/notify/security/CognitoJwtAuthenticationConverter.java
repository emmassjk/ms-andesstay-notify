package com.andesstay.notify.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Convierte un JWT de AWS Cognito en un {@link JwtAuthenticationToken}
 * extrayendo los roles del claim {@code cognito:groups}.
 * <p>
 * Cognito almacena los grupos del usuario en el claim {@code cognito:groups}
 * como un arreglo de strings (ej. {@code ["ADMIN", "RECEPCIONISTA"]}). Este
 * converter los transforma a authorities de Spring Security con el prefijo
 * {@code ROLE_} y en mayúsculas (ej. {@code ROLE_ADMIN}).
 * <p>
 * Si el token no tiene el claim {@code cognito:groups} (por ejemplo, un token
 * de máquina o un usuario sin grupos asignados), se autentica sin roles.
 */
@Component
public class CognitoJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(CognitoJwtAuthenticationConverter.class);

    /** Prefijo estándar de Spring Security para hasRole(). */
    private static final String ROLE_PREFIX = "ROLE_";

    /** Nombre del claim de Cognito que contiene los grupos del usuario. */
    private static final String GROUPS_CLAIM = "cognito:groups";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        log.debug("[SECURITY] JWT convertido: sub='{}', authorities={}", jwt.getSubject(), authorities);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /**
     * Extrae los roles del claim {@code cognito:groups} y los mapea a
     * {@link GrantedAuthority} con prefijo {@code ROLE_}.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList(GROUPS_CLAIM);

        if (groups == null || groups.isEmpty()) {
            log.debug("[SECURITY] Token sin claim '{}'. sub='{}'", GROUPS_CLAIM, jwt.getSubject());
            return Collections.emptyList();
        }

        return groups.stream()
                .map(group -> (GrantedAuthority) new SimpleGrantedAuthority(
                        ROLE_PREFIX + group.toUpperCase(Locale.ROOT)))
                .toList();
    }
}

