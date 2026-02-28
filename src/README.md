# Source Directory Structure

This project has two `src/` directories by design:

## 1. `app/src/` - Application Source Code
- **Location**: `app/src/main/java/` and `app/src/test/java/`
- **Purpose**: All Java source code for the CE application
- **Type**: Application module in Gradle multi-module structure
- **Contains**: Core logic, GUI, identification engines, MCS, etc.
- **Resources**: Static configs (cluster definitions, YAML configs)

## 2. `src/` (root) - Runtime Data Resources  
- **Location**: `src/main/resources/cluster_data/`
- **Purpose**: Pre-computed cluster identification results
- **Type**: Shared resources packaged into JAR at build time
- **Contains**: Auto-generated JSON files from background jobs
- **Resources**: Dynamic data (cluster_result.json files)

## Why This Structure?

This follows Gradle's multi-module convention where:
- **Module-specific** code lives in `app/src/`
- **Shared runtime resources** live at the root `src/`

The build system packages both into the final application JAR.

## Documentation

- See [src/main/resources/README.md](src/main/resources/README.md) for runtime data details
- See [app/src/main/resources/README.md](app/src/main/resources/README.md) for static resource details
