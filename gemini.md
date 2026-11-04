# COMP 512: Distributed Systems Project Context & Guidelines

Welcome to the **COMP 512** workspace. This repository contains the lecture materials, project specifications, starter code, test suites, and implementations for **COMP 512: Distributed Systems** at McGill University.

---

## 🔄 Self-Updating & Synchronization Protocol

> [!IMPORTANT]
> **Mandatory Agent Rule**: This document (`gemini.md`) is a dynamic, living repository guide. The AI Agent MUST strictly adhere to the following self-updating synchronization protocol:
>
> 1. **Session Initialization & Startup Scan**:
>    * Whenever a session starts or `gemini.md` is read into context, the agent **MUST** scan the repository (checking `git status`, modified/added files in `pa2/`, `p2-students/`, `pa1/`, configuration files, and assignment requirements) to detect any unsynchronized repository changes.
>    * If any drift, new files, modified methods, or completed milestones are detected, the agent **MUST automatically update `gemini.md`** before proceeding with new user tasks.
>
> 2. **Incremental Update Trigger (On Every Code / Feature Change)**:
>    * **After completing any task or making edits** (e.g., implementing Paxos rounds, ballot numbering, message buffering, GCL delivery handlers, failure points, or test scripts), the agent **MUST immediately update `gemini.md`**.
>    * Updates must keep the **Implementation Status Matrix**, **Scope Matrix**, **Architecture Details**, and **Workflow Descriptions** fully synchronized with the codebase.
>
> 3. **Continuous Course Content Monitoring**:
>    * The [`Course Content/`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content) directory contains the authoritative course slides, assignments, and tutorials.
>    * Whenever new documents are added or existing documents are modified in [`Course Content/`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content), the agent **MUST immediately run the `course-content-monitor` skill**:
>      ```bash
>      python3 .agents/skills/course-content-monitor/scripts/monitor_course_content.py --audit
>      ```
>    * When changes are detected:
>      1. Parse and extract key requirements, constraints, and protocol rules.
>      2. Update `gemini.md` (Implementation Status Matrix, Scope Matrix, Architecture Details, Protocol Workflows).
>      3. Update verification skills and test scripts in `.agents/skills/comp512-verifier/` (`verify_pa2.py`, `check_course_adherence.py`, `spec_checklist.md`).
>      4. Update `README.md` and codebase implementation if the active milestone is affected.
>      5. Save updated state via `python3 .agents/skills/course-content-monitor/scripts/monitor_course_content.py --save`.

---

## 📚 Course Overview & Academic Context

* **Course**: COMP 512 – Distributed Systems (Fall 2026, McGill University)
* **Instructor**: Prof. Bettina Kemme
* **Team Members**: Nathan Hu (261147733) & Project Partner

### Milestones Summary

| Milestone | Project Title | Status | Due Date | Weight |
| :--- | :--- | :--- | :--- | :--- |
| **PA2** | **Total Order Using Paxos (Treasure Island)** | 🚀 **Active** | **November 2, 2026** | **100 Points** |
| **PA1** | **Distributed Travel Reservation System (RMI & TCP)** | ✅ **Completed** | **October 5, 2026** | **100 Points** |

---

## 🏝 Active Milestone: Programming Assignment 2 (PA2)

* **Assignment Title**: **Total Order Using Paxos**
* **Application**: **Treasure Island** (Distributed multi-player replicated state game)
* **Report Due Date**: **November 2, 2026**
* **Live TA Demonstration**: Week following submission
* **Total Points**: **100 Points**
  * **Question 1: Implementation (65 Points)** — Paxos total order consensus over GCL, update-anywhere model, fail points.
  * **Question 2: Performance Analysis (25 Points)** — Empirical study of throughput/latency vs move interval (down to 0ms) and player count (1–9), fairness analysis, max throughput saturation.
  * **Question 3: Project Report & Demonstration (10 Points)** — 5 Points Project Description (max 2 pages, No-AI declaration, 4 key design questions answered, collaboration breakdown) + 5 Points Live TA Demonstration.

