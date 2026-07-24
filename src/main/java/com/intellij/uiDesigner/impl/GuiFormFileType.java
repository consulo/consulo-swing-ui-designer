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

import consulo.application.AllIcons;
import consulo.ide.localize.IdeLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeWithPredefinedCharset;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class GuiFormFileType implements FileTypeWithPredefinedCharset
{
	public static final GuiFormFileType INSTANCE = new GuiFormFileType();

	public static final String DEFAULT_EXTENSION = "form";
	public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

	@Override
	@Nonnull
	public String getId()
	{
		return "GUI_DESIGNER_FORM";
	}

	@Override
	@Nonnull
	public LocalizeValue getDescription()
	{
		return IdeLocalize.filetypeDescriptionGuiDesignerForm();
	}

	@Override
	@Nonnull
	public String getDefaultExtension()
	{
		return DEFAULT_EXTENSION;
	}

	@Override
	public Image getIcon()
	{
		return AllIcons.FileTypes.UiForm;
	}

	@Override
	public boolean isBinary()
	{
		return false;
	}

	@Override
	public boolean isReadOnly()
	{
		return false;
	}

	@Override
	public String getCharset(@Nonnull VirtualFile file, byte[] content)
	{
		return StandardCharsets.UTF_8.name();
	}

	@Nonnull
	@Override
	public Pair<Charset, String> getPredefinedCharset(@Nonnull VirtualFile virtualFile)
	{
		return Pair.create(StandardCharsets.UTF_8, "Consulo GUI Designer form");
	}
}
