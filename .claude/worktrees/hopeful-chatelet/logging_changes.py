#!/usr/bin/env python3
"""Logging replacement script for CE project - all 11 files."""

BASE = "C:/codes/ce/.claude/worktrees/hopeful-chatelet/app/src/main/java/org/ce"
results = {}

def process_file(name, filepath, changes):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except FileNotFoundError:
        results[name] = ["  ERROR: File not found: " + filepath]
        return

    file_results = []
    for item in changes:
        old, new, desc = item[0], item[1], item[2]
        max_count = item[3] if len(item) > 3 else 0

        occurrences = content.count(old)
        if occurrences > 0:
            if max_count > 0:
                content = content.replace(old, new, max_count)
                replaced = min(occurrences, max_count)
                file_results.append(f"  OK [replaced {replaced}/{occurrences} occ]: {desc}")
            else:
                content = content.replace(old, new)
                file_results.append(f"  OK [replaced {occurrences} occ]: {desc}")
        else:
            file_results.append(f"  MISS [0 occurrences]: {desc}")

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    results[name] = file_results

