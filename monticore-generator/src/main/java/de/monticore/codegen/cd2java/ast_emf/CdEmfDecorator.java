/*
 * Copyright (c) 2015 RWTH Aachen. All rights reserved.
 *
 * http://www.se-rwth.de/
 */
package de.monticore.codegen.cd2java.ast_emf;

import static de.monticore.codegen.GeneratorHelper.getPlainName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.RecognitionException;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.monticore.codegen.GeneratorHelper;
import de.monticore.codegen.cd2java.ast.AstAdditionalAttributes;
import de.monticore.codegen.cd2java.ast.AstGeneratorHelper;
import de.monticore.codegen.cd2java.ast.CdDecorator;
import de.monticore.codegen.cd2java.visitor.VisitorGeneratorHelper;
import de.monticore.codegen.mc2cd.TransformationHelper;
import de.monticore.generating.templateengine.GlobalExtensionManagement;
import de.monticore.generating.templateengine.HookPoint;
import de.monticore.generating.templateengine.StringHookPoint;
import de.monticore.generating.templateengine.TemplateHookPoint;
import de.monticore.io.paths.IterablePath;
import de.monticore.symboltable.GlobalScope;
import de.monticore.types.TypesHelper;
import de.monticore.types.TypesPrinter;
import de.monticore.types.types._ast.ASTImportStatement;
import de.monticore.types.types._ast.ASTSimpleReferenceType;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDAttribute;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDClass;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDCompilationUnit;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDDefinition;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDInterface;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDMethod;
import de.monticore.umlcd4a.cd4analysis._ast.ASTCDType;
import de.monticore.umlcd4a.cd4analysis._ast.CD4AnalysisNodeFactory;
import de.monticore.umlcd4a.cd4analysis._visitor.CD4AnalysisInheritanceVisitor;
import de.monticore.umlcd4a.symboltable.CDSymbol;
import de.monticore.umlcd4a.symboltable.CDTypeSymbol;
import de.se_rwth.commons.StringTransformations;
import groovyjarjarantlr.ANTLRException;

/**
 * TODO: Write me!
 *
 * @author (last commit) $Author$
 * @version $Revision$, $Date$
 */
public class CdEmfDecorator extends CdDecorator {
  
  public static final String EFACTORY = "Factory";
  
  public static final String EFACTORY_IMPL = "FactoryImpl";
  
  public static final String EPACKAGE = "Package";
  
  public static final String EPACKAGE_IMPL = "PackageImpl";
  
  public CdEmfDecorator(
      GlobalExtensionManagement glex,
      GlobalScope symbolTable,
      IterablePath targetPath) {
    super(glex, symbolTable, targetPath);
  }
  
  public void decorate(ASTCDCompilationUnit cdCompilationUnit) {
    AstEmfGeneratorHelper astHelper = new AstEmfGeneratorHelper(cdCompilationUnit, symbolTable);
    ASTCDDefinition cdDefinition = cdCompilationUnit.getCDDefinition();
    
    List<ASTCDClass> nativeClasses = Lists.newArrayList(cdDefinition.getCDClasses());
    List<ASTCDInterface> nativeInterfaces = Lists.newArrayList(cdDefinition.getCDInterfaces());
    List<ASTCDType> types = new ArrayList<>();
    types.addAll(nativeClasses);
    types.addAll(nativeInterfaces);
    // TODO GV
    types = excludeGenTypes(types, astHelper);
    
    List<ASTCDClass> astNotAbstractClasses = cdDefinition.getCDClasses().stream()
        .filter(e -> e.getModifier().isPresent())
        .filter(e -> !e.getModifier().get().isAbstract())
        .collect(Collectors.toList());
        // .forEach(e ->
        // astNotAbstractClasses.add(GeneratorHelper.getPlainName(e)));
        
    // .forEach(e -> astNoListsClasses.add(GeneratorHelper.getPlainName(e)));
    createEmfAttributes(astHelper, nativeClasses);
    
    // Run over classdiagramm and converts cd types to mc-java types
    new Cd2JavaTypeConverter(astHelper).handle(cdDefinition);
    
    // Interface for all ast nodes of the language
    decorateBaseInterface(cdDefinition);
    
    // Decorate with builder pattern
    addBuilders(cdDefinition, astHelper);
    
    addNodeFactoryClass(cdCompilationUnit, astNotAbstractClasses, astHelper);
    
    // Check if handwritten ast types exist
    transformCdTypeNamesForHWTypes(cdCompilationUnit);
    
    cdDefinition.getCDClasses().stream()
        .forEach(c -> addSuperInterfaces(c));
        
    // Decorate with additional methods and attributes
    for (ASTCDClass clazz : nativeClasses) {
      addConstructors(clazz, astHelper);
      addAdditionalMethods(clazz, astHelper);
      addAdditionalAttributes(clazz, astHelper);
      addGetter(clazz, astHelper);
      addSetter(clazz, astHelper);
      addSymbolGetter(clazz, astHelper);
      glex.replaceTemplate("ast.AstImports", clazz, new TemplateHookPoint("ast_emf.AstEImports"));
    }
    
    for (ASTCDInterface interf : cdDefinition.getCDInterfaces()) {
      addGetter(interf);
    }
    
    // Add ASTConstant class
    addConstantsClass(cdDefinition, astHelper);
    
    // Additional imports
    cdCompilationUnit.getImportStatements().add(
        ASTImportStatement
            .getBuilder()
            .importList(
                Lists.newArrayList(VisitorGeneratorHelper.getQualifiedVisitorType(astHelper
                    .getPackageName(), cdDefinition.getName())))
            .build());
            
    addEmfCode(cdCompilationUnit, nativeClasses, types, astHelper, astHelper.getExternalTypes());
    
  }
  
