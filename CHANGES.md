v1.x Releases
=============
- 1.0.4
     - SAE-411 Reduce lock contention for mutating operations, by Christian Esken
- 1.0.3
     - SAE-340 Fix expiration for high TTL, time based expiration logging, by Christian Esken
- 1.0.1
    - SAE-336 Fix NPE in synchronous listener by Christian Esken
    - SAE-299 Add logging of eviction policy by Rupesh Patayane
    - SAE-327 Cleanup deprecated method. Updates to documentation. by Alexander Lakir, Christian Esken

- 1.0.0
    - Release v1.0.0

- 1.0-rc1
    - Release candidate for v1.0


v0.x Releases
=============

- 0.9.15
    - Fixing the API for Version 1.0.
- 0.9.14
    - cache: Fix missing notification of Listeners
- 0.9.13
    - cache: API cleanup, fix duplicate notification of Listeners
- 0.9.9
    - cache: API cleanup, adding TimeUnit param to API
- 0.9.8
    - cache: Fully JCache / JSR107 compliance 
- 0.9.6
    - cache: JSR107 ExpiryPolicy support    
    - cache: Incompatible changes:
        - idleTime=0 means immediately expired instead of no expiration
        - Signature of put() with idle/cache times changed from long to int
- 0.9.5
    - cache: Support Store-By-Value.
    - cache: Fully JSR107 compliance for Statistic, Write-Through, Read-Through
- 0.9.4
    - cache: Listener support for synchronous and asynchronous events.
    - cache: Support CacheLoader, ListenerConfiguration, MutableEntry, EntryProcessor
    - util:  Added a UnitFormatter to format values to units, e.g. "10.53MiB", "20.5s" or "10GW, 200MW, 25W". The Unit formatter
             supports SI units (1000 based, kilo, k), IEC60027-2 units (1024 based, kibi, Ki) and JEDEC units (1024 based, Kilo, K)
- v0.9.1
    - cache: Implementing JSR107 compliance (work in progress)
    - MXBean support: Configuration and Statistics
    - Added CacheManager.destroyCache()
    - Added JSR methods ...replace...() methods.
- v0.9.0
    - Finalized package structure. Moved existing unit tests to triava project.
- v0.4.0
    - Initial version. Production ready.
        