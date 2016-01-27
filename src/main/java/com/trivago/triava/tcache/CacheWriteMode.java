package com.trivago.triava.tcache;

/**
 * Defines how entries are put in the cache. Either they are put using the reference of the instance (Identity),
 * cloned via clone() (Clone) or interned similar to {@link String#intern()}  (Intern). Call {@link #isStoreByValue()}
 * to determine whether the store method is store-by-value.
 * 
 * @author cesken
 *
 */
public enum CacheWriteMode
{
	Identity(false), Clone(true), Intern(false);
	
	final boolean jsr107compatibleStoreByValue;
	
	CacheWriteMode(boolean jsr107compatibleCopying)
	{
		this.jsr107compatibleStoreByValue = jsr107compatibleCopying;
	}
	
	public static CacheWriteMode fromStoreByValue(boolean storeByValue)
	{
		return storeByValue ? Clone : Identity;
	}
	
	/**
	 * Returns whether this CacheWriteMode is using store-by-value as defined by JSR107.
	 * Only for {@link #Clone} true is returned. Mode {@link #Identity} always shares Objects, and {@link #Intern} also most of the time.
	 * @return
	 */
	public boolean isStoreByValue()
	{
		return jsr107compatibleStoreByValue;
	}
}
