/**
 * @author VISTALL
 * @since 31/01/2023
 */
open module com.intellij.uiDesigner
{
    requires consulo.ide.api;

    requires consulo.java;
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;
    requires consulo.java.properties.impl;
    requires com.intellij.xml;
    requires com.intellij.properties;
    requires consulo.util.nodep;

    requires jgoodies.common;
    requires jgoodies.forms;
    requires jgoodies.looks;

    requires instrumentation.util;
    requires forms.compiler;

    // TODO remove in future
    requires java.desktop;
    requires consulo.ide.impl;
    requires forms.rt;

    exports com.intellij.ide.palette;
    exports com.intellij.ide.palette.impl;
    exports com.intellij.uiDesigner.impl;
    exports com.intellij.uiDesigner.impl.actions;
    exports com.intellij.uiDesigner.impl.binding;
    exports com.intellij.uiDesigner.impl.clientProperties;
    exports com.intellij.uiDesigner.impl.componentTree;
    exports com.intellij.uiDesigner.impl.designSurface;
    exports com.intellij.uiDesigner.impl.editor;
    exports com.intellij.uiDesigner.impl.fileTemplate;
    exports com.intellij.uiDesigner.impl.i18n;
    exports com.intellij.uiDesigner.impl.inspections;
    exports com.intellij.uiDesigner.impl.make;
    exports com.intellij.uiDesigner.impl.palette;
    exports com.intellij.uiDesigner.impl.projectView;
    exports com.intellij.uiDesigner.impl.propertyInspector;
    exports com.intellij.uiDesigner.impl.propertyInspector.editors;
    exports com.intellij.uiDesigner.impl.propertyInspector.editors.string;
    exports com.intellij.uiDesigner.impl.propertyInspector.properties;
    exports com.intellij.uiDesigner.impl.propertyInspector.renderers;
    exports com.intellij.uiDesigner.impl.quickFixes;
    exports com.intellij.uiDesigner.impl.radComponents;
    exports com.intellij.uiDesigner.impl.snapShooter;
    exports com.intellij.uiDesigner.impl.wizard;
    exports consulo.uiDesigner.impl.icon;
    exports consulo.uiDesigner.impl.localize;


    //opens com.intellij.uiDesigner.impl.actions to consulo.component.impl;
}