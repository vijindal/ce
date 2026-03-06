/**
 * Calculation result types following a sealed hierarchy.
 *
 * <p>This package unifies CVM and MCS results under a common abstraction,
 * enabling type-safe pattern matching and polymorphic result handling.</p>
 *
 * <h2>Hierarchy</h2>
 * <pre>
 * CalculationResult (sealed interface)
 * â”œâ”€â”€ ThermodynamicResult (interface) - common thermodynamic quantities
 * â”‚   â”œâ”€â”€ CVMResult (record) - CVM-specific results
 * â”‚   â””â”€â”€ MCSResult (record) - MCS-specific results
 * â””â”€â”€ CalculationFailure (record) - failed calculations
 * </pre>
 *
 * <h2>Usage with Pattern Matching</h2>
 * <pre>{@code
 * CalculationResult result = ...;
 * switch (result) {
 *     case CVMResult cvm -> handleCVM(cvm);
 *     case MCSResult mcs -> handleMCS(mcs);
 *     case CalculationFailure f -> handleFailure(f);
 * }
 * }</pre>
 *
 * @since 2.0
 */
package org.ce.domain.model.result;

