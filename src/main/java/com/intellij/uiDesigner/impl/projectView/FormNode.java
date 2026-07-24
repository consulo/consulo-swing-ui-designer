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
package com.intellij.uiDesigner.impl.projectView;

import com.intellij.java.impl.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.ide.IdeBundle;
import consulo.language.editor.util.NavigationItemFileStatus;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.project.ui.view.tree.*;
import consulo.ui.ex.tree.PresentationData;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public class FormNode extends ProjectViewNode<Form>{
  private final Collection<BasePsiNode<? extends PsiElement>> myChildren;

  public FormNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (Form)value, viewSettings, getChildren(project, (Form) value, viewSettings));
  }

  public FormNode(Project project, Form value, ViewSettings viewSettings, Collection<BasePsiNode<? extends PsiElement>> children) {
    super(project, value, viewSettings);
    myChildren = children;
  }

  @Nonnull
  public Collection<BasePsiNode<? extends PsiElement>> getChildren() {
    return myChildren;
  }

  public String getTestPresentation() {
    return "Form:" + getValue().getName();
  }

  public boolean contains(@Nonnull VirtualFile file) {
    for (AbstractTreeNode aMyChildren : myChildren) {
      ProjectViewNode treeNode = (ProjectViewNode)aMyChildren;
      if (treeNode.contains(file)) return true;
    }
    return false;
  }

  public void update(PresentationData presentation) {
    if (getValue() == null || !getValue().isValid()) {
      setValue(null);
    } else {
      presentation.setPresentableText(getValue().getName());
      presentation.setIcon(GuiFormFileType.INSTANCE.getIcon());
    }
  }

  public void navigate(boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  public boolean canNavigate() {
    Form value = getValue();
    return value != null && value.canNavigate();
  }

  public boolean canNavigateToSource() {
    Form value = getValue();
    return value != null && value.canNavigateToSource();
  }

  public String getToolTip() {
    return IdeBundle.message("tooltip.ui.designer.form");
  }

  @Override
  public FileStatus getFileStatus() {
    for(BasePsiNode<? extends PsiElement> child: myChildren) {
      PsiElement value = child.getValue();
      if (value == null || !value.isValid()) continue;
      FileStatus fileStatus = NavigationItemFileStatus.get(child);
      if (fileStatus != FileStatus.NOT_CHANGED) {
        return fileStatus;
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  @Override
  public boolean canHaveChildrenMatching(Predicate<PsiFile> condition) {
    for(BasePsiNode<? extends PsiElement> child: myChildren) {
      if (condition.test(child.getValue().getContainingFile())) {
        return true;
      }
    }
    return false;
  }

  public static AbstractTreeNode constructFormNode(PsiClass classToBind, Project project, ViewSettings settings) {
    Form form = new Form(classToBind);
    Collection<BasePsiNode<? extends PsiElement>> children = getChildren(project, form, settings);
    return new FormNode(project, form, settings, children);
  }

  private static Collection<BasePsiNode<? extends PsiElement>> getChildren(Project project, Form form, ViewSettings settings) {
    Set<BasePsiNode<? extends PsiElement>> children = new LinkedHashSet<>();
    children.add(new ClassTreeNode(project, form.getClassToBind(), settings));
    for (PsiFile formBoundToClass : form.getFormFiles()) {
      children.add(new PsiFileNode(project, formBoundToClass, settings));
    }
    return children;
  }
}
