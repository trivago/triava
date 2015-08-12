package com.trivago.triava.logging;


/**
 * A log implementation that does nothing. It swallows all output. 
 * @author cesken
 *
 */
public class TriavaNullLogger implements TriavaLogger
{

	@Override
	public void info(String message)
	{
	}

	@Override
	public void error(String message)
	{
	}

	@Override
	public void error(String message, Throwable exc)
	{
	}

}