### Strict Academic Integrity & AI Policy for PA2
* **Question 1 (Implementation - 65 Points)**:
  > [!CAUTION]
  > **STRICT NO AI POLICY**: Students are **NOT allowed to use AI in any form** for Question 1 code development.
  > The project report must begin with the exact mandatory No-AI declaration:
  > *"We, Nathan Hu and [Partner Name], did not use any form of AI for the code development of this assignment."*
* **Question 2 (Performance Analysis - 25 Points)**:
  * AI is **NOT allowed** for setting up the test environment or running tests.
  * AI **IS PERMITTED ONLY** to generate final figures and charts from collected empirical data.
* **Question 3 (Report & Demo - 10 Points)**:
  * Maximum 2 pages of text (Times New Roman, 11pt, single-column) for the Project Description report.
  * Maximum 2 pages of text for the Performance Analysis report.
  * Figures may take additional space.

---

## 🏗 PA2 System Architecture & Design Details

```mermaid
flowchart TD
    subgraph Game Tier
        TI1["Player 1\nTreasureIslandApp"]
        TI2["Player 2\nTreasureIslandApp"]
        TIN["Player N\nTreasureIslandApp"]
    end

    subgraph Consensus Tier
        PX1["Paxos Engine (Node 1)\n• Proposer / Acceptor / Learner\n• Instance Counter: turn 1, 2, ...\n• Ballot Ordering: (num, pid)\n• In-Flight Move Queue\n• Total Order Delivery Buffer"]
        PX2["Paxos Engine (Node 2)\n• Proposer / Acceptor / Learner\n• Instance Counter: turn 1, 2, ...\n• Ballot Ordering: (num, pid)\n• In-Flight Move Queue\n• Total Order Delivery Buffer"]
        PXN["Paxos Engine (Node N)\n• Proposer / Acceptor / Learner\n• Instance Counter: turn 1, 2, ...\n• Ballot Ordering: (num, pid)\n• In-Flight Move Queue\n• Total Order Delivery Buffer"]
    end

    subgraph GCL Tier
        GCL1["Group Communication Library\n(FIFO Point-to-Point, Broadcast, Multicast)"]
        GCL2["Group Communication Library\n(FIFO Point-to-Point, Broadcast, Multicast)"]
        GCLN["Group Communication Library\n(FIFO Point-to-Point, Broadcast, Multicast)"]
    end

    TI1 -->|"broadcastTOMsg(move)"| PX1
    PX1 -->|"acceptTOMsg() -> move"| TI1
    TI2 -->|"broadcastTOMsg(move)"| PX2
    PX2 -->|"acceptTOMsg() -> move"| TI2
    TIN -->|"broadcastTOMsg(move)"| PXN
    PXN -->|"acceptTOMsg() -> move"| TIN

    PX1 <-->|"sendMsg / broadcastMsg / readGCMessage"| GCL1
    PX2 <-->|"sendMsg / broadcastMsg / readGCMessage"| GCL2
    PXN <-->|"sendMsg / broadcastMsg / readGCMessage"| GCLN

    GCL1 <===="TCP FIFO Mesh"====> GCL2
    GCL2 <===="TCP FIFO Mesh"====> GCLN
    GCL1 <===="TCP FIFO Mesh"====> GCLN
```

### 1. Replicated State Machine & Treasure Island Domain
* **Treasure Island Game**:
  * 1–9 pirates (represented as characters `A` through `J`).
  * Island grid populated with treasures (valued 1–9) and barriers (`#`).
  * Moves: `L` (Left), `R` (Right), `U` (Up), `D` (Down).
  * Every player maintains a local copy of the island state.
  * To keep state perfectly synchronized across all replicas without centralized server bottleneck, all moves must be agreed upon and delivered in **strict Total Order**.
* **Sequence of Paxos Instances (Turns / Positions)**:
  * Each move corresponds to one **Paxos Instance** (position $1, 2, 3, \dots$ in the total order log).
  * Multiple players attempting to move concurrently compete to become the leader for position $k$.
  * Only one proposal succeeds for position $k$.
  * **No Dropped Moves (Critical Invariant)**: When a player's proposed move is superseded at turn $k$, the player's process **MUST NOT drop the move**. It must automatically carry the move forward and propose it for turn $k+1$ (and subsequent turns until successfully committed).

