# COMP 512: Grading Rubrics & Specification Checklists

Comprehensive grading rubrics, specification checklists, and architectural invariants for **COMP 512: Distributed Systems** (McGill University).

---

# 🏆 Milestone: Programming Assignment 2 (PA2) — Active Milestone
**Assignment**: Total Order Using Paxos (Treasure Island Replicated State Game)  
**Total Points**: 100 Points  
**Deadline**: Report Due: November 2, 2026 | Demos: Following Days  
**Source Directory**: `pa2/` (Prereq archive in `p2-students/`)

---

## 1. Question 1: Implementation (65 Points)
> [!CAUTION]
> **Strict Academic Rule**: You are NOT allowed to use AI in any form for Question 1 code development. A mandatory "No AI Declaration" must be included at the beginning of the project report.

### Core Paxos Protocol & Consensus Engine
- [ ] **Paxos Layer Architecture**:
  - [ ] Built on top of the provided Group Communication Library (`comp512.gcl.GCL`).
  - [ ] Implements an **Update Anywhere** replicated state machine approach where each process proposes moves originating from its local player (no primary redelegation).
  - [ ] Sequence of Paxos instances (turns/log positions): instance 1, instance 2, ...
  - [ ] Each instance represents one successful execution of Paxos agreeing on one move value.
- [ ] **Public API & Visibility Invariants (Mandatory)**:
  - [ ] `Paxos.java` in package `comp512st.paxos` must be the **ONLY public class** in the module.
  - [ ] Public methods on `Paxos` must be strictly restricted to:
    - [ ] `public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck)`
    - [ ] `public void broadcastTOMsg(Object val)`
    - [ ] `public Object acceptTOMsg() throws InterruptedException`
    - [ ] `public void shutdownPaxos()`
  - [ ] **All helper classes, message classes, and utility types MUST be package-private** to `comp512st.paxos`.
- [ ] **Communication Invariants (Mandatory)**:
  - [ ] **ALL inter-process communication MUST use GCL** (`sendMsg`, `broadcastMsg`, `multicastMsg`).
  - [ ] Absolute prohibition of any other communication paradigm (no RMI, no raw TCP/UDP sockets, no NIO channels).
- [ ] **Ballot & Election Protocol**:
  - [ ] Monotonically increasing Ballot IDs composed of `(ballotNumber, processId)` ensuring total ballot ordering and tie-breaking.
  - [ ] Phase 1 (Prepare / Propose): Proposer broadcasts proposal for an instance with its ballot ID. Acceptors promise not to accept smaller ballots and return any previously accepted value with its ballot ID.
  - [ ] Phase 2 (Accept): Proposer receives majority promises; chooses the value associated with the highest ballot ID among promises (or its own value if none); broadcasts `accept` request.
  - [ ] Majority Quorum ($N/2 + 1$): Proposer proceeds only upon receiving a majority of promises and a majority of accept ACKs.
  - [ ] Phase 3 (Commit / Confirm): Upon majority acceptance, proposer commits value and informs all processes (learners/acceptors) to install the move in total order.
- [ ] **Liveness, Fairness & No-Drop Guarantees**:
  - [ ] **No moves discarded**: If a process attempts to propose a move for turn $k$ and fails because another proposer succeeded, it **MUST** automatically carry forward and retry proposing its move for turn $k+1$ (and subsequent turns until accepted).
  - [ ] Fair chance mechanism: Prevents rapid-proposing processes from completely starving slower processes.
  - [ ] `broadcastTOMsg(Object val)` **MUST block** until a majority has accepted the value. It must NOT wait for every process to accept before returning, but must ensure all correct processes eventually receive the move.
  - [ ] `acceptTOMsg()` **MUST block** if no message is ready to be delivered.
  - [ ] Out-of-order buffering: Internally buffers messages if Paxos instances complete or arrive out of sequence, delivering them to the application strictly in total order.
  - [ ] Graceful thread interruption support on `acceptTOMsg()` during shutdown.
- [ ] **Fault Tolerance & Failure Points**:
  - [ ] System tolerates process crash failures as long as a majority of processes remain alive.
  - [ ] Process recovery does NOT need to be implemented.
  - [ ] Exact instrumentation of all 5 required failure points via `failCheck.checkFailure(...)`:
    - [ ] `FailCheck.FailureType.RECEIVEPROPOSE`: immediately when a process receives a propose message.
    - [ ] `FailCheck.FailureType.AFTERSENDVOTE`: immediately AFTER a process sends out its vote (promise/refuse).
    - [ ] `FailCheck.FailureType.AFTERSENDPROPOSE`: immediately AFTER a process sends out its proposal to become leader.
    - [ ] `FailCheck.FailureType.AFTERBECOMINGLEADER`: immediately AFTER a process sees that it has been accepted by the majority as leader.
    - [ ] `FailCheck.FailureType.AFTERVALUEACCEPT`: immediately AFTER a majority has accepted its proposed value.

