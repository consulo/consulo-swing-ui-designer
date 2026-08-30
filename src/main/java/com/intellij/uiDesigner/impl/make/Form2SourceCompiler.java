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
package com.intellij.uiDesigner.impl.make;

import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerManager;
import consulo.compiler.CompilerPaths;
import consulo.compiler.SourceInstrumentingCompiler;
import consulo.compiler.TimestampValidityState;
import consulo.compiler.ValidityState;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.CompilerUtil;
import consulo.document.FileDocumentManager;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtensionImpl
public final class Form2SourceCompiler implements SourceInstrumentingCompiler {
    @Override
    public String getDescription() {
        return UIDesignerLocalize.componentGuiDesignerFormToSourceCompiler().get();
    }

    @Override
    public boolean validateConfiguration(CompileScope scope) {
        return true;
    }

    @Override
    public ProcessingItem[] getProcessingItems(CompileContext context) {
        Project project = context.getProject();
        if (GuiDesignerConfiguration.getInstance(project).INSTRUMENT_CLASSES) {
            return ProcessingItem.EMPTY_ARRAY;
        }

        List<ProcessingItem> items = new ArrayList<>();

        ApplicationManager.getApplication().runReadAction(() -> {
            CompileScope scope = context.getCompileScope();
            CompileScope projectScope = CompilerManager.getInstance(project).createProjectCompileScope();

            Collection<Path> formFiles = projectScope.getFiles(GuiFormFileType.INSTANCE);
            CompilerManager compilerManager = CompilerManager.getInstance(project);
            LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            BindingsCache bindingsCache = new BindingsCache(project);

            try {
                Map<String, VirtualFile> class2form = new HashMap<>();

                for (Path formPath : formFiles) {
                    if (compilerManager.isExcludedFromCompilation(formPath)) {
                        continue;
                    }

                    VirtualFile formFile = localFileSystem.findFileByNioFile(formPath);
                    if (formFile == null) {
                        continue;
                    }

                    String classToBind;
                    try {
                        classToBind = bindingsCache.getBoundClassName(formFile);
                    }
                    catch (AlienFormFileException e) {
                        continue;
                    }
                    catch (Exception e) {
                        addError(context, new FormErrorInfo(null, UIDesignerLocalize.errorCannotProcessFormFile(e).get()), formFile);
                        continue;
                    }

                    if (classToBind == null) {
                        continue;
                    }

                    Path sourceFile = Form2ByteCodeCompiler.findSourceFile(context, formFile, classToBind);
                    if (sourceFile == null) {
                        if (scope.belongs(formPath)) {
                            addError(
                                context,
                                new FormErrorInfo(null, UIDesignerLocalize.errorClassToBindDoesNotExist(classToBind).get()),
                                formFile
                            );
                        }
                        continue;
                    }

                    boolean inScope = scope.belongs(sourceFile) || scope.belongs(formPath);

                    VirtualFile alreadyProcessedForm = class2form.get(classToBind);
                    if (alreadyProcessedForm != null) {
                        if (inScope) {
                            addError(
                                context,
                                new FormErrorInfo(
                                    null,
                                    UIDesignerLocalize.errorDuplicateBind(classToBind, alreadyProcessedForm.getPresentableUrl()).get()
                                ),
                                formFile
                            );
                        }
                        continue;
                    }
                    class2form.put(classToBind, formFile);

                    if (!inScope) {
                        continue;
                    }

                    items.add(new MyInstrumentationItem(sourceFile, formFile));
                }
            }
            finally {
                bindingsCache.close();
            }
        });

        return items.toArray(new ProcessingItem[items.size()]);
    }

    @Override
    public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
        List<ProcessingItem> compiledItems = new ArrayList<>();

        context.getProgressIndicator().setText(UIDesignerLocalize.progressCompilingUiForms());

        int formsProcessed = 0;

        Project project = context.getProject();
        FormSourceCodeGenerator generator = new FormSourceCodeGenerator(project);

        Set<Module> processedModules = new HashSet<>();

        for (ProcessingItem item1 : items) {
            context.getProgressIndicator().setFraction((double) (++formsProcessed) / ((double) items.length));

            MyInstrumentationItem item = (MyInstrumentationItem) item1;

            VirtualFile formFile = item.getFormFile();

            if (GuiDesignerConfiguration.getInstance(project).COPY_FORMS_RUNTIME_TO_OUTPUT) {
                ApplicationManager.getApplication().runReadAction(() -> {
                    Module module = ModuleUtilCore.findModuleForFile(formFile, project);
                    if (module != null && !processedModules.contains(module)) {
                        processedModules.add(module);
                        String moduleOutputPath =
                            CompilerPaths.getModuleOutputPath(module, ProductionContentFolderTypeProvider.getInstance());
                        try {
                            if (moduleOutputPath != null) {
                                CopyResourcesUtil.copyFormsRuntime(moduleOutputPath, false);
                            }
                            String testsOutputPath =
                                CompilerPaths.getModuleOutputPath(module, TestContentFolderTypeProvider.getInstance());
                            if (testsOutputPath != null && !testsOutputPath.equals(moduleOutputPath)) {
                                CopyResourcesUtil.copyFormsRuntime(testsOutputPath, false);
                            }
                        }
                        catch (IOException e) {
                            addError(
                                context,
                                new FormErrorInfo(
                                    null,
                                    UIDesignerLocalize.errorCannotCopyGuiDesignerFormRuntime(
                                        module.getName(),
                                        ExceptionUtil.getThrowableText(e)
                                    ).get()
                                ),
                                null
                            );
                        }
                    }
                });
            }

            ApplicationManager.getApplication().invokeAndWait(
                () -> {
                    CommandProcessor.getInstance().executeCommand(
                        project,
                        () -> ApplicationManager.getApplication().runWriteAction(() -> {
                            PsiDocumentManager.getInstance(project).commitAllDocuments();
                            generator.generate(formFile);
                            List<FormErrorInfo> errors = generator.getErrors();
                            if (errors.size() == 0) {
                                compiledItems.add(item);
                            }
                            else {
                                for (FormErrorInfo e : errors) {
                                    addError(context, e, formFile);
                                }
                            }
                        }),
                        "",
                        null
                    );
                    FileDocumentManager.getInstance().saveAllDocuments();
                },
                ApplicationManager.getApplication().getNoneModalityState()
            );
        }

        return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
    }

    private static void addError(CompileContext context, FormErrorInfo e, @Nullable VirtualFile formFile) {
        if (formFile != null) {
            FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
            context.newError(LocalizeValue.of(formFile.getPresentableUrl() + ": " + e.getErrorMessage()))
                .url(formFile.getUrl())
                .navigatable(navigatable)
                .add();
        }
        else {
            context.newError(LocalizeValue.of(e.getErrorMessage())).add();
        }
    }

    @Override
    public ValidityState createValidityState(DataInput in) throws IOException {
        return TimestampValidityState.load(in);
    }

    private static final class MyInstrumentationItem implements ProcessingItem {
        private final Path mySourceFile;
        private final VirtualFile myFormFile;
        private final TimestampValidityState myState;

        public MyInstrumentationItem(Path sourceFile, VirtualFile formFile) {
            mySourceFile = sourceFile;
            myFormFile = formFile;
            myState = new TimestampValidityState(CompilerUtil.lastModified(formFile.toNioPath()));
        }

        @Override
        public Path getFile() {
            return mySourceFile;
        }

        public VirtualFile getFormFile() {
            return myFormFile;
        }

        @Override
        public ValidityState getValidityState() {
            return myState;
        }
    }
}
