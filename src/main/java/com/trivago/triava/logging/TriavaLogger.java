package com.trivago.triava.logging;

/**
 * A log facade for usage within the triava library.
 * @author cesken
 *
 */
public interface TriavaLogger
{
	void info(String message);
	void error(String message);
	void error(String message, Throwable exc);
}
