# COMP 512 PA1: Grading Rubric & Specification Checklist

**Assignment**: Distributed Travel Reservation System (RMI & TCP Sockets)  
**Total Points**: 100 Points  
**Deadline**: Report Due: October 5, 2026 | Demos: Following 3 Days (Week 6)

---

## 1. Implementation (75 Points)

### Part 1.1: RMI Distribution (25 Points)
- [ ] **Intermediary Middleware Server**:
  - [ ] Sits between Client and backend ResourceManagers.
  - [ ] Receives list/hostnames of backend ResourceManagers on startup.
  - [ ] Implements the `IResourceManager` interface (identical to monolithic RM).
  - [ ] Zero modifications required to Client interface or protocol.
- [ ] **Backend ResourceManagers**:
  - [ ] 3 separate ResourceManagers: `Flights`, `Cars`, `Rooms`.
  - [ ] Each RM runs on its own process/port/machine.
  - [ ] Middleware routes flight requests to `Flights` RM, car requests to `Cars` RM, room requests to `Rooms` RM.
- [ ] **Customer Handling Strategy**:
  - [ ] Architectural choice documented and implemented:
    - *Option A*: Dedicated Customer RM server.
    - *Option B*: Replicated customer state across backend RMs.
    - *Option C (Recommended)*: Customer management centralized at Middleware.
  - [ ] Correctly adds customer (`newCustomer()`, `newCustomer(cid)`).
  - [ ] Correctly deletes customer and cascades cancellation to all reserved items across all RMs (`deleteCustomer(cid)`).
  - [ ] Correctly computes cumulative customer bill across all item types (`queryCustomerInfo(cid)`).
- [ ] **Bundle Reservation**:
  - [ ] Implements `bundle(cid, flightNumbers, location, car, room)`.
  - [ ] Atomicity / Consistency handling: ensures partial failures do not corrupt state or strand resources.
- [ ] **RMI Registry & Port Management**:
  - [ ] Unique group prefix (e.g. `group_xx_`) to avoid collisions on shared machines.
  - [ ] Configurable registry port (e.g. `30XX` or `1099`).

### Part 1.2: TCP Sockets Distribution & Concurrency (50 Points)
- [ ] **TCP Socket Communication Across All Layers**:
  - [ ] Client $\leftrightarrow$ Middleware communicates over TCP sockets.
  - [ ] Middleware $\leftrightarrow$ Backend ResourceManagers communicate over TCP sockets.
  - [ ] Distributed architecture and Middleware layer preserved.
- [ ] **Client Execution Model**:
  - [ ] Client is blocking (synchronous request-reply).
- [ ] **Middleware Non-blocking Concurrency Model (Mandatory Requirement)**:
  - [ ] Middleware MUST NOT block when waiting for a backend RM to execute a request.
  - [ ] Middleware continues accepting incoming client requests concurrently while RM requests are in flight.
  - [ ] When RM replies, Middleware routes the response back to the appropriate waiting client.
- [ ] **Backend ResourceManagers Concurrency**:
  - [ ] ResourceManagers handle multiple incoming requests concurrently (multi-threaded request dispatch).
  - [ ] Thread safety over shared storage (`RMHashMap`, synchronization on data items).
- [ ] **Generic Message Service / Envelope Requirement**:
  - [ ] Generic wrapping/message creation service used for all (or nearly all) client-callable methods.
  - [ ] Clean serialization/deserialization of requests (method name, params) and responses (result, status, exception).

---

## 2. Technical Report (15 Points)

### Project Description (10 Points)
- [ ] 3–5 pages length.
- [ ] System architecture diagrams & descriptions for both RMI and TCP.
- [ ] In-depth discussion of TCP socket strategy: message passing format and concurrency design.
- [ ] Justification of customer handling choice and bundling implementation.
- [ ] Test cases documented: sequences of updates, queries, edge cases, and proof of correct handling.
- [ ] AI Token Estimation & Log Attachments:
  - [ ] Estimation of total AI tokens used.
  - [ ] Attached conversation logs: PDF for web chats, JSON export for IDE coding agents.

### Collaboration & Effort Log (5 Points)
- [ ] Detailed contribution breakdown for each team member across all tasks (RMI, TCP, custom features, report).
- [ ] Documentation of meetings: dates, durations, discussions.
- [ ] RMI comparative development: identifies who worked with AI and who worked without AI, and explains final code selection.

---

## 3. Live TA Demonstration (10 Points)

- [ ] **5-Machine Distributed Deployment**:
  - [ ] Client running on Machine 1.
  - [ ] Middleware running on Machine 2.
  - [ ] Flights RM running on Machine 3.
  - [ ] Cars RM running on Machine 4.
  - [ ] Rooms RM running on Machine 5.
  - [ ] Tested on McGill Trottier machines (`tr-open-*` or `open-gpu*`).
- [ ] **Live Presentation & Defense**:
  - [ ] Short slide presentation highlighting architecture, concurrency models, and test cases.
  - [ ] Confident execution of TA-specified scenarios in UNIX command prompt.
  - [ ] Clear explanation of design choices, socket protocols, thread safety, and AI usage.
