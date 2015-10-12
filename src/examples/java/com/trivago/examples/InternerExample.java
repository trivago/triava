package com.trivago.examples;

import com.trivago.triava.collections.HashInterner;
import com.trivago.triava.collections.InternerInterface;

/**
 * Examples for creating and using an Interner.
 * 
 * @author cesken
 *
 */
public class InternerExample
{
	public static void main(String[] args)
	{
		internOnce();
		internMultiround();
	}

	private static void internOnce()
	{
		InternerInterface<Integer> intInterner = new HashInterner<>();
		for (int i = 0; i < 20; i++)
		{
			intInterner.get(1_000_000 * i);
		}
		System.out.println(intInterner);
	}

	private static void internMultiround()
	{
		InternerInterface<Integer> intInterner = new HashInterner<>();
		for (int round = 0; round < 1000; round++)
		{
			for (int i = 0; i < 20; i++)
			{
				int finalFactor = round < 300 ? i : 20+i;
				intInterner.get(1_000_000 * finalFactor);
			}
		}

		System.out.println(intInterner);
	}

}
