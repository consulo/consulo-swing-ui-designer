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
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.Presentation;

import java.util.Stack;

/**
 * For each component selects all non selected siblings (if any). If
 * all component's siblings are already selected then selects component's
 * parent (if any).
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ExpandSelectionAction extends AnAction implements AnActionWithSyncUpdate {
    @RequiredUIAccess
    @Override
    public void actionPerformed(final AnActionEvent e) {
        final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
        assert editor != null;
        final SelectionState selectionState = editor.getSelectionState();
        selectionState.setInsideChange(true);

        ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
        if (builder != null) {
            builder.beginUpdateSelection();
        }

        final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();

        try {
            final ComponentPtr[] ptrs = history.peek();
            for (int i = ptrs.length - 1; i >= 0; i--) {
                // Skip invalid components
                final ComponentPtr ptr = ptrs[i];
                ptr.validate();
                if (!ptr.isValid()) {
                    continue;
                }

                // Extend selection
                final RadComponent component = ptr.getComponent();
                final RadContainer parent = component.getParent();
                if (parent == null) { // skip components without parents
                    continue;
                }
                boolean shouldSelectParent = true;
                for (int j = parent.getComponentCount() - 1; j >= 0; j--) {
                    final RadComponent sibling = parent.getComponent(j);
                    if (!sibling.isSelected()) {
                        shouldSelectParent = false;
                        sibling.setSelected(true);
                    }
                }
                if (shouldSelectParent) {
                    parent.setSelected(true);
                }
            }

            // Store new selection
            history.push(SelectionState.getSelection(editor));
        }
        finally {
            if (builder != null) {
                builder.endUpdateSelection();
            }
            selectionState.setInsideChange(false);
        }
    }

    @Override
    public void update(final AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());

        if (editor == null) {
            presentation.setEnabled(false);
            return;
        }

        final SelectionState selectionState = editor.getSelectionState();
        selectionState.setInsideChange(true);
        final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();

        presentation.setEnabled(!history.isEmpty());
    }
}
