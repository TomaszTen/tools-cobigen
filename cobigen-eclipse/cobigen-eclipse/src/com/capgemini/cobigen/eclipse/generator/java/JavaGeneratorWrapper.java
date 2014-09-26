/*******************************************************************************
 * Copyright © Capgemini 2013. All rights reserved.
 ******************************************************************************/
package com.capgemini.cobigen.eclipse.generator.java;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.xml.sax.SAXException;

import com.capgemini.cobigen.CobiGen;
import com.capgemini.cobigen.config.ContextConfiguration.ContextSetting;
import com.capgemini.cobigen.eclipse.common.constants.ConfigResources;
import com.capgemini.cobigen.eclipse.common.exceptions.GeneratorProjectNotExistentException;
import com.capgemini.cobigen.eclipse.common.tools.ClassLoaderUtil;
import com.capgemini.cobigen.eclipse.common.tools.PathUtil;
import com.capgemini.cobigen.eclipse.generator.java.entity.ComparableIncrement;
import com.capgemini.cobigen.exceptions.InvalidConfigurationException;
import com.capgemini.cobigen.exceptions.MergeException;
import com.capgemini.cobigen.exceptions.UnknownContextVariableException;
import com.capgemini.cobigen.exceptions.UnknownExpressionException;
import com.capgemini.cobigen.extension.to.IncrementTo;
import com.capgemini.cobigen.extension.to.TemplateTo;
import com.capgemini.cobigen.javaplugin.inputreader.to.PackageFolder;
import com.capgemini.cobigen.javaplugin.util.JavaParserUtil;
import com.google.common.collect.Lists;

import freemarker.template.TemplateException;

/**
 * The generator interface for the external generator library
 *
 * @author mbrunnli (13.02.2013)
 */
public class JavaGeneratorWrapper {

    /**
     * Register Factory Generator instance
     */
    private CobiGen cobiGen;

    /**
     * Current input type
     */
    private IType type;

    /**
     * Package folder to be generated
     */
    private PackageFolder packageFolder;

    /**
     * Current input {@link Class}
     */
    private Class<?> pojo;

    /**
     * Target Project for the generation
     */
    private IProject targetProject;

    /**
     * A set of removed fields for the generation.
     */
    private Set<String> ignoreFields = new HashSet<String>();

    /**
     * All matching templates for the currently configured {@link #pojo}
     */
    private List<TemplateTo> matchingTemplates = Lists.newLinkedList();

    /**
     * Creates a new generator instance
     *
     * @throws InvalidConfigurationException
     *             if the given configuration does not match the templates.xsd
     * @throws IOException
     *             if the generator project "RF-Generation" could not be accessed
     * @throws GeneratorProjectNotExistentException
     *             if the generator configuration project "RF-Generation" is not existent
     * @throws CoreException
     *             if the generator configuration project could not be opened
     * @throws UnknownContextVariableException
     *             if the destination path contains an undefined context variable
     * @throws UnknownExpressionException
     *             if there is an unknown variable modifier
     * @author mbrunnli (21.03.2014)
     */
    public JavaGeneratorWrapper() throws UnknownExpressionException, UnknownContextVariableException,
        GeneratorProjectNotExistentException, CoreException, IOException, InvalidConfigurationException {

        this.cobiGen = initializeGenerator();
    }

    /**
     * Creates a new generator instance
     *
     * @param type
     *            of the input POJO
     * @throws InvalidConfigurationException
     *             if the given configuration does not match the templates.xsd
     * @throws IOException
     *             if the generator project "RF-Generation" could not be accessed
     * @throws GeneratorProjectNotExistentException
     *             if the generator configuration project "RF-Generation" is not existent
     * @throws CoreException
     *             if the generator configuration project could not be opened
     * @throws UnknownContextVariableException
     *             if the destination path contains an undefined context variable
     * @throws UnknownExpressionException
     *             if there is an unknown variable modifier
     * @throws ClassNotFoundException
     *             if the given type could not be found by the project {@link ClassLoader}
     * @author mbrunnli (13.02.2013)
     */
    public JavaGeneratorWrapper(IType type) throws IOException, InvalidConfigurationException,
        GeneratorProjectNotExistentException, CoreException, UnknownExpressionException,
        UnknownContextVariableException, ClassNotFoundException {

        this();
        setInputType(type);
    }

