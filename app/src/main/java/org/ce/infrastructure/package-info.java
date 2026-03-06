/**
 * Infrastructure layer - implementations of domain port interfaces.
 *
 * <p>This package contains adapters that implement the repository interfaces
 * defined in {@code org.ce.domain.port}, connecting the domain layer to
 * external systems like file storage, databases, and caches.</p>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@code persistence} - Data access adapters (cache, ECI, registry)</li>
 * </ul>
 *
 * <p>Classes here may depend on:</p>
 * <ul>
 *   <li>Domain layer ({@code org.ce.domain.*})</li>
 *   <li>Java I/O and NIO</li>
 *   <li>Third-party libraries (JSON, etc.)</li>
 * </ul>
 *
 * @since 2.0
 */
package org.ce.infrastructure;

