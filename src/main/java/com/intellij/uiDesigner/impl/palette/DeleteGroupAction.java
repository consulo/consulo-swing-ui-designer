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
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class DeleteGroupAction extends AnAction implements AnActionWithSyncUpdate {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        GroupItem groupToBeRemoved = e.getData(GroupItem.DATA_KEY);
        if (groupToBeRemoved == null || project == null) {
            return;
        }

        if (!Palette.isRemovable(groupToBeRemoved)) {
            Messages.showInfoMessage(
                project,
                UIDesignerLocalize.errorCannotRemoveDefaultGroup().get(),
                CommonLocalize.titleError().get()
            );
            return;
        }

        Palette palette = Palette.getInstance(project);
        List<GroupItem> groups = new ArrayList<>(palette.getGroups());
        groups.remove(groupToBeRemoved);
        palette.setGroups(groups);
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
        ComponentItem selectedItem = e.getData(ComponentItem.DATA_KEY);
        e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly() && selectedItem == null);
    }
}
