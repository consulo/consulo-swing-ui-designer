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
package com.intellij.uiDesigner.impl.quickFixes;

import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.keymap.util.KeymapUtil;
import consulo.ui.ex.awt.ClickListener;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class LightBulbComponentImpl extends JComponent{
  private final QuickFixManager myManager;
  private final Image myIcon;

  public LightBulbComponentImpl(@Nonnull QuickFixManager manager, @Nonnull Image icon) {
    myManager = manager;
    myIcon = icon;

    setPreferredSize(new Dimension(icon.getWidth(), icon.getHeight()));
    String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      setToolTipText(UIDesignerBundle.message("tooltip.press.accelerator", acceleratorsText));
    }

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        myManager.showIntentionPopup();
        return true;
      }
    }.installOn(this);
  }

  protected void paintComponent(Graphics g) {
    TargetAWT.to(myIcon).paintIcon(this, g, 0, 0);
  }
}
