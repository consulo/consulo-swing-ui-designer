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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.SwingProperties;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class NoLabelForInspection extends BaseFormInspection {
  public NoLabelForInspection() {
    super("NoLabelFor");
  }

  @Nonnull
  @Override public LocalizeValue getDisplayName() {
    return UIDesignerLocalize.inspectionNoLabelFor();
  }

  protected void checkComponentProperties(final Module module, final IComponent component, FormErrorCollector collector) {
    ComponentItem item = Palette.getInstance(module.getProject()).getItem(component.getComponentClassName());
    if (item != null && item.isCanAttachLabel()) {
      IComponent root = component;
      while(root.getParentContainer() != null) {
        root = root.getParentContainer();
      }
      final Ref<Boolean> found = new Ref<Boolean>(Boolean.FALSE);
      final Ref<RadComponent> candidateLabel = new Ref<RadComponent>();
      final List<RadComponent> allLabels = new ArrayList<RadComponent>();
      FormEditingUtil.iterate(root, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent c2) {
          if (FormInspectionUtil.isComponentClass(module, c2, JLabel.class)) {
            IProperty prop = FormInspectionUtil.findProperty(c2, SwingProperties.LABEL_FOR);
            if (prop != null && component.getId().equals(prop.getPropertyValue(c2))) {
              found.set(Boolean.TRUE);
              return false;
            }
            else if (component instanceof RadComponent &&
                     (prop == null || StringUtil.isEmpty((String)prop.getPropertyValue(c2)))) {
              RadComponent radComponent = (RadComponent) component;
              final RadComponent radComponent2 = ((RadComponent)c2);
              allLabels.add(radComponent2);
              if (radComponent.getParent() == radComponent2.getParent() && radComponent.getParent().getLayoutManager().isGrid()) {
                GridConstraints gc1 = radComponent.getConstraints();
                GridConstraints gc2 = radComponent2.getConstraints();
                int nextColumn = FormEditingUtil.nextCol(radComponent.getParent(), gc2.getColumn());
                int nextRow = FormEditingUtil.nextRow(radComponent.getParent(), gc2.getRow());
                if ((gc1.getRow() == gc2.getRow() && nextColumn == gc1.getColumn()) ||
                    (gc1.getColumn() == gc2.getColumn() && nextRow == gc1.getRow())) {
                  candidateLabel.set(radComponent2);
                }
              }
            }
          }
          return true;
        }
      });
      if (!found.get().booleanValue()) {
        if (!candidateLabel.isNull()) {
          allLabels.clear();
          allLabels.add(candidateLabel.get());
        }
        EditorQuickFixProvider[] quickFixProviders = new EditorQuickFixProvider[allLabels.size()];
        for (int i = 0; i < quickFixProviders.length; i++) {
          final RadComponent label = allLabels.get(i);
          quickFixProviders[i] = new EditorQuickFixProvider() {
            public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
              return new MyQuickFix(editor, component, label);
            }
          };
        }
        collector.addError(getID(), component, null, UIDesignerLocalize.inspectionNoLabelForError().get(), quickFixProviders);
      }
    }
  }

  private static class MyQuickFix extends QuickFix {
    private final RadComponent myLabel;

    public MyQuickFix(final GuiEditor editor, RadComponent component, RadComponent label) {
      super(editor, UIDesignerLocalize.inspectionNoLabelForQuickfix(label.getComponentTitle()).get(), component);
      myLabel = label;
    }

    public void run() {
      if (!myEditor.ensureEditable()) {
        return;
      }
      Runnable runnable = new Runnable() {
        public void run() {
          final Palette palette = Palette.getInstance(myEditor.getProject());
          IntrospectedProperty[] props = palette.getIntrospectedProperties(myLabel);
          boolean modified = false;
          for(IntrospectedProperty prop: props) {
            if (prop.getName().equals(SwingProperties.LABEL_FOR) && prop instanceof IntroComponentProperty) {
              IntroComponentProperty icp = (IntroComponentProperty) prop;
              icp.setValueEx(myLabel, myComponent.getId());
              modified = true;
              break;
            }
          }
          if (modified) myEditor.refreshAndSave(false);
        }
      };
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), runnable,
          UIDesignerLocalize.inspectionNoLabelForCommand().get(), null);
    }
  }
}
