package com.trivago.triava.tcache;

/**
 * Defines how entries are put in the cache. Either they are put using the reference of the instance (Identity),
 * cloned via clone() (Clone) or interned similar to {@link String#intern()}  (Intern).
 * @author cesken
 *
 */
public enum CacheWriteMode
{
	Identity, Clone, Intern
}
