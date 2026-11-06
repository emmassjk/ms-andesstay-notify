package com.andesstay.notify.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validador personalizado que verifica el claim {@code client_id} del
 * <strong>access token</strong> de AWS Cognito.
 * <p>
 * En Cognito, los access tokens contienen {@code client_id} (el App Client ID
 * del User Pool) pero <strong>no</strong> contienen {@code aud}. Los ID tokens,
 * en cambio, traen {@code aud} pero no {@code client_id}. Este validador
 * garantiza que solo se acepten access tokens legítimos emitidos para la
 * aplicación correcta.
 * <p>
 * Si el claim {@code client_id} no está presente o no coincide con el valor
 * esperado, la validación falla y Spring Security devuelve 401.
 *
 * @see <a href="https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html">
 *      Cognito – Verificación de JWT</a>
 */
public class CognitoClientIdValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log = LoggerFactory.getLogger(CognitoClientIdValidator.class);

    private static final OAuth2Error MISSING_CLIENT_ID = new OAuth2Error(
            "invalid_token",
            "El token no contiene el claim 'client_id'. ¿Es un ID token en vez de un access token?",
            null
    );

    private static final OAuth2Error WRONG_CLIENT_ID = new OAuth2Error(
            "invalid_token",
            "El claim 'client_id' del token no coincide con el App Client ID configurado.",
            null
    );

    private final String expectedClientId;

    /**
     * @param expectedClientId App Client ID del User Pool de Cognito
     *                         (variable {@code COGNITO_APP_CLIENT_ID}).
     */
    public CognitoClientIdValidator(String expectedClientId) {
        this.expectedClientId = expectedClientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");

        if (clientId == null || clientId.isBlank()) {
            log.warn("[SECURITY] Token rechazado: no contiene claim 'client_id'. sub={}", jwt.getSubject());
            return OAuth2TokenValidatorResult.failure(MISSING_CLIENT_ID);
        }

        if (!expectedClientId.equals(clientId)) {
            log.warn("[SECURITY] Token rechazado: client_id='{}' != esperado='{}'. sub={}",
                    clientId, expectedClientId, jwt.getSubject());
            return OAuth2TokenValidatorResult.failure(WRONG_CLIENT_ID);
        }

        log.debug("[SECURITY] Token válido: client_id='{}', sub={}", clientId, jwt.getSubject());
        return OAuth2TokenValidatorResult.success();
    }
}

