import http from "k6/http";
import { check } from "k6";
import {
  KEYCLOAK_URL,
  KEYCLOAK_REALM,
  CLIENT_ID,
  CLIENT_SECRET,
} from "./config.js";

let _cachedToken = null;
let _tokenExpiry = 0;

// Mémorise le token par VU
function getToken() {
  const now = Date.now();
  if (_cachedToken && now < _tokenExpiry) {
    return _cachedToken;
  }

  const tokenUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;

  const res = http.post(
    tokenUrl,
    {
      grant_type: "client_credentials",
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    },
    {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      tags: { name: "auth_token" },
    }
  );

  const ok = check(res, {
    "token request succeeded": (r) => r.status === 200,
  });

  if (!ok) {
    console.error(`Auth failed: ${res.status} — ${res.body}`);
    return "";
  }

  const body = res.json();
  _cachedToken = body.access_token;
  // Rafraîchit le token 30 s avant son expiration réelle
  _tokenExpiry = now + (body.expires_in - 30) * 1000;
  return _cachedToken;
}

// Entêtes d'authentification
function authHeaders() {
  const token = getToken();
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

// Raccourci : GET authentifié
export function authGet(url, params = {}) {
  return http.get(url, {
    ...params,
    headers: { ...authHeaders(), ...(params.headers || {}) },
  });
}

// Raccourci : POST authentifié
export function authPost(url, body, params = {}) {
  return http.post(url, JSON.stringify(body), {
    ...params,
    headers: { ...authHeaders(), ...(params.headers || {}) },
  });
}

// Raccourci : PATCH authentifié
export function authPatch(url, body, params = {}) {
  return http.patch(url, JSON.stringify(body), {
    ...params,
    headers: { ...authHeaders(), ...(params.headers || {}) },
  });
}
