package com.trivago.triava.tcache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class Serializing
{
	public static byte[] toBytearray(Object obj) throws IOException
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		try
		{
		  out = new ObjectOutputStream(bos);   
		  out.writeObject(obj);
		  return bos.toByteArray();
		}
		finally
		{
		  try
		  {
		    if (out != null)
		    {
		      out.close();
		    }
		  }
		  catch (IOException ex) {}

		  try
		  {
		    bos.close();
		  }
		  catch (IOException ex) {}
		}
	}
	
	public static Object fromBytearray(byte[] serialized) throws IOException, ClassNotFoundException
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
		ObjectInput in = null;
		try {
		  in = new ObjectInputStream(bis);
		  return in.readObject(); 
		}
		finally
		{
		  try
		  {
		    bis.close();
		  }
		  catch (IOException ex) {}
		  
		  try
		  {
		    if (in != null)
		    {
		      in.close();
		    }
		  }
		  catch (IOException ex) {} 
		}
	}


}
