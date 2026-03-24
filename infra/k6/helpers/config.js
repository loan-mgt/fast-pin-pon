export const BASE_URL = __ENV.K6_API_URL || "http://localhost:8081";
export const KEYCLOAK_URL =
  __ENV.K6_KEYCLOAK_URL || "http://localhost:8082";
export const KEYCLOAK_REALM = __ENV.K6_KEYCLOAK_REALM || "sdmis-realm";
export const CLIENT_ID = __ENV.K6_CLIENT_ID || "sdmis-api";
export const CLIENT_SECRET = __ENV.K6_CLIENT_SECRET || "changeme";

export const LYON = {
  lat: 45.764,
  lon: 4.8357,
  latMin: 45.714,
  latMax: 45.814,
  lonMin: 4.786,
  lonMax: 4.886,
};

// Retourne une coordonnée aléatoire dans la zone Lyon
export function randomLyonCoord() {
  return {
    latitude: LYON.latMin + Math.random() * (LYON.latMax - LYON.latMin),
    longitude: LYON.lonMin + Math.random() * (LYON.lonMax - LYON.lonMin),
  };
}
