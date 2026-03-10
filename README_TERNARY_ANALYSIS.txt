================================================================================
CE WORKBENCH: TERNARY SYSTEM GENERALIZATION ANALYSIS
================================================================================

OVERVIEW
--------
This analysis identifies why ternary (K=3) and higher-order systems cannot
currently flow through the CE Workbench UI, despite domain engines already
supporting them.

ROOT CAUSE: Binary-specific "double composition" scalar blocks multi-component
"double[] composition" arrays in DTOs, contexts, and adapters.

GOOD NEWS: Domain layer (engines) already supports K>=2. Only application/
presentation layers need refactoring to enable ternary support.

EFFORT ESTIMATE: 4-5 days (systematic refactoring, no algorithmic changes)


DOCUMENTS INCLUDED
==================

1. TERNARY_GENERALIZATION_REPORT.md (26 KB, 671 lines)
   Comprehensive analysis covering:
   - Executive Summary
   - What works at domain level (MCS/CVM engines already general)
   - Five critical issues (DTOs, contexts, adapters, UI, results)
   - Before/after code examples for all five blockers
   - Implementation priority roadmap (Phase 1-3)
   - File modification checklist with complexity ratings
   - Test evidence (CVMTernaryIntegrationTest.java proves ternary works)

2. TERNARY_QUICK_REFERENCE.md (6 KB, 174 lines)
   One-page executive summary with:
   - Core problem in one sentence
   - The five blockers (with file names)
   - File-by-file change checklist
   - Validation rules for composition arrays
   - Backward compatibility approach
   - Code snippets (DTO, context, adapter, UI)
   - Effort estimate breakdown

3. TERNARY_ARCHITECTURE_DIAGRAM.txt (24 KB, 470 lines)
   Visual walkthroughs covering:
   - Layer structure and data flow (ASCII diagrams)
   - Current (blocked) vs. required (fixed) state per layer
   - Complete data flow scenarios for binary/ternary cases
   - Key changes summary (layer x file x change type matrix)
   - Backward compatibility preservation strategy
   - Test coverage requirements per category
   - Validation rules as step-by-step checklist


KEY FINDINGS AT A GLANCE
=========================

BLOCKING ISSUES (5 STRUCTURAL PROBLEMS)
---------------------------------------

1. PRESENTATION LAYER
   File: CalculationSetupPanel.java (line 47)
   Problem: Single TextField, parsed as double
   Fix: Dynamic array of TextFields (count = numComponents)

2. APPLICATION DTOs
   Files: MCSCalculationRequest.java, CVMCalculationRequest.java
   Problem: Stores only "double composition" (binary B-fraction)
   Fix: Add "double[] composition" field + builder method

3. INFRASTRUCTURE CONTEXTS
   File: AbstractCalculationContext.java (line 15-16)
   Problem: Only "protected double composition" field
   Fix: Add "protected double[] compositionArray" field + constructor

4. INFRASTRUCTURE ADAPTERS
   Files: MCSRunnerAdapter.java (line 43), CVMEngineAdapter.java
   Problem: Hard-code binary conversion (.compositionBinary(...))
   Fix: Use array from context, fallback to binary if needed

5. DOMAIN ENGINE INPUT
   File: CVMEngine.java (line 70)
   Problem: Converts scalar to {1-x, x} (always 2 elements)
   Fix: Accept double[] directly, no binary conversion


FILES TO MODIFY (EXECUTION ORDER)
---------------------------------
Priority 1 (Foundation):
  - AbstractCalculationContext.java

Priority 2 (Request Objects):
  - MCSCalculationRequest.java
  - CVMCalculationRequest.java

Priority 3 (Context & Service):
  - MCSCalculationContext.java
  - CVMCalculationContext.java
  - CalculationService.java

Priority 4 (Adapters):
  - MCSRunnerAdapter.java
  - CVMEngineAdapter.java

Priority 5 (Domain):
  - CVMEngine.java

Priority 6 (Presentation):
  - CalculationSetupPanel.java


TIMELINE ESTIMATE
-----------------
Context refactoring (4 files): 1.5 days
DTO/builder updates (2 files): 1 day
Adapter fixes (2 files): 0.5 days
UI replacement (1 file): 1.5 days
Testing & validation: 1.5 days
-------------------------------------
TOTAL: 5-6 days


KEY INSIGHTS
============

1. DOMAIN LAYER ALREADY GENERAL
   Proof: CVMTernaryIntegrationTest passes (K=3 works at domain level)
   No algorithmic changes needed to engines
   Only plumbing work required

