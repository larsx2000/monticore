/*
 * ******************************************************************************
 * MontiCore Language Workbench
 * Copyright (c) 2015, MontiCore, All rights reserved.
 *
 * This project is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this project. If not, see <http://www.gnu.org/licenses/>.
 * ******************************************************************************
 */

package de.monticore.generating.templateengine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.monticore.ast.ASTNode;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import de.monticore.generating.templateengine.freemarker.SimpleHashFactory;
import de.monticore.generating.templateengine.reporting.Reporting;
import de.se_rwth.commons.logging.Log;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.SimpleHash;
import freemarker.template.TemplateCollectionModel;
import freemarker.template.TemplateModelException;

/**
 * Class for managing hook points, features and (global) variables in templates.
 *
 * @author Pedram Nazari, Alexander Roth
 */
public class GlobalExtensionManagement {
  
  private SimpleHash globalData = SimpleHashFactory.getInstance().createSimpleHash();
  
  // use these list to handle replacements aka template forwardings
  private final Multimap<String, HookPoint> before = ArrayListMultimap.create();
  
  private final Multimap<String, HookPoint> replace = ArrayListMultimap.create();
  
  private final Multimap<String, HookPoint> after = ArrayListMultimap.create();
  
  private final Map<String, Map<ASTNode, HookPoint>> specificReplacement = Maps.newHashMap();
  
  /**
   * Map of all hook points
   */
  private final Map<String, HookPoint> hookPoints = Maps.newHashMap();
  
  public GlobalExtensionManagement() {
  }
  
  /**
   * Set a list of global data. The parameter should not be null.
   * 
   * @param data list of global data
   */
  public void setGlobalData(SimpleHash data) {
    Log.errorIfNull(data);
    
    this.globalData = data;
  }
  
  /**
   * Retrieve a list of all global data
   */
  SimpleHash getGlobalData(){
	  return this.globalData;
  }
  
  /**
   * Returns a list of all registered global value names
   * 
   * @return collection of all names of defined values.
   */
  public TemplateCollectionModel getGlobalValueNames() {
    return globalData.keys();
  }
  
  /**
   * Checks whether a value with the given name is defined and is not null
   * 
   * @param name of the value to check
   * @return true if a variable exists and its value is not null
   */
  public boolean hasGlobalValue(String name) {
    try {
      return globalData.get(name) != null;
    }
    catch (TemplateModelException e) {
      Log.error("0xA7123 Internal Error on global value for \"" + name + "\"");
      return false;
    }
  }
  
  /**
   * Sets a new value which can be accessed with the given name in the
   * templates. If the name is already in use an error is reported the previous
   * value is overridden.
   * 
   * @param name of the value to set
   * @param value the actual content
   */
  public void setGlobalValue(String name, Object value) {
    Log.errorIfNull(name);
    
    Reporting.reportSetValue(name, value);
    globalData.put(name, value);
  }
  
  /**
   * Defines a new value which can be accessed with the given name in the
   * templates. If the name is already in use an error is reported.
   * 
   * @param name of the value to set
   * @param value the actual content
   */
  public void defineGlobalValue(String name, Object value) {
    if (hasGlobalValue(name)) {
      // TODO: in which template? hinzufügen
      Log.error("0xA0122 Global Value '" + name + "' has already been set.\n Old value: " +
          getGlobalValue(name) + "\n New value: " + value);
    }
    setGlobalValue(name, value);
  }
  
  /**
   * Adds a new value to the given name. It converts a single value into a list
   * if necessary.
   * 
   * @param name of the value to set
   * @param value the actual content
   */
  @SuppressWarnings({ "unchecked", "deprecation" })
  public void addGlobalValue(String name, Object value) {
    Object currentValue = null;
    try {
      currentValue = BeansWrapper.getDefaultInstance().unwrap(globalData.get(name));
    }
    catch (TemplateModelException e) {
      // addValue is robust: if the key doesn't exist it acts like setValue, but
      // still:
      Log.error("0xA8123 Internal Error on global value for \"" + name + "\"");
    }
    if (currentValue != null) {
      Collection<Object> newValue = new ArrayList<>();
      if (currentValue instanceof Collection<?>) {
        newValue.addAll((Collection<Object>) currentValue);
      }
      else {
        newValue.add(currentValue);
      }
      if (value instanceof Collection<?>) {
        newValue.addAll((Collection<Object>) value);
        // TODO: GenLogger ersetzen (Überall)
        // Dieser existiert noch nicht (AR)
        Reporting.reportAddValue(name, value, newValue.size());
      }
      else {
        newValue.add(value);
        Reporting.reportAddValue(name, value, 1);
      }
      setGlobalValue(name, newValue);
    }
    else {
      setGlobalValue(name, value);
    }
  }
  