  /**
   * TODO: Write me!
   * 
   * @param astHelper
   * @param astNotListClasses
   */
  void createEmfAttributes(AstEmfGeneratorHelper astHelper,
      List<ASTCDClass> astClasses) {
    // TODO GV: interfaces, enums
    for (ASTCDClass clazz : astClasses) {
      for (int i = 0; i < clazz.getCDAttributes().size(); i++) {
        ASTCDAttribute cdAttribute = clazz.getCDAttributes().get(i);
        if (Arrays.asList(AstAdditionalAttributes.values()).stream().map(a -> a.toString())
            .anyMatch(a -> a.equals(cdAttribute.getName()))) {
          continue;
        }
        astHelper.createEmfAttribute(clazz, cdAttribute);
      }
    }
    
  }
  
  /**
   * TODO: Write me!
   * 
   * @param astClasses
   * @param collection
   */
  void addEmfCode(ASTCDCompilationUnit cdCompilationUnit, List<ASTCDClass> astClasses,
      List<ASTCDType> types, AstEmfGeneratorHelper astHelper, Collection<String> collection) {
      
    addEFactoryInterface(cdCompilationUnit, types, astHelper);
    // addEFactoryImplementation(cdCompilationUnit, astClasses, astHelper);
    addEPackageInterface(cdCompilationUnit, types, collection, astHelper);
    addEPackageImplementation(cdCompilationUnit, types, collection, astHelper);
    
    // Decorate with additional EMF methods and attributes
    for (ASTCDClass clazz : astClasses) {
      addEGetter(clazz, astHelper);
      addESetter(clazz, astHelper);
      addEUnset(clazz, astHelper);
      addEIsSet(clazz, astHelper);
      // addValuesForEListAttributes(clazz, astHelper);
      addToString(clazz, astHelper);
      addEStaticClass(clazz, astHelper);
    }
    
    addResourceController(cdCompilationUnit, astClasses, astHelper);
  }
  
  /**
   * TODO: Write me!
   */
  void addEFactoryInterface(ASTCDCompilationUnit cdCompilationUnit, List<ASTCDType> astClasses,
      AstEmfGeneratorHelper astHelper)
          throws RecognitionException {
    ASTCDDefinition cdDef = cdCompilationUnit.getCDDefinition();
    ASTCDInterface factory = CD4AnalysisNodeFactory.createASTCDInterface();
    String factoryName = cdDef.getName() + EFACTORY;
    
    // Check if a handwritten node factory exists
    if (TransformationHelper.existsHandwrittenClass(targetPath,
        TransformationHelper.getAstPackageName(cdCompilationUnit)
            + factoryName)) {
      factoryName += TransformationHelper.GENERATED_CLASS_SUFFIX;
    }
    factory.setName(factoryName);
    cdDef.getCDInterfaces().add(factory);
    
    List<String> classNames = astClasses.stream().map(e -> getPlainName(e))
        .collect(Collectors.toList());
        
    glex.replaceTemplate("ast.AstInterfaceContent", factory, new TemplateHookPoint(
        "ast_emf.EFactory", factory, cdDef.getName(), "http://" + cdDef.getName()
            + "/1.0",
        classNames));
  }
  
