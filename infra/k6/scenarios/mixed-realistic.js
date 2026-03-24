/**
 * Scénario k6 composite — charge réaliste
 *
 *   - Consultation dashboard (lecture)
 *   - Simulation véhicules (écriture)
 *   - Calculs d'itinéraires (CPU)
 *   - Suivi dispatch (requêtes candidats)
 *
 * Lancer à la main :
 *   k6 run infra/k6/scenarios/mixed-realistic.js
 *
 */

import { check, sleep } from "k6";
import { Trend, Counter, Rate } from "k6/metrics";
import { authGet, authPatch, authPost } from "../helpers/auth.js";
import {
  BASE_URL,
  LYON,
  randomLyonCoord,
} from "../helpers/config.js";

const endpointDuration = new Trend("endpoint_duration", true);
const errorRate = new Rate("error_rate");
const totalErrors = new Counter("total_errors");

export const options = {
  scenarios: {
    // Utilisateurs dashboard
    dashboard: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 10 },
        { duration: "2m", target: 30 },
        { duration: "1m", target: 50 },
        { duration: "30s", target: 0 },
      ],
      exec: "dashboardUser",
      gracefulRampDown: "10s",
    },
    // Simulation véhicules
    simulation: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 10 },
        { duration: "2m", target: 40 },
        { duration: "1m", target: 80 },
        { duration: "30s", target: 0 },
      ],
      exec: "vehicleSimulation",
      gracefulRampDown: "10s",
    },
    // Calculs d'itinéraires
    routing: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 2 },
        { duration: "2m", target: 8 },
        { duration: "1m", target: 15 },
        { duration: "30s", target: 0 },
      ],
      exec: "routeCalculation",
      gracefulRampDown: "10s",
    },
    // Tableau dispatch
    dispatch: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 3 },
        { duration: "2m", target: 10 },
        { duration: "1m", target: 15 },
        { duration: "30s", target: 0 },
      ],
      exec: "dispatchPanel",
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<1000", "p(99)<3000"],
    error_rate: ["rate<0.05"],
  },
};

export function setup() {
  let unitIDs = [];
  let interventionIDs = [];

  const unitsRes = authGet(`${BASE_URL}/v1/units`);
  if (unitsRes.status === 200) {
    unitIDs = unitsRes.json().map((u) => u.id).filter(Boolean);
  }
  const eventsRes = authGet(`${BASE_URL}/v1/events?limit=50&offset=0`);
  if (eventsRes.status === 200) {
    const events = eventsRes.json();
    if (Array.isArray(events)) {
      interventionIDs = events
        .map((e) => e.intervention_id)
        .filter(Boolean);
    }
  }

  console.log(`setup: ${unitIDs.length} units, ${interventionIDs.length} interventions`);
  return { unitIDs, interventionIDs };
}

export function dashboardUser(data) {
  const endpoints = [
    { name: "GET /v1/units", url: `${BASE_URL}/v1/units` },
    { name: "GET /v1/events", url: `${BASE_URL}/v1/events?limit=20&offset=0` },
    { name: "GET /v1/sync", url: `${BASE_URL}/v1/sync` },
    {
      name: "GET /v1/event-logs/recent",
      url: `${BASE_URL}/v1/event-logs/recent`,
    },
    { name: "GET /v1/dispatch/static", url: `${BASE_URL}/v1/dispatch/static` },
    {
      name: "GET /v1/units/nearby",
      url: `${BASE_URL}/v1/units/nearby?lat=${LYON.lat}&lon=${LYON.lon}`,
    },
    { name: "GET /v1/buildings", url: `${BASE_URL}/v1/buildings` },
  ];

  for (const ep of endpoints) {
    const res = authGet(ep.url, { tags: { name: ep.name } });
    endpointDuration.add(res.timings.duration);
    const ok = check(res, { [`${ep.name} OK`]: (r) => r.status === 200 });
    errorRate.add(!ok);
    if (!ok) totalErrors.add(1);
  }

  sleep(Math.random() * 3 + 2);
}

export function vehicleSimulation(data) {
  const { unitIDs } = data;
  if (!unitIDs || unitIDs.length === 0) {
    sleep(1);
    return;
  }

  const uid = unitIDs[Math.floor(Math.random() * unitIDs.length)];
  const coord = randomLyonCoord();

  const locRes = authPatch(
    `${BASE_URL}/v1/units/${uid}/location`,
    { latitude: coord.latitude, longitude: coord.longitude },
    { tags: { name: "PATCH /units/{id}/location" } }
  );
  endpointDuration.add(locRes.timings.duration);
  const locOk = check(locRes, { "location OK": (r) => r.status === 200 });
  errorRate.add(!locOk);
  if (!locOk) totalErrors.add(1);

  const telRes = authPost(
    `${BASE_URL}/v1/units/${uid}/telemetry`,
    {
      latitude: coord.latitude,
      longitude: coord.longitude,
      heading: Math.floor(Math.random() * 360),
      speed_kmh: Math.random() * 80,
      status_snapshot: {},
    },
    { tags: { name: "POST /units/{id}/telemetry" } }
  );
  endpointDuration.add(telRes.timings.duration);
  const telOk = check(telRes, {
    "telemetry OK": (r) => r.status >= 200 && r.status < 300,
  });
  errorRate.add(!telOk);
  if (!telOk) totalErrors.add(1);

  sleep(Math.random() * 0.5 + 0.25);
}

export function routeCalculation() {
  const from = randomLyonCoord();
  const to = randomLyonCoord();

  const res = authPost(
    `${BASE_URL}/v1/routing/calculate`,
    {
      from_lat: from.latitude,
      from_lon: from.longitude,
      to_lat: to.latitude,
      to_lon: to.longitude,
    },
    { tags: { name: "POST /routing/calculate" } }
  );
  endpointDuration.add(res.timings.duration);
  const ok = check(res, {
    "route calc OK": (r) => r.status >= 200 && r.status < 300,
  });
  errorRate.add(!ok);
  if (!ok) totalErrors.add(1);

  sleep(Math.random() * 3 + 2);
}


export function dispatchPanel(data) {
  const { interventionIDs } = data;

  const pendRes = authGet(`${BASE_URL}/v1/dispatch/pending`, {
    tags: { name: "GET /dispatch/pending" },
  });
  endpointDuration.add(pendRes.timings.duration);
  const pendOk = check(pendRes, { "pending OK": (r) => r.status === 200 });
  errorRate.add(!pendOk);
  if (!pendOk) totalErrors.add(1);

  if (interventionIDs && interventionIDs.length > 0) {
    const iid =
      interventionIDs[Math.floor(Math.random() * interventionIDs.length)];
    const candRes = authGet(
      `${BASE_URL}/v1/interventions/${iid}/candidates`,
      { tags: { name: "GET /interventions/{id}/candidates" } }
    );
    endpointDuration.add(candRes.timings.duration);
    const candOk = check(candRes, {
      "candidates OK": (r) =>
        (r.status >= 200 && r.status < 300) || r.status === 404,
    });
    errorRate.add(!candOk);
    if (!candOk) totalErrors.add(1);
  }

  const staticRes = authGet(`${BASE_URL}/v1/dispatch/static`, {
    tags: { name: "GET /dispatch/static" },
  });
  endpointDuration.add(staticRes.timings.duration);
  const staticOk = check(staticRes, { "static OK": (r) => r.status === 200 });
  errorRate.add(!staticOk);
  if (!staticOk) totalErrors.add(1);

  sleep(Math.random() * 3 + 2);
}
