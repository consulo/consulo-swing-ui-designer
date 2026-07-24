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

import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.application.CommonBundle;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.Messages;

/**
 * @author yole
 */
public class DeleteComponentAction extends AnAction implements AnActionWithSyncUpdate {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
        GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
        if (project == null || selectedItem == null || groupItem == null) {
            return;
        }

        if (!selectedItem.isRemovable()) {
            Messages.showInfoMessage(
                project,
                UIDesignerBundle.message("error.cannot.remove.default.palette"),
                CommonBundle.getErrorTitle()
            );
            return;
        }

        int rc = Messages.showYesNoDialog(project, UIDesignerBundle.message("delete.component.prompt", selectedItem.getClassShortName()),
            UIDesignerBundle.message("delete.component.title"), Messages.getQuestionIcon());
        if (rc != 0) {
            return;
        }

        Palette palette = Palette.getInstance(project);
        palette.removeItem(groupItem, selectedItem);
        palette.fireGroupsChanged();
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
        GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
        e.getPresentation().setEnabled(project != null && selectedItem != null && groupItem != null &&
            !selectedItem.isAnyComponent() && selectedItem.isRemovable());
    }
}