---

## 2. Question 2: Performance Analysis (25 Points)
> [!NOTE]
> AI is NOT allowed for setting up the test environment or running tests. AI is permitted ONLY to generate final figures/charts.

- [ ] **Automated Testing Suite (`TreasureIslandAppAuto`)**:
  - [ ] Configured via `run_tiapp_auto.sh` across varied player counts (e.g. 2, 3, 5, 7, 9) and intervals (100ms down towards 0ms).
- [ ] **Empirical Analysis Requirements**:
  - [ ] Impact of player count and interval on time until move acceptance (latency).
  - [ ] Impact of player count and interval on move throughput (moves processed per time unit).
  - [ ] Rate fairness: whether all processes achieve equal acceptance rates under short intervals, identifying which process performs better and explaining algorithmic/tie-breaker reasons.
  - [ ] Saturation point: maximum move throughput as interval approaches 0ms; whether throughput flattens before reaching 0ms and how this changes with player count.
- [ ] **Performance Report Format**:
  - [ ] Maximum 2 pages of text (Times New Roman, 11pt, single-column).
  - [ ] Figures/charts may take additional space.
  - [ ] Thorough discussion and explanation of observed trends for each figure.

---

## 3. Question 3: Project Report and Demonstration (10 Points)

### Project Description Report (5 Points)
- [ ] Maximum 2 pages of text (Times New Roman, 11pt, single-column). Figures take additional space.
- [ ] **Mandatory No-AI Declaration** at the beginning:
  > *"We, [Member 1] and [Member 2], did not use any form of AI for the code development of this assignment"*
- [ ] High-level architecture and design discussion.
- [ ] Explicit answers to the 4 required architectural questions:
  1. How simultaneous player moves eventually make it to all game instances.
  2. How fairness is guaranteed when move generation rate increases (preventing process dominance).
  3. How processes not in the accepting majority still receive the committed move.
  4. How specific failure scenarios (the 5 fail points) are handled.
- [ ] **Collaboration Section**: Detailed contribution breakdown per team member and collaboration logs.

### Live TA Demonstration (5 Points)
- [ ] Live demo with TA within 2-3 days following report submission.
- [ ] Short slide presentation or architecture narrative.
- [ ] System demonstrated live on SOCS Unix hosts (`tr-open-xx` or single host with distinct ports).
- [ ] Live demonstration of normal gameplay and fault-injection scenarios using fail points (`FRP`, `FSV`, `FSP`, `FOL`, `FMV`, `FI`).
- [ ] TA Q&A defense on architecture, concurrency, and Paxos invariants. All team members present.

---
---

# 📦 Milestone: Programming Assignment 1 (PA1) — Completed
**Assignment**: Distributed Travel Reservation System (RMI & TCP Sockets)  
**Total Points**: 100 Points  
**Status**: Completed & Submitted (October 5, 2026)  
**Source Directory**: `pa1/` (Prereq archive in `pa1-students/`)

### Summary of PA1 Deliverables & Validations
- [x] **Part 1.1: RMI Distribution (25 Points)**:
  - 3 backend ResourceManagers (`Flights`, `Cars`, `Rooms`).
  - Intermediary `RMIMiddleware` implementing `IResourceManager`.
  - Option C centralized customer management at Middleware.
  - Atomic bundle reservation with compensating rollback.
  - Configurable RMI registry port and `group_xx_` naming.
- [x] **Part 1.2: TCP Sockets & Asynchronous Concurrency (50 Points)**:
  - Synchronous blocking client (`TCPClient`).
  - Non-blocking worker dispatcher at `TCPMiddleware` using `ExecutorService` and generic envelope correlation.
  - Multi-threaded backend ResourceManagers with synchronized `RMHashMap` storage.
  - Generic message wrapping service (`TCPMessage`, `TCPResponse`, `TCPProxy` dynamic invocation handler).
- [x] **Technical Report & Logs (15 Points)**:
  - Comprehensive 14-page report with architecture diagrams and performance analysis.
  - Full collaboration statement and AI conversation logs.
- [x] **Live TA Demonstration (10 Points)**:
  - 5-machine cluster deployment on Trottier hosts (`tr-open-01` through `tr-open-05`).
