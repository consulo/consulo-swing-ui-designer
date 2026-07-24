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
package com.intellij.uiDesigner.impl.binding;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiField;
import com.intellij.uiDesigner.impl.editor.UIFormEditor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.image.Image;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author yole
 */
public class BoundIconRenderer extends GutterIconRenderer
{
	@Nonnull
	private final PsiElement myElement;
	private Image myIcon;
	private final String myQName;

	public BoundIconRenderer(@Nonnull PsiElement element)
	{
		myElement = element;
		if(myElement instanceof PsiField field)
		{
            if(field.getType() instanceof PsiClassType classType)
			{
				PsiClass componentClass = classType.resolve();
				if(componentClass != null)
				{
					String qName = componentClass.getQualifiedName();
					if(qName != null)
					{
						ComponentItem item = Palette.getInstance(myElement.getProject()).getItem(qName);
						if(item != null)
						{
							myIcon = item.getIcon();
						}
					}
				}
			}
			myQName = field.getContainingClass().getQualifiedName() + "#" + field.getName();
		}
		else
		{
			myQName = ((PsiClass) element).getQualifiedName();
		}
	}

	@Nonnull
    @Override
	public Image getIcon()
	{
		if(myIcon != null)
		{
			return myIcon;
		}
		return PlatformIconGroup.filetypesUiform();
	}

	@Override
    public boolean isNavigateAction()
	{
		return true;
	}

	@Nullable
    @Override
    public AnAction getClickAction()
	{
		return new AnAction()
		{
            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e)
			{
				List<PsiFile> formFiles = getBoundFormFiles();
				if(formFiles.size() > 0)
				{
					VirtualFile virtualFile = formFiles.get(0).getVirtualFile();
					if(virtualFile == null)
					{
						return;
					}
					Project project = myElement.getProject();
					FileEditor[] editors = FileEditorManager.getInstance(project).openFile(virtualFile, true);
					if(myElement instanceof PsiField field)
					{
						for(FileEditor editor : editors)
						{
							if(editor instanceof UIFormEditor formEditor)
							{
								formEditor.selectComponent(field.getName());
							}
						}
					}
				}
			}
		};
	}

    @Nullable
    @Override
    @RequiredReadAction
    public String getTooltipText()
	{
		List<PsiFile> formFiles = getBoundFormFiles();

		if(formFiles.size() > 0)
		{
			return composeText(formFiles);
		}
		return super.getTooltipText();
	}

	private List<PsiFile> getBoundFormFiles()
	{
		List<PsiFile> formFiles = Collections.emptyList();
		PsiClass aClass;
		if(myElement instanceof PsiField field)
		{
			aClass = field.getContainingClass();
		}
		else
		{
			aClass = (PsiClass) myElement;
		}
		if(aClass != null && aClass.getQualifiedName() != null)
		{
			formFiles = FormClassIndex.findFormsBoundToClass(aClass);
		}
		return formFiles;
	}

	@RequiredReadAction
    private static String composeText(List<PsiFile> formFiles)
	{
		StringBuilder result = new StringBuilder("<html><body>");
		result.append(UIDesignerLocalize.uiIsBoundHeader().get());
		String sep = "";
		for(PsiFile file : formFiles)
		{
			result.append(sep);
			sep = "<br>";
			result.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			result.append(file.getName());
		}
		result.append("</body></html>");
		return result.toString();
	}

	@Override
	public boolean equals(@Nullable Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		BoundIconRenderer that = (BoundIconRenderer) o;

        return myQName.equals(that.myQName)
            && Objects.equals(myIcon, that.myIcon);
    }

	@Override
	public int hashCode()
	{
        return 31 * myElement.hashCode() + Objects.hashCode(myIcon);
	}
}