  /**
   * TODO: Write me!
   * 
   * @param cdCompilationUnit
   * @param nativeClasses
   * @param astHelper
   * @throws ANTLRException
   */
  void addEFactoryImplementation(ASTCDCompilationUnit cdCompilationUnit,
      List<ASTCDClass> astClasses, AstEmfGeneratorHelper astHelper) throws RecognitionException {
    ASTCDDefinition cdDef = cdCompilationUnit.getCDDefinition();
    ASTCDClass factoryClass = CD4AnalysisNodeFactory.createASTCDClass();
    String factoryClassName = cdDef.getName() + EFACTORY_IMPL;
    
    // Check if a handwritten node factory exists
    if (TransformationHelper.existsHandwrittenClass(targetPath,
        TransformationHelper.getAstPackageName(cdCompilationUnit)
            + factoryClassName)) {
      factoryClassName += TransformationHelper.GENERATED_CLASS_SUFFIX;
    }
    factoryClass.setName(factoryClassName);
    
    List<String> classNames = astClasses.stream()
        .filter(e -> e.getModifier().isPresent())
        .filter(e -> !e.getModifier().get().isAbstract())
        .map(e -> getPlainName(e))
        .collect(Collectors.toList());
        
    cdDef.getCDClasses().add(factoryClass);
    glex.replaceTemplate("ast.ClassContent", factoryClass, new TemplateHookPoint(
        "ast_emf.EFactoryImpl", factoryClass, cdDef.getName(), "http://" + cdDef.getName()
            + "/1.0",
        classNames));
        
  }
  
  /**
   * TODO: Write me!
   * 
   * @param collection
   */
  void addEPackageInterface(ASTCDCompilationUnit cdCompilationUnit, List<ASTCDType> astClasses,
      Collection<String> collection, AstEmfGeneratorHelper astHelper)
          throws RecognitionException {
    ASTCDDefinition cdDef = cdCompilationUnit.getCDDefinition();
    ASTCDInterface packageInterface = CD4AnalysisNodeFactory.createASTCDInterface();
    String interfaceName = cdDef.getName() + EPACKAGE;
    
    // Check if a handwritten node factory exists
    if (TransformationHelper.existsHandwrittenClass(targetPath,
        TransformationHelper.getAstPackageName(cdCompilationUnit)
            + interfaceName)) {
      interfaceName += TransformationHelper.GENERATED_CLASS_SUFFIX;
    }
    packageInterface.setName(interfaceName);
    cdDef.getCDInterfaces().add(packageInterface);
    
    for (ASTCDType clazz : astClasses) {
      List<EmfAttribute> attributes = astHelper.getEmfAttributes(clazz);
      for (int i = 0; i < attributes.size(); i++) {
        EmfAttribute emfAttribute = attributes.get(i);
        String toParseAttr = "int " + emfAttribute.getFullName() + " = " + i + ";";
        cdTransformation.addCdAttributeUsingDefinition(packageInterface, toParseAttr);
        String toParse = emfAttribute.getEmfType() + " get" + emfAttribute.getFullName() + "();";
        cdTransformation.addCdMethodUsingDefinition(packageInterface,
            toParse);
      }
    }
    
    for (String typeName : collection) {
      int i = astClasses.size() + 1;
      String toParseAttr = "int " + typeName + " = " + i + ";";
      cdTransformation.addCdAttributeUsingDefinition(packageInterface, toParseAttr);
      String toParse = "EDataType get" + typeName + "();";
      cdTransformation.addCdMethodUsingDefinition(packageInterface,
          toParse);
      i++;
    }
    
    List<String> classNames = astClasses.stream().map(e -> getPlainName(e))
        .collect(Collectors.toList());
        
    glex.replaceTemplate("ast.AstInterfaceContent", packageInterface,
        new TemplateHookPoint(
            "ast_emf.EPackage", packageInterface, cdDef.getName(), "http://" + cdDef.getName()
                + "/1.0",
            classNames));
  }
  
