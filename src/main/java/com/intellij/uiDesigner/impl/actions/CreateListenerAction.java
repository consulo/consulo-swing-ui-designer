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

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class CreateListenerAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance(CreateListenerAction.class);

  @Override
  protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    DefaultActionGroup actionGroup = prepareActionGroup(selection);
    JComponent selectedComponent = selection.get(0).getDelegee();
    DataContext context = DataManager.getInstance().getDataContext(selectedComponent);
    JBPopupFactory factory = JBPopupFactory.getInstance();
    ListPopup popup = factory.createActionGroupPopup(
      UIDesignerLocalize.createListenerTitle().get(),
      actionGroup,
      context,
      JBPopupFactory.ActionSelectionAid.NUMBERING,
      true
    );

    FormEditingUtil.showPopupUnderComponent(popup, selection.get(0));
  }

  private DefaultActionGroup prepareActionGroup(List<RadComponent> selection) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    EventSetDescriptor[] eventSetDescriptors;
    try {
      BeanInfo beanInfo = Introspector.getBeanInfo(selection.get(0).getComponentClass());
      eventSetDescriptors = beanInfo.getEventSetDescriptors();
    }
    catch (IntrospectionException e) {
      LOG.error(e);
      return null;
    }
    EventSetDescriptor[] sortedDescriptors = new EventSetDescriptor[eventSetDescriptors.length];
    System.arraycopy(eventSetDescriptors, 0, sortedDescriptors, 0, eventSetDescriptors.length);
    Arrays.sort(sortedDescriptors, (o1, o2) -> o1.getListenerType().getName().compareTo(o2.getListenerType().getName()));
    for(EventSetDescriptor descriptor: sortedDescriptors) {
      actionGroup.add(new MyCreateListenerAction(selection, descriptor));
    }
    return actionGroup;
  }

  @Override
  @RequiredUIAccess
  protected void update(@Nonnull GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    e.getPresentation().setEnabled(canCreateListener(selection));
  }

  private static boolean canCreateListener(List<RadComponent> selection) {
    if (selection.size() == 0) return false;
    RadRootContainer root = (RadRootContainer)FormEditingUtil.getRoot(selection.get(0));
    if (root.getClassToBind() == null) return false;
    String componentClass = selection.get(0).getComponentClassName();
    for(RadComponent c: selection) {
      if (!c.getComponentClassName().equals(componentClass) || c.getBinding() == null) return false;
      if (BindingProperty.findBoundField(root, c.getBinding()) == null) return false;
    }
    return true;
  }

  private class MyCreateListenerAction extends AnAction
  {
    private final List<RadComponent> mySelection;
    private final EventSetDescriptor myDescriptor;
    private static final String LISTENER_SUFFIX = "Listener";
    private static final String ADAPTER_SUFFIX = "Adapter";

    public MyCreateListenerAction(List<RadComponent> selection, EventSetDescriptor descriptor) {
      super(descriptor.getListenerType().getSimpleName());
      mySelection = selection;
      myDescriptor = descriptor;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        CommandProcessor.getInstance().newCommand()
            .project(mySelection.get(0).getProject())
            .name(UIDesignerLocalize.createListenerCommand())
            .inWriteAction()
            .run(this::createListener);
    }

    @RequiredWriteAction
    private void createListener() {
      RadRootContainer root = (RadRootContainer)FormEditingUtil.getRoot(mySelection.get(0));
      PsiField[] boundFields = new PsiField[mySelection.size()];
      for (int i = 0; i < mySelection.size(); i++) {
        boundFields[i] = BindingProperty.findBoundField(root, mySelection.get(i).getBinding());
      }
      final PsiClass myClass = boundFields[0].getContainingClass();

      if (!FileModificationService.getInstance().preparePsiElementForWrite(myClass)) return;

      try {
        PsiMethod constructor = findConstructorToInsert(myClass);
        Module module = myClass.getModule();
        PsiClass listenerClass = null;
        String listenerClassName = myDescriptor.getListenerType().getName();
        if (listenerClassName.endsWith(LISTENER_SUFFIX)) {
          String adapterClassName = listenerClassName.substring(0, listenerClassName.length() - LISTENER_SUFFIX.length()) + ADAPTER_SUFFIX;
          listenerClass = JavaPsiFacade.getInstance(myClass.getProject())
            .findClass(adapterClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
        if (listenerClass == null) {
          listenerClass = JavaPsiFacade.getInstance(myClass.getProject())
            .findClass(listenerClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
        if (listenerClass == null) {
          Messages.showErrorDialog(
            myClass.getProject(),
            UIDesignerLocalize.createListenerClassNotFound().get(),
            CommonLocalize.titleError().get()
          );
          return;
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        PsiCodeBlock body = constructor.getBody();
        LOG.assertTrue(body != null);

        StringBuilder builder = new StringBuilder();
        String variableName = null;
        if (boundFields.length == 1) {
          builder.append(boundFields[0].getName());
          builder.append(".");
          builder.append(myDescriptor.getAddListenerMethod().getName());
          builder.append("(");
        }
        else {
          builder.append(listenerClass.getQualifiedName()).append(" ");
          if (body.getLastBodyElement() == null) {
            variableName = "listener";
          }
          else {
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myClass.getProject());
            variableName = codeStyleManager.suggestUniqueVariableName("listener", body.getLastBodyElement(), false);
          }
          builder.append(variableName).append("=");
        }
        builder.append("new ");
        builder.append(listenerClass.getQualifiedName());
        builder.append("() { } ");
        if (boundFields.length == 1) {
          builder.append(");");
        }
        else {
          builder.append(";");
        }

        PsiStatement stmt = factory.createStatementFromText(builder.toString(), constructor);
        stmt = (PsiStatement)body.addAfter(stmt, body.getLastBodyElement());
        JavaCodeStyleManager.getInstance(body.getProject()).shortenClassReferences(stmt);

        if (boundFields.length > 1) {
          PsiElement anchor = stmt;
          for (PsiField field : boundFields) {
            PsiElement addStmt = factory
              .createStatementFromText(field.getName() + "." + myDescriptor.getAddListenerMethod().getName() + "(" + variableName + ");",
                                       constructor);
            addStmt = body.addAfter(addStmt, anchor);
            anchor = addStmt;
          }
        }

        final Ref<PsiClass> newClassRef = new Ref<>();
        stmt.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitClass(PsiClass aClass) {
            newClassRef.set(aClass);
          }
        });
        PsiClass newClass = newClassRef.get();
        final SmartPsiElementPointer ptr = SmartPointerManager.getInstance(myClass.getProject()).createSmartPsiElementPointer(newClass);
        newClass.navigate(true);
        IdeFocusManager.findInstance().doWhenFocusSettlesDown(new Runnable() {
          public void run() {
            PsiClass newClass = (PsiClass)ptr.getElement();
            Editor editor = DataManager.getInstance().getDataContext().getData(PlatformDataKeys.EDITOR);
            if (editor != null && newClass != null) {
              CommandProcessor.getInstance().newCommand()
                .project(myClass.getProject())
                .run(() -> {
                  if (!OverrideImplementUtil.getMethodSignaturesToImplement(newClass).isEmpty()) {
                    OverrideImplementUtil.chooseAndImplementMethods(newClass.getProject(), editor, newClass);
                  }
                  else {
                    OverrideImplementUtil.chooseAndOverrideMethods(newClass.getProject(), editor, newClass);
                  }
                });
            }
          }
        });
      }
      catch (consulo.language.util.IncorrectOperationException ex) {
        LOG.error(ex);
      }
    }

    @RequiredWriteAction
    private PsiMethod findConstructorToInsert(PsiClass aClass) throws IncorrectOperationException {
      PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
        PsiMethod newConstructor = factory.createMethodFromText("public " + aClass.getName() + "() { }", aClass);
        PsiMethod[] psiMethods = aClass.getMethods();
        PsiMethod firstMethod = (psiMethods.length == 0) ? null : psiMethods [0];
        return (PsiMethod) aClass.addBefore(newConstructor, firstMethod);
      }
      for(PsiMethod method: constructors) {
        if (method.getParameterList().getParametersCount() == 0) {
          return method;
        }
      }
      return constructors [0];
    }
  }
}
