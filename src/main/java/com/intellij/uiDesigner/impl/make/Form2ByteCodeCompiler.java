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

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.java.language.impl.projectRoots.JavaSdkVersionUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.compiler.ClassInstrumentingCompiler;
import consulo.compiler.CompileContext;
import consulo.compiler.CompileContextEx;
import consulo.compiler.CompilerManager;
import consulo.compiler.CompilerMessageCategory;
import consulo.compiler.CompilerPaths;
import consulo.compiler.ModuleChunk;
import consulo.compiler.TimestampValidityState;
import consulo.compiler.ValidityState;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.CompilerUtil;
import consulo.content.bundle.Sdk;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.internal.org.objectweb.asm.ClassWriter;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.DirectoryIndex;
import consulo.project.Project;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.Chunk;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

@ExtensionImpl
public final class Form2ByteCodeCompiler implements ClassInstrumentingCompiler {
    private static final String CLASS_SUFFIX = ".class";
    private static final Logger LOG = Logger.getInstance(Form2ByteCodeCompiler.class);

    @Override
    public String getDescription() {
        return UIDesignerLocalize.componentGuiDesignerFormToBytecodeCompiler().get();
    }

    @Override
    public boolean validateConfiguration(CompileScope scope) {
        return true;
    }

    public static InstrumentationClassFinder createClassFinder(String classPath) {
        List<URL> urls = new ArrayList<>();
        for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens(); ) {
            String s = tokenizer.nextToken();
            try {
                urls.add(new File(s).toURI().toURL());
            }
            catch (Exception exc) {
                throw new RuntimeException(exc);
            }
        }
        return new InstrumentationClassFinder(urls.toArray(new URL[urls.size()]));
    }

    public static InstrumentationClassFinder createClassFinder(CompileContext context, Module module) {
        ModuleChunk moduleChunk =
            new ModuleChunk((CompileContextEx) context, new Chunk<>(module), Collections.<Module, List<Path>>emptyMap());

        Set<Path> compilationBootClasspath = JavaCompilerUtil.getCompilationBootClasspath(context, moduleChunk);
        Set<Path> compilationClasspath = JavaCompilerUtil.getCompilationClasspath(context, moduleChunk);

        URL[] platformUrls = toUrls(compilationBootClasspath);

        Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
        if (sdk != null && JavaSdkVersionUtil.getJavaSdkVersion(sdk).isAtLeast(JavaSdkVersion.JDK_1_9)) {
            try {
                platformUrls = ArrayUtil.append(platformUrls, InstrumentationClassFinder.createJDKPlatformUrl(sdk.getHomePath()));
            }
            catch (MalformedURLException ignored) {
            }
        }
        return new InstrumentationClassFinder(platformUrls, toUrls(compilationClasspath));
    }

    private static URL[] toUrls(Set<Path> files) {
        List<URL> urls = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                urls.add(file.toFile().getCanonicalFile().toURI().toURL());
            }
            catch (Exception e) {
                LOG.error(e);
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    @Override
    public ProcessingItem[] getProcessingItems(CompileContext context) {
        Project project = context.getProject();
        if (!GuiDesignerConfiguration.getInstance(project).INSTRUMENT_CLASSES) {
            return ProcessingItem.EMPTY_ARRAY;
        }

        List<ProcessingItem> items = new ArrayList<>();

        ApplicationManager.getApplication().runReadAction(() -> {
            CompileScope scope = context.getCompileScope();
            CompileScope projectScope = CompilerManager.getInstance(project).createProjectCompileScope();

            Collection<Path> formFiles = projectScope.getFiles(GuiFormFileType.INSTANCE);
            if (formFiles.isEmpty()) {
                return;
            }
            CompilerManager compilerManager = CompilerManager.getInstance(project);
            BindingsCache bindingsCache = new BindingsCache(project);

            Map<Module, List<VirtualFile>> module2formFiles = sortByModules(project, formFiles);

            try {
                for (Module module : module2formFiles.keySet()) {
                    Map<String, VirtualFile> class2form = new HashMap<>();

                    List<VirtualFile> list = module2formFiles.get(module);
                    for (VirtualFile formFile : list) {
                        if (compilerManager.isExcludedFromCompilation(formFile.toNioPath())) {
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
                            addMessage(
                                context,
                                UIDesignerLocalize.errorCannotProcessFormFile(ExceptionUtil.getThrowableText(e)),
                                formFile,
                                CompilerMessageCategory.ERROR
                            );
                            continue;
                        }

                        if (classToBind == null) {
                            continue;
                        }

                        Path classFile = findFile(context, classToBind, module);
                        if (classFile == null) {
                            if (scope.belongs(formFile.toNioPath())) {
                                addMessage(
                                    context,
                                    UIDesignerLocalize.errorClassToBindDoesNotExist(classToBind),
                                    formFile,
                                    CompilerMessageCategory.ERROR
                                );
                            }
                            continue;
                        }

                        VirtualFile alreadyProcessedForm = class2form.get(classToBind);
                        if (alreadyProcessedForm != null) {
                            if (belongsToCompileScope(context, formFile, classToBind)) {
                                addMessage(
                                    context,
                                    UIDesignerLocalize.errorDuplicateBind(classToBind, alreadyProcessedForm.getPresentableUrl()),
                                    formFile,
                                    CompilerMessageCategory.ERROR
                                );
                            }
                            continue;
                        }
                        class2form.put(classToBind, formFile);

                        items.add(new MyInstrumentationItem(classFile, formFile, classToBind));
                    }
                }
            }
            finally {
                bindingsCache.close();
            }
        });

        return items.toArray(new ProcessingItem[items.size()]);
    }

    private static boolean belongsToCompileScope(CompileContext context, VirtualFile formFile, String classToBind) {
        CompileScope compileScope = context.getCompileScope();
        if (compileScope.belongs(formFile.toNioPath())) {
            return true;
        }
        Path sourceFile = findSourceFile(context, formFile, classToBind);
        return sourceFile != null && compileScope.belongs(sourceFile);
    }

    private static Map<Module, List<VirtualFile>> sortByModules(Project project, Collection<Path> formFiles) {
        LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
        Map<Module, List<VirtualFile>> module2formFiles = new HashMap<>();
        for (Path formFile : formFiles) {
            VirtualFile virtualFile = localFileSystem.findFileByNioFile(formFile);
            if (virtualFile == null) {
                continue;
            }
            Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
            if (module != null) {
                List<VirtualFile> list = module2formFiles.computeIfAbsent(module, k -> new ArrayList<>());
                list.add(virtualFile);
            }
        }
        return module2formFiles;
    }

    private static Map<Module, List<MyInstrumentationItem>> sortByModules(Project project, ProcessingItem[] items) {
        Map<Module, List<MyInstrumentationItem>> module2formFiles = new HashMap<>();
        for (ProcessingItem item1 : items) {
            MyInstrumentationItem item = (MyInstrumentationItem) item1;
            VirtualFile formFile = item.getFormFile();

            Module module = ModuleUtilCore.findModuleForFile(formFile, project);
            if (module != null) {
                List<MyInstrumentationItem> list = module2formFiles.computeIfAbsent(module, k -> new ArrayList<>());
                list.add(item);
            }
        }
        return module2formFiles;
    }

    @Nullable
    private static Path findFile(CompileContext context, String className, Module module) {
        String classPath = className.replace('.', '/');

        Path file = findFileByRelativePath(context, module, classPath + CLASS_SUFFIX);
        if (file != null) {
            return file;
        }

        int prev = 0;
        while (true) {
            int i = classPath.indexOf('/', prev);
            if (i == -1) {
                if (prev == 0) {
                    return findFileByRelativePath(context, module, classPath);
                }
                else {
                    break;
                }
            }

            prev = i + 1;

            String targetFilePath = classPath.substring(0, i) + CLASS_SUFFIX;
            Path targetFile = findFileByRelativePath(context, module, targetFilePath);
            if (targetFile != null) {
                String mergedPath = classPath.substring(0, i) + '$' + classPath.substring(i + 1).replace('/', '$') + CLASS_SUFFIX;
                return findFileByRelativePath(context, module, mergedPath);
            }
        }
        return null;
    }

    @Nullable
    private static Path findFileByRelativePath(CompileContext context, Module module, String relativePath) {
        Path output = context.getOutputForFile(module, ProductionContentFolderTypeProvider.getInstance());

        Path file = output != null ? getFileByRelativeOrNull(output, relativePath) : null;
        if (file == null) {
            Path testsOutput = context.getOutputForFile(module, TestContentFolderTypeProvider.getInstance());
            if (testsOutput != null && !testsOutput.equals(output)) {
                file = getFileByRelativeOrNull(testsOutput, relativePath);
            }
        }
        return file;
    }

    @Nullable
    private static Path getFileByRelativeOrNull(Path root, String path) {
        Path file = root.resolve(path);
        return Files.exists(file) ? file : null;
    }

    @Override
    public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
        DirectoryIndex directoryIndex = DirectoryIndex.getInstance(context.getProject());
        List<ProcessingItem> compiledItems = new ArrayList<>();

        context.getProgressIndicator().pushState();
        context.getProgressIndicator().setText(UIDesignerLocalize.progressCompilingUiForms());

        Project project = context.getProject();
        Map<Module, List<MyInstrumentationItem>> module2itemsList = sortByModules(project, items);

        for (Module module : module2itemsList.keySet()) {
            InstrumentationClassFinder finder = createClassFinder(context, module);

            try {
                GuiDesignerConfiguration designerConfiguration = GuiDesignerConfiguration.getInstance(project);
                if (designerConfiguration.COPY_FORMS_RUNTIME_TO_OUTPUT) {
                    String moduleOutputPath = CompilerPaths.getModuleOutputPath(module, ProductionContentFolderTypeProvider.getInstance());
                    try {
                        if (moduleOutputPath != null) {
                            CopyResourcesUtil.copyFormsRuntime(moduleOutputPath, false);
                        }
                        String testsOutputPath = CompilerPaths.getModuleOutputPath(module, TestContentFolderTypeProvider.getInstance());
                        if (testsOutputPath != null && !testsOutputPath.equals(moduleOutputPath)) {
                            CopyResourcesUtil.copyFormsRuntime(testsOutputPath, false);
                        }
                    }
                    catch (IOException e) {
                        addMessage(
                            context,
                            UIDesignerLocalize.errorCannotCopyGuiDesignerFormRuntime(module.getName(), ExceptionUtil.getThrowableText(e)),
                            null,
                            CompilerMessageCategory.ERROR
                        );
                    }
                }

                List<MyInstrumentationItem> list = module2itemsList.get(module);

                for (MyInstrumentationItem item : list) {
                    VirtualFile formFile = item.getFormFile();
                    context.getProgressIndicator().setText2(LocalizeValue.of(formFile.getPresentableUrl()));

                    String text = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
                        if (!belongsToCompileScope(context, formFile, item.getClassToBindFQname())) {
                            return null;
                        }
                        Document document = FileDocumentManager.getInstance().getDocument(formFile);
                        return document == null ? null : document.getText();
                    });
                    if (text == null) {
                        continue;
                    }

                    LwRootContainer rootContainer;
                    try {
                        rootContainer = Utils.getRootContainer(text, new CompiledClassPropertiesProvider(finder.getLoader()));
                    }
                    catch (Exception e) {
                        addMessage(
                            context,
                            UIDesignerLocalize.errorCannotProcessFormFile(ExceptionUtil.getThrowableText(e)),
                            formFile,
                            CompilerMessageCategory.ERROR
                        );
                        continue;
                    }

                    if (designerConfiguration.COPY_FORMS_TO_OUTPUT) {
                        Path outputForFile = context.getOutputForFile(module, formFile.toNioPath());
                        if (outputForFile != null) {
                            String packageName = directoryIndex.getPackageName(formFile.getParent());

                            Path outputFormFile;
                            if (packageName == null || packageName.isEmpty()) {
                                outputFormFile = outputForFile.resolve(formFile.getName());
                            }
                            else {
                                outputFormFile = outputForFile.resolve(packageName.replace(".", "/") + "/" + formFile.getName());
                            }

                            try {
                                Files.createDirectories(outputFormFile.getParent());
                                Files.copy(formFile.toNioPath(), outputFormFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                            catch (IOException e) {
                                addMessage(
                                    context,
                                    UIDesignerLocalize.errorCannotProcessFormFile(ExceptionUtil.getThrowableText(e)),
                                    formFile,
                                    CompilerMessageCategory.ERROR
                                );
                                continue;
                            }
                        }
                    }

                    Path classFile = item.getFile();
                    LOG.assertTrue(Files.exists(classFile), classFile.toString());

                    AsmCodeGenerator codeGenerator = new AsmCodeGenerator(
                        rootContainer,
                        finder,
                        new PsiNestedFormLoader(module),
                        false,
                        new InstrumenterClassWriter(isJdk6(module) ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, finder),
                        designerConfiguration.USE_JB_SCALING
                    );
                    ApplicationManager.getApplication().runReadAction(() -> codeGenerator.patchFile(classFile.toFile()));
                    FormErrorInfo[] errors = codeGenerator.getErrors();
                    FormErrorInfo[] warnings = codeGenerator.getWarnings();
                    for (FormErrorInfo warning : warnings) {
                        addMessage(context, warning, formFile, CompilerMessageCategory.WARNING);
                    }
                    for (FormErrorInfo error : errors) {
                        addMessage(context, error, formFile, CompilerMessageCategory.ERROR);
                    }
                    if (errors.length == 0) {
                        compiledItems.add(item);
                    }
                }
            }
            finally {
                finder.releaseResources();
            }
        }
        context.getProgressIndicator().popState();

        return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
    }

    private static boolean isJdk6(Module module) {
        Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
        return sdk != null && JavaSdkTypeUtil.isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_6);
    }

    private static void addMessage(
        CompileContext context,
        LocalizeValue message,
        @Nullable VirtualFile formFile,
        CompilerMessageCategory severity
    ) {
        addMessage(context, new FormErrorInfo(null, message.get()), formFile, severity);
    }

    private static void addMessage(
        CompileContext context,
        FormErrorInfo e,
        @Nullable VirtualFile formFile,
        CompilerMessageCategory severity
    ) {
        if (formFile != null) {
            FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
            context.newMessage(severity, LocalizeValue.of(formFile.getPresentableUrl() + ": " + e.getErrorMessage()))
                .url(formFile.getUrl())
                .navigatable(navigatable)
                .add();
        }
        else {
            context.newMessage(severity, LocalizeValue.of(e.getErrorMessage())).add();
        }
    }

    @Override
    public ValidityState createValidityState(DataInput in) throws IOException {
        return TimestampValidityState.load(in);
    }

    @Nullable
    public static Path findSourceFile(CompileContext context, VirtualFile formFile, String className) {
        Module module = context.getModuleByFile(formFile.toNioPath());
        if (module == null) {
            return null;
        }
        PsiClass aClass = FormEditingUtil.findClassToBind(module, className);
        if (aClass == null) {
            return null;
        }

        PsiFile containingFile = aClass.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return null;
        }
        return virtualFile.toNioPath();
    }

    private static final class MyInstrumentationItem implements ProcessingItem {
        private final Path myClassFile;
        private final VirtualFile myFormFile;
        private final String myClassToBindFQname;
        private final TimestampValidityState myState;

        private MyInstrumentationItem(Path classFile, VirtualFile formFile, String classToBindFQname) {
            myClassFile = classFile;
            myFormFile = formFile;
            myClassToBindFQname = classToBindFQname;
            myState = new TimestampValidityState(CompilerUtil.lastModified(formFile.toNioPath()));
        }

        @Override
        public Path getFile() {
            return myClassFile;
        }

        public VirtualFile getFormFile() {
            return myFormFile;
        }

        public String getClassToBindFQname() {
            return myClassToBindFQname;
        }

        @Override
        public ValidityState getValidityState() {
            return myState;
        }
    }
}
