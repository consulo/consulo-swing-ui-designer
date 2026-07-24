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

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.ide.actions.SaveFileAsTemplateHandler;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.PsiFile;
import consulo.util.jdom.JDOMUtil;
import org.jdom.Attribute;
import org.jdom.Document;

import java.io.CharArrayWriter;

/**
 * @author yole
 */
@ExtensionImpl
public class SaveFormAsTemplateHandler implements SaveFileAsTemplateHandler
{
	public String getTemplateText(PsiFile file, String fileText, String nameWithoutExtension)
	{
		if(GuiFormFileType.INSTANCE.equals(file.getFileType()))
		{
			LwRootContainer rootContainer = null;
			try
			{
				rootContainer = Utils.getRootContainer(fileText, null);
			}
			catch(Exception ignored)
			{
			}
			if(rootContainer != null && rootContainer.getClassToBind() != null)
			{
				try
				{
					Document document = JDOMUtil.loadDocument(fileText);

					Attribute attribute = document.getRootElement().getAttribute(UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS);
					attribute.detach();

					CharArrayWriter writer = new CharArrayWriter();
					JDOMUtil.writeDocument(document, writer, CodeStyleSettingsManager.getSettings(file.getProject()).getLineSeparator());

					return writer.toString();
				}
				catch(Exception ignored)
				{
				}
			}
		}
		return null;
	}
}
