/**
 * Domain layer - the core of the application following Clean Architecture principles.
 *
 * <p>This package contains pure domain logic with <strong>zero dependencies</strong>
 * on external frameworks or infrastructure. All types here should be:</p>
 * <ul>
 *   <li>Framework-agnostic (no JavaFX, Spring, etc.)</li>
 *   <li>Infrastructure-agnostic (no file I/O, network, database)</li>
 *   <li>Testable in isolation</li>
 * </ul>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@code model} - Pure domain entities and value objects</li>
 *   <li>{@code engine} - Stateless computation engines (CVM, MCS)</li>
 *   <li>{@code port} - Interfaces for external dependencies (repository pattern)</li>
 * </ul>
 *
 * <h2>Dependency Rule</h2>
 * <p>Dependencies flow inward only. This package should not import from:</p>
 * <ul>
 *   <li>{@code org.ce.infrastructure.*}</li>
 *   <li>{@code org.ce.application.*}</li>
 *   <li>{@code org.ce.presentation.*}</li>
 * </ul>
 *
 * @since 2.0
 */
package org.ce.domain;

