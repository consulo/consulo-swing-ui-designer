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
package com.intellij.uiDesigner.impl.actions;

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.undoRedo.CommandProcessor;
import consulo.util.concurrent.coroutine.Coroutine;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author yole
 */
public abstract class AbstractGuiEditorAction extends AnAction implements DumbAware, AnActionWithAsyncUpdate
{
  private final boolean myModifying;

  protected AbstractGuiEditorAction() {
    myModifying = false;
  }

  protected AbstractGuiEditorAction(final boolean modifying) {
    myModifying = modifying;
  }

  @Override
  @RequiredUIAccess
  public final void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      List<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      if (myModifying) {
        if (!editor.ensureEditable()) return;
      }
      Runnable runnable = () -> {
        actionPerformed(editor, selection, e);
        if (myModifying) {
          editor.refreshAndSave(true);
        }
      };
      if (getCommandName().isNotEmpty()) {
        CommandProcessor.getInstance().newCommand()
          .project(editor.getProject())
          .name(getCommandName())
          .run(runnable);
      }
      else {
        runnable.run();
      }
    }
  }

  protected abstract void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e);

  @Override
  public final Coroutine<?, ?> updateAsync(AnActionEvent e) {
    return Coroutine.first(UIAction.apply(input -> {
      GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
      if (editor == null) {
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
      }
      else {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
        List<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
        update(editor, selection, e);
      }
      return input;
    }));
  }

  @RequiredUIAccess
  protected void update(@Nonnull GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
  }

  protected LocalizeValue getCommandName() {
    return LocalizeValue.empty();
  }
}
