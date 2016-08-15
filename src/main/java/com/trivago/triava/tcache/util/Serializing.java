/*********************************************************************************
 * Copyright 2016-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************************/

package com.trivago.triava.tcache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

/**
 * Methods that help serializing and deserializing.
 * @author cesken
 *
 */
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
