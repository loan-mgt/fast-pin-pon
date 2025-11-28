# Fast pin pom

A minimal project repository. This README contains a small project illustration and the recommended directory layout.

## Illustration

![Doc illustration](doc/illustration.png)

> 📄 Latest API contract: [Swagger UI](https://loan-mgt.github.io/fast-pin-pon/swagger) (auto-published from `main` via GitHub Pages)


## Project structure

Please follow this file structure:

```
.
├─ .github/   # CI
├─ infra/     # infra config
├─ network/   # network definitions
├─ api/       # API code
└─ simu/      # simulators
```

## Components and Features

### Network (`network/`)

**IoT and Radio Frequency Communication**

- **Radio Network Infrastructure**
  - Microbit gateway (emitter) connected to simulation via serial
  - Microbit receiver (centralized) connected to datacenter
  - Serial communication between microbits and host machines

- **Communication Protocol**
  - Support various message types (GPS coordinates, intervention status, etc.)
  - Automatic periodic transmissions (e.g., GPS coordinates)
  - On-demand transmissions (e.g., end of intervention)
  - Reliable and robust communications (low sensitivity to packet loss, data coherence)

- **Security**
  - Data integrity verification
  - Message authenticity
  - Confidentiality of transmitted data
  - Encryption of radio communications

### API (`api/`)

**Backend Services and REST API**

- **HQ Application Backend**
  - Real-time incident tracking
  - Resource management (vehicles, personnel)
  - Intervention coordination
  - Decision-making engine for resource allocation (Java required)

- **Core Features**
  - Incident declaration and management
  - Intervention triggering and lifecycle
  - Automatic resource allocation suggestions based on:
    - Incident type
    - Distance to event location
    - Operational status (availability, functional state, resource gauges)
  - Manual override capability for operators

- **Data Integration**
  - External data sources (optional enhancements):
    - Weather events (critical weather conditions)
    - Road traffic (resource allocation, delays)
    - Incident history for post-analysis

- **Security & Authentication**
  - Keycloak authentication server integration
  - OWASP Top 10 best practices
  - Secure/encrypted API exchanges
  - Access control and restricted permissions

- **Real-time Communication**
  - Support for pull/push mechanisms (HTTP REST, SSE, WebSocket)
  - Optimized refresh rate for real-time data

### Simulation (`simu/`)

**Incident and Vehicle Simulation System (Java required)**

- **Incident Simulation**
  - Generate simulated incidents with:
    - Incident type
    - Location coordinates
    - Severity level
    - Timestamp
  - Incident evolution over time
  - Coherent transmission to HQ App

- **Vehicle Simulation**
  - Simulate vehicles, their location, and movements
  - GPS coordinate generation and updates
  - Vehicle status simulation (availability, operational state)
  - Terminal input simulation or test interface for operator input

- **Integration Modes**
  - Option 1: Dedicated simulation interface for operator monitoring
  - Option 2: Direct automated access to HQ App API (with clearly defined permissions)
  - Modular architecture for easy switch between simulation and real-world data

- **Testing & Validation**
  - Represent external system states and state changes
  - Validate end-to-end system functionality (capture, processing/decision, restitution)


## Database

```mermaid
erDiagram
  EventTypes ||--o{ Events : "categorizes"
  Events ||--o{ EventLogs : "timeline"
  Events ||--o{ Interventions : "spawn"
  Interventions ||--o{ InterventionAssignments : "dispatches"
  Units ||--o{ InterventionAssignments : "allocated"
  Units ||--o{ UnitTelemetry : "reports"
  UnitTypes ||--o{ Units : "defines"
  Personnel ||--o{ InterventionCrew : "assigned"
  Interventions ||--o{ InterventionCrew : "staffs"

  EventTypes {
    string ETY_code
    string ETY_name
    string ETY_description
    int ETY_default_severity
    json ETY_recommended_units
  }

  Events {
    string EVT_id
    string EVT_title
    string EVT_description
    string EVT_report_source
    string EVT_address
    float EVT_latitude
    float EVT_longitude
    int EVT_severity
    string EVT_status
    string EVT_ETY_code
    datetime EVT_reported_at
    datetime EVT_closed_at
  }

  EventLogs {
    string EVL_id
    string EVL_EVT_id
    datetime EVL_datetime
    string EVL_code
    json EVL_payload
    string EVL_actor
  }

  Interventions {
    string IVN_id
    string IVN_EVT_id
    string IVN_status
    int IVN_priority
    string IVN_decision_mode
    string IVN_created_by
    datetime IVN_created_at
    datetime IVN_started_at
    datetime IVN_completed_at
  }

  InterventionAssignments {
    string IAS_id
    string IAS_IVN_id
    string IAS_UIT_id
    string IAS_role
    string IAS_status
    datetime IAS_dispatched_at
    datetime IAS_arrived_at
    datetime IAS_released_at
  }

  UnitTypes {
    string UTY_code
    string UTY_name
    string UTY_capabilities
    int UTY_speed
    int UTY_max_crew
    string UTY_illustration
  }

  Units {
    string UIT_id
    string UIT_call_sign
    string UIT_UTY_code
    string UIT_home_base
    string UIT_status
    float UIT_latitude
    float UIT_longitude
    datetime UIT_last_contact_at
  }

  UnitTelemetry {
    string UTM_id
    string UTM_UIT_id
    datetime UTM_recorded_at
    float UTM_latitude
    float UTM_longitude
    int UTM_heading
    float UTM_speed
    json UTM_status_snapshot
  }

  Personnel {
    string PRS_id
    string PRS_name
    string PRS_rank
    string PRS_status
    string PRS_home_base
    string PRS_contact
  }

  InterventionCrew {
    string IRC_id
    string IRC_IVN_id
    string IRC_PRS_id
    string IRC_role
    string IRC_status
  }
```