  /**
   * Returns the value of the given name.
   * 
   * @param name of the value
   * @return the value
   */
  @SuppressWarnings("deprecation")
  public Object getGlobalValue(String name) {
    try {
      return BeansWrapper.getDefaultInstance().unwrap(globalData.get(name));
    }
    catch (TemplateModelException e) {
      Log.error("0xA0123 Internal Error on global value for \"" + name + "\"");
    }
    return null;
  }
  
  /**
   * check whether the variable name (parameter) is defined: if not issue an
   * error and continue
   * 
   * @param name variable name
   */
  public void requiredGlobalVars(String name) {
    if (getGlobalValue(name) == null) {
      // TODO: in which template? hinzufügen
      // Sollte dieser Fehler kommen, dann wird auch das Template ausgeworfen,
      // das zuletzt verarbeitet wurde (also den Fehler produziert hat)
      Log.error("0xA0126 Missing required value \"" + name + "\"");
    }
  }
  
  /**
   * check whether the list of variable names (parameter) is defined: if not
   * issue an error and continue
   * 
   * @param names list of variable names
   */
  public void requiredGlobalVars(List<String> names) {
    names.forEach(this::requiredGlobalVars);
  }
  
  /**
   * if the listed variable does not exist, it is initialized with an empty
   * string
   * 
   * @param name variable name
   */
  public void setOptionalGlobalVars(String name) {
    if (getGlobalValue(name) == null) {
      globalData.put(name, "");
    }
  }
  
  /**
   * if the listed variables do not exist, each is initialized with an empty
   * string
   * 
   * @param names list of variable names
   */
  public void setOptionalGlobalVars(List<String> names) {
    names.forEach(this::setOptionalGlobalVars);
  }
  
  /**
   * @param hookName name of the hook point
   * @param hp
   */
  public void bindHookPoint(String hookName, HookPoint hp) {
    Reporting.reportSetHookPoint(hookName, hp);
    warnIfHookPointExists(hookName);
    hookPoints.put(hookName, hp);
  }
  
  /**
   * @param hookName name of the hook point
   * @return the (processed) value of the hook point
   */
  public String defineHookPoint(TemplateController controller, String hookName, ASTNode ast) {
    String result = null;
    HookPoint hp = hookPoints.get(hookName);
        
    Reporting.reportCallHookPointStart(hookName, hp, ast);
    if (hookPoints.containsKey(hookName)) {
      result = hp.processValue(controller, ast);
    }
    Reporting.reportCallHookPointEnd(hookName);
    
    
    return Strings.nullToEmpty(result);
  }
  
  /**
   * Returns a set of templates that have been defined to replace the template
   * <code>templateName</code>. If no template forwardings have been defined,
   * then <code>templateName</code> is returned.
   * 
   * @param templateName The name of the template
   * @return A list of templates that have been defined to replace the
   * 'templateNames' templates
   */
  protected List<HookPoint> getTemplateForwardings(String templateName, ASTNode ast) {
    List<HookPoint> replacements = Lists.newArrayList();
    Collection<HookPoint> before = this.before.get(templateName);
    Collection<HookPoint> after = this.after.get(templateName);
    
    if (before != null) {
      replacements.addAll(before);
      Reporting.reportCallBeforeHookPoint(templateName, before, ast);
    }
    
    List<HookPoint> hps = getSpecificReplacement(templateName, ast);
    if(hps != null){
      Reporting.reportCallSpecificReplacementHookPoint(templateName, hps, ast);
    }
    else {
      hps = getTemplateForwardingsX(templateName, ast);
    }
    replacements.addAll(hps);
    
    if (after != null) {
      replacements.addAll(after);
      Reporting.reportCallAfterHookPoint(templateName, after, ast);
    }
    return replacements;
  }
  
  protected List<HookPoint> getSpecificReplacement(String templateName, ASTNode ast) {
    Map<ASTNode, HookPoint> replacedTemplates = this.specificReplacement.get(templateName);
    if (replacedTemplates != null && replacedTemplates.containsKey(ast)) {
      return Lists.newArrayList(replacedTemplates.get(ast));
    }
    return null;
  }
  
