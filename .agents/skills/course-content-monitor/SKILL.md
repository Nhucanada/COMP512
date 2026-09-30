---
name: course-content-monitor
description: Continuous course content delta detector and sync manager for COMP 512. Audits slides, assignments, and guidelines.
---

# Course Content Monitor Skill

Audits `Course Content/` for changes, additions, or modifications to lecture slides, assignments, and tutorials.

## Usage

```bash
# Check for any new or modified slides or specs
python3 .agents/skills/course-content-monitor/scripts/monitor_course_content.py --audit

# Update manifest after reconciling changes
python3 .agents/skills/course-content-monitor/scripts/monitor_course_content.py --save
```
