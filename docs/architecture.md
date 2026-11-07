
# Feature Flag Platform — Architecture

## 1. High-Level System Architecture

```mermaid
flowchart TB
    User["Developer / User"]

    subgraph Frontend["Frontend Layer"]
        React["React Application"]
        Dashboard["Feature Flag Dashboard"]
        Playground["Evaluation Playground"]
        React --> Dashboard
        React --> Playground
    end

    subgraph Backend["Backend Layer - Spring Boot"]
        API["REST Controllers"]
        Service["Feature Flag Service"]
        Evaluator["Rollout Evaluation Engine"]
        Cache["Cache Service"]
        Events["Flag Change Events"]
    end

    subgraph Storage["Data Layer"]
        Redis[("Redis Cache")]
        Postgres[("PostgreSQL")]
    end

    User --> React
    React -->|"HTTP / JSON"| API
    API --> Service
    Service --> Evaluator
    Service --> Cache
    Cache --> Redis
    Service --> Postgres
    Service --> Events
    Events -->|"After transaction commit"| Redis
```

## 2. Feature Flag Evaluation Flow

```mermaid
sequenceDiagram
    actor Client
    participant API as Spring Boot API
    participant Service as Feature Flag Service
    participant Redis
    participant DB as PostgreSQL
    participant Evaluator as Rollout Evaluator

    Client->>API: POST /api/v1/flags/evaluate
    API->>Service: evaluate(flagKey, userId)
    Service->>Redis: Get cached flag

    alt Cache Hit
        Redis-->>Service: Cached flag
    else Cache Miss
        Redis-->>Service: Cache miss
        Service->>DB: Find flag by key
        DB-->>Service: Feature flag
        Service->>Redis: Store flag with TTL
    end

    Service->>Evaluator: Evaluate enabled + rollout percentage
    Evaluator-->>Service: Evaluation result
    Service-->>API: Evaluation response
    API-->>Client: JSON response
```

## 3. Cache Invalidation Flow

```mermaid
sequenceDiagram
    actor Client
    participant API as Spring Boot API
    participant Service as Feature Flag Service
    participant DB as PostgreSQL
    participant Event as Change Event Listener
    participant Redis

    Client->>API: PUT /api/v1/flags/{id}
    API->>Service: Update feature flag
    Service->>DB: Update entity
    DB-->>Service: Transaction commits
    Service->>Event: Publish flag changed event
    Event->>Redis: Evict cached flag
    Redis-->>Event: Cache entry removed
    Service-->>API: Updated flag response
    API-->>Client: 200 OK

    Note over DB,Redis: Cache invalidation happens after database commit
```

## 4. Key Architectural Decisions

### PostgreSQL — Persistent Storage

PostgreSQL stores feature flag definitions and acts as the source of truth.

### Redis — Caching Layer

Redis reduces repeated database reads during feature flag evaluation. Cached entries use a time-to-live (TTL).

### Cache Invalidation After Transaction Commit

A flag update or deletion publishes a change event. The event listener invalidates the corresponding Redis entry after the database transaction commits.

This prevents invalidating the cache before the database change is successfully committed.

### Optimistic Locking

The feature flag entity uses a version field to detect conflicting concurrent updates. A stale update is rejected rather than silently overwriting a newer change.

### Deterministic Percentage Rollout

The evaluation engine uses stable hash-based assignment so the same user receives a consistent rollout decision for a given flag.

### Graceful Cache Fallback

If Redis is unavailable, the application can fall back to the database rather than making the cache a hard dependency for evaluation.

## 5. API Overview

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/flags` | List flags |
| POST | `/api/v1/flags` | Create a flag |
| GET | `/api/v1/flags/{id}` | Get a flag |
| PUT | `/api/v1/flags/{id}` | Update a flag |
| DELETE | `/api/v1/flags/{id}` | Delete a flag |
| POST | `/api/v1/flags/evaluate` | Evaluate a flag for a user |

## 6. Current Scope and Limitations

- Authentication and authorization are not implemented.
- The application currently supports a single environment configuration.
- The cache is Redis-backed, with database fallback.
- The platform supports percentage-based rollout rather than a full experimentation or targeting rules engine.
- Load-test results represent a local development benchmark, not production capacity.