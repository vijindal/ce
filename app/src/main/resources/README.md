# Application Resources

This directory contains **static configuration files** packaged with the application.

## Contents

### cluster/
- `A1-TO.txt`, `A2-T.txt`, `B2-T.txt`
- Pre-defined cluster topology definitions

### data/
- `elements.yaml` - Element database
- `structure_models.yaml` - Crystal structure templates
- `models/`, `systems/` - System-specific configurations

### symmetry/
- `A2-SG.txt`, `B2-SG.txt`, etc.
- Space group and symmetry operation matrices

## Difference from Root-Level src/

| Location | Purpose | Content Type |
|----------|---------|--------------|
| **`app/src/main/resources/`** (here) | Static config | Hand-written definitions |
| **`src/main/resources/`** (root) | Runtime data | Auto-generated cluster results |

See `../../../../../src/main/resources/README.md` for details on the root-level resources.
