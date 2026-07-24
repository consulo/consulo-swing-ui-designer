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

package com.intellij.uiDesigner.impl.designSurface;

import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.UIDesignerIcons;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import consulo.application.util.function.Processor;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.navigation.Navigatable;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;

/**
 * @author yole
 */
public class ListenerNavigateButton extends JButton implements ActionListener {
    private static final Logger LOG = Logger.getInstance(ListenerNavigateButton.class);

    private final RadComponent myComponent;

    public ListenerNavigateButton(RadComponent component) {
        myComponent = component;
        setIcon(TargetAWT.to(UIDesignerIcons.Listener));
        setOpaque(false);
        setFocusable(false);
        setBorderPainted(false);
        setSize(new Dimension(getIcon().getIconWidth(), getIcon().getIconHeight()));
        addActionListener(this);
    }

    public void actionPerformed(ActionEvent e) {
        showNavigatePopup(myComponent, false);
    }

    public static void showNavigatePopup(RadComponent component, boolean showIfEmpty) {
        DefaultActionGroup actionGroup = prepareActionGroup(component);
        if (actionGroup != null && actionGroup.getChildrenCount() == 0 && showIfEmpty) {
            actionGroup.add(new MyNavigateAction(UIDesignerBundle.message("navigate.to.listener.empty"), null));
        }
        if (actionGroup != null && actionGroup.getChildrenCount() > 0) {
            DataContext context = DataManager.getInstance().getDataContext(component.getDelegee());
            JBPopupFactory factory = JBPopupFactory.getInstance();
            ListPopup popup = factory.createActionGroupPopup(UIDesignerBundle.message("navigate.to.listener.title"), actionGroup, context,
                JBPopupFactory.ActionSelectionAid.NUMBERING, true);
            FormEditingUtil.showPopupUnderComponent(popup, component);
        }
    }

    @Nullable
    public static DefaultActionGroup prepareActionGroup(RadComponent component) {
        IRootContainer root = FormEditingUtil.getRoot(component);
        String classToBind = root == null ? null : root.getClassToBind();
        if (classToBind != null) {
            PsiClass aClass = FormEditingUtil.findClassToBind(component.getModule(), classToBind);
            if (aClass != null) {
                PsiField boundField = aClass.findFieldByName(component.getBinding(), false);
                if (boundField != null) {
                    return buildNavigateActionGroup(component, boundField);
                }
            }
        }
        return null;
    }

    private static DefaultActionGroup buildNavigateActionGroup(RadComponent component, PsiField boundField) {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        final EventSetDescriptor[] eventSetDescriptors;
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(component.getComponentClass());
            eventSetDescriptors = beanInfo.getEventSetDescriptors();
        }
        catch (IntrospectionException e) {
            LOG.error(e);
            return null;
        }
        LocalSearchScope scope = new LocalSearchScope(boundField.getContainingFile());
        ReferencesSearch.search(boundField, scope).forEach(new Processor<>() {
            public boolean process(PsiReference ref) {
                PsiElement element = ref.getElement();
                if (element.getParent() instanceof PsiReferenceExpression) {
                    PsiReferenceExpression refExpr = (PsiReferenceExpression) element.getParent();
                    if (refExpr.getParent() instanceof PsiMethodCallExpression) {
                        PsiMethodCallExpression methodCall = (PsiMethodCallExpression) refExpr.getParent();
                        PsiElement psiElement = refExpr.resolve();
                        if (psiElement instanceof PsiMethod) {
                            PsiMethod method = (PsiMethod) psiElement;
                            for (EventSetDescriptor eventSetDescriptor : eventSetDescriptors) {
                                if (Comparing.equal(eventSetDescriptor.getAddListenerMethod().getName(), method.getName())) {
                                    String eventName = eventSetDescriptor.getName();
                                    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                                    if (args.length > 0) {
                                        addListenerRef(actionGroup, eventName, args[0]);
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }
        });

        return actionGroup;
    }

    private static void addListenerRef(DefaultActionGroup actionGroup, String eventName, PsiExpression listenerArg) {
        PsiType type = listenerArg.getType();
        if (type instanceof PsiClassType) {
            PsiClass listenerClass = ((PsiClassType) type).resolve();
            if (listenerClass != null) {
                if (!isAbstractOrInterface(listenerClass)) {
                    actionGroup.add(new MyNavigateAction(eventName + ": " + ClassPresentationUtil.getNameForClass(listenerClass, false),
                        listenerClass));
                    return;
                }
                else if (listenerArg instanceof PsiReferenceExpression) {
                    PsiElement psiElement = ((PsiReferenceExpression) listenerArg).resolve();
                    if (psiElement instanceof PsiVariable) {
                        PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(listenerArg, PsiCodeBlock.class);
                        PsiElement[] defs = DefUseUtil.getDefs(codeBlock, (PsiVariable) psiElement, listenerArg);
                        if (defs.length == 1) {
                            PsiElement def = defs[0];
                            if (def instanceof PsiVariable) {
                                PsiVariable var = (PsiVariable) def;
                                if (var.getInitializer() != listenerArg) {
                                    addListenerRef(actionGroup, eventName, var.getInitializer());
                                    return;
                                }
                            }
                            else if (def.getParent() instanceof PsiAssignmentExpression) {
                                PsiAssignmentExpression assignmentExpr = (PsiAssignmentExpression) def.getParent();
                                if (def.equals(assignmentExpr.getLExpression())) {
                                    addListenerRef(actionGroup, eventName, assignmentExpr.getRExpression());
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
        actionGroup.add(new MyNavigateAction(eventName + ": " + listenerArg.getText(), listenerArg));
    }

    private static boolean isAbstractOrInterface(PsiClass element) {
        return element.isInterface() ||
            element.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    private static class MyNavigateAction extends AnAction implements AnActionWithSyncUpdate {
        private final PsiElement myElement;

        public MyNavigateAction(String name, PsiElement element) {
            super(name);
            myElement = element;
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            if (myElement instanceof Navigatable) {
                ((Navigatable) myElement).navigate(true);
            }
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(myElement != null &&
                (!(myElement instanceof PsiClass) || !isAbstractOrInterface((PsiClass) myElement)));
        }
    }
}
