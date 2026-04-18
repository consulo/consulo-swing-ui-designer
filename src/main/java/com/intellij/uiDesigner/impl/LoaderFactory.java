/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.uiDesigner.core.Spacer;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.ServiceManager;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.layer.OrderEnumerator;
import consulo.module.content.layer.event.ModuleRootAdapter;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.ClassPathUtil;
import consulo.util.lang.StringUtil;
import consulo.util.nodep.classloader.UrlClassLoader;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class LoaderFactory
{
	private final Project myProject;

	private final Map<consulo.module.Module, ClassLoader> myModule2ClassLoader;
	private ClassLoader myProjectClassLoader = null;
	private final MessageBusConnection myConnection;

	public static LoaderFactory getInstance(final Project project)
	{
		return ServiceManager.getService(project, LoaderFactory.class);
	}

	@Inject
	public LoaderFactory(final Project project)
	{
		myProject = project;
		myModule2ClassLoader = ContainerUtil.createWeakMap();
		myConnection = myProject.getMessageBus().connect();
		myConnection.subscribe(ModuleRootListener.class, new ModuleRootAdapter()
		{
			public void rootsChanged(final ModuleRootEvent event)
			{
				clearClassLoaderCache();
			}
		});

		Disposer.register(project, new Disposable()
		{
			public void dispose()
			{
				myConnection.disconnect();
				myModule2ClassLoader.clear();
			}
		});
	}

	@Nonnull
	public ClassLoader getLoader(final VirtualFile formFile)
	{
		final consulo.module.Module module = ModuleUtilCore.findModuleForFile(formFile, myProject);
		if(module == null)
		{
			return getClass().getClassLoader();
		}

		return getLoader(module);
	}

	public ClassLoader getLoader(final Module module)
	{
		final ClassLoader cachedLoader = myModule2ClassLoader.get(module);
		if(cachedLoader != null)
		{
			return cachedLoader;
		}

		final String runClasspath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString();

		final ClassLoader classLoader = createClassLoader(runClasspath, module.getName());

		myModule2ClassLoader.put(module, classLoader);

		return classLoader;
	}

	@Nonnull
	public ClassLoader getProjectClassLoader()
	{
		if(myProjectClassLoader == null)
		{
			final String runClasspath = OrderEnumerator.orderEntries(myProject).withoutSdk().getPathsList().getPathsString();
			myProjectClassLoader = createClassLoader(runClasspath, "<project>");
		}
		return myProjectClassLoader;
	}

	private static ClassLoader createClassLoader(final String runClasspath, final String moduleName)
	{
		final ArrayList<URL> urls = new ArrayList<URL>();
		final VirtualFileManager manager = VirtualFileManager.getInstance();
		final StringTokenizer tokenizer = new StringTokenizer(runClasspath, File.pathSeparator);
		while(tokenizer.hasMoreTokens())
		{
			final String s = tokenizer.nextToken();
			try
			{
				VirtualFile vFile = manager.findFileByUrl(VirtualFileUtil.pathToUrl(s));

				VirtualFile archiveFile = ArchiveVfsUtil.getVirtualFileForArchive(vFile);
				if(archiveFile != null)
				{
					urls.add(new File(archiveFile.getCanonicalPath()).toURI().toURL());
				}
				else
				{
					urls.add(new File(s).toURI().toURL());
				}
			}
			catch(Exception e)
			{
				// ignore ?
			}
		}

		try
		{
			urls.add(new File(ClassPathUtil.getJarPathForClass(Spacer.class)).toURI().toURL());
		}
		catch(MalformedURLException ignored)
		{
		}

		try
		{
			urls.add(new File(ClassPathUtil.getJarPathForClass(StringUtil.class)).toURI().toURL());
		}
		catch(MalformedURLException ignored)
		{
		}

		return new DesignTimeClassLoader(urls, LoaderFactory.class.getClassLoader(), moduleName);
	}

	public void clearClassLoaderCache()
	{
		// clear classes with invalid classloader from UIManager cache
		final UIDefaults uiDefaults = UIManager.getDefaults();
		for(Iterator it = uiDefaults.keySet().iterator(); it.hasNext(); )
		{
			Object key = it.next();
			Object value = uiDefaults.get(key);
			if(value instanceof Class)
			{
				ClassLoader loader = ((Class) value).getClassLoader();
				if(loader instanceof DesignTimeClassLoader)
				{
					it.remove();
				}
			}
		}
		myModule2ClassLoader.clear();
		myProjectClassLoader = null;
	}

	private static class DesignTimeClassLoader extends UrlClassLoader
	{
		private final String myModuleName;

		public DesignTimeClassLoader(final List<URL> urls, final ClassLoader parent, final String moduleName)
		{
			super(build().urls(urls).parent(parent));
			myModuleName = moduleName;
		}

		@Override
		public String toString()
		{
			return "DesignTimeClassLoader:" + myModuleName;
		}
	}
}
