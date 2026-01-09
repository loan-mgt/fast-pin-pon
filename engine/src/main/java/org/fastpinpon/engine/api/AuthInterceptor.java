package org.fastpinpon.engine.api;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interceptor that adds the Authorization header with a Bearer token.
 */
public class AuthInterceptor implements Interceptor {
    private static final Logger LOG = Logger.getLogger(AuthInterceptor.class.getName());
    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token;
        try {
            token = tokenManager.getAccessToken();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[AuthInterceptor] Failed to get access token", e);
            throw e;
        }

        Request original = chain.request();
        Request.Builder builder = original.newBuilder()
                .header("Authorization", "Bearer " + token);

        return chain.proceed(builder.build());
    }
}
