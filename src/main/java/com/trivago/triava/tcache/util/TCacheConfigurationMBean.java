package com.trivago.triava.tcache.util;

import com.trivago.triava.tcache.eviction.TCacheJSR107;

public class TCacheConfigurationMBean extends TCacheMBean
{
	static final TCacheConfigurationMBean inst = new TCacheConfigurationMBean();
	
	public static TCacheConfigurationMBean instance()
	{
		return inst;
	}
	
	@Override
	public Object getMBean(TCacheJSR107<?, ?> jsr107cache)
	{
		return jsr107cache.getCacheConfigMBean();
	}

	@Override
	public String objectNameType()
	{
		return "Configuration";
	}

}
