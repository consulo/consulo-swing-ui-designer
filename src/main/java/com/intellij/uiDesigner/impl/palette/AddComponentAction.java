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

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.coroutine.ActionSafeReadLock;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.concurrent.coroutine.Coroutine;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/**
 * @author yole
 */
public class AddComponentAction extends AnAction implements AnActionWithAsyncUpdate {
    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        PsiElement elementToAdd = (psiFile != null) ? findElementToAdd(psiFile) : null;
        String className = "";
        if (elementToAdd instanceof PsiClass) {
            className = ((PsiClass) elementToAdd).getQualifiedName();
            assert className != null;
        }
        else if (elementToAdd instanceof PsiFile) {
            try {
                className = Utils.getBoundClassName(elementToAdd.getText());
            }
            catch (Exception e1) {
                className = "";
            }
        }

        // Show dialog
        final ComponentItem itemToBeAdded = new ComponentItem(
            project,
            className,
            null,
            null,
            new GridConstraints(),
            new HashMap<String, StringDescriptor>(),
            true/*all user defined components are removable*/,
            false,
            false
        );
        Window parentWindow = TargetAWT.to(WindowManager.getInstance().suggestParentWindow(project));
        final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeAdded, false);
        dialog.setTitle(UIDesignerLocalize.titleAddComponent());
        dialog.showGroupChooser(groupItem);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        groupItem = dialog.getSelectedGroup();
        // If the itemToBeAdded is already in palette do nothing
        if (groupItem.containsItemClass(itemToBeAdded.getClassName())) {
            return;
        }

        assignDefaultIcon(project, itemToBeAdded);

        // add to the group

        final Palette palette = Palette.getInstance(project);
        palette.addItem(groupItem, itemToBeAdded);
        palette.fireGroupsChanged();
    }

    private static void assignDefaultIcon(final Project project, final ComponentItem itemToBeAdded) {
        Palette palette = Palette.getInstance(project);
        if (itemToBeAdded.getIconPath() == null || itemToBeAdded.getIconPath().length() == 0) {
            PsiClass aClass =
                JavaPsiFacade.getInstance(project).findClass(itemToBeAdded.getClassName().replace('$', '.'), (GlobalSearchScope) ProjectScopes.getAllScope(project));
            while (aClass != null) {
                final ComponentItem item = palette.getItem(aClass.getQualifiedName());
                if (item != null) {
                    String iconPath = item.getIconPath();
                    if (iconPath != null && iconPath.length() > 0) {
                        itemToBeAdded.setIconPath(iconPath);
                        return;
                    }
                }
                aClass = aClass.getSuperClass();
            }
        }
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        return ActionSafeReadLock.run(e, presentation -> {
            Project project = e.getData(Project.KEY);
            if (e.getData(GroupItem.DATA_KEY) != null ||
                e.getData(ComponentItem.DATA_KEY) != null) {
                e.getPresentation().setVisible(true);
                GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
                e.getPresentation().setEnabled(project != null && (groupItem == null || !groupItem.isReadOnly()));
            }
            else {
                PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
                e.getPresentation().setVisible(psiFile != null && findElementToAdd(psiFile) != null);
            }
        }).toCoroutine();
    }

    @Nullable
    @RequiredReadAction
    private static PsiElement findElementToAdd(final PsiFile psiFile) {
        if (psiFile.getFileType().equals(GuiFormFileType.INSTANCE)) {
            return psiFile;
        }
        else if (psiFile.getFileType().equals(JavaFileType.INSTANCE)) {
            final PsiClass psiClass = PsiTreeUtil.getChildOfType(psiFile, PsiClass.class);
            Project project = psiFile.getProject();
            final PsiClass componentClass =
                JavaPsiFacade.getInstance(project).findClass(JComponent.class.getName(), (GlobalSearchScope) ProjectScopes.getAllScope(project));
            if (psiClass != null && componentClass != null && psiClass.isInheritor(componentClass, true) && psiClass.getQualifiedName() != null) {
                return psiClass;
            }
        }
        return null;
    }
}
