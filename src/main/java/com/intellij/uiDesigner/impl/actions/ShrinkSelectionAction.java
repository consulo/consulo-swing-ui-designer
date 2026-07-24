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
import com.intellij.uiDesigner.impl.SelectionState;
import com.intellij.uiDesigner.impl.componentTree.ComponentPtr;
import com.intellij.uiDesigner.impl.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.Presentation;

import java.util.Stack;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShrinkSelectionAction extends AnAction implements AnActionWithSyncUpdate {
    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
        assert editor != null;
        SelectionState selectionState = editor.getSelectionState();
        selectionState.setInsideChange(true);
        ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
        builder.beginUpdateSelection();

        try {
            Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();
            history.pop();
            SelectionState.restoreSelection(editor, history.peek());
        }
        finally {
            builder.endUpdateSelection();
            selectionState.setInsideChange(false);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
        if (editor == null) {
            presentation.setEnabled(false);
            return;
        }

        Stack<ComponentPtr[]> history = editor.getSelectionState().getSelectionHistory();
        presentation.setEnabled(history.size() > 1);
    }
}
