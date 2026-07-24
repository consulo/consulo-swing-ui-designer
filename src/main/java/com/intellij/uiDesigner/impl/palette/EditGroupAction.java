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

import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author yole
 */
public class EditGroupAction extends AnAction implements AnActionWithSyncUpdate {
    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        GroupItem groupToBeEdited = e.getData(GroupItem.DATA_KEY);
        if (groupToBeEdited == null || project == null) {
            return;
        }

        // Ask group name
        String groupName = Messages.showInputDialog(
            project,
            UIDesignerLocalize.editEnterGroupName().get(),
            UIDesignerLocalize.titleEditGroup().get(),
            UIUtil.getQuestionIcon(),
            groupToBeEdited.getName(),
            null
        );
        if (groupName == null || groupName.equals(groupToBeEdited.getName())) {
            return;
        }

        Palette palette = Palette.getInstance(project);
        List<GroupItem> groups = palette.getGroups();
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (groupName.equals(groups.get(i).getName())) {
                Messages.showErrorDialog(project, UIDesignerLocalize.errorGroupNameUnique().get(), CommonLocalize.titleError().get());
                return;
            }
        }

        groupToBeEdited.setName(groupName);
        palette.fireGroupsChanged();
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
        e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly());
    }
}
