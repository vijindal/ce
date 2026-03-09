/**
 * Port interfaces defining contracts for external dependencies.
 *
 * <p>Following the Ports and Adapters (Hexagonal) architecture pattern,
 * these interfaces define what the domain needs from external systems
 * without specifying how those needs are fulfilled.</p>
 *
 * <h2>Key Ports</h2>
 * <ul>
 *   <li>{@link org.ce.domain.port.ClusterDataRepository} - Cluster topology data access</li>
 *   <li>{@link org.ce.domain.port.ECIRepository} - Effective Cluster Interactions access</li>
 *   <li>{@link org.ce.domain.port.SystemRepository} - System identity management</li>
 * </ul>
 *
 * <p>Implementations of these interfaces live in the infrastructure layer
 * ({@code org.ce.infrastructure.*}), allowing the domain to remain pure.</p>
 *
 * @since 2.0
 */
package org.ce.domain.port;

