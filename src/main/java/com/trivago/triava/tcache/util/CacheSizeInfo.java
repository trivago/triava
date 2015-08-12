package com.trivago.triava.tcache.util;

/**
 * Data class which stores  information of the cache size, namely number of elements and
 * size in bytes. It is used in {@link com.trivago.triava.tcache.eviction.Cache#reportSize(ObjectSizeCalculatorInterface)}.
 * As reportSize() can take a serious amount of time, the number of elements is stored twice: Once before
 * the actual byte count is calculated, and one after. This makes it easier to see whether important changes
 * took place during calaculation, like expiration, eviction or a mass-insert of elements.
 *  
 * @author cesken
 *
 */
public class CacheSizeInfo
{
	private final String id;
	private final int elemsBefore;

	private final long sizeInByte;
	private final int elemsAfter;

	public CacheSizeInfo(String id, int elemsBefore, long sizeInByte, int elemsAfter)
	{
		this.id = id;
		this.elemsBefore = elemsBefore;
		this.sizeInByte = sizeInByte;
		this.elemsAfter = elemsAfter;
	}

	public String getId()
	{
		return id;
	}

	public int getElemsBefore()
	{
		return elemsBefore;
	}

	public long getSizeInByte()
	{
		return sizeInByte;
	}

	public int getElemsAfter()
	{
		return elemsAfter;
	}

	@Override
	public String toString()
	{
		return "CacheSizeInfo [id=" + id + ", elemsBefore=" + elemsBefore + ", elemsAfter=" + elemsAfter
				+ ", elemsDiff=" + (elemsAfter - elemsBefore) + ", sizeInByte=" + sizeInByte + ", sizeReadable="
				+ humanReadableSize(sizeInByte) + "]";
	}

	/**
	 * Transforms a size in bytes to human readable representation.
	 * 
	 * @param size
	 * @return
	 */
	private static final long BASE = 1000;
	private static final long BASE_KILO = BASE;
	private static final long BASE_MEGA = BASE * BASE;
	private static final long BASE_GIGA = BASE * BASE * BASE;

	private static String humanReadableSize(long size)
	{

		StringBuilder formatedSize = new StringBuilder(32);

		long remainder = size;
		long GB = remainder / BASE_GIGA;
		remainder -= GB * BASE_GIGA;
		long MB = remainder / BASE_MEGA;
		remainder -= MB * BASE_MEGA;
		long KB = remainder / BASE_KILO;
		remainder -= KB * BASE_KILO;
		long bytes = remainder;

		if (GB > 0)
			formatedSize.append(GB).append("GB ");
		if (MB > 0)
			formatedSize.append(MB).append("MB ");
		if (KB > 0)
			formatedSize.append(KB).append("KB ");
		formatedSize.append(bytes).append("B");

		return formatedSize.toString();
	}

}