### 2. Paxos Consensus Engine Specification
* **Roles**: Unified Proposer, Acceptor, and Learner at every node (**Update-Anywhere** model, no primary copy redelegation).
* **Ballot Numbering**:
  * Unique, monotonically increasing ballots: $\text{BallotID} = (\text{ballotNum}, \text{processId})$.
  * Total ordering: $(b_1, p_1) > (b_2, p_2) \iff b_1 > b_2 \lor (b_1 = b_2 \land p_1 > p_2)$.
* **Phase 1: Propose & Promise (Leader Election for Instance $k$)**:
  * Proposer chooses next BallotID and sends `PROPOSE(instance=k, ballotID)` to all acceptors.
  * Acceptor checks if $\text{ballotID} > \text{maxBallot}[k]$:
    - If no: replies `REFUSE(instance=k, maxBallot[k])`.
    - If yes: $\text{maxBallot}[k] = \text{ballotID}$.
      - If acceptor already accepted a value for instance $k$: replies `PROMISE(instance=k, ballotID, acceptBallot[k], acceptVal[k])`.
      - If no accepted value: replies `PROMISE(instance=k, ballotID, null, null)`.
* **Phase 2: Accept & AcceptAck (Value Consensus)**:
  * Proposer waits for a majority of promises ($\lfloor N/2 \rfloor + 1$).
  * Value Selection Rule: If any promise contains an already-accepted value, the proposer **MUST adopt the value associated with the highest `acceptBallot`**. Otherwise, the proposer uses its own move value $V$.
  * Proposer broadcasts `ACCEPT?(instance=k, ballotID, chosenValue)`.
  * Acceptor checks if $\text{ballotID} \ge \text{maxBallot}[k]$:
    - If yes: $\text{acceptBallot}[k] = \text{ballotID}$, $\text{acceptVal}[k] = \text{chosenValue}$, and replies `ACCEPT_ACK(instance=k, ballotID)`.
    - If no: replies `DENY(instance=k, maxBallot[k])`.
* **Phase 3: Commit & Total Order Delivery**:
  * Proposer waits for a majority of `ACCEPT_ACK`s.
  * Once majority accepts, the value is committed.
  * Proposer broadcasts `CONFIRM(instance=k, chosenValue)` to all nodes.
  * All nodes install `chosenValue` at position $k$ in the total order log.
  * Any buffered out-of-order turns are released sequentially to `acceptTOMsg()`.

### 3. Public API & Visibility Invariants
> [!IMPORTANT]
> **Strict Visibility Constraint**: `Paxos.java` in package `comp512st.paxos` must be the **ONLY public class** in the module.
> Public methods must strictly consist of:
> * `public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck)`
> * `public void broadcastTOMsg(Object val)`:
>   - **MUST BLOCK** until a majority accepts the value.
>   - MUST NOT wait for all processes before unblocking.
>   - Ensures all correct processes eventually receive the committed value.
> * `public Object acceptTOMsg() throws InterruptedException`:
>   - **MUST BLOCK** if no message is ready to be delivered.
>   - Internally buffers messages and delivers them strictly in total order.
>   - Supports `InterruptedException` for clean application shutdown.
> * `public void shutdownPaxos()`:
>   - Performs orderly shutdown of GCL and worker threads.
> * **All other classes** (e.g. `PaxosMessage`, `Ballot`, `Vote`, etc.) **MUST BE PACKAGE-PRIVATE** to `comp512st.paxos`.

### 4. Group Communication Library (GCL) Constraints
* **Sole IPC Mechanism**: All inter-process communication MUST be executed through GCL (`comp512.gcl.GCL`).
* No RMI, no raw sockets (`Socket`, `ServerSocket`), and no NIO channels are permitted.
* GCL provides:
  * `sendMsg(Object msg, String destProcess)`: Non-blocking point-to-point FIFO delivery.
  * `broadcastMsg(Object msg)`: Non-blocking broadcast to all group members.
  * `multicastMsg(Object msg, String[] processes)`: Non-blocking multicast to a subset.
  * `readGCMessage()`: Blocking FIFO read of incoming messages.
  * Messages must be `Serializable`.

