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

import consulo.application.dumb.DumbAware;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.undoRedo.CommandProcessor;
import consulo.util.concurrent.coroutine.Coroutine;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
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

  public final void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      if (myModifying) {
        if (!editor.ensureEditable()) return;
      }
      Runnable runnable = new Runnable() {
        public void run() {
          actionPerformed(editor, selection, e);
          if (myModifying) {
            editor.refreshAndSave(true);
          }
        }
      };
      if (getCommandName() != null) {
        CommandProcessor.getInstance().executeCommand(editor.getProject(), runnable, getCommandName(), null);
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
        final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
        update(editor, selection, e);
      }
      return input;
    }));
  }

  @RequiredUIAccess
  protected void update(@Nonnull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
  }

  @Nullable
  protected String getCommandName() {
    return null;
  }
}