  /**
   * TODO: Write me!
   * 
   * @param cdCompilationUnit
   * @param collection
   * @param nativeClasses
   * @param astHelper
   * @throws ANTLRException
   */
  void addEPackageImplementation(ASTCDCompilationUnit cdCompilationUnit,
      List<ASTCDType> astClasses, Collection<String> collection, AstEmfGeneratorHelper astHelper)
          throws RecognitionException {
    ASTCDDefinition cdDef = cdCompilationUnit.getCDDefinition();
    ASTCDClass packageImpl = CD4AnalysisNodeFactory.createASTCDClass();
    String className = cdDef.getName() + EPACKAGE_IMPL;
    
    // Check if a handwritten node factory exists
    if (TransformationHelper.existsHandwrittenClass(targetPath,
        TransformationHelper.getAstPackageName(cdCompilationUnit)
            + className)) {
      className += TransformationHelper.GENERATED_CLASS_SUFFIX;
    }
    packageImpl.setName(className);
    
    List<String> classNames = astClasses.stream().map(e -> getPlainName(e))
        .collect(Collectors.toList());
        
    for (String clazz : classNames) {
      String toParse = "protected static " + className + " factory" + clazz + " = null;";
      cdTransformation.addCdAttributeUsingDefinition(packageImpl, toParse);
    }
    
    for (int i = 0; i < astHelper.getAllEmfAttributes().size(); i++) {
      EmfAttribute emfAttribute = astHelper.getAllEmfAttributes().get(i);
      
      // TODO GV: replace StringHookPoint by TemplaeHookPoint
      String toParse = "public " + emfAttribute.getEmfType() + " get" + emfAttribute.getFullName()
          + "();";
      HookPoint getMethodBody = new StringHookPoint("return (" + emfAttribute.getEmfType() + ")"
          + StringTransformations.uncapitalize(getPlainName(emfAttribute.getCdType()).substring(3))
          + "EClass.getEStructuralFeatures().get(" + emfAttribute.getFullName() + ");");
      replaceMethodBodyTemplate(packageImpl, toParse, getMethodBody);
      
    }
    
    for (String typeName : collection) {
      String toParse = "public EDataType get" + typeName + "();";
      HookPoint getMethodBody = new StringHookPoint(
          "return " + StringTransformations.uncapitalize(typeName) + ";");
      replaceMethodBodyTemplate(packageImpl, toParse, getMethodBody);
    }
    
    String toParse = "public void createPackageContents();";
    HookPoint getMethodBody = new TemplateHookPoint(
        "ast_emf.epackagemethods.CreatePackageContents", cdDef.getName(), astClasses,
        astHelper.getAllEmfAttributes());
    replaceMethodBodyTemplate(packageImpl, toParse, getMethodBody);
    
    List<String> superCDs = astHelper.getAllSuperCds(astHelper.getCdSymbol()).stream()
        .map(CDSymbol::getFullName).collect(Collectors.toList());
    toParse = "public void initializePackageContents();";
    getMethodBody = new TemplateHookPoint(
        "ast_emf.epackagemethods.InitializePackageContents", cdDef.getName(),
        superCDs, astClasses,
        astHelper.getAllEmfAttributes());
    replaceMethodBodyTemplate(packageImpl, toParse, getMethodBody);
    
    cdDef.getCDClasses().add(packageImpl);
    
    glex.replaceTemplate("ast.ClassContent", packageImpl, new TemplateHookPoint(
        "ast_emf.EPackageImpl", packageImpl, cdDef.getName(), classNames, collection));
  }
  
  protected void addSetter(ASTCDClass clazz, AstEmfGeneratorHelper astHelper)
      throws RecognitionException {
    for (EmfAttribute attribute : astHelper.getEmfAttributes(clazz)) {
      ASTCDAttribute cdAttribute = attribute.getCdAttribute();
      if (GeneratorHelper.isInherited(cdAttribute)) {
        continue;
      }
      String attributeName = cdAttribute.getName();
      boolean isOptional = GeneratorHelper.isOptional(cdAttribute);
      String typeName = TypesHelper.printSimpleRefType(cdAttribute.getType());
      String toParse = "public void " + GeneratorHelper.getPlainSetter(cdAttribute) + "("
          + typeName + " " + attributeName + ") ;";
      HookPoint methodBody = new TemplateHookPoint("ast_emf.additionalmethods.Set",
          astHelper.getCdName(),
          attribute, attributeName);
      ASTCDMethod setMethod = replaceMethodBodyTemplate(clazz, toParse, methodBody);
      
      if (isOptional) {
        glex.replaceTemplate("ast.ErrorIfNull", setMethod, new StringHookPoint(""));
      }
      
      if (isOptional) {
        toParse = "public boolean " + attributeName + "IsPresent() ;";
        methodBody = new StringHookPoint("  return " + attributeName + ".isPresent(); \n");
        replaceMethodBodyTemplate(clazz, toParse, methodBody);
      }
    }
  }
  
