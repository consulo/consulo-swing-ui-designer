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
package com.intellij.uiDesigner.impl.editor;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.disposer.Disposer;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorPolicy;
import consulo.fileEditor.FileEditorProvider;
import consulo.fileEditor.FileEditorState;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public final class UIFormEditorProvider implements FileEditorProvider, DumbAware
{
	private static final Logger LOG = Logger.getInstance(UIFormEditorProvider.class);

	@Override
	public boolean accept(@Nonnull Project project, @Nonnull VirtualFile file)
	{
		return file.getFileType() == GuiFormFileType.INSTANCE &&
				!GuiFormFileType.INSTANCE.isBinary() &&
				(ModuleUtilCore.findModuleForFile(file, project) != null || file instanceof LightVirtualFile);
	}

	@Override
	@Nonnull
	public FileEditor createEditor(@Nonnull Project project, @Nonnull VirtualFile file)
	{
		LOG.assertTrue(accept(project, file));
		return new UIFormEditor(project, file);
	}

	@Override
	public void disposeEditor(@Nonnull FileEditor editor)
	{
		Disposer.dispose(editor);
	}

	@Override
	@Nonnull
	public FileEditorState readState(@Nonnull Element element, @Nonnull Project project, @Nonnull VirtualFile file)
	{
		//TODO[anton,vova] implement
		return new MyEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
	}

	@Override
	public void writeState(@Nonnull FileEditorState state, @Nonnull Project project, @Nonnull Element element)
	{
		//TODO[anton,vova] implement
	}

	@Override
	@Nonnull
	public String getEditorTypeId()
	{
		return "ui-designer";
	}

	@Override
	@Nonnull
	public FileEditorPolicy getPolicy()
	{
		return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
	}
}