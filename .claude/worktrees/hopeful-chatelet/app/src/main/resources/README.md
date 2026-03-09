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

## Runtime Data

Runtime-generated cluster data (from background jobs) is stored in `data/cluster_cache/` at the project root — separate from these static resources.
