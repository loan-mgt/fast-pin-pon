# Incident Creation Service

Standalone service for generating random incidents in the Fast Pin Pon system.

## Overview

This service is responsible for generating simulated incidents and posting them to the API. It runs independently from the simulation engine and decision engine, allowing for modular deployment.

## Configuration

Environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `API_BASE_URL` | Base URL of the Fast Pin Pon API | `http://localhost:8081` |
| `INCIDENT_INTERVAL_SECONDS` | Seconds between incident generation | `60` |
| `INCIDENT_CREATION_LOG_FILE` | Path to log file | `/app/logs/incident-creation/incident-creation.log` |
| `INCIDENT_CREATION_FILE_LOGGING_ENABLED` | Enable file logging | `true` |

## Building

```bash
cd incidentCreation
mvn clean package
```

## Running Locally

```bash
# With environment variables
API_BASE_URL=http://localhost:8081 mvn exec:java

# Or with .env file
mvn exec:java
```

## Docker

```bash
# Build the image
docker build -t incident-creation .

# Run the container
docker run -e API_BASE_URL=http://api:8080 incident-creation
```

## Incident Types

The service generates three types of incidents:
- **FEU** (Fire) - Requires FPT (fire truck) and EPA (ladder truck)
- **ACCIDENT** - Requires VSAV (rescue vehicle) and FPT
- **INONDATION** (Flood) - Requires MPR (pumping equipment) and VLHR

## Architecture

```
incidentCreation/
├── src/main/java/org/fastpinpon/incidentcreation/
│   ├── IncidentCreationApp.java    # Main entry point
│   ├── api/
│   │   └── ApiClient.java          # HTTP client for API
│   ├── generator/
│   │   └── IncidentGenerator.java  # Random incident generation
│   └── model/
│       ├── Incident.java           # Incident data model
│       └── IncidentType.java       # Incident type enum
```
