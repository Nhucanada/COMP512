---
name: comp512-verifier
description: Automated verification and testing suite for COMP 512 PA1 and PA2. Audits code adherence, invariants, Paxos consensus, RMI/TCP distribution, and concurrency.
---

# COMP 512 Verification Skill

Runs course adherence audits, baseline verification, package visibility audits, and edge-case invariant tests for COMP 512 programming milestones (PA1 & PA2).

## Active Milestone: PA2 (Total Order Using Paxos & Treasure Island)

```bash
# Verify PA2 layout, javac compilation, package visibility, GCL IPC, and failure points
python3 .agents/skills/comp512-verifier/scripts/verify_pa2.py
```

### Checks Performed by `verify_pa2.py`
1. **Workspace Layout**: Verifies presence of `pa2/`, `p2-students/`, `comp512p2.jar`, scripts, and app sources.
2. **Compilation**: Compiles `pa2/comp512st` using `comp512p2.jar` classpath.
3. **Visibility Invariants**: Ensures `Paxos.java` is the **only public class**, only permitted public methods exist, and all helper classes in `comp512st.paxos` are package-private.
4. **GCL Communication Invariant**: Enforces that all IPC utilizes GCL without banned networking imports (`java.rmi.*`, raw `Socket`, `ServerSocket`).
5. **FailCheck Instrumentation**: Audits for required invocation hooks for all 5 failure types (`RECEIVEPROPOSE`, `AFTERSENDVOTE`, `AFTERSENDPROPOSE`, `AFTERBECOMINGLEADER`, `AFTERVALUEACCEPT`).

---

## Completed Milestone: PA1 (Travel Reservation System)

```bash
# 1. Course content & specification adherence audit
python3 .agents/skills/comp512-verifier/scripts/check_course_adherence.py

# 2. Baseline build & startup verification
python3 .agents/skills/comp512-verifier/scripts/verify_pa1.py

# 3. Specification invariants & domain logic audit
python3 .agents/skills/comp512-verifier/scripts/test_edge_cases.py

# 4. Distributed cluster integration test (RMI & TCP)
python3 .agents/skills/comp512-verifier/scripts/test_distributed_system.py
```