2. BACKWARD COMPATIBILITY IS EASY
   Keep scalar "composition" field for binary fallback
   Add array "compositionArray" field for multi-component
   Builder supports both ".composition(double[])" and ".compositionBinary(double)"

3. STRUCTURAL NOT ALGORITHMIC
   No changes to solvers, samplers, or mathematical algorithms
   Only data structure changes (scalar to array) in application layer
   One-to-one refactoring (well-understood pattern)

4. CLEAR EXECUTION PATH
   No unknowns or research needed
   Specific files and line numbers identified
   Code patterns for all five blockers provided

5. LOW RISK
   Binary code path unchanged (tested continuously)
   Ternary new code path (separate, additive)
   No touching of proven domain logic


HOW TO USE THESE DOCUMENTS
===========================

FOR PROJECT MANAGERS:
  Read TERNARY_QUICK_REFERENCE.md
  Focus on "Effort Estimate" section
  Timeline is 4-5 days for full implementation

FOR ARCHITECTS:
  Read TERNARY_ARCHITECTURE_DIAGRAM.txt
  Review layer structure and data flow diagrams
  Understand backward compatibility approach

FOR DEVELOPERS:
  Read TERNARY_GENERALIZATION_REPORT.md (sections 4-6)
  Get detailed "before/after" code examples
  Understand validation rules
  See line number references to actual files

FOR SPRINT PLANNING:
  Use TERNARY_QUICK_REFERENCE.md "File-by-File Changes" table
  Task breakdown (Priority levels 1-6)
  Can parallelize Priority 2 + Priority 3 with Priority 5

FOR CODE REVIEW:
  Use TERNARY_GENERALIZATION_REPORT.md "Code Examples" section
  Validate implementations against patterns
  Check validation rules are applied


DOCUMENT INDEX BY SECTION
==========================

GENERALIZATION_REPORT.md:
  1. Executive Summary
  2. Good News - Already General (Domain layer)
  3. Critical Issues (5 blockers)
  4. Affected Components (layer breakdown)
  5. Implementation Priority (phases)
  6. Code Examples (before/after)
  7. Key Files to Modify
  8. Test Evidence
  9. Summary Table

QUICK_REFERENCE.md:
  1. Core Problem
  2. Five Blockers
  3. File-by-File Changes
  4. Validation Rules
  5. Backward Compatibility
  6. Testing Checklist
  7. Code Snippets
  8. Why This Works
  9. Effort Estimate

ARCHITECTURE_DIAGRAM.txt:
  1. Layer Structure (ASCII)
  2. Data Flow Through Blockers
  3. Key Changes Summary (Table)
  4. Backward Compatibility
  5. Test Coverage Requirements
  6. Validation Rules
  7. Domain Layer Generality Notes


QUESTIONS ANSWERED
==================

Q: Why doesn't ternary work in the UI?
A: Single TextField (TERNARY_GENERALIZATION_REPORT.md, Issue 2)

Q: Can the domain engines handle K>=3?
A: Yes. Test evidence in section "Test Evidence"

Q: What needs to change to enable ternary?
A: Five structural issues listed in section "Critical Issues"

Q: What is the implementation order?
A: "Implementation Priority" section gives Phase 1, 2, 3

Q: How much effort is this?
A: "Effort Estimate" in QUICK_REFERENCE.md (5 days total)

Q: Will binary calculations break?
A: No. Backward Compatibility Preservation section

Q: What are the validation rules?
A: "Validation Rules (UI Layer)" in ARCHITECTURE_DIAGRAM.txt

Q: Can I see code examples?
A: Yes. GENERALIZATION_REPORT.md section "Code Examples"

Q: Is this a big architectural change?
A: No. Structural refactoring only (scalar to array)


VERSION INFORMATION
===================
Generated: March 10, 2026
Analysis Scope: Full CE Workbench codebase
Codebase Version: Post-PR#2 (commit ff4c3e8)
Target: Ternary (K=3) and higher-order (K>=4) system support


USAGE SUMMARY
=============
1. Start with README_TERNARY_ANALYSIS.txt (this file) for overview
2. Read TERNARY_QUICK_REFERENCE.md for project planning
3. Study TERNARY_GENERALIZATION_REPORT.md for implementation details
4. Use TERNARY_ARCHITECTURE_DIAGRAM.txt for architecture review

All documents are in /d/codes/ce/ directory