### 5. Failure Points Instrumentation
PA2 requires instrumenting 5 specific fail points via `failCheck.checkFailure(...)`:
1. `FailCheck.FailureType.RECEIVEPROPOSE`: Invoked immediately when a process receives a propose message.
2. `FailCheck.FailureType.AFTERSENDVOTE`: Invoked immediately AFTER a process sends out its vote (promise or refuse) for leader election.
3. `FailCheck.FailureType.AFTERSENDPROPOSE`: Invoked immediately AFTER a process sends out its proposal to become leader.
4. `FailCheck.FailureType.AFTERBECOMINGLEADER`: Invoked immediately AFTER a process sees that it has been accepted by the majority as leader.
5. `FailCheck.FailureType.AFTERVALUEACCEPT`: Invoked immediately AFTER a majority has accepted its proposed value.

---

## 📈 PA2 Performance Analysis Requirements (Question 2)

* **Harness**: `TreasureIslandAppAuto` executed via `run_tiapp_auto.sh`.
* **Configurable Variables**:
  * `maxmoves`: Number of moves each player executes (e.g. 50, 100, 500).
  * `interval`: Pause between generated moves in milliseconds (e.g. 100ms, 50ms, 20ms, 5ms, 0ms).
  * `numplayers`: Process group size (tested across 2, 3, 5, 7, 9 players).
  * `randseed`: Deterministic seed for reproducible move generation.
  * `failmode_N`: Fault injection testing (`IMMEDIATE`, `RECEIVEPROPOSE`, `AFTERSENDVOTE`, `AFTERSENDPROPOSE`, `AFTERBECOMINGLEADER`, `AFTERVALUEACCEPT`).
* **Empirical Research Questions**:
  1. **Latency & Throughput vs Interval & Players**: How do player count and move interval impact time-to-acceptance and total system throughput?
  2. **Process Fairness Under High Contention**: When intervals approach 0ms, do all processes achieve equal acceptance rates? Why might a specific process dominate (e.g., tie-breaking preference in BallotIDs)?
  3. **Throughput Saturation (Bandwidth/CPU Ceiling)**: Does throughput plateau before interval reaches 0ms? How does peak throughput scale with increasing player count?

---

## ✈️ Completed Milestone: Programming Assignment 1 (PA1)

* **Title**: **Distributing an Application (Travel Reservation System)**
* **Status**: **Completed & Verified** (Submitted October 5, 2026)
* **Architecture**: 3-Tier Distributed Reservation System with Flights, Cars, Rooms backend ResourceManagers, centralized Middleware customer tracking (Option C), and 2-phase atomic bundle reservations with compensating rollback.
* **Dual Networking Paradigms**:
  * **Java RMI**: Dynamic stub export via `UnicastRemoteObject`, naming registry (`group_xx_`), clean shutdown hooks.
  * **TCP Sockets & Non-blocking Concurrency**: Generic RPC message envelope (`TCPMessage`, `TCPResponse`), dynamic reflection proxy (`TCPProxy`), non-blocking worker thread pool at Middleware (`TCPMiddleware`), multi-threaded backend ResourceManagers (`TCPResourceManager`).
* **Deliverables**: Source code in `pa1/`, clean submission package `COMP512_PA1_Submission.zip`, full AI conversation log `pa1_ai_chat_log.json`.

---

## 📚 Authoritative Course Documents & Slides

