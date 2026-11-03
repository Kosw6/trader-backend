package com.example.trader.security.oauth2;

/** Fixed same-origin destinations; no provider attributes or return URLs. */
public final class OAuthFailureRoute {
    private OAuthFailureRoute() {}
    public static String destination(org.springframework.security.core.AuthenticationException exception) {
        if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth) {
            return switch (oauth.getError().getErrorCode()) {
                case "policy_consent_required_google" -> "/consent/oauth/google";
                case "policy_consent_required_kakao" -> "/consent/oauth/kakao";
                default -> "/sign-in?oauth2_error";
            };
        }
        return "/sign-in?oauth2_error";
    }
}
