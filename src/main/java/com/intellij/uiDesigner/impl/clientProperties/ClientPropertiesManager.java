/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl.clientProperties;

import com.intellij.uiDesigner.impl.LoaderFactory;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.AtomicNotNullLazyValue;
import consulo.application.util.NotNullLazyValue;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.jdom.JDOMUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

@Singleton
@State(name = "ClientPropertiesManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE), defaultStateFilePath = "defaultState/ClientPropertiesManager.xml")
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class ClientPropertiesManager implements PersistentStateComponent<Element>
{
	private static final Logger LOG = Logger.getInstance(ClientPropertiesManager.class);

	private static final String ELEMENT_PROPERTIES = "properties";
	private static final String ELEMENT_PROPERTY = "property";
	private static final String ATTRIBUTE_CLASS = "class";
	private static final String ATTRIBUTE_NAME = "name";

	public static ClientPropertiesManager getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, ClientPropertiesManager.class);
	}

	private static final NotNullLazyValue<ClientPropertiesManager> ourDefaultManager = new AtomicNotNullLazyValue<>()
	{
		@Nonnull
		@Override
		protected ClientPropertiesManager compute()
		{
			ClientPropertiesManager result = new ClientPropertiesManager();
			try
			{
				result.loadState(JDOMUtil.load(ClientPropertiesManager.class.getResourceAsStream("/defaultState/ClientPropertiesManager.xml")));
			}
			catch(Exception e)
			{
				LOG.error(e);
			}
			return result;
		}
	};

	private final Map<String, List<ClientProperty>> myPropertyMap = new TreeMap<>();

	@Inject
	public ClientPropertiesManager()
	{
	}

	private ClientPropertiesManager(Map<String, List<ClientProperty>> propertyMap)
	{
		this();
		myPropertyMap.putAll(propertyMap);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public ClientPropertiesManager clone()
	{
		return new ClientPropertiesManager(myPropertyMap);
	}

	public void saveFrom(ClientPropertiesManager manager)
	{
		myPropertyMap.clear();
		myPropertyMap.putAll(manager.myPropertyMap);
	}

	public static class ClientProperty implements Comparable
	{
		private final String myName;
		private final String myClass;

		public ClientProperty(String name, String aClass)
		{
			myName = name;
			myClass = aClass;
		}

		public String getName()
		{
			return myName;
		}

		public String getValueClass()
		{
			return myClass;
		}

		@Override
		public int compareTo(Object o)
		{
			ClientProperty prop = (ClientProperty) o;
			return myName.compareTo(prop.getName());
		}

		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}

			ClientProperty that = (ClientProperty) o;

			if(!myClass.equals(that.myClass))
			{
				return false;
			}
			if(!myName.equals(that.myName))
			{
				return false;
			}

			return true;
		}

		public int hashCode()
		{
			int result;
			result = myName.hashCode();
			result = 31 * result + myClass.hashCode();
			return result;
		}
	}

	@Override
	public void loadState(Element state)
	{
		myPropertyMap.clear();
		for(Element propertiesElement : state.getChildren(ELEMENT_PROPERTIES))
		{
			String aClass = propertiesElement.getAttributeValue(ATTRIBUTE_CLASS);
			List<ClientProperty> classProps = new ArrayList<>();
			for(Element propertyElement : propertiesElement.getChildren(ELEMENT_PROPERTY))
			{
				String propName = propertyElement.getAttributeValue(ATTRIBUTE_NAME);
				String propClass = propertyElement.getAttributeValue(ATTRIBUTE_CLASS);
				classProps.add(new ClientProperty(propName, propClass));
			}
			myPropertyMap.put(aClass, classProps);
		}
	}

	@Nullable
	@Override
	public Element getState()
	{
		if(equals(ourDefaultManager.getValue()))
		{
			return null;
		}

		Element element = new Element("state");
		for(Map.Entry<String, List<ClientProperty>> entry : myPropertyMap.entrySet())
		{
			Element propertiesElement = new Element(ELEMENT_PROPERTIES);
			propertiesElement.setAttribute(ATTRIBUTE_CLASS, entry.getKey());
			for(ClientProperty prop : entry.getValue())
			{
				Element propertyElement = new Element(ELEMENT_PROPERTY);
				propertyElement.setAttribute(ATTRIBUTE_NAME, prop.getName());
				propertyElement.setAttribute(ATTRIBUTE_CLASS, prop.getValueClass());
				propertiesElement.addContent(propertyElement);
			}
			element.addContent(propertiesElement);
		}

		return element;
	}

	public void addConfiguredProperty(Class selectedClass, ClientProperty enteredProperty)
	{
		List<ClientProperty> list = myPropertyMap.get(selectedClass.getName());
		if(list == null)
		{
			list = new ArrayList<>();
			myPropertyMap.put(selectedClass.getName(), list);
		}
		list.add(enteredProperty);
	}

	public void removeConfiguredProperty(Class selectedClass, String name)
	{
		List<ClientProperty> list = myPropertyMap.get(selectedClass.getName());
		if(list != null)
		{
			for(ClientProperty prop : list)
			{
				if(prop.getName().equals(name))
				{
					list.remove(prop);
					break;
				}
			}
		}
	}

	public List<Class> getConfiguredClasses(@Nonnull Project project)
	{
		List<Class> result = new ArrayList<>();
		for(String className : myPropertyMap.keySet())
		{
			try
			{
				result.add(Class.forName(className, true, LoaderFactory.getInstance(project).getProjectClassLoader()));
			}
			catch(ClassNotFoundException e)
			{
				// TODO: do something better than ignore?
			}
		}
		return result;
	}

	public void addClientPropertyClass(String className)
	{
		if(!myPropertyMap.containsKey(className))
		{
			myPropertyMap.put(className, new ArrayList<>());
		}
	}

	public void removeClientPropertyClass(Class selectedClass)
	{
		myPropertyMap.remove(selectedClass.getName());
	}

	public List<ClientProperty> getConfiguredProperties(Class componentClass)
	{
		List<ClientProperty> list = myPropertyMap.get(componentClass.getName());
		if(list == null)
		{
			return Collections.emptyList();
		}
		return new ArrayList<>(list);
	}

	@Nonnull
	public List<ClientProperty> getClientProperties(Class componentClass)
	{
		List<ClientProperty> result = new ArrayList<>();
		while(!componentClass.getName().equals(Object.class.getName()))
		{
			List<ClientProperty> props = myPropertyMap.get(componentClass.getName());
			if(props != null)
			{
				result.addAll(props);
			}
			componentClass = componentClass.getSuperclass();
		}
		result.sort(null);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof ClientPropertiesManager))
		{
			return false;
		}
		ClientPropertiesManager rhs = (ClientPropertiesManager) obj;
		if(rhs.myPropertyMap.size() != myPropertyMap.size())
		{
			return false;
		}
		for(Map.Entry<String, List<ClientProperty>> entry : myPropertyMap.entrySet())
		{
			List<ClientProperty> rhsList = rhs.myPropertyMap.get(entry.getKey());
			if(rhsList == null || rhsList.size() != entry.getValue().size())
			{
				return false;
			}

			for(ClientProperty prop : entry.getValue())
			{
				if(!rhsList.contains(prop))
				{
					return false;
				}
			}
		}
		return true;
	}
}