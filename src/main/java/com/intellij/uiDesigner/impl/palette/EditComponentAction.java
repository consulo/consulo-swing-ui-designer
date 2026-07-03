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

package com.intellij.uiDesigner.impl.palette;

import consulo.language.editor.CommonDataKeys;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;

import java.awt.*;

/**
 * @author yole
 */
public class EditComponentAction extends AnAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(EditComponentAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
        if (project == null || selectedItem == null || selectedItem.isAnyComponent() || selectedItem.isSpacer()) {
            return;
        }

        final ComponentItem itemToBeEdited = selectedItem.clone(); /*"Cancel" should work, so we need edit copy*/
        Window parentWindow = TargetAWT.to(WindowManager.getInstance().suggestParentWindow(project));
        final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeEdited, false);
        dialog.setTitle(UIDesignerLocalize.titleEditComponent());
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        GroupItem groupItem = null;
        Palette palette = Palette.getInstance(project);
        // If the itemToBeAdded is already in palette do nothing
        for (GroupItem group : palette.getGroups()) {
            if (group.containsItemCopy(selectedItem, itemToBeEdited.getClassName())) {
                return;
            }
            if (group.containsItemClass(selectedItem.getClassName())) {
                groupItem = group;
            }
        }
        LOG.assertTrue(groupItem != null);

        palette.replaceItem(groupItem, selectedItem, itemToBeEdited);
        palette.fireGroupsChanged();
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
        GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
        e.getPresentation().setEnabled(project != null &&
            selectedItem != null &&
            groupItem != null &&
            !selectedItem.isAnyComponent() &&
            !selectedItem.isSpacer());
    }
}
