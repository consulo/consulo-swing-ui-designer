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

package com.intellij.uiDesigner.impl.make;

import com.intellij.uiDesigner.impl.editor.UIFormEditor;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author yole
 */
public class FormElementNavigatable implements Navigatable
{
	private final Project myProject;
	private final VirtualFile myVirtualFile;
	private
	@Nullable
	final String myComponentId;

	public FormElementNavigatable(Project project, VirtualFile virtualFile, @Nullable String componentId)
	{
		myProject = project;
		myVirtualFile = virtualFile;
		myComponentId = componentId;
	}

	public void navigate(boolean requestFocus)
	{
		if(!myVirtualFile.isValid())
		{
			return;
		}
		OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(myProject).builder(myVirtualFile).build();
		List<FileEditor> fileEditors = FileEditorManager.getInstance(myProject).openEditor(descriptor, requestFocus);
		if(myComponentId != null)
		{
			for(FileEditor editor : fileEditors)
			{
				if(editor instanceof UIFormEditor)
				{
					((UIFormEditor) editor).selectComponentById(myComponentId);
					break;
				}
			}
		}
	}

	public boolean canNavigate()
	{
		return myVirtualFile.isValid();
	}

	public boolean canNavigateToSource()
	{
		return myVirtualFile.isValid();
	}
}