  /**
   * Returns a set of templates that have been defined to replace the
   * 'templateName' template. If no template forwardings have been defined, then
   * the 'templateName' template is returned.
   * 
   * @param templateName The name of the template
   * @return A list of templats that have been defined to replace the
   * 'templateName' template
   */
  protected List<HookPoint> getTemplateForwardingsX(String templateName, ASTNode ast) {
    List<HookPoint> forwardings = Lists.newArrayList();
    
    if (containsTemplateForwarding(templateName)) {
      if(this.replace.containsKey(templateName)){
        forwardings.addAll(this.replace.get(templateName));
        Reporting.reportCallReplacementHookPoint(templateName, forwardings, ast);
      } else{
        forwardings.addAll(Lists.newArrayList(new TemplateHookPoint(templateName)));
      }
    }
    else {
      forwardings.add(new TemplateHookPoint(templateName));
      Reporting.reportExecuteStandardTemplate(templateName, ast);
    }
    return forwardings;
  }
  
  private boolean containsTemplateForwarding(String templateName) {
    return this.before.containsKey(templateName)
        | this.replace.containsKey(templateName)
        | this.after.containsKey(templateName);
  }
  
  /**
   * Future inclusion of 'oldTemplate' will be replaced by 'newTemplate'. NOTE:
   * This replacement has only an effect if 'oldTemplate' is included directly.
   * 
   * @param oldTemplate qualified name of template to be replaced
   */
  public void replaceTemplate(String oldTemplate, HookPoint hp) {
    replaceTemplate(oldTemplate, Lists.newArrayList(hp));
  }
  
  /**
   * Future inclusion of 'oldTemplate' will be replaced by list of
   * 'newTemplates'. NOTE: This replacement has only an effect if 'oldTemplate'
   * is included directly.
   * 
   * @param oldTemplate qualified name of template to be replaced
   */
  public void replaceTemplate(String oldTemplate, List<? extends HookPoint> newHps) {
    Reporting.reportTemplateReplacement(oldTemplate, newHps);
    
    if (!newHps.isEmpty()) {
      // remove all previous replacements
      this.replace.removeAll(oldTemplate);
      this.replace.putAll(oldTemplate, newHps);
    }
    else {
      this.replace.removeAll(oldTemplate);
    }
  }
  
  public void replaceTemplate(String oldTemplate, ASTNode node, HookPoint newHp) {
    Reporting.reportASTSpecificTemplateReplacement(oldTemplate, node, newHp);
    
    Map<ASTNode, HookPoint> replacedTemplates = this.specificReplacement.get(oldTemplate);
    if (replacedTemplates != null && !replacedTemplates.containsKey(node)) {
      replacedTemplates.put(node, newHp);
    }
    else {
      Map<ASTNode, HookPoint> specificTemplate = Maps.newHashMap();
      specificTemplate.put(node, newHp);
      this.specificReplacement.put(oldTemplate, specificTemplate);
    }
  }
  
  /**
   * Everytime 'template' is included directly (e.g. by
   * {@link TemplateController#include(String, ASTNode)}), 'beforeTemplate' will
   * be included before it.
   * 
   * @param template qualified name of the template
   */
  public void setBeforeTemplate(String template, HookPoint beforeHp) {
    setBeforeTemplate(template, Lists.newArrayList(beforeHp));
  }
  
  /**
   * Everytime 'template' is included directly (e.g. by
   * {@link TemplateController#include(String, ASTNode)}), the templates in
   * 'beforeTemplate' will be included before it.
   * 
   * @param template qualified name of the template
   */
  public void setBeforeTemplate(String template, List<? extends HookPoint> beforeHps) {
    Reporting.reportSetBeforeTemplate(template, beforeHps);
    
    // remove all previous replacements
    this.before.removeAll(template);
    if (!beforeHps.isEmpty()) {
      this.before.putAll(template, beforeHps);
    }
  }
  
  /**
   * Everytime 'template' is included directly (e.g. by
   * {@link TemplateController#include(String, ASTNode)}), 'afterTemplate' will
   * be included after it.
   * 
   * @param template qualified name of the template
   */
  public void setAfterTemplate(String template, HookPoint afterHp) {
    setAfterTemplate(template, Lists.newArrayList(afterHp));
  }
  
  /**
   * Everytime 'template' is included directly (e.g. by
   * {@link TemplateController#include(String, ASTNode)}), the templates in
   * 'afterTemplate' will be included after it.
   * 
   * @param template qualified name of the template
   */
  public void setAfterTemplate(String template, List<? extends HookPoint> afterHps) {
    Reporting.reportSetAfterTemplate(template, afterHps);
    
    // remove all previous replacements
    this.after.removeAll(template);
    if (!afterHps.isEmpty()) {
      this.after.putAll(template, afterHps);
    }
  }
  
  private void warnIfHookPointExists(String hookName) {
    if (hookPoints.containsKey(hookName)) {
      Log.warn("0xA1036 Hook point '" + hookName + "' is already defined. It will be overwritten.");
    }
  }
}
