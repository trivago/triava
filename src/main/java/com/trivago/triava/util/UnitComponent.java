/*********************************************************************************
 * Copyright 2016-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 **********************************************************************************/

package com.trivago.triava.util;

/**
 * Holder that stores a long value separated in its unit prefixes giga, mega, kilo and non-prefix. The meaning
 * of "mega" and the other fields are dependent on the UnitSystem. Typically kilo is either 1000 (Si) or 1024
 * (IEC 60027-2).
 * <p>
 * For example the value 1025 is split in kilo=1, base=25 for the SI {@link UnitSystem}, but in kilo=1, base=1 for the
 * IEC 60027-2 UnitSystem.
 * 
 * @author cesken
 *
 */
public class UnitComponent
{
	private final int base;
	private final int kilo;
	private final int mega;
	private final int giga;

	/**
	 * Creates a representation in the {@link UnitSystem} for the value.
	 * 
	 * @param value
	 * @param unitSystem
	 */
	public UnitComponent(long value, UnitSystem unitSystem)
	{
		// Convert from double to long to force integer arithmetics
		final long BASE_KILO = (long) unitSystem.kiloPrefix().value();
		final long BASE_MEGA = (long) unitSystem.megaPrefix().value();
		final long BASE_GIGA = (long) unitSystem.gigaPrefix().value();

		// The following calculations are using integer arithmetics
		long remainder = value;
		giga = (int) (remainder / BASE_GIGA);
		remainder -= giga * BASE_GIGA;
		mega = (int) (remainder / BASE_MEGA);
		remainder -= mega * BASE_MEGA;
		kilo = (int) (remainder / BASE_KILO);
		remainder -= kilo * BASE_KILO;
		base = (int) remainder;
	}

	/**
	 * Returns the base value, from the remainder less than the "kilo" value
	 * @return The base value
	 */
	public int base()
	{
		return base;
	}

	/**
	 * Returns the full kilo value, from the remainder less than the "mega" value
	 * @return The kilo value
	 */
	public int kilo()
	{
		return kilo;
	}

	/**
	 * Returns the full mega value, from the remainder less than the "giga" value
	 * @return The mega value
	 */
	public int mega()
	{
		return mega;
	}

	/**
	 * Returns the full giga value. This value can have any value between 0 and {@link Integer#MAX_VALUE}
	 * @return The giga value
	 */
	public int giga()
	{
		return giga;
	}
	
	
}
