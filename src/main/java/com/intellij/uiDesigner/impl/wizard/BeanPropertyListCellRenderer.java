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
package com.intellij.uiDesigner.impl.wizard;

import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanPropertyListCellRenderer extends ColoredListCellRenderer<BeanProperty> {
    private final SimpleTextAttributes myAttrs1;
    private final SimpleTextAttributes myAttrs2;

    public BeanPropertyListCellRenderer() {
        myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    @Override
    protected void customizeCellRenderer(JList list, BeanProperty value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
            append(UIDesignerLocalize.propertyNotDefined(), myAttrs2);
        }
        else {
            append(value.myName, myAttrs1);
            append(" ", myAttrs1);
            append(value.myType, myAttrs2);
        }
    }
}
