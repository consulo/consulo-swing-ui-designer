/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl;

import com.intellij.uiDesigner.impl.radComponents.LayoutManagerRegistry;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.*;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@ExtensionImpl
public final class GuiDesignerConfigurable implements ProjectConfigurable, Configurable.NoScroll {
    private static final Logger LOG = Logger.getInstance(GuiDesignerConfigurable.class);

    private final Project myProject;
    private final Provider<GuiDesignerConfiguration> myGuiDesignerConfigurationProvider;

    private MyLayout myGeneralUI;

    @Inject
    public GuiDesignerConfigurable(final Project project, Provider<GuiDesignerConfiguration> guiDesignerConfigurationProvider) {
        myProject = project;
        myGuiDesignerConfigurationProvider = guiDesignerConfigurationProvider;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return UIDesignerLocalize.titleGuiDesigner();
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(@Nonnull Disposable uiDisposable) {
        if (myGeneralUI == null) {
            myGeneralUI = new MyLayout();
        }

        return myGeneralUI.myPanel;
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        if (myGeneralUI == null) {
            return false;
        }

        GuiDesignerConfiguration configuration = myGuiDesignerConfigurationProvider.get();

        if (myGeneralUI.myChkCopyFormsRuntime.getValueOrError() != configuration.COPY_FORMS_RUNTIME_TO_OUTPUT) {
            return true;
        }

        if (myGeneralUI.myChkCopyForms.getValueOrError() != configuration.COPY_FORMS_TO_OUTPUT) {
            return true;
        }

        if (!Comparing.equal(configuration.DEFAULT_LAYOUT_MANAGER, myGeneralUI.myLayoutManagerCombo.getValueOrError())) {
            return true;
        }

        if (!Comparing.equal(configuration.DEFAULT_FIELD_ACCESSIBILITY, myGeneralUI.myDefaultFieldAccessibilityCombo.getValueOrError())) {
            return true;
        }

        if (configuration.INSTRUMENT_CLASSES != myGeneralUI.myRbInstrumentClasses.getValueOrError()) {
            return true;
        }

        if (configuration.RESIZE_HEADERS != myGeneralUI.myResizeHeaders.getValueOrError()) {
            return true;
        }

        if (configuration.USE_JB_SCALING != myGeneralUI.myUseJBScalingCheckBox.getValueOrError()) {
            return true;
        }
        return false;
    }

    @RequiredUIAccess
    @Override
    public void apply() {
        GuiDesignerConfiguration configuration = myGuiDesignerConfigurationProvider.get();

        configuration.COPY_FORMS_RUNTIME_TO_OUTPUT = myGeneralUI.myChkCopyFormsRuntime.getValueOrError();
        configuration.COPY_FORMS_TO_OUTPUT = myGeneralUI.myChkCopyForms.getValueOrError();
        configuration.DEFAULT_LAYOUT_MANAGER = myGeneralUI.myLayoutManagerCombo.getValueOrError();
        configuration.INSTRUMENT_CLASSES = myGeneralUI.myRbInstrumentClasses.getValueOrError();
        configuration.DEFAULT_FIELD_ACCESSIBILITY = myGeneralUI.myDefaultFieldAccessibilityCombo.getValueOrError();
        configuration.RESIZE_HEADERS = myGeneralUI.myResizeHeaders.getValueOrError();
        configuration.USE_JB_SCALING = myGeneralUI.myUseJBScalingCheckBox.getValueOrError();

        if (configuration.INSTRUMENT_CLASSES && !myProject.isDefault()) {
            new RemovingSetupMethodProcessor(myProject).run();
        }
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        if (myGeneralUI == null) {
            return;
        }

        GuiDesignerConfiguration configuration = myGuiDesignerConfigurationProvider.get();

        if (configuration.INSTRUMENT_CLASSES) {
            myGeneralUI.myRbInstrumentClasses.setValue(true);
        }
        else {
            myGeneralUI.myRbInstrumentSources.setValue(true);
        }
        myGeneralUI.myChkCopyFormsRuntime.setValue(configuration.COPY_FORMS_RUNTIME_TO_OUTPUT);
        myGeneralUI.myChkCopyForms.setValue(configuration.COPY_FORMS_TO_OUTPUT);

        myGeneralUI.myLayoutManagerCombo.setValue(configuration.DEFAULT_LAYOUT_MANAGER);

        myGeneralUI.myDefaultFieldAccessibilityCombo.setValue(configuration.DEFAULT_FIELD_ACCESSIBILITY);

        myGeneralUI.myResizeHeaders.setValue(configuration.RESIZE_HEADERS);
        myGeneralUI.myUseJBScalingCheckBox.setValue(configuration.USE_JB_SCALING);
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myGeneralUI = null;
    }

    private static class MyLayout {
        public VerticalLayout myPanel;
        public RadioButton myRbInstrumentClasses;
        public RadioButton myRbInstrumentSources;
        public CheckBox myChkCopyFormsRuntime;
        private ComboBox<String> myLayoutManagerCombo;
        private ComboBox<String> myDefaultFieldAccessibilityCombo;
        private CheckBox myResizeHeaders;
        private CheckBox myChkCopyForms;
        private CheckBox myUseJBScalingCheckBox;

        @RequiredUIAccess
        private MyLayout() {
            myPanel = VerticalLayout.create();

            Label label = Label.create(UIDesignerLocalize.labelGenerateGuiInto());

            ValueGroup<Boolean> group = ValueGroup.createBool();
            myRbInstrumentClasses = RadioButton.create(UIDesignerLocalize.radioGenerateIntoClass()).toGroup(group);
            myRbInstrumentSources = RadioButton.create(UIDesignerLocalize.radioGenerateIntoJava()).toGroup(group);

            myPanel.add(HorizontalLayout.create().add(DockLayout.create().top(label)).add(VerticalLayout.create().add(myRbInstrumentClasses).add(myRbInstrumentSources)));

            myUseJBScalingCheckBox = CheckBox.create(LocalizeValue.localizeTODO("Use scaling util class (JBUI)"));
            myPanel.add(myUseJBScalingCheckBox);

            myChkCopyFormsRuntime = CheckBox.create(UIDesignerLocalize.chkCopyFormRuntime());
            myPanel.add(myChkCopyFormsRuntime);

            myChkCopyForms = CheckBox.create(UIDesignerLocalize.chkCopyForm());
            myPanel.add(myChkCopyForms);

            myLayoutManagerCombo = ComboBox.create(LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames());
            myLayoutManagerCombo.selectFirst();
            myLayoutManagerCombo.setTextRender(value -> LocalizeValue.of(LayoutManagerRegistry.getLayoutManagerDisplayName(value)));

            myPanel.add(LabeledBuilder.sided(UIDesignerLocalize.defaultLayoutManager(), myLayoutManagerCombo));

            myDefaultFieldAccessibilityCombo = ComboBox.create("private", "package local", "protected", "public");
            myDefaultFieldAccessibilityCombo.selectFirst();

            myPanel.add(LabeledBuilder.sided(UIDesignerLocalize.defaultFieldAccessibility(), myDefaultFieldAccessibilityCombo));

            myResizeHeaders = CheckBox.create(LocalizeValue.localizeTODO("&Resize column and row headers with mouse"));
            myPanel.add(myResizeHeaders);
        }
    }

//	private final class MyApplyRunnable implements Runnable
//	{
//		private final DispatchThreadProgressWindow myProgressWindow;
//
//		public MyApplyRunnable(final DispatchThreadProgressWindow progressWindow)
//		{
//			myProgressWindow = progressWindow;
//		}
//
//		/**
//		 * Removes all generated sources
//		 */
//		private void vanishGeneratedSources()
//		{
//			final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myProject);
//			final PsiMethod[] methods = cache.getMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME,
//					GlobalSearchScope.projectScope(myProject));
//
//			CodeInsightUtil.preparePsiElementsForWrite(methods);
//
//			for(int i = 0; i < methods.length; i++)
//			{
//				final PsiMethod method = methods[i];
//				final PsiClass aClass = method.getContainingClass();
//				if(aClass != null)
//				{
//					try
//					{
//						final PsiFile psiFile = aClass.getContainingFile();
//						LOG.assertTrue(psiFile != null);
//						final VirtualFile vFile = psiFile.getVirtualFile();
//						LOG.assertTrue(vFile != null);
//						myProgressWindow.setText(UIDesignerBundle.message("progress.converting", vFile.getPresentableUrl()));
//						myProgressWindow.setFraction(((double) i) / ((double) methods.length));
//						if(vFile.isWritable())
//						{
//							FormSourceCodeGenerator.cleanup(aClass);
//						}
//					}
//					catch(IncorrectOperationException e)
//					{
//						LOG.error(e);
//					}
//				}
//			}
//		}
//
//		/**
//		 * Launches vanish/generate sources processes
//		 */
//		private void applyImpl()
//		{
//			CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
//				PsiDocumentManager.getInstance(myProject).commitAllDocuments();
//				vanishGeneratedSources();
//			}), "", null);
//		}
//
//		@Override
//		public void run()
//		{
//			ProgressManager.getInstance().runProcess(() -> applyImpl(), myProgressWindow);
//		}
//	}

    @Override
    @Nonnull
    public String getId() {
        return "project.propGUI";
    }

    @Nullable
    @Override
    public String getParentId() {
        return StandardConfigurableIds.EDITOR_GROUP;
    }
}
