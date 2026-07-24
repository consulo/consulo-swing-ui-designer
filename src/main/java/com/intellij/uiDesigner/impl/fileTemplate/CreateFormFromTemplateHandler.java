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
package com.intellij.uiDesigner.impl.fileTemplate;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.CreateFromTemplateHandler;
import consulo.fileTemplate.FileTemplate;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.PsiDirectory;
import consulo.virtualFileSystem.fileType.FileType;

/**
 * @author yole
 */
@ExtensionImpl
public class CreateFormFromTemplateHandler implements CreateFromTemplateHandler
{
	public boolean handlesTemplate(FileTemplate template)
	{
		FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
		return fileType.equals(GuiFormFileType.INSTANCE);
	}

	public boolean canCreate(PsiDirectory[] dirs)
	{
		for(PsiDirectory dir : dirs)
		{
			if(JavaDirectoryService.getInstance().getPackage(dir) != null)
			{
				return true;
			}
		}
		return false;
	}
}
