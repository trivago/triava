package com.trivago.triava.logging;

import java.util.Arrays;


/**
 * A log implementation that logs info messages to System.out and error messages to System.err.
 * @author cesken
 *
 */
public class TriavaConsoleLogger implements TriavaLogger
{

	@Override
	public void info(String message)
	{
		System.out.println(message);
	}

	@Override
	public void error(String message)
	{
		System.err.println(message);
	}

	@Override
	public void error(String message, Throwable exc)
	{
		System.err.println(message + " : " + exc.getMessage() + Arrays.toString(exc.getStackTrace()));
	}

}
