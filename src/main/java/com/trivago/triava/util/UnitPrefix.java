package com.trivago.triava.util;

/**
 * Represents a unit prefix, for example milli or giga.
 * A unit prefix has a long name ("mega"), a unit symbol ("M") and a value (1.000.000) representing the factor.
 * The value is a factor of 10 (k = kilo = 1000) for Si based prefixes, but there may be
 * variations like IEC based (Ki = kibi = 1024) which are commonly used in the IT world.   
 * The value can be smaller than 1, for example 0.001 for millis.
 * 
 * @author cesken
 *
 */
public class UnitPrefix
{
	private final String name;
	private final String symbol;
	private final double value;
	
	UnitPrefix(String name, String symbol, int value)
	{
		this.name = name;
		this.symbol = symbol;
		this.value = value;
	}

	public String symbol()
	{
		return symbol;
	}

	public double value()
	{
		return value;
	}

	public String name()
	{
		return name;
	}
}
