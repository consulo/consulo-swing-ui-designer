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
package com.intellij.uiDesigner.impl.propertyInspector.editors;

import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import consulo.ui.ex.awt.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class BooleanEditor extends PropertyEditor<Boolean> {
    private final JCheckBox myCheckBox;
    private boolean myInsideChange;

    public BooleanEditor() {
        myCheckBox = new JCheckBox();
        myCheckBox.addActionListener(new MyActionListener());
    }

    @Override
    public void updateUI() {
        SwingUtilities.updateComponentTreeUI(myCheckBox);
    }

    @Override
    public Boolean getValue() throws Exception {
        return myCheckBox.isSelected();
    }

    @Override
    public JComponent getComponent(RadComponent ignored, Boolean value, InplaceContext inplaceContext) {
        myInsideChange = true;
        try {
            myCheckBox.setBackground(UIUtil.getTableBackground());
            myCheckBox.setSelected(value != null && value);
            return myCheckBox;
        }
        finally {
            myInsideChange = false;
        }
    }

    private final class MyActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!myInsideChange) {
                fireValueCommitted(true, false);
            }
        }
    }
}