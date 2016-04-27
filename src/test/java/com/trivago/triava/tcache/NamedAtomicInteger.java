package com.trivago.triava.tcache;

import java.util.concurrent.atomic.AtomicInteger;

class NamedAtomicInteger extends AtomicInteger
{
	private static final long serialVersionUID = 6608822331361354835L;
	
	private final String name;
	
	NamedAtomicInteger(String name)
	{
		this.name = name;
	}

	public String name()
	{
		return name;
	}
	
}