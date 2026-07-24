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

import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.GroupItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.popup.*;
import consulo.ui.image.Image;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author yole
 */
class PaletteListPopupStep implements ListPopupStep<ComponentItem>, SpeedSearchFilter<ComponentItem> {
  private final List<ComponentItem> myItems = new ArrayList<>();
  private final ComponentItem myInitialSelection;
  private final Predicate<ComponentItem> myRunnable;
  private final LocalizeValue myTitle;
  private final Project myProject;

  PaletteListPopupStep(GuiEditor editor, ComponentItem initialSelection, Predicate<ComponentItem> runnable, LocalizeValue title) {
    myInitialSelection = initialSelection;
    myRunnable = runnable;
    myProject = editor.getProject();
    Palette palette = Palette.getInstance(editor.getProject());
    for(GroupItem group: palette.getToolWindowGroups()) {
      Collections.addAll(myItems, group.getItems());
    }
    myTitle = title;
  }

  @Nonnull
  @Override
  public List<ComponentItem> getValues() {
    return myItems;
  }

  @Override
  public boolean isSelectable(final ComponentItem value) {
    return true;
  }

  @Override
  public Image getIconFor(final ComponentItem aValue) {
    return aValue.getSmallIcon();
  }

  @Nonnull
  @Override
  public String getTextFor(final ComponentItem value) {
    if (value.isAnyComponent()) {
      return UIDesignerLocalize.paletteNonPaletteComponent().get();
    }
    return value.getClassShortName();
  }

  @Override
  public ListSeparator getSeparatorAbove(final ComponentItem value) {
    return null;
  }

  @Override
  public int getDefaultOptionIndex() {
    if (myInitialSelection != null) {
      int index = myItems.indexOf(myInitialSelection);
      if (index >= 0) {
        return index;
      }
    }
    return 0;
  }

  @Override
  public String getTitle() {
    return myTitle.get();
  }

  @Override
  public PopupStep onChosen(final ComponentItem selectedValue, final boolean finalChoice) {
    myRunnable.test(selectedValue);
    return PopupStep.FINAL_CHOICE;
  }

  @Override
  public Runnable getFinalRunnable() {
    return null;
  }

  @Override
  public boolean hasSubstep(final ComponentItem selectedValue) {
    return false;
  }

  @Override
  public void canceled() {
  }

  @Override
  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  @Override
  public MnemonicNavigationFilter<ComponentItem> getMnemonicNavigationFilter() {
    return null;
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public boolean isAutoSelectionEnabled() {
    return false;
  }

  @Override
  public SpeedSearchFilter<ComponentItem> getSpeedSearchFilter() {
    return this;
  }

  @Override
  public boolean canBeHidden(final ComponentItem value) {
    return true;
  }

  @Override
  public String getIndexedString(final ComponentItem value) {
    if (value.isAnyComponent()) {
      return "";
    }
    return value.getClassShortName();
  }

  public void hideComponentClass(final String componentClassName) {
    for(ComponentItem item: myItems) {
      if (item.getClassName().equals(componentClassName)) {
        myItems.remove(item);
        break;
      }
    }
  }

  public void hideNonAtomic() {
    for(int i=myItems.size()-1; i >= 0; i--) {
      ComponentItem item = myItems.get(i);
      if (InsertComponentProcessor.getRadComponentFactory(myProject, item.getClassName()) != null || item.getBoundForm() != null) {
        myItems.remove(i);
      }
    }
  }
}