    /**
     * Initializes the {@link CobiGen} with the correct configuration
     *
     * @return the configured{@link CobiGen}
     * @throws IOException
     *             if the {@link IFile} could not be accessed
     * @throws InvalidConfigurationException
     *             if the given configuration does not match the templates.xsd
     * @throws UnknownContextVariableException
     *             if the destination path contains an undefined context variable
     * @throws UnknownExpressionException
     *             if there is an unknown variable modifier
     * @author mbrunnli (05.02.2013)
     * @throws CoreException
     * @throws GeneratorProjectNotExistentException
     */
    private CobiGen initializeGenerator() throws GeneratorProjectNotExistentException, CoreException,
        UnknownExpressionException, UnknownContextVariableException, IOException,
        InvalidConfigurationException {

        IProject generatorProj = ConfigResources.getGeneratorConfigurationProject();
        return new CobiGen(generatorProj.getLocation().toFile());
    }

    /**
     * Sets a {@link IPackageFragment} as input for the generator
     *
     * @param packageFragment
     *            generator input
     * @author mbrunnli (03.06.2014)
     */
    public void setInputPackage(IPackageFragment packageFragment) {

        this.type = null;
        this.pojo = null;
        this.packageFolder =
            new PackageFolder(packageFragment.getResource().getLocationURI(),
                packageFragment.getElementName());
        this.matchingTemplates = this.cobiGen.getMatchingTemplates(this.packageFolder);
    }

    /**
     * Changes the {@link JavaGeneratorWrapper}'s type an by this its pojo, model and template configuration.
     * Useful for batch generation.
     *
     * @param type
     *            of the input POJO
     * @throws CoreException
     *             if the Java runtime class path could not be determined
     * @throws ClassNotFoundException
     *             if the given type could not be found by the project {@link ClassLoader}
     * @throws UnknownExpressionException
     *             if there is an unknown variable modifier
     * @throws UnknownContextVariableException
     *             if the destination path contains an undefined context variable
     * @throws IOException
     *             if one of the template configurations could not be accessed
     * @author trippl (22.04.2013)
     */
    public void setInputType(IType type) throws CoreException, ClassNotFoundException, IOException {

        this.type = type;
        this.packageFolder = null;
        ClassLoader projectClassLoader = ClassLoaderUtil.getProjectClassLoader(type.getJavaProject());
        this.pojo = projectClassLoader.loadClass(type.getFullyQualifiedName());
        this.matchingTemplates = this.cobiGen.getMatchingTemplates(this.pojo);
    }

    /**
     * Builds an adapted model for the generation process containing javadoc
     *
     * @param type
     *            input {@link IType}
     * @param origModel
     *            the original model
     * @return the adapted model
     * @throws JavaModelException
     *             if the given type does not exist or if an exception occurs while accessing its
     *             corresponding resource
     * @author mbrunnli (05.04.2013)
     */
    private Map<String, Object> adaptModel(Map<String, Object> origModel, IType type)
        throws JavaModelException {

        Map<String, Object> newModel = new HashMap<String, Object>(origModel);
        JavaModelAdaptor javaModelAdaptor = new JavaModelAdaptor(newModel);
        javaModelAdaptor.addAttributesDescription(type);
        javaModelAdaptor.addMethods(type);
        return newModel;
    }

    /**
     * Sets the generation root target for all templates
     *
     * @param proj
     *            {@link IProject} which represents the target root
     * @author mbrunnli (13.02.2013)
     */
    public void setGenerationTargetProject(IProject proj) {

        this.targetProject = proj;
        this.cobiGen.setContextSetting(ContextSetting.GenerationTargetRootPath, proj.getProject()
            .getLocation().toString());
    }

    /**
     * Returns the generation target project
     *
     * @return the generation target project
     * @author mbrunnli (13.02.2013)
     */
    public IProject getGenerationTargetProject() {

        return this.targetProject;
    }

