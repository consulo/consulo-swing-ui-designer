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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.uiDesigner.lw.LwXmlReader;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntEnumEditor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.ide.ServiceManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import jakarta.inject.Singleton;
import org.jdom.Element;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@Singleton
@State(name = "gui-designer-properties", storages = @Storage(value = "other.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public final class Properties implements PersistentStateComponent<Element>
{
	private final HashMap<String, String> myClass2InplaceProperty;
	private final HashMap<String, HashSet<String>> myClass2ExpertProperties;
	private final Map<String, Map<String, IntEnumEditor.Pair[]>> myClass2EnumProperties;
	private final Map<String, Set<String>> myClass2DeprecatedProperties;

	public static Properties getInstance()
	{
		return ServiceManager.getService(Properties.class);
	}

	public Properties()
	{
		myClass2InplaceProperty = new HashMap<>();
		myClass2ExpertProperties = new HashMap<>();
		myClass2EnumProperties = new HashMap<>();
		myClass2DeprecatedProperties = new HashMap<>();
	}

	/**
	 * @return it is possible that properties do not exist in class; returned values are ones specified in config. Never null
	 */
	public boolean isExpertProperty(Module module, @Nonnull Class aClass, String propertyName)
	{
		for(Class c = aClass; c != null; c = c.getSuperclass())
		{
			HashSet<String> properties = myClass2ExpertProperties.get(c.getName());
			if(properties != null && properties.contains(propertyName))
			{
				return true;
			}
		}
		return isPropertyDeprecated(module, aClass, propertyName);
	}

	public boolean isPropertyDeprecated(Module module, Class aClass, String propertyName)
	{
		// TODO[yole]: correct module-dependent caching
		Set<String> deprecated = myClass2DeprecatedProperties.get(aClass.getName());
		if(deprecated == null)
		{
			deprecated = new HashSet<>();
			PsiClass componentClass = JavaPsiFacade.getInstance(module.getProject()).findClass(aClass.getName(), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true));
			if(componentClass != null)
			{
				PsiMethod[] methods = componentClass.getAllMethods();
				for(PsiMethod method : methods)
				{
					if(method.isDeprecated() && PropertyUtil.isSimplePropertySetter(method))
					{
						deprecated.add(PropertyUtil.getPropertyNameBySetter(method));
					}
				}
			}
			myClass2DeprecatedProperties.put(aClass.getName(), deprecated);
		}

		return deprecated.contains(propertyName);
	}

	/**
	 * @return it is possible that property does not exist in class; returned value is one specified in config
	 */
	@Nullable
	public String getInplaceProperty(Class aClass)
	{
		for(Class c = aClass; c != null; c = c.getSuperclass())
		{
			String property = myClass2InplaceProperty.get(c.getName());
			if(property != null)
			{
				return property;
			}
		}
		return null;
	}

	@Nullable
	public IntEnumEditor.Pair[] getEnumPairs(Class aClass, String name)
	{
		for(Class c = aClass; c != null; c = c.getSuperclass())
		{
			Map<String, IntEnumEditor.Pair[]> map = myClass2EnumProperties.get(c.getName());
			if(map != null)
			{
				return map.get(name);
			}
		}
		return null;
	}

	@Override
	public void loadState(@Nonnull Element element)
	{
		for(Element classElement : element.getChildren("class"))
		{
			String className = LwXmlReader.getRequiredString(classElement, "name");

			// Read "expert" properties
			Element expertPropertiesElement = classElement.getChild("expert-properties");
			if(expertPropertiesElement != null)
			{
				HashSet<String> expertProperties = new HashSet<>();

				for(Object o : expertPropertiesElement.getChildren("property"))
				{
					Element e = (Element) o;
					String name = LwXmlReader.getRequiredString(e, "name");
					expertProperties.add(name);
				}

				myClass2ExpertProperties.put(className, expertProperties);
			}

			// Read "inplace" property. This property is optional
			Element inplacePropertyElement = classElement.getChild("inplace-property");
			if(inplacePropertyElement != null)
			{
				myClass2InplaceProperty.put(className, LwXmlReader.getRequiredString(inplacePropertyElement, "name"));
			}

			Element enumPropertyElement = classElement.getChild("enum-properties");
			if(enumPropertyElement != null)
			{
				loadEnumProperties(className, enumPropertyElement);
			}
		}
	}

	@SuppressWarnings({"HardCodedStringLiteral"})
	private void loadEnumProperties(String className, Element enumPropertyElement)
	{
		Map<String, IntEnumEditor.Pair[]> map = new HashMap<>();
		for(Object o : enumPropertyElement.getChildren("property"))
		{
			Element e = (Element) o;
			String name = LwXmlReader.getRequiredString(e, "name");
			List list = e.getChildren("constant");
			IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[list.size()];
			for(int i = 0; i < list.size(); i++)
			{
				Element constant = (Element) list.get(i);
				int value = LwXmlReader.getRequiredInt(constant, "value");
				String message = constant.getAttributeValue("message");
				String text = (message != null) ? UIDesignerBundle.message(message) : LwXmlReader.getRequiredString(constant, "name");
				pairs[i] = new IntEnumEditor.Pair(value, text);
			}
			map.put(name, pairs);
		}
		if(map.size() > 0)
		{
			myClass2EnumProperties.put(className, map);
		}
	}

	@Nullable
	@Override
	public Element getState()
	{
		return null;
	}
}
