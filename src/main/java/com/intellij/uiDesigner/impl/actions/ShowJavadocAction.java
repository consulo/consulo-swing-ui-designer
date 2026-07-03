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

import com.intellij.uiDesigner.impl.propertyInspector.PropertyInspectorTable;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import jakarta.annotation.Nonnull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShowJavadocAction extends AnAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(ShowJavadocAction.class);

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull final AnActionEvent e) {
//		final PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
//		final IntrospectedProperty introspectedProperty = inspector.getSelectedIntrospectedProperty();
//		final PsiClass aClass = inspector.getComponentClass();
//
//		final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, introspectedProperty.getName(), false, true);
//		LOG.assertTrue(getter != null);
//
//		final PsiMethod setter = PropertyUtil.findPropertySetter(aClass, introspectedProperty.getName(), false, true);
//		LOG.assertTrue(setter != null);
//
//		final DocumentationManager documentationManager = consulo.ide.impl.idea.codeInsight.documentation.DocumentationManager.getInstance(aClass.getProject());
//
//		final consulo.ide.impl.idea.codeInsight.documentation.DocumentationComponent component1 = new DocumentationComponent(documentationManager);
//		final DocumentationComponent component2 = new consulo.ide.impl.idea.codeInsight.documentation.DocumentationComponent(documentationManager);
//
//		final Disposable disposable = Disposable.newDisposable();
//		final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(disposable);
//
//		tabbedPane.addTab(UIDesignerBundle.message("tab.getter"), component1);
//		tabbedPane.addTab(UIDesignerBundle.message("tab.setter"), component2);
//
//		documentationManager.fetchDocInfo(getter, component1);
//		documentationManager.queueFetchDocInfo(setter, component2).doWhenProcessed(new Runnable()
//		{
//			@Override
//			public void run()
//			{
//				final JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(tabbedPane.getComponent(), component1).setDimensionServiceKey(aClass.getProject(), consulo.ide.impl.idea
//						.codeInsight.documentation.DocumentationManager
//						.JAVADOC_LOCATION_AND_SIZE, false).setResizable(true).setMovable(true).setRequestFocus(true).setTitle(UIDesignerBundle.message("property.javadoc.title", introspectedProperty
//						.getName())).createPopup();
//				component1.setHint(hint);
//				component2.setHint(hint);
//				Disposer.register(hint, component1);
//				Disposer.register(hint, component2);
//				Disposer.register(hint, disposable);
//				hint.show(new RelativePoint(inspector, new Point(0, 0)));
//				//component1.requestFocus();
//			}
//		});
    }

    @Override
    public void update(@Nonnull final AnActionEvent e) {
        final PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
        e.getPresentation().setEnabled(inspector != null && inspector.getSelectedIntrospectedProperty() != null && inspector.getComponentClass() != null);
    }
}
