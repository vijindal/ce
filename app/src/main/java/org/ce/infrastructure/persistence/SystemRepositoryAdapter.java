package org.ce.infrastructure.persistence;

import org.ce.domain.port.SystemRepository;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;

import java.util.Collection;
import java.util.Optional;

/**
 * Adapter implementing {@link SystemRepository} by delegating to
 * an existing {@link SystemRegistry} instance.
 *
 * <p>Unlike the other adapters, this one wraps an instance rather than
 * static methods, since SystemRegistry maintains mutable state.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SystemRegistry registry = new SystemRegistry(workspacePath);
 * SystemRepository repo = new SystemRepositoryAdapter(registry);
 * repo.register(system);
 * Optional<SystemIdentity> found = repo.findById("Nb-Ti_BCC_A2_T_bin");
 * }</pre>
 *
 * @since 2.0
 */
public class SystemRepositoryAdapter implements SystemRepository<SystemIdentity> {

    private final SystemRegistry delegate;

    /**
     * Creates a new adapter wrapping the given registry.
     *
     * @param registry the underlying registry instance (must not be null)
     */
    public SystemRepositoryAdapter(SystemRegistry registry) {
        this.delegate = java.util.Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void register(SystemIdentity system) {
        if (delegate.getSystem(system.getId()) != null) {
            throw new IllegalArgumentException(
                    "System with ID '" + system.getId() + "' already exists");
        }
        delegate.registerSystem(system);
    }

    @Override
    public Optional<SystemIdentity> findById(String systemId) {
        return Optional.ofNullable(delegate.getSystem(systemId));
    }

    @Override
    public Collection<SystemIdentity> findAll() {
        return java.util.Collections.unmodifiableCollection(delegate.getAllSystems());
    }

    @Override
    public boolean remove(String systemId) throws Exception {
        if (delegate.getSystem(systemId) == null) {
            return false;
        }
        delegate.removeSystem(systemId);
        return true;
    }

    @Override
    public boolean isClustersComputed(String systemId) {
        return delegate.isClustersComputed(systemId);
    }

    @Override
    public boolean isCfsComputed(String systemId) {
        return delegate.isCfsComputed(systemId);
    }

    @Override
    public void setClustersComputed(String systemId, boolean computed) {
        delegate.markClustersComputed(systemId, computed);
    }

    @Override
    public void setCfsComputed(String systemId, boolean computed) {
        delegate.markCfsComputed(systemId, computed);
    }

    @Override
    public void flush() throws Exception {
        delegate.persistIfDirty();
    }

    /**
     * Returns the underlying registry (for legacy code interop).
     *
     * @return the wrapped SystemRegistry instance
     */
    public SystemRegistry getDelegate() {
        return delegate;
    }
}