  /**
   * Adds getter for all attributes of ast classes
   * 
   * @param clazz
   * @param astHelper
   * @throws ANTLRException
   */
  void addEGetter(ASTCDClass clazz, AstEmfGeneratorHelper astHelper)
      throws RecognitionException {
    String toParse = "public Object eGet(int featureID, boolean resolve, boolean coreType);";
    HookPoint getMethodBody = new TemplateHookPoint("ast_emf.additionalmethods.EGet",
        astHelper.getCdName(), astHelper.getEmfAttributes(clazz));
    replaceMethodBodyTemplate(clazz, toParse, getMethodBody);
  }
  
  /**
   * Adds setter for all attributes of ast classes
   * 
   * @param clazz
   * @param astHelper
   * @throws ANTLRException
   */
  void addESetter(ASTCDClass clazz, AstEmfGeneratorHelper astHelper)
      throws RecognitionException {
    String toParse = "public void eSet(int featureID, Object newValue);";
    HookPoint getMethodBody = new TemplateHookPoint("ast_emf.additionalmethods.ESet",
        astHelper.getCdName(), astHelper.getEmfAttributes(clazz));
    Optional<ASTCDMethod> astMethod = cdTransformation.addCdMethodUsingDefinition(clazz,
        toParse);
    Preconditions.checkArgument(astMethod.isPresent());
    glex.replaceTemplate(EMPTY_BODY_TEMPLATE, astMethod.get(), getMethodBody);
    glex.replaceTemplate("ast.ErrorIfNull", astMethod.get(), new StringHookPoint(""));
  }
  
  /**
   * TODO: Write me!
   * 
   * @param clazz
   * @param astHelper
   */
  @Override
  protected void addAdditionalAttributes(ASTCDClass clazz, AstGeneratorHelper astHelper) {
    // Add Symbol attribute
    Optional<ASTCDAttribute> attribute = cdTransformation.addCdAttributeUsingDefinition(clazz,
        AstAdditionalAttributes.symbol.getDeclaration());
    addSetterForAdditionalAttribute(clazz, attribute.get(), "Symbol",
        AstAdditionalAttributes.symbol.toString(), true);
    // Add Scope attribute
    attribute = cdTransformation.addCdAttributeUsingDefinition(clazz,
        AstAdditionalAttributes.enclosingScope.getDeclaration());
    addSetterForAdditionalAttribute(clazz, attribute.get(), "Scope",
        AstAdditionalAttributes.enclosingScope.toString(), true);
  }
  
  private void addSetterForAdditionalAttribute(ASTCDClass clazz, ASTCDAttribute attribute,
      String typeName, String attributeName, boolean isOptional) {
    String toParse = "public void set" + StringTransformations.capitalize(attributeName) + "("
        + typeName + " " + attributeName + ") ;";
    HookPoint methodBody = new TemplateHookPoint("ast.additionalmethods.Set", clazz,
        attribute, attributeName);
    ASTCDMethod setMethod = replaceMethodBodyTemplate(clazz, toParse, methodBody);
    
    if (isOptional) {
      glex.replaceTemplate("ast.ErrorIfNull", setMethod, new StringHookPoint(""));
    }
    
    if (isOptional) {
      toParse = "public boolean " + attributeName + "IsPresent() ;";
      methodBody = new StringHookPoint("  return " + attributeName + ".isPresent(); \n");
      replaceMethodBodyTemplate(clazz, toParse, methodBody);
    }
  }
  
  /**
   * Adds setter for all attributes of ast classes
   * 
   * @param clazz
   * @param astHelper
   * @throws ANTLRException
   */
  void addEUnset(ASTCDClass clazz, AstEmfGeneratorHelper astHelper)
      throws RecognitionException {
    String toParse = "public void eUnset(int featureID);";
    HookPoint getMethodBody = new TemplateHookPoint("ast_emf.additionalmethods.EUnset",
        astHelper.getCdName(), astHelper.getEmfAttributes(clazz));
    replaceMethodBodyTemplate(clazz, toParse, getMethodBody);
  }
  
