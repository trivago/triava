/*********************************************************************************
 * Copyright 2015-present trivago GmbH
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
