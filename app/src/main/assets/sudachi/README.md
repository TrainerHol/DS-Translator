# Sudachi Dictionary

This directory requires the Sudachi system_core.dic dictionary file (~72MB).

## How to obtain

1. Download the Sudachi dictionary from:
   https://github.com/WorksApplications/SudachiDict

2. Download the "core" dictionary for your desired version (matching Sudachi 0.7.5).

3. Extract `system_core.dic` from the downloaded archive.

4. Place `system_core.dic` in this directory:
   `app/src/main/assets/sudachi/system_core.dic`

## Note

The dictionary file is NOT committed to git due to its size (~72MB).
The app copies this file to internal storage on first launch for
memory-mapped access by the Sudachi morphological analyzer.
