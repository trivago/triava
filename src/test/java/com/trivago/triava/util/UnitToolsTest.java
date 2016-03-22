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

import static org.junit.Assert.assertEquals;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests the UnitTools methods
 * 
 * @author cesken
 *
 */
public class UnitToolsTest
{
	@BeforeClass
	public static void init()
	{
		Locale.setDefault(Locale.ENGLISH);
	}

	@AfterClass
	public static void tearDown()
	{
	}

	@Before
	public void setUpEach()
	{
	}

	@Test
	public void testSIsingleValue()
	{
		UnitSystem us = UnitSystem.SI;

		// Value "without" decimal places
		String k100 = UnitFormatter.formatAsUnit(1000, us, "B");
		assertEquals("", "1kB", k100); // Depends on locale, especially comma vs point

		// Rounding down
		String k112 = UnitFormatter.formatAsUnit(1123, us, "B");
		assertEquals("", "1.12kB", k112); // Depends on locale, especially comma vs point

		// Rounding up
		String k113 = UnitFormatter.formatAsUnit(1126, us, "B");
		assertEquals("", "1.13kB", k113); // Depends on locale, especially comma vs point
	}

	@Test
	public void testIECsingleValue()
	{
		UnitSystem us = UnitSystem.IEC;
		// Exact 2^n value
		String k200 = UnitFormatter.formatAsUnit(2048, us, "B");
		assertEquals("", "2KiB", k200); // Depends on locale, especially comma vs point

		// Rounding down: 2.04449219
		String k204 = UnitFormatter.formatAsUnit(2094, us, "B");
		assertEquals("", "2.04KiB", k204); // Depends on locale, especially comma vs point

		// Rounding up: 2,0458984
		String k205 = UnitFormatter.formatAsUnit(2095, us, "B");
		assertEquals("", "2.05KiB", k205); // Depends on locale, especially comma vs point
	}


	/**
	 * Test to format to multi Units prefixes. The test is conducted with {@link UnitSystem}.SI.
	 */
	@Test
	public void testUnits()
	{
		UnitSystem us = UnitSystem.SI;

		// Simple conversion
		String megawatt1 = UnitFormatter.formatAsUnits(12_345_678, us, "W", ",");
		assertEquals("", "12MW,345kW,678W", megawatt1);

		// Multiple Characters for separator
		String megawatt2 = UnitFormatter.formatAsUnits(12_345_678, us, "W", ", ");
		assertEquals("", "12MW, 345kW, 678W", megawatt2);

		// Simple conversion, with Kilos empty
		String megawatt3 = UnitFormatter.formatAsUnits(12_000_678, us, "W", ",");
		assertEquals("", "12MW,678W", megawatt3);

		// Multiple Characters, with Kilos empty
		String megawatt4 = UnitFormatter.formatAsUnits(12_000_678, us, "W", ", ");
		assertEquals("", "12MW, 678W", megawatt4);
	}
	
	

	/**
	 * Test method calls with DecimalFormat. The test is conducted with {@link UnitSystem}.IEC.
	 */
	@Test
	public void testDecimalFormat()
	{
		UnitSystem us = UnitSystem.IEC;

		// Rounding up: 2,0361328 , using 2 fraction digits
		String k2085 = UnitFormatter.formatAsUnit(2085, us, "B", new DecimalFormat("0.00"));
		assertEquals("", "2.04KiB", k2085); // Depends on locale, especially comma vs point

		// Rounding down: 2,0361328 , using 0 or 1 fraction digits
		String k200b = UnitFormatter.formatAsUnit(2085, us, "B", new DecimalFormat("0.#"));
		assertEquals("", "2KiB", k200b); // Depends on locale, especially comma vs point

		// Rounding down: 2,0361328 , using 1 fraction digit
		String k200c = UnitFormatter.formatAsUnit(2085, us, "B", new DecimalFormat("0.0"));
		assertEquals("", "2.0KiB", k200c); // Depends on locale, especially comma vs point
	}
	
	
	/**
	 * Test DecimalFormatSymbols override
	 */
	@Test
	public void testDecimalFormatSymbolslOverride()
	{
		UnitSystem us = UnitSystem.SI;

		DecimalFormat dfDE = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
		DecimalFormat dfUS = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));

		String kDE = UnitFormatter.formatAsUnit(1123, us, "B", dfDE);
		assertEquals("", "1,12kB", kDE); // Depends on locale, comma for Germany
		String kUS = UnitFormatter.formatAsUnit(1123, us, "B", dfUS);
		assertEquals("", "1.12kB", kUS); // Depends on locale, point for US
	}
	
	
	
	/**
	 * Tests that numbers will be properly split into components
	 */
	@Test
	public void testComponents()
	{
		UnitComponent uc;
		
		uc = new  UnitComponent(123_345_567_789L, UnitSystem.SI);
		assertEquals(123, uc.giga());
		assertEquals(345, uc.mega());
		assertEquals(567, uc.kilo());
		assertEquals(789, uc.base());
		
		uc = new  UnitComponent(1000_345_567_789L, UnitSystem.SI);
		assertEquals(1000, uc.giga());
		assertEquals(345, uc.mega());
		assertEquals(567, uc.kilo());
		assertEquals(789, uc.base());

		// Highest component (Giga) can exceed 1000
		uc = new  UnitComponent(1001_345_567_789L, UnitSystem.SI);
		assertEquals(1001, uc.giga());
		assertEquals(345, uc.mega());
		assertEquals(567, uc.kilo());
		assertEquals(789, uc.base());
		
		uc = new  UnitComponent(123_345_567_789L, UnitSystem.IEC);
		assertEquals(114, uc.giga());
		assertEquals(895, uc.mega());
		assertEquals(512, uc.kilo());
		assertEquals(45, uc.base());

		uc = new  UnitComponent(0x100_0000_000CL, UnitSystem.IEC);
		assertEquals(1024, uc.giga());
		assertEquals(0, uc.mega());
		assertEquals(0, uc.kilo());
		assertEquals(12, uc.base());
		
		// Highest component (Gibi) can exceed 1024
		uc = new  UnitComponent(0x10_0400_0000CL, UnitSystem.IEC);
		assertEquals(1025, uc.giga());
		assertEquals(0, uc.mega());
		assertEquals(0, uc.kilo());
		assertEquals(12, uc.base());
	}
}
