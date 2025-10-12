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

package com.intellij.uiDesigner.impl.inspections;

import com.intellij.uiDesigner.impl.actions.SurroundAction;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IComponent;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author yole
 */
@ExtensionImpl
public class NoScrollPaneInspection extends BaseFormInspection {
    public NoScrollPaneInspection() {
        super("NoScrollPane");
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return UIDesignerLocalize.inspectionNoScrollPane();
    }

    protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
        if (FormInspectionUtil.isComponentClass(module, component, Scrollable.class) &&
            !FormInspectionUtil.isComponentClass(module, component, JTextField.class) &&
            !FormInspectionUtil.isComponentClass(module, component.getParentContainer(), JScrollPane.class)) {
            collector.addError(getID(), component, null, UIDesignerLocalize.inspectionNoScrollPane().get(), MyQuickFix::new);

        }
    }

    private static class MyQuickFix extends QuickFix {
        public MyQuickFix(final GuiEditor editor, RadComponent component) {
            super(editor, UIDesignerLocalize.inspectionNoScrollPaneQuickfix().get(), component);
        }

        public void run() {
            String scrollPane = JScrollPane.class.getName();
            ComponentItem item = Palette.getInstance(myEditor.getProject()).getItem(scrollPane);

            SurroundAction action = new SurroundAction(item == null ? JBScrollPane.class.getName() : scrollPane);

            ArrayList<RadComponent> targetList = new ArrayList<RadComponent>(Collections.singletonList(myComponent));
            action.actionPerformed(myEditor, targetList, null);
        }
    }
}
