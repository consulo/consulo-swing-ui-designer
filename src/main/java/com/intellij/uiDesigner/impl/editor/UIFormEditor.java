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

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.FormHighlightingPass;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorLocation;
import consulo.fileEditor.FileEditorState;
import consulo.fileEditor.FileEditorStateLevel;
import consulo.fileEditor.highlight.BackgroundEditorHighlighter;
import consulo.fileEditor.highlight.HighlightingPass;
import consulo.fileEditor.structureView.StructureViewBuilder;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kava.beans.PropertyChangeListener;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class UIFormEditor extends UserDataHolderBase implements /*Navigatable*/FileEditor {
    private final VirtualFile myFile;
    private final GuiEditor myEditor;
    private UIFormEditor.MyBackgroundEditorHighlighter myBackgroundEditorHighlighter;

    public UIFormEditor(@Nonnull final Project project, @Nonnull final VirtualFile file) {
        final VirtualFile vf = file instanceof LightVirtualFile ? ((LightVirtualFile) file).getOriginalFile() : file;
        final Module module = ModuleUtilCore.findModuleForFile(vf, project);
        if (module == null) {
            throw new IllegalArgumentException("No module for file " + file + " in project " + project);
        }
        myFile = file;
        myEditor = new GuiEditor(this, project, module, file);
    }

    @Override
    @Nonnull
    public JComponent getComponent() {
        return myEditor;
    }

    @Override
    public void dispose() {
        myEditor.dispose();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myEditor.getPreferredFocusedComponent();
    }

    @Override
    @Nonnull
    public String getName() {
        return UIDesignerBundle.message("title.gui.designer");
    }

    public GuiEditor getEditor() {
        return myEditor;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        //TODO[anton,vova] fire when changed
        return FileDocumentManager.getInstance().getDocument(myFile) != null && myFile.getFileType() == GuiFormFileType.INSTANCE;
    }

    @Override
    public void selectNotify() {
    }

    @Override
    public void deselectNotify() {
    }

    @Override
    public void addPropertyChangeListener(@Nonnull final PropertyChangeListener listener) {
        //TODO[anton,vova]
    }

    @Override
    public void removePropertyChangeListener(@Nonnull final PropertyChangeListener listener) {
        //TODO[anton,vova]
    }

    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        if (myBackgroundEditorHighlighter == null) {
            myBackgroundEditorHighlighter = new MyBackgroundEditorHighlighter(myEditor);
        }
        return myBackgroundEditorHighlighter;
    }

    @Override
    public FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    @Nonnull
    public FileEditorState getState(@Nonnull final FileEditorStateLevel ignored) {
        final Document document = FileDocumentManager.getInstance().getCachedDocument(myFile);
        long modificationStamp = document != null ? document.getModificationStamp() : myFile.getModificationStamp();
        final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
        final String[] ids = new String[selection.size()];
        for (int i = ids.length - 1; i >= 0; i--) {
            ids[i] = selection.get(i).getId();
        }
        return new MyEditorState(modificationStamp, ids);
    }

    @Override
    public void setState(@Nonnull final FileEditorState state) {
        FormEditingUtil.clearSelection(myEditor.getRootContainer());
        final String[] ids = ((MyEditorState) state).getSelectedComponentIds();
        for (final String id : ids) {
            final RadComponent component = (RadComponent) FormEditingUtil.findComponent(myEditor.getRootContainer(), id);
            if (component != null) {
                component.setSelected(true);
            }
        }
    }

    public void selectComponent(@Nonnull final String binding) {
        final RadComponent component = (RadComponent) FormEditingUtil.findComponentWithBinding(myEditor.getRootContainer(), binding);
        if (component != null) {
            FormEditingUtil.selectSingleComponent(getEditor(), component);
        }
    }

    public void selectComponentById(@Nonnull final String id) {
        final RadComponent component = (RadComponent) FormEditingUtil.findComponent(myEditor.getRootContainer(), id);
        if (component != null) {
            FormEditingUtil.selectSingleComponent(getEditor(), component);
        }
    }

    @Override
    public StructureViewBuilder getStructureViewBuilder() {
        return null;
    }

    @Nullable
    @Override
    public VirtualFile getFile() {
        return myFile;
    }

  /*
  public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
    if (navigatable instanceof ComponentNavigatable) {
      return true;
    }
    return (navigatable instanceof OpenFileDescriptor) && (((OpenFileDescriptor)navigatable).getOffset() >= 0 || (
      ((OpenFileDescriptor)navigatable).getLine() != -1 && ((OpenFileDescriptor)navigatable).getColumn() != -1));
  }

  public void navigateTo(@NotNull final Navigatable navigatable) {
    if (navigatable instanceof ComponentNavigatable) {
      String componentId = ((ComponentNavigatable))
    }
  }
  */

    private class MyBackgroundEditorHighlighter implements BackgroundEditorHighlighter {
        private final HighlightingPass[] myPasses;

        public MyBackgroundEditorHighlighter(final GuiEditor editor) {
            myPasses = new HighlightingPass[]{new FormHighlightingPass(editor)};
        }

        @Override
        @Nonnull
        public HighlightingPass[] createPassesForEditor() {
            PsiDocumentManager.getInstance(myEditor.getProject()).commitAllDocuments();
            return myPasses;
        }
    }
}
