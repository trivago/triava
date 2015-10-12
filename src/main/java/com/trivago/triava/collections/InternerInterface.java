package com.trivago.triava.collections;

/**
 * Interface for interning objects, similar to {@link String#intern()} but generic and multi-instance capable.
 * The use case for an Interner is to save memory and reduce garbage collection work. Recommended use is on
 * deserialization of common objects from DB, Cache or REST-Interfaces.
 * <p>
 * 
 * <b>Example: Memory and GC calculation for short strings</b>: A locale code like "EN" can be estimated to 56
 * byte:<br>
 * <ul>
 * <li>
 * 12 byte : object header for the String</li>
 * <li>
 * 4 byte: for cached int hash</li>
 * <li>
 * 4 byte : The reference to the char array, assuming compressed OOPS</li>
 * <li>
 * Sum : 24 byte (20 byte plus 4 byte padding/waste)</li>
 * </ul>
 * 
 * <ul>
 * <li>
 * 16 byte : object header for the char array</li>
 * <li>
 * 8 byte: 2*length for the char array</li>
 * <li>
 * Sum : 24 byte</li>
 * </ul>
 * 
 * Each locale code String thus takes 48 byte (24+24). If 1 Million Strings like these are shared (it could
 * even be 1 million times the same string "EN"), 48 MB will be saved. Longer Strings have more saving potential.
 * Often more important, the garbage collector has to visit 1 Million objects less.
 * 
 * @author cesken
 *
 * @param <T>
 *            The type to intern
 */
public interface InternerInterface<T>
{

	/**
	 * Returns a shared instance for the given value. The given value is interned similar to
	 * {@link String#intern()}, if it is not found. Identity is determined in an implementation specific way,
	 * which is documented by the implementation.
	 * <p>
	 * Further calls with the same value will typically return the interned value, but this interface <b>does
	 * not</b> mandate that the same shared instance is returned under <i>under all circumstances</i>. The
	 * implementing class should clearly document exemptions. Typical reasons are values that are only stored
	 * for a limited lifetime, or tradeoffs during concurrent writes for the sake of speed.
	 * <p>
	 * Implementations must be thread-safe.
	 * 
	 * @param value
	 * @return The shared instance, or null for a null value
	 */
	public T get(T value);
}
