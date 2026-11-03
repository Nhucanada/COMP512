---
name: submission-packager
description: Automated submission packaging and compilation auditing skill for COMP 512 (PA1 & PA2). Produces clean zips without AI or build artifacts.
---

# Submission Packager Skill

Packages the project into a clean zip deliverable ready for MyCourses submission, automatically stripping forbidden files (OS metadata, IDE config, build artifacts, AI logs) and verifying compilation in an isolated temporary sandbox.

## Usage

```bash
# Package active milestone: PA2 (Total Order Using Paxos)
python3 .agents/skills/submission-packager/scripts/package_submission.py --assignment PA2

# Package completed milestone: PA1 (Travel Reservation System)
python3 .agents/skills/submission-packager/scripts/package_submission.py --assignment PA1
```
