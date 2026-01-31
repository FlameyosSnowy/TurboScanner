# TurboScanner
High-performance and minimal SIMD library for byte scanning and classification with zero dependencies, by FlameyosFlow

It provides SIMD-friendly APIs that scan byte buffers in a single pass and produce compact bitmask representations of structural characters, string boundaries, escapes, and control bytes.

TurboScanner centralizes the work of locating structural characters and validating input into a single SIMD-friendly scan, allowing downstream code to operate on precomputed structural masks instead of re-scanning raw bytes.

# Features:
- Low-level byte scanning and classification of masks.
- One loop and branchless equals/hashCode on ScanResult for all five mask arrays
- In-string masking.

This can be used to make high-performance parsers that scan, validate and decode multiple characters at once (Such as JSON parsers)

# License
MIT License.