    /**
     * Generates the given template
     *
     * @param template
     *            {@link TemplateTo} to be generated
     * @param forceOverride
     *            forces the generator to override the maybe existing target file of the template
     * @throws TemplateException
     *             any exception of the FreeMarker engine
     * @throws IOException
     *             if the specified template could not be found
     * @throws TransformerException
     *             if an unrecoverable error occurs during the course of the transformation.
     * @throws SAXException
     *             if any parse errors occur.
     * @throws JavaModelException
     *             if any exception occurs while retrieving additional information from the eclipse java model
     *             like javaDoc
     * @throws MergeException
     *             if there are some problems while merging
     * @throws InvalidConfigurationException
     * @author mbrunnli (14.02.2013)
     */
    public void generate(TemplateTo template, boolean forceOverride) throws IOException, TemplateException,
        SAXException, TransformerException, JavaModelException, MergeException, InvalidConfigurationException {

        if (this.packageFolder != null) {
            this.cobiGen.generate(this.packageFolder, template, forceOverride);
        } else {
            Object[] inputSourceAndClass =
                new Object[] { this.pojo,
                    JavaParserUtil.getJavaClass(new StringReader(this.type.getCompilationUnit().getSource())) };
            Map<String, Object> model =
                this.cobiGen.getModelBuilder(inputSourceAndClass, template.getTriggerId()).createModel();
            adaptModel(model, this.type);
            removeIgnoredFieldsFromModel(model);
            this.cobiGen.generate(inputSourceAndClass, template, model, forceOverride);
        }
    }

    /**
     * Returns all available generation packages
     *
     * @return all available generation packages
     * @author mbrunnli (25.02.2013)
     */
    public ComparableIncrement[] getAllIncrements() {

        LinkedList<ComparableIncrement> result = Lists.newLinkedList();
        for (IncrementTo increment : this.cobiGen.getMatchingIncrements(this.pojo)) {
            result.add(new ComparableIncrement(increment.getId(), increment.getDescription(), increment
                .getTriggerId(), increment.getTemplates(), increment.getDependentIncrements()));
        }

        ComparableIncrement all =
            new ComparableIncrement("all", "All", null, Lists.<TemplateTo> newLinkedList(),
                Lists.<IncrementTo> newLinkedList());
        for (TemplateTo t : this.matchingTemplates) {
            all.addTemplate(t);
        }
        result.push(all);
        ComparableIncrement[] array = result.toArray(new ComparableIncrement[0]);
        Arrays.sort(array);
        return array;
    }

    /**
     * Returns all matching trigger ids for the currently stored input
     *
     * @return a list of matching trigger ids
     * @author mbrunnli (03.06.2014)
     */
    public List<String> getMatchingTriggerIds() {

        if (this.packageFolder != null)
            return this.cobiGen.getMatchingTriggerIds(this.packageFolder);
        else
            return this.cobiGen.getMatchingTriggerIds(this.pojo);
    }

    /**
     * Returns all available generation packages (sorted and element "all" added on top)
     *
     * @return all available generation packages
     * @author mbrunnli (25.02.2013)
     */
    public List<TemplateTo> getAllTemplates() {

        return this.matchingTemplates;
    }

    /**
     * Returns the attributes and its types from the current model
     *
     * @return a {@link Map} mapping attribute name to attribute type name
     * @author mbrunnli (12.03.2013)
     * @throws InvalidConfigurationException
     */
    public Map<String, String> getAttributesToTypeMap() throws InvalidConfigurationException {

        Map<String, String> result = new HashMap<String, String>();
        List<String> matchingTriggerIds = this.cobiGen.getMatchingTriggerIds(this.pojo);
        Map<String, Object> model =
            this.cobiGen.getModelBuilder(this.pojo, matchingTriggerIds.get(0)).createModel();
        @SuppressWarnings("unchecked")
        Map<String, Object> pojo = (Map<String, Object>) model.get("pojo");
        if (pojo != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> attributes = (List<Map<String, String>>) pojo.get("attributes");
            for (Map<String, String> attr : attributes) {
                result.put(attr.get("name"), attr.get("type"));
            }
        }
        return result;
    }

