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

import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author yole
 */
public class ImageFileFilter implements Predicate<PsiFile>
{
	private final Set<String> myExtensions;
	private GlobalSearchScope myModuleScope;

	public ImageFileFilter(@Nullable Module module)
	{
		String[] formatNames = ImageIO.getReaderFormatNames();
		for(int i = 0; i < formatNames.length; i++)
		{
			formatNames[i] = formatNames[i].toLowerCase();
		}
		myExtensions = new HashSet<>(Arrays.asList(formatNames));
		if(module != null)
		{
			myModuleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true);
		}
	}

	public boolean test(PsiFile file)
	{
		VirtualFile virtualFile = file.getVirtualFile();
		if(virtualFile != null)
		{
			String extension = virtualFile.getExtension();
			return extension != null &&
					myExtensions.contains(extension.toLowerCase()) &&
					(myModuleScope == null || myModuleScope.contains(virtualFile));
		}
		return false;
	}
}