### Slides Location: [`Course Content/Slides/`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides)
* [`512-2-inter-process-communication.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-2-inter-process-communication.pdf) / [`512-2-point-to-point-basics.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-2-point-to-point-basics%20-%20Copy.pdf): IPC fundamentals, blocking vs non-blocking I/O, failure models, synchronous vs asynchronous distributed systems.
* [`512-3-network-stack.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-3-network-stack.pdf): Network layers, TCP vs UDP, socket programming, sliding window flow control, serialization.
* [`512-4-RMI.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-4-RMI.pdf): Request-Reply, RMI architecture, remote interfaces, stubs/skeletons, registry, parameter passing semantics.
* [`512-5-time.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-5-time.pdf): Physical clocks, clock drift, Lamport logical clocks, happens-before $\rightarrow$, Vector clocks, causal ordering.
* [`512-6-gc.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-6-gc.pdf): Group Communication Systems (GCS), static/dynamic groups, multicast, FIFO, Causal, and Total ordering semantics.
* [`512-7-paxos-revised.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-7-paxos-revised.pdf): **Core Paxos lecture**: Consensus problem, FLP impossibility, safety over liveness, proposer/acceptor/learner roles, majority quorums, ballot numbering, prepare/promise, accept/ack, Multi-Paxos log replication, stable leader optimization.
* [`512-8-replication.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/512-8-replication.pdf): Data replication, update-anywhere vs primary copy, replicated state machines, consistency models.
* [`simplisticreliableP2Pprotocol.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/simplisticreliableP2Pprotocol.pdf): Reliable point-to-point delivery protocol.
* [`agreement.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/agreement.pdf): Asynchronous agreement & consensus impossibility.
* [`COMP512-sa1-2026.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Slides/COMP512-sa1-2026.pdf): Theoretical assignment 1.

### Project Documents: [`Course Content/Project Docs/`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs)
* **PA2 Specifications**:
  * [`COMP512-p2-2026.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/COMP512-p2-2026.pdf): Comprehensive PA2 project specification (Paxos total order, failure points, evaluation questions, rubric).
  * [`COMP512p2-GCL.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/COMP512p2-GCL.pdf): Group Communication Library manual (`sendMsg`, `broadcastMsg`, `multicastMsg`, `readGCMessage`).
  * [`COMP512p2-GettingStarted.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/COMP512p2-GettingStarted.pdf): Setup, compilation, manual playing, auto tester, and log file reference.
  * [`COMP512p2-UserGuide.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/COMP512p2-UserGuide.pdf): Game interface manual, pirate characters, commands (`L`, `R`, `U`, `D`, `E`, `FI`, `FRP`, `FSV`, `FSP`, `FOL`, `FMV`).
* **PA1 Specifications**:
  * [`COMP512-p1-2026.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/COMP512-p1-2026.pdf): PA1 project specification document.
  * [`GettingStarted.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/GettingStarted.pdf): PA1 setup and registry guide.
  * [`clientUserGuide.pdf`](file:///Users/nathanhu/downloads/COMP%20512/Course%20Content/Project%20Docs/clientUserGuide.pdf): PA1 interactive client user guide.

---

## 📂 Repository Directory Structure

```
COMP 512/
├── pa2/                                # Active PA2 workspace (Treasure Island & Paxos)
│   ├── build_tiapp.sh                  # PA2 compilation script (javac with comp512p2.jar)
│   ├── run_benchmarks.py               # Automated 24-run performance analysis benchmarking suite
│   ├── generate_report_figures.py      # Publication figure generator (Figure 1, 2, 3 in 300 DPI PNG & PDF)
│   ├── comp512p2/                      # Active student workspace
│   │   ├── comp512p2.jar               # Binary library (GCL & Treasure Island engine)
│   │   ├── build_tiapp.sh              # Build script
│   │   └── comp512st/
│   │       ├── paxos/
│   │       │   └── Paxos.java          # Paxos consensus module (ONLY public class)
│   │       ├── tiapp/
│   │       │   └── TreasureIslandApp.java # Interactive game application
│   │       └── tests/
│   │           ├── TreasureIslandAppAuto.java # Automated stress testing harness
│   │           └── run_tiapp_auto.sh   # Automated tester script
│   └── performance_analysis/           # Empirical benchmark evaluation outputs
│       ├── PERFORMANCE_REPORT.md       # Comprehensive 2-page empirical report
│       ├── data/                       # Raw benchmark JSON & summary CSV table
│       └── figures/                    # 300 DPI publication figures (Figure 1, 2, 3)
├── p2-students/                        # Pristine PA2 starter distribution & docs
│   ├── comp512p2/                      # Starter code directory
│   ├── comp512p2.tar.gz                # Starter code tarball
│   ├── p2docs/                         # Official PDF manuals
│   └── p2docs.tar.gz                   # Documentation tarball
├── pa1/                                # Completed PA1 workspace (Travel Reservation System)
│   ├── Server/                         # Backend ResourceManagers & Middleware (RMI & TCP)
│   │   ├── Makefile                    # Server build rules
│   │   ├── Server/Interface/           # IResourceManager remote interface
│   │   ├── Server/Common/              # Shared data entities (Flight, Car, Room, Customer)
│   │   ├── Server/RMI/                 # RMI server & middleware implementations
│   │   └── Server/TCP/                 # TCP server, middleware, proxy, & message envelopes
│   ├── Client/                         # Client codebase (CLI & dynamic proxy)
│   │   ├── Makefile                    # Client build rules
│   │   └── Client/                     # Client CLI, RMIClient, TCPClient
│   └── README.md                       # PA1 documentation
├── pa1-students/                       # Pristine PA1 starter distribution & docs
├── Course Content/                     # Authoritative course materials
│   ├── Slides/                         # Lecture slides (IPC, Network Stack, RMI, Paxos, Replication)
│   └── Project Docs/                   # PA1 & PA2 official specification PDFs
├── .agents/skills/                     # Automated developer & grading skills
│   ├── comp512-verifier/               # Automated test suites and specification checkers
│   │   ├── SKILL.md                    # Verification instructions
│   │   ├── references/spec_checklist.md# Grading rubric & checklist (PA1 & PA2)
│   │   └── scripts/
│   │       ├── verify_pa2.py           # PA2 layout, javac, visibility, GCL IPC, & fail points audit
│   │       ├── check_course_adherence.py # PA1 imports, thread safety, & invariant checker
│   │       ├── verify_pa1.py           # PA1 baseline build & runtime verifier
│   │       ├── test_edge_cases.py      # PA1 domain logic invariant test
│   │       └── test_distributed_system.py # PA1 5-node cluster integration test
│   ├── course-content-monitor/         # Continuous delta scanner for Course Content
│   │   ├── SKILL.md                    # Monitor instructions
│   │   ├── manifest.json               # SHA256 file manifest
│   │   └── scripts/monitor_course_content.py # Delta detector & synchronizer
│   └── submission-packager/            # Clean submission packaging utility
│       ├── SKILL.md                    # Packager instructions
│       └── scripts/package_submission.py # Packaging script supporting --assignment PA1 and PA2
├── README.md                           # Repository documentation
└── gemini.md                           # Dynamic living course guide & context
```

---

## 🎯 Implementation Status & Scope Matrix

### PA2 Implementation Status Matrix (Active Milestone)

| Component / Requirement | Category | Points | Status | Details & Invariants |
| :--- | :--- | :--- | :--- | :--- |
| **Workspace & Starter Setup** | Plumbing | — | 🟢 Completed | `pa2/` and `p2-students/` structured cleanly; relative `BASEDIR` and space-quoting fixes applied to scripts |
| **Compilation with `comp512p2.jar`** | Plumbing | — | 🟢 Completed | `./build_tiapp.sh` compiles cleanly with zero errors |
| **Automated Verification Harness** | Plumbing | — | 🟢 Completed | [`verify_pa2.py`](file:///Users/nathanhu/downloads/COMP%20512/.agents/skills/comp512-verifier/scripts/verify_pa2.py) verifies layout, javac, visibility, GCL IPC, and fail points |
| **Submission Packager for PA2** | Plumbing | — | 🟢 Completed | [`package_submission.py --assignment PA2`](file:///Users/nathanhu/downloads/COMP%20512/.agents/skills/submission-packager/scripts/package_submission.py) bundles source, scripts, docs, and validates sandbox build |
| **Package Visibility Enforcement** | Q1 Spec | — | 🟢 Enforced | Only `Paxos.java` is public; helper classes must be package-private |
| **GCL IPC Invariant** | Q1 Spec | — | 🟢 Enforced | All IPC restricted to GCL (`sendMsg`, `broadcastMsg`, `multicastMsg`); no RMI or raw sockets |
| **Paxos Proposer / Acceptor Core** | Question 1 | 25 pts | 🟢 Completed | Full Paxos Phase 1 (propose/promise) & Phase 2 (accept/ack) with monotonic `localBallotSeq * 100 + myProcessId` BallotIDs |
| **Total Order Delivery & Buffering** | Question 1 | 20 pts | 🟢 Completed | `acceptTOMsg()` sequential queue delivery via `LinkedBlockingQueue` with contiguous turn ordering |
| **No-Drop Move Persistence** | Question 1 | 10 pts | 🟢 Completed | `ProposalValue` sequence tracking across consecutive instances ensures superseded moves automatically retry |
| **Failure Points Instrumentation** | Question 1 | 10 pts | 🟢 Completed | All 5 fail points instrumented (`RECEIVEPROPOSE`, `AFTERSENDVOTE`, `AFTERSENDPROPOSE`, `AFTERBECOMINGLEADER`, `AFTERVALUEACCEPT`) and verified |
| **Empirical Performance Study** | Question 2 | 25 pts | 🟢 Completed | Automated harness `run_benchmarks.py` executed across 24 configurations ($N \in [3, 5, 7, 9]$, intervals $[100, 50, 20, 10, 5, 0]\text{ms}$); 100% total order agreement verified; publication figures and `PERFORMANCE_REPORT.md` generated |
| **Project Description Report** | Question 3 | 5 pts | 🟡 Planned | Max 2 pages text, mandatory No-AI declaration, 4 key design questions answered, collaboration log |
| **Live TA Demonstration** | Question 3 | 5 pts | 🟡 Planned | Live execution on SOCS Unix nodes, fault-injection demo, architecture Q&A defense |

---

### PA1 Implementation Status Matrix (Completed Milestone)

| Component / Task | Category | Points | Status | Details |
| :--- | :--- | :--- | :--- | :--- |
| **3-Tier RMI Middleware** | Part 1.1 | 10 pts | 🟢 Completed | `RMIMiddleware.java` implementing `IResourceManager`, binding to registry, routing to 3 RMs |
| **3 Dedicated RMs** | Part 1.1 | 5 pts | 🟢 Completed | Standalone `Flights`, `Cars`, `Rooms` RMs running on separate ports/machines |
| **Customer Strategy (Option C)** | Part 1.1 | 5 pts | 🟢 Completed | Centralized customer tracking at Middleware (`newCustomer`, cascading `deleteCustomer`, billing) |
| **Bundle Implementation** | Part 1.1 | 5 pts | 🟢 Completed | Multi-resource reservation with availability pre-check, duplicate flight rejection, compensating rollback |
| **TCP Generic RPC Service** | Part 1.2 | 15 pts | 🟢 Completed | Generic `TCPMessage`, `TCPResponse`, & dynamic `TCPProxy` client reflection proxy |
| **Non-blocking Middleware** | Part 1.2 | 20 pts | 🟢 Completed | `TCPMiddleware`: multi-threaded executor thread pool, dedicated sockets per call |
| **Concurrent RMs (TCP)** | Part 1.2 | 15 pts | 🟢 Completed | `TCPResourceManager`: multi-threaded request dispatch across cached thread pool |
| **Technical Report & Logs** | Part 2 | 15 pts | 🟢 Completed | Comprehensive 14-page report with architecture diagrams, performance analysis, AI token audit |
| **Live TA Demonstration** | Part 3 | 10 pts | 🟢 Completed | 5-machine cluster deployment on Trottier nodes (`tr-open-01` through `tr-open-05`) |

---

## 🧪 Verification & Submission Commands

### PA2 Verification & Packaging
```bash
# 1. Audit PA2 code adherence, visibility rules, GCL invariants, and fail points
python3 .agents/skills/comp512-verifier/scripts/verify_pa2.py

# 2. Package clean PA2 submission ZIP and run isolated sandbox compilation test
python3 .agents/skills/submission-packager/scripts/package_submission.py --assignment PA2
```

### PA1 Verification & Packaging
```bash
# 1. Course content adherence audit
python3 .agents/skills/comp512-verifier/scripts/check_course_adherence.py

# 2. Baseline build & startup verification
python3 .agents/skills/comp512-verifier/scripts/verify_pa1.py

# 3. Specification invariants & domain logic audit
python3 .agents/skills/comp512-verifier/scripts/test_edge_cases.py

# 4. Distributed cluster integration test (RMI & TCP)
python3 .agents/skills/comp512-verifier/scripts/test_distributed_system.py

# 5. Package clean PA1 submission ZIP
python3 .agents/skills/submission-packager/scripts/package_submission.py --assignment PA1
```