  /**
   * Adds setter for all attributes of ast classes
   * 
   * @param clazz
   * @param astHelper
   * @throws ANTLRException
   */
  void addEIsSet(ASTCDClass clazz, AstEmfGeneratorHelper astHelper)
      throws RecognitionException {
    String toParse = "public boolean eIsSet(int featureID);";
    HookPoint getMethodBody = new TemplateHookPoint("ast_emf.additionalmethods.EIsSet",
        astHelper.getCdName(), astHelper.getEmfAttributes(clazz));
    replaceMethodBodyTemplate(clazz, toParse, getMethodBody);
  }
  
  /**
   * TODO: Write me!
   * 
   * @param clazz
   * @param astHelper
   */
  void addToString(ASTCDClass clazz, AstEmfGeneratorHelper astHelper) {
    if (clazz.getCDMethods().stream().anyMatch(m -> m.getName().equals("toString"))) {
      return;
    }
    String toParse = "public String toString();";
    HookPoint getMethodBody = new TemplateHookPoint("ast_emf.additionalmethods.EToString",
        astHelper.getCdName(), astHelper.getEmfAttributes(clazz));
    replaceMethodBodyTemplate(clazz, toParse, getMethodBody);
  }
  
  /**
   * TODO: Write me!
   * 
   * @param clazz
   * @param astHelper
   */
  void addEStaticClass(ASTCDClass clazz, AstEmfGeneratorHelper astHelper) {
    String toParse = "protected EClass eStaticClass();";
    HookPoint getMethodBody = new StringHookPoint("return " + astHelper.getCdName()
        + "Package.Literals." + GeneratorHelper.getPlainName(clazz) + ";");
    replaceMethodBodyTemplate(clazz, toParse, getMethodBody);
  }
  
  /**
   * TODO: Write me!
   * 
   * @param types
   * @param astHelper
   */
  List<ASTCDType> excludeGenTypes(List<ASTCDType> types, AstEmfGeneratorHelper astHelper) {
    return types.stream().filter(c -> !c.getName().equals("AST" + astHelper.getCdName() + "Node"))
        .collect(Collectors.toList());
  }
  
  /**
   * TODO: Write me!
   * 
   * @return
   */
  // TODO GV: not used now
  protected void addAdditionalCreateMethods(ASTCDClass nodeFactoryClass, ASTCDClass clazz) {
    String className = GeneratorHelper.getPlainName(clazz);
    String params = "owner, featureID";
    String toParse = "public static " + className + " create" + className
        + "(InternalEObject owner, int featureID) ;";
    HookPoint methodBody = new TemplateHookPoint("ast.factorymethods.Create", clazz, className,
        params);
    replaceMethodBodyTemplate(nodeFactoryClass, toParse, methodBody);
    
    toParse = "protected " + className + " doCreate" + className
        + "(InternalEObject owner, int featureID) ;";
    methodBody = new TemplateHookPoint("ast.factorymethods.DoCreate", clazz, className, params);
    replaceMethodBodyTemplate(nodeFactoryClass, toParse, methodBody);
  }
  
  /**
   * TODO: Write me!
   * 
   * @param cdCompilationUnit
   * @param nativeClasses
   * @param astHelper
   * @throws ANTLRException
   */
  void addResourceController(ASTCDCompilationUnit cdCompilationUnit,
      List<ASTCDClass> astClasses, AstEmfGeneratorHelper astHelper) throws RecognitionException {
    ASTCDDefinition cdDef = cdCompilationUnit.getCDDefinition();
    ASTCDClass resourceControllerClass = CD4AnalysisNodeFactory.createASTCDClass();
    String className = cdDef.getName() + "ResourceController";
    
    // Check if a handwritten node factory exists
    if (TransformationHelper.existsHandwrittenClass(targetPath,
        TransformationHelper.getAstPackageName(cdCompilationUnit)
            + className)) {
      className += TransformationHelper.GENERATED_CLASS_SUFFIX;
    }
    resourceControllerClass.setName(className);
    
    List<String> classNames = astClasses.stream().map(e -> getPlainName(e))
        .collect(Collectors.toList());
        
    cdDef.getCDClasses().add(resourceControllerClass);
    glex.replaceTemplate("ast.ClassContent", resourceControllerClass,
        new TemplateHookPoint(
            "ast_emf.ResourceController", resourceControllerClass, cdDef.getName(), "http://"
                + cdDef.getName()
                + "/1.0",
            classNames));
            
  }
  
}
