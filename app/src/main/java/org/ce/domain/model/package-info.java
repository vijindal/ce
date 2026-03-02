/**
 * Domain model types - pure entities and value objects.
 *
 * <p>This package contains the core domain abstractions:</p>
 * <ul>
 *   <li>{@code result} - Calculation result types (sealed hierarchy)</li>
 *   <li>{@code system} - System identity and status (future)</li>
 *   <li>{@code cluster} - Cluster topology types (future)</li>
 * </ul>
 *
 * <p>All types here should be:</p>
 * <ul>
 *   <li>Immutable where possible</li>
 *   <li>Free of infrastructure concerns</li>
 *   <li>Self-validating</li>
 * </ul>
 *
 * @since 2.0
 */
package org.ce.domain.model;
