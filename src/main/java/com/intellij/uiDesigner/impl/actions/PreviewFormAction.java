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

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.java.compiler.impl.PsiClassWriter;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.make.CopyResourcesUtil;
import com.intellij.uiDesigner.impl.make.Form2ByteCodeCompiler;
import com.intellij.uiDesigner.impl.make.PreviewNestedFormLoader;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.annotation.component.ActionImpl;
import consulo.application.Application;
import consulo.application.util.AsyncFileService;
import consulo.application.util.TempFileService;
import consulo.compiler.CompilerManager;
import consulo.compiler.scope.FileSetCompileScope;
import consulo.container.boot.ContainerPathManager;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.CantRunException;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.ModuleRunProfile;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.runner.RunnerRegistry;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.layer.OrderEnumerator;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.process.ExecutionException;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.ui.image.Image;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@ActionImpl(id = "GuiDesigner.PreviewForm")
public final class PreviewFormAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(PreviewFormAction.class);

    /**
     * The problem is that this class is in a default package so it's not
     * import this class to refer
     */
    private static final String CLASS_TO_BIND_NAME = "FormPreviewFrame";
    private static final String RUNTIME_BUNDLE_PREFIX = "RuntimeBundle";
    public static final String PREVIEW_BINDING_FIELD = "myComponent";

    private final TempFileService myTempFileService;

    @Inject
    public PreviewFormAction(TempFileService tempFileService) {
        super(
            UIDesignerLocalize.actionGuidesignerPreviewformText(),
            UIDesignerLocalize.actionGuidesignerPreviewformDescription(),
            PlatformIconGroup.actionsPreview()
        );

        myTempFileService = tempFileService;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
        if (editor != null) {
            showPreviewFrame(editor.getModule(), editor.getFile(), editor.getStringDescriptorLocale());
        }
    }

    @Override
    public void update(AnActionEvent e) {
        GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());

        if (editor == null) {
            e.getPresentation().setVisible(false);
            return;
        }

        VirtualFile file = editor.getFile();
        e.getPresentation().setVisible(
            FileDocumentManager.getInstance().getDocument(file) != null
                && file.getFileType() == GuiFormFileType.INSTANCE
        );
    }

    @RequiredUIAccess
    private void showPreviewFrame(@Nonnull Module module, @Nonnull VirtualFile formFile, @Nullable Locale stringDescriptorLocale) {
        String tempPath;
        try {
            Path tempDirectory = myTempFileService.createTempDirectory("FormPreview", "");
            tempPath = tempDirectory.toAbsolutePath().toString();

            CopyResourcesUtil.copyFormsRuntime(tempPath, true);
        }
        catch (IOException e) {
            Messages.showErrorDialog(
                module.getProject(),
                UIDesignerLocalize.errorCannotPreviewForm(formFile.getPath().replace('/', File.separatorChar), e.toString()).get(),
                CommonLocalize.titleError().get()
            );
            return;
        }

        PathsList sources = OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().withoutDepModules().getSourcePathsList();
        String classPath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString() + File.pathSeparator +
            sources.getPathsString() + File.pathSeparator + /* resources bundles */
            tempPath;
        InstrumentationClassFinder finder = Form2ByteCodeCompiler.createClassFinder(classPath);

        try {
            Document doc = FileDocumentManager.getInstance().getDocument(formFile);
            LwRootContainer rootContainer;
            try {
                rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(finder.getLoader()));
            }
            catch (Exception e) {
                Messages.showErrorDialog(
                    module.getProject(),
                    UIDesignerLocalize.errorCannotReadForm(formFile.getPath().replace('/', File.separatorChar), e.getMessage()).get(),
                    CommonLocalize.titleError().get()
                );
                return;
            }

            if (rootContainer.getComponentCount() == 0) {
                Messages.showErrorDialog(
                    module.getProject(),
                    UIDesignerLocalize.errorCannotPreviewEmptyForm(formFile.getPath().replace('/', File.separatorChar)).get(),
                    CommonLocalize.titleError().get()
                );
                return;
            }

            setPreviewBindings(rootContainer, CLASS_TO_BIND_NAME);

            // 2. Copy previewer class and all its superclasses into TEMP directory and instrument it.
            try {
                PreviewNestedFormLoader nestedFormLoader = new PreviewNestedFormLoader(module, tempPath, finder);

                File tempFile = CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME, true);
                //CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$1", true);
                CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyExitAction", true);
                CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MyPackAction", true);
                CopyResourcesUtil.copyClass(tempPath, CLASS_TO_BIND_NAME + "$MySetLafAction", true);

                Locale locale = Locale.getDefault();
                if (locale.getCountry().length() > 0 && locale.getLanguage().length() > 0) {
                    CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() +
                        "_" + locale.getCountry() + PropertiesFileType.DOT_DEFAULT_EXTENSION);
                }
                if (locale.getLanguage().length() > 0) {
                    CopyResourcesUtil.copyProperties(
                        tempPath,
                        RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() + PropertiesFileType.DOT_DEFAULT_EXTENSION
                    );
                }
                CopyResourcesUtil.copyProperties(
                    tempPath,
                    RUNTIME_BUNDLE_PREFIX + "_" + locale.getLanguage() + PropertiesFileType.DOT_DEFAULT_EXTENSION
                );
                CopyResourcesUtil.copyProperties(tempPath, RUNTIME_BUNDLE_PREFIX + PropertiesFileType.DOT_DEFAULT_EXTENSION);

                AsmCodeGenerator codeGenerator =
                    new AsmCodeGenerator(rootContainer, finder, nestedFormLoader, true, new PsiClassWriter(module));
                codeGenerator.patchFile(tempFile);
                FormErrorInfo[] errors = codeGenerator.getErrors();
                if (errors.length != 0) {
                    Messages.showErrorDialog(
                        module.getProject(),
                        UIDesignerLocalize.errorCannotPreviewForm(
                            formFile.getPath().replace('/', File.separatorChar),
                            errors[0].getErrorMessage()
                        ).get(),
                        CommonLocalize.titleError().get()
                    );
                    return;
                }
            }
            catch (Exception e) {
                LOG.debug(e);
                Messages.showErrorDialog(
                    module.getProject(),
                    UIDesignerLocalize.errorCannotPreviewForm(
                        formFile.getPath().replace('/', File.separatorChar),
                        e.getMessage() != null ? e.getMessage() : e.toString()
                    ).get(),
                    CommonLocalize.titleError().get()
                );
                return;
            }

            // 2.5. Copy up-to-date properties files to the output directory.
            Set<String> bundleSet = new HashSet<>();
            FormEditingUtil.iterateStringDescriptors(
                rootContainer,
                (component, descriptor) -> {
                    if (descriptor.getBundleName() != null) {
                        bundleSet.add(descriptor.getDottedBundleName());
                    }
                    return true;
                }
            );

            if (bundleSet.size() > 0) {
                Set<VirtualFile> virtualFiles = new HashSet<>();
                Set<Module> modules = new HashSet<>();
                PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(module.getProject());
                for (String bundleName : bundleSet) {
                    for (PropertiesFile propFile : manager.findPropertiesFiles(module, bundleName)) {
                        virtualFiles.add(propFile.getVirtualFile());
                        Module moduleForFile = ModuleUtilCore.findModuleForFile(propFile.getVirtualFile(), module.getProject());
                        if (moduleForFile != null) {
                            modules.add(moduleForFile);
                        }
                    }
                }
                FileSetCompileScope scope = new FileSetCompileScope(virtualFiles, modules.toArray(new Module[modules.size()]));

                CompilerManager.getInstance(module.getProject()).make(
                    scope,
                    (aborted, errors, warnings, compileContext) -> {
                        if (!aborted && errors == 0) {
                            runPreviewProcess(tempPath, sources, module, formFile, stringDescriptorLocale);
                        }
                    }
                );
            }
            else {
                runPreviewProcess(tempPath, sources, module, formFile, stringDescriptorLocale);
            }
        }
        finally {
            finder.releaseResources();
        }
    }

    public static void setPreviewBindings(LwRootContainer rootContainer, String classToBindName) {
        // 1. Prepare form to preview. We have to change container so that it has only one binding.
        rootContainer.setClassToBind(classToBindName);
        FormEditingUtil.iterate(
            rootContainer,
            new FormEditingUtil.ComponentVisitor<LwComponent>() {
                @Override
                public boolean visit(LwComponent iComponent) {
                    iComponent.setBinding(null);
                    return true;
                }
            }
        );
        if (rootContainer.getComponentCount() == 1) {
            //noinspection HardCodedStringLiteral
            ((LwComponent) rootContainer.getComponent(0)).setBinding(PREVIEW_BINDING_FIELD);
        }
    }

    @RequiredUIAccess
    private static void runPreviewProcess(
        String tempPath,
        PathsList sources,
        Module module,
        VirtualFile formFile,
        @Nullable Locale stringDescriptorLocale
    ) {
        // 3. Now we are ready to launch Java process
        OwnJavaParameters parameters = new OwnJavaParameters();
        parameters.getClassPath().add(tempPath);
        parameters.getClassPath().add(ContainerPathManager.get().findFileInLibDirectory("jgoodies-forms.jar").getAbsolutePath());
        List<String> paths = sources.getPathList();
        for (String path : paths) {
            parameters.getClassPath().add(path);
        }
        try {
            parameters.configureByModule(module, OwnJavaParameters.JDK_AND_CLASSES);
        }
        catch (CantRunException e) {
            Messages.showErrorDialog(
                module.getProject(),
                UIDesignerLocalize.errorCannotPreviewForm(formFile.getPath().replace('/', File.separatorChar), e.getMessage()).get(),
                CommonLocalize.titleError().get()
            );
            return;
        }
        parameters.setMainClass("FormPreviewFrame");
        parameters.setWorkingDirectory(tempPath);
        if (stringDescriptorLocale != null && stringDescriptorLocale.getDisplayName().length() > 0) {
            parameters.getVMParametersList().add("-Duser.language=" + stringDescriptorLocale.getLanguage());
        }

        try {
            RunProfile profile = new MyRunProfile(module, parameters, tempPath);
            ProgramRunner defaultRunner = RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, profile);
            LOG.assertTrue(defaultRunner != null);
            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            defaultRunner.execute(new ExecutionEnvironment(profile, executor, module.getProject(), null));
        }
        catch (ExecutionException e) {
            Messages.showErrorDialog(
                module.getProject(),
                UIDesignerLocalize.errorCannotPreviewForm(formFile.getPath().replace('/', File.separatorChar), e.getMessage()).get(),
                CommonLocalize.titleError().get()
            );
        }
    }

    private static final class MyRunProfile implements ModuleRunProfile {
        private final Module myModule;
        private final OwnJavaParameters myParams;
        private final String myTempPath;

        public MyRunProfile(Module module, OwnJavaParameters params, String tempPath) {
            myModule = module;
            myParams = params;
            myTempPath = tempPath;
        }

        @Override
        public Image getIcon() {
            return null;
        }

        @Override
        public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
            return new JavaCommandLineState(env) {
                @Override
                protected OwnJavaParameters createJavaParameters() {
                    return myParams;
                }

                @Override
                public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException {
                    ExecutionResult executionResult = super.execute(executor, runner);
                    executionResult.getProcessHandler().addProcessListener(new ProcessListener() {
                        @Override
                        public void processTerminated(ProcessEvent event) {
                            AsyncFileService asyncFileService = Application.get().getInstance(AsyncFileService.class);
                            asyncFileService.asyncDelete(new File(myTempPath));
                        }
                    });
                    return executionResult;
                }
            };
        }

        @Override
        public String getName() {
            return UIDesignerLocalize.titleFormPreview().get();
        }

        @Override
        @Nonnull
        public Module[] getModules() {
            return new Module[]{myModule};
        }
    }
}