    /**
     * Removes a given attributes from the model
     *
     * @param name
     *            name of the attribute to be removed
     * @author mbrunnli (21.03.2013)
     */
    public void removeFieldFromModel(String name) {

        this.ignoreFields.add(name);
    }

    /**
     * Removes all fields from the model which have been flagged to be ignored
     *
     * @param model
     *            in which the ignored fields should be removed
     * @author mbrunnli (15.10.2013)
     */
    private void removeIgnoredFieldsFromModel(Map<String, Object> model) {

        @SuppressWarnings("unchecked")
        Map<String, Object> pojo = (Map<String, Object>) model.get("pojo");
        if (pojo != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> fields = (List<Map<String, String>>) pojo.get("attributes");
            for (Iterator<Map<String, String>> it = fields.iterator(); it.hasNext();) {
                Map<String, String> next = it.next();
                for (String ignoredField : this.ignoreFields) {
                    if (next.get("name").equals(ignoredField)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Returns the {@link List} of templates, which target to the given path.
     *
     * @param filePath
     *            for which templates should be retrieved
     * @param consideredIncrements
     *            increments which should be considered for fetching templates
     * @return the {@link List} of templates, which generates the given file
     * @author mbrunnli (14.02.2013)
     */
    public List<TemplateTo> getTemplatesForFilePath(String filePath, Set<IncrementTo> consideredIncrements) {

        List<TemplateTo> templates = Lists.newLinkedList();
        if (consideredIncrements != null) {
            for (IncrementTo increment : getAllIncrements()) {
                if (consideredIncrements.contains(increment)) {
                    for (TemplateTo tmp : increment.getTemplates()) {
                        if (tmp.getDestinationPath().equals(PathUtil.getProjectDependendFilePath(filePath))) {
                            templates.add(tmp);
                        }
                    }
                }
            }
        } else {
            for (TemplateTo tmp : getAllTemplates()) {
                if (tmp.getDestinationPath().equals(PathUtil.getProjectDependendFilePath(filePath))) {
                    templates.add(tmp);
                }
            }
        }

        return templates;
    }

    /**
     * Returns the {@link TemplateTo}, which has the given templateId and belongs to the trigger with the
     * given triggerId or <code>null</code> if there is no template with the given id
     *
     * @param templateId
     *            the template id
     * @param triggerId
     *            the trigger id
     * @return the template, which has the given id<br>
     *         <code>null</code>, if there is no template with the given id
     * @author trippl (22.04.2013)
     */
    public TemplateTo getTemplateForId(String templateId, String triggerId) {

        List<TemplateTo> templates = getAllTemplates();
        for (TemplateTo tmp : templates) {
            if (tmp.getTriggerId().equals(triggerId)) {
                if (tmp.getId().equals(templateId)) {
                    return tmp;
                }
            }
        }
        return null;
    }

    /**
     * Returns project dependent paths of all resources which are marked to be mergeable
     *
     * @return The set of all mergeable project dependent file paths
     * @author mbrunnli (15.03.2013)
     */
    public Set<IFile> getMergeableFiles() {

        Set<IFile> mergeableFiles = new HashSet<IFile>();
        IProject targetProjet = getGenerationTargetProject();
        for (TemplateTo t : getAllTemplates()) {
            if (t.getMergeStrategy() != null) {
                mergeableFiles.add(targetProjet.getFile(t.getDestinationPath()));
            }
        }
        return mergeableFiles;
    }

    /**
     * Returns project dependent paths of all possible generated resources
     *
     * @return project dependent paths of all possible generated resources
     * @author mbrunnli (26.04.2013)
     */
    public Set<IFile> getAllFiles() {

        Set<IFile> files = new HashSet<IFile>();
        IProject targetProjet = getGenerationTargetProject();
        for (TemplateTo t : getAllTemplates()) {
            files.add(targetProjet.getFile(t.getDestinationPath()));
        }
        return files;
    }
}
