/**
 * Persistence adapters implementing domain repository interfaces.
 *
 * <p>Each adapter wraps existing static cache/loader classes to provide
 * instance-based, injectable implementations:</p>
 * <ul>
 *   <li>{@link org.ce.infrastructure.persistence.ClusterDataRepositoryAdapter}
 *       â†’ wraps {@code AllClusterDataCache}</li>
 *   <li>{@link org.ce.infrastructure.persistence.ECIRepositoryAdapter}
 *       â†’ wraps {@code ECILoader}</li>
 *   <li>{@link org.ce.infrastructure.persistence.SystemRepositoryAdapter}
 *       â†’ wraps {@code SystemRegistry}</li>
 * </ul>
 *
 * @since 2.0
 */
package org.ce.infrastructure.persistence;

