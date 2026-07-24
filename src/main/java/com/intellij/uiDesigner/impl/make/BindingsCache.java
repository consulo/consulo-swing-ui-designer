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

/*
 * @author: Eugene Zhuravlev
 * Date: Jul 4, 2003
 * Time: 7:39:27 PM
 */
package com.intellij.uiDesigner.impl.make;

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.compiler.CompilerPaths;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import java.io.*;

final class BindingsCache
{
	private static final Logger LOG = Logger.getInstance(BindingsCache.class);
	private static final String BINDINGS_FILE_NAME = "formbinding.dat";
	private BindingsStateCache<MyState> myCache;

	public BindingsCache(Project project)
	{
		File cacheStoreDirectory = CompilerPaths.getCacheStoreDirectory(project);
		try
		{
			if(cacheStoreDirectory != null)
			{
				FileUtil.createParentDirs(cacheStoreDirectory);
				myCache = createCache(cacheStoreDirectory);
			}
			else
			{
				myCache = null;
			}
		}
		catch(IOException e)
		{
			LOG.info(e);
			for(File file : cacheStoreDirectory.listFiles())
			{
				if(file.getName().startsWith(BINDINGS_FILE_NAME))
				{
					FileUtil.delete(file);
				}
			}
			try
			{
				myCache = createCache(cacheStoreDirectory);
			}
			catch(IOException e1)
			{
				LOG.info(e1);
				myCache = null;
			}
		}
	}

	private static BindingsStateCache<MyState> createCache(final File cacheStoreDirectory) throws IOException
	{
		return new BindingsStateCache<>(new File(cacheStoreDirectory, BINDINGS_FILE_NAME))
		{
			@Override
            public MyState read(DataInput stream) throws IOException
			{
				return new MyState(stream.readLong(), stream.readUTF());
			}

			@Override
            public void write(MyState myState, DataOutput out) throws IOException
			{
				out.writeLong(myState.getFormTimeStamp());
				out.writeUTF(myState.getClassName());
			}
		};
	}

	public String getBoundClassName(VirtualFile formFile) throws Exception
	{
		File file = VirtualFileUtil.virtualToIoFile(formFile);
		String classToBind = getSavedBinding(file);
		if(classToBind == null)
		{
			Document doc = FileDocumentManager.getInstance().getDocument(formFile);
			LwRootContainer rootContainer = Utils.getRootContainer(doc.getText(), null);
			classToBind = rootContainer.getClassToBind();
		}
		if(classToBind != null)
		{
			updateCache(file, classToBind);
		}
		return classToBind;
	}

	private String getSavedBinding(File formFile)
	{
		if(myCache != null)
		{
			try
			{
				MyState state = myCache.getState(formFile);
				if(state != null)
				{
					if(formFile.lastModified() == state.getFormTimeStamp())
					{
						return state.getClassName();
					}
				}
			}
			catch(IOException e)
			{
				myCache.wipe();
			}
		}
		return null;
	}

	private void updateCache(File formFile, String classToBind)
	{
		if(myCache != null)
		{
			MyState state = new MyState(formFile.lastModified(), classToBind);
			try
			{
				myCache.update(formFile, state);
			}
			catch(IOException e)
			{
				LOG.info(e);
				myCache.wipe();
				try
				{
					myCache.update(formFile, state);
				}
				catch(IOException ignored)
				{
				}
			}
		}
	}

	public void close()
	{
		try
		{
			myCache.close();
		}
		catch(IOException e)
		{
			LOG.info(e);
		}
	}

	private static final class MyState implements Serializable
	{
		private final long myFormTimeStamp;
		private final String myClassName;

		public MyState(long formTimeStamp, String className)
		{
			myFormTimeStamp = formTimeStamp;
			myClassName = className;
		}

		public long getFormTimeStamp()
		{
			return myFormTimeStamp;
		}

		public String getClassName()
		{
			return myClassName;
		}
	}
}
