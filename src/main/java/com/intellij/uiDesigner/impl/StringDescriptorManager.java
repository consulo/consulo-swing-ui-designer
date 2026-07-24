/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.impl;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.module.Module;
import consulo.module.content.layer.event.ModuleRootAdapter;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SoftReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author yole
 */
@Singleton
@ServiceAPI(ComponentScope.MODULE)
@ServiceImpl
public class StringDescriptorManager
{
	public static StringDescriptorManager getInstance(consulo.module.Module module)
	{
		return ServiceManager.getService(module, StringDescriptorManager.class);
	}

	private Module myModule;
	private final Map<Pair<Locale, String>, SoftReference<PropertiesFile>> myPropertiesFileCache = new HashMap<>();

	@Inject
	public StringDescriptorManager(consulo.module.Module module)
	{
		myModule = module;
		module.getMessageBus().connect().subscribe(ModuleRootListener.class, new ModuleRootAdapter()
		{
			public void rootsChanged(ModuleRootEvent event)
			{
				synchronized(myPropertiesFileCache)
				{
					myPropertiesFileCache.clear();
				}
			}
		});
	}

	@Nullable
	public String resolve(@Nonnull RadComponent component, @Nullable StringDescriptor descriptor)
	{
		RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
		Locale locale = (root != null) ? root.getStringDescriptorLocale() : null;
		return resolve(descriptor, locale);
	}

	@Nullable
	public String resolve(@Nullable StringDescriptor descriptor, @Nullable Locale locale)
	{
		if(descriptor == null)
		{
			return null;
		}

		if(descriptor.getValue() != null)
		{
			return descriptor.getValue();
		}

		IProperty prop = resolveToProperty(descriptor, locale);
		if(prop != null)
		{
			String value = prop.getUnescapedValue();
			if(value != null)
			{
				return value;
			}
		}
		// We have to return surrogate string in case if propFile name is invalid or bundle doesn't have specified key
		return "[" + descriptor.getKey() + " / " + descriptor.getBundleName() + "]";
	}

	public IProperty resolveToProperty(@Nonnull StringDescriptor descriptor, @Nullable Locale locale)
	{
		String propFileName = descriptor.getDottedBundleName();
		Pair<Locale, String> cacheKey = new Pair<>(locale, propFileName);
		SoftReference<PropertiesFile> propertiesFileRef;
		synchronized(myPropertiesFileCache)
		{
			propertiesFileRef = myPropertiesFileCache.get(cacheKey);
		}
		PropertiesFile propertiesFile = (propertiesFileRef == null) ? null : propertiesFileRef.get();
		if(propertiesFile == null || !propertiesFile.getContainingFile().isValid())
		{
			propertiesFile = PropertiesUtil.getPropertiesFile(propFileName, myModule, locale);
			synchronized(myPropertiesFileCache)
			{
				myPropertiesFileCache.put(cacheKey, new SoftReference<>(propertiesFile));
			}
		}

		if(propertiesFile != null)
		{
			IProperty propertyByKey = propertiesFile.findPropertyByKey(descriptor.getKey());
			if(propertyByKey != null)
			{
				return propertyByKey;
			}
		}
		return null;
	}
}
