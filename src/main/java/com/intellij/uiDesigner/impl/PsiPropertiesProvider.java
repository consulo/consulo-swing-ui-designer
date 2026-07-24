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
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.uiDesigner.lw.*;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PsiPropertiesProvider implements PropertiesProvider
{
	private final Module myModule;
	private final HashMap<String, HashMap> myCache;

	public PsiPropertiesProvider(@Nonnull Module module)
	{
		myModule = module;
		myCache = new HashMap<>();
	}

	@Nullable
	public HashMap getLwProperties(String className)
	{
		if(myCache.containsKey(className))
		{
			return myCache.get(className);
		}

		PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
		GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule);
		PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(className, scope);
		if(aClass == null)
		{
			return null;
		}

		HashMap result = new HashMap();

		PsiMethod[] methods = aClass.getAllMethods();
		for(PsiMethod method : methods)
		{
			// it's a setter candidate.. try to find getter

			if(!PropertyUtil.isSimplePropertySetter(method))
			{
				continue;
			}
			String name = PropertyUtil.getPropertyName(method);
			if(name == null)
			{
				throw new IllegalStateException();
			}
			PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, name, false, true);
			if(getter == null)
			{
				continue;
			}

			PsiType type = getter.getReturnType();
			String propertyClassName = type.getCanonicalText();

			LwIntrospectedProperty property = CompiledClassPropertiesProvider.propertyFromClassName(propertyClassName, name);
			if(property == null)
			{
				PsiClass propClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(propertyClassName, scope);
				if(propClass == null)
				{
					continue;
				}
				if(propClass.isEnum())
				{
					String enumClassName = ClassUtil.getJVMClassName(propClass);
					ClassLoader loader = LoaderFactory.getInstance(myModule.getProject()).getLoader(myModule);
					try
					{
						property = new LwIntroEnumProperty(name, loader.loadClass(enumClassName));
					}
					catch(ClassNotFoundException e)
					{
						continue;
					}
				}
				else
				{
					PsiClass componentClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(Component.class.getName(), scope);
					PsiClass listModelClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(ListModel.class.getName(), scope);
					if(componentClass != null && InheritanceUtil.isInheritorOrSelf(propClass, componentClass, true))
					{
						property = new LwIntroComponentProperty(name, propertyClassName);
					}
					else if(componentClass != null && listModelClass != null && InheritanceUtil.isInheritorOrSelf(propClass, listModelClass, true))
					{
						property = new LwIntroListModelProperty(name, propertyClassName);
					}
					else
					{
						// type is not supported
						continue;
					}
				}
			}

			result.put(name, property);
		}

		myCache.put(className, result);
		return result;
	}
}
