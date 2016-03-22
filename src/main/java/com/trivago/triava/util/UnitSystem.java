package com.trivago.triava.util;


/**
 * Defines a Unit system that contains {@link UnitPrefix}es for kilo, mega ang giga. Unit systems are used by {@link UnitTools}
 * and {@link UnitComponent} to split a value into components and format values to readable output like "23.5kB".  
 * Own systems can be defined, but most use cases will be covered by the 3 predefined systems:
 * <ul>
 * <li> {@link #SI} uses Si units and names (k, kilo), using 1000 as a base.</li>
 * <li> {@link #IEC}60027-2 uses "base 2" units and names (Ki, kibi), using 1024 as a base.</li>
 * <li> {@link #JEDEC} uses JEDEC units and names (similar to Si, except JEDEC uses "K" for Kilo), using 1024 as a base. 
 * JEDEC usage is discouraged for normal usage, except where it is a requirement like in the microelectronics industry</li>
 * </ul>
 * 
 * 
 * 
 * @author cesken
 *
 */
public class UnitSystem
{
	/**
	 * Si units (k) and names (kilo), using 1000 as a base
	 */
	public static final UnitSystem SI;
	/**
	 * IEC base 2 units (Ki) and names (kibi), using 1024 as a base
	 */
	public static final UnitSystem IEC;
	/**
	 * JEDEC units (K) and names (kilo), using 1024 as a base. JEDEC should only used when mandatory, as
	 * {@link #SI} and {@link #IEC} are more commonplace.
	 */
	public static final UnitSystem JEDEC;


	private final UnitPrefix base;
	private final UnitPrefix kilo;
	private final UnitPrefix mega;
	private final UnitPrefix giga;

	static
	{
		int SIBASE = 1000;		
		UnitPrefix SI_BASE = new UnitPrefix("", "", 1);
		UnitPrefix SI_KILO = new UnitPrefix("kilo", "k", SIBASE);
		UnitPrefix SI_MEGA = new UnitPrefix("mega", "M", SIBASE*SIBASE);
		UnitPrefix SI_GIGA = new UnitPrefix("giga", "G", SIBASE*SIBASE*SIBASE);
		SI = new UnitSystem(SI_BASE, SI_KILO, SI_MEGA, SI_GIGA);

		int IECBASE = 1024;
		UnitPrefix IEC_BASE = new UnitPrefix("", "", 1);
		UnitPrefix IEC_KILO = new UnitPrefix("kibi", "Ki", IECBASE);
		UnitPrefix IEC_MEGA = new UnitPrefix("mebi", "Mi", IECBASE*IECBASE);
		UnitPrefix IEC_GIGA = new UnitPrefix("gibi", "Gi", IECBASE*IECBASE*IECBASE);		
		IEC = new UnitSystem(IEC_BASE, IEC_KILO, IEC_MEGA, IEC_GIGA);
		
		int JEDECBASE = 1024;		
		UnitPrefix JEDEC_BASE = new UnitPrefix("", "", 1);
		UnitPrefix JEDEC_KILO = new UnitPrefix("kilo", "K", JEDECBASE);
		UnitPrefix JEDEC_MEGA = new UnitPrefix("mega", "M", JEDECBASE*JEDECBASE);
		UnitPrefix JEDEC_GIGA = new UnitPrefix("giga", "G", JEDECBASE*JEDECBASE*JEDECBASE);
		JEDEC = new UnitSystem(JEDEC_BASE, JEDEC_KILO, JEDEC_MEGA, JEDEC_GIGA);

	}
	
	
	UnitSystem(UnitPrefix base, UnitPrefix kilo, UnitPrefix mega, UnitPrefix giga)
	{
		this.base = base;
		this.kilo = kilo;
		this.mega = mega;
		this.giga = giga;
	}


	public UnitPrefix basePrefix()
	{
		return base;
	}


	public UnitPrefix kiloPrefix()
	{
		return kilo;
	}


	public UnitPrefix megaPrefix()
	{
		return mega;
	}


	public UnitPrefix gigaPrefix()
	{
		return giga;
	}
}

