---
name: comp512-verifier
description: Automated verification and testing suite for COMP 512 PA1. Audits code adherence, invariants, RMI/TCP distribution, and concurrency.
---

# COMP 512 Verification Skill

Runs course adherence audits, baseline verification, and edge case invariant tests for COMP 512 PA1.

## Usage

```bash
# 1. Course content & specification adherence audit
python3 .agents/skills/comp512-verifier/scripts/check_course_adherence.py

# 2. Baseline build & startup verification
python3 .agents/skills/comp512-verifier/scripts/verify_pa1.py

# 3. Specification invariants & domain logic audit
python3 .agents/skills/comp512-verifier/scripts/test_edge_cases.py
```